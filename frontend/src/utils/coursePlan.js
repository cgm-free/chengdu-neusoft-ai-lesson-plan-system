const STRATEGY_FIXED_TWO_HOURS = 'FIXED_TWO_HOURS'
const STRATEGY_FLEXIBLE_HOURS = 'FLEXIBLE_HOURS'
const SCHEDULE_ISSUE_CODES = new Set([
  'unit.hoursMissing',
  'unit.hoursNotDivisible',
  'unit.splitPlanInvalid',
  'unit.calendarHoursMismatch',
  'teachingCalendar.countMismatch',
  'teachingCalendar.hoursMismatch',
])

export function normalizeCoursePlanAnalysis(analysis, teacherRequirementText) {
  if (!analysis) return null
  const base = JSON.parse(JSON.stringify(analysis))
  const pptMap = new Map((base.sourceContext?.pptMaterials || []).map((item) => [item.fileName, item]))
  const calendarEntries = (base.sourceContext?.teachingCalendar?.entries || []).filter((entry) => entry?.topic)
  const splitStrategy = determineSplitStrategy(base.units || [], calendarEntries)
  const plannedUnits = planUnits(base.units || [], calendarEntries, splitStrategy)

  const units = plannedUnits.map((plannedUnit) => {
    const matchedPptFiles = Array.isArray(plannedUnit.matchedPptFiles) ? plannedUnit.matchedPptFiles.filter(Boolean) : []
    const matchedPptTitles = matchedPptFiles
      .map((fileName) => pptMap.get(fileName))
      .filter(Boolean)
      .map((item) => item.title || item.fileName)
    const slideHeadings = matchedPptFiles
      .map((fileName) => pptMap.get(fileName))
      .filter(Boolean)
      .flatMap((item) => item.headings || [])
    const issues = dedupeIssues([
      ...filterBaseIssues(plannedUnit.issues || []),
      ...buildLocalUnitIssues(plannedUnit, splitStrategy, matchedPptFiles),
    ])
    return {
      ...plannedUnit,
      matchedPptFiles,
      matchedPptTitles,
      slideHeadings: Array.from(new Set(slideHeadings)),
      status: issues.some((item) => item.level === 'error') ? 'blocked' : 'ready',
      issues,
    }
  })

  const conflicts = [
    ...(base.templateCheck?.valid ? [] : (base.templateCheck?.issues || [])),
    ...buildGlobalIssues(base, units, calendarEntries, splitStrategy),
    ...units.flatMap((unit) => unit.issues || []),
  ]

  return {
    ...base,
    teacherRequirements: teacherRequirementText,
    splitStrategy,
    units,
    conflicts: dedupeIssues(conflicts),
    valid: !dedupeIssues(conflicts).some((item) => item.level === 'error'),
  }
}

export function cloneCoursePlan(value) {
  return value == null ? null : JSON.parse(JSON.stringify(value))
}

function determineSplitStrategy(units, calendarEntries) {
  const allEven = (units || [])
    .map((unit) => Number(unit.hours || 0))
    .filter((hours) => hours > 0)
    .every((hours) => hours % 2 === 0)
  const dominant = dominantPeriodCount(calendarEntries)
  if (allEven && (!dominant || dominant === 2)) {
    return STRATEGY_FIXED_TWO_HOURS
  }
  return STRATEGY_FLEXIBLE_HOURS
}

function planUnits(units, calendarEntries, splitStrategy) {
  return splitStrategy === STRATEGY_FIXED_TWO_HOURS
    ? planFixedUnits(units, calendarEntries)
    : planFlexibleUnits(units, calendarEntries)
}

function planFixedUnits(units, calendarEntries) {
  let cursor = 0
  return (units || []).map((unit) => {
    const hours = Number(unit.hours || 0)
    const teachingDesignHours = hours > 0 && hours % 2 === 0 ? Array.from({ length: hours / 2 }, () => 2) : []
    const teachingCalendarEntries = calendarEntries.slice(cursor, cursor + teachingDesignHours.length)
    cursor += teachingDesignHours.length
    return {
      ...unit,
      hours,
      teachingDesignCount: teachingDesignHours.length,
      teachingDesignHours,
      teachingCalendarEntries,
      weekRange: buildWeekRange(teachingCalendarEntries),
    }
  })
}

function planFlexibleUnits(units, calendarEntries) {
  const preferredHours = dominantPeriodCount(calendarEntries) || 3
  const coverages = buildCalendarCoverages(units, calendarEntries, preferredHours)
  return (units || []).map((unit, index) => {
    const hours = Number(unit.hours || 0)
    const coverage = coverages[index] || { teachingCalendarEntries: [], weekRange: '', allocatedHours: 0 }
    const teachingDesignHours = smartSplitHours(hours, preferredHours)
    return {
      ...unit,
      hours,
      teachingDesignCount: teachingDesignHours.length,
      teachingDesignHours,
      teachingCalendarEntries: coverage.teachingCalendarEntries,
      weekRange: coverage.weekRange,
      allocatedHours: coverage.allocatedHours,
    }
  })
}

function buildCalendarCoverages(units, calendarEntries, preferredHours) {
  if (!calendarEntries.length) return (units || []).map(() => ({ teachingCalendarEntries: [], weekRange: '', allocatedHours: 0 }))
  const coverages = []
  let cursor = 0
  let remainingHours = periodCountOrDefault(calendarEntries[0], preferredHours)
  ;(units || []).forEach((unit) => {
    const requiredHours = Number(unit.hours || 0)
    if (!requiredHours) {
      coverages.push({ teachingCalendarEntries: [], weekRange: '', allocatedHours: 0 })
      return
    }
    let remainingUnitHours = requiredHours
    const teachingCalendarEntries = []
    let allocatedHours = 0
    while (remainingUnitHours > 0 && cursor < calendarEntries.length) {
      const entry = calendarEntries[cursor]
      const currentEntryHours = periodCountOrDefault(entry, preferredHours)
      if (remainingHours <= 0) remainingHours = currentEntryHours
      const allocated = Math.min(remainingUnitHours, remainingHours)
      if (allocated <= 0) {
        cursor += 1
        if (cursor < calendarEntries.length) {
          remainingHours = periodCountOrDefault(calendarEntries[cursor], preferredHours)
        }
        continue
      }
      teachingCalendarEntries.push({ ...entry, allocatedHours: allocated })
      remainingUnitHours -= allocated
      allocatedHours += allocated
      remainingHours -= allocated
      if (remainingHours <= 0) {
        cursor += 1
        if (cursor < calendarEntries.length) {
          remainingHours = periodCountOrDefault(calendarEntries[cursor], preferredHours)
        }
      }
    }
    coverages.push({
      teachingCalendarEntries,
      weekRange: buildWeekRange(teachingCalendarEntries),
      allocatedHours,
    })
  })
  return coverages
}

function smartSplitHours(hours, preferredHours) {
  if (!hours || hours <= 0) return []
  const candidates = Array.from(new Set([preferredHours, 3, 2, 1])).filter((value) => value > 0)
  return chooseSplit(hours, candidates, []).parts
}

function chooseSplit(remainingHours, candidates, current) {
  if (remainingHours === 0) return { parts: [...current] }
  let best = null
  candidates.forEach((candidate) => {
    if (candidate > remainingHours) return
    current.push(candidate)
    const choice = chooseSplit(remainingHours - candidate, candidates, current)
    current.pop()
    if (!choice) return
    if (!best || betterSplit(choice.parts, best.parts, candidates[0])) {
      best = choice
    }
  })
  return best || { parts: [remainingHours] }
}

function betterSplit(current, other, preferredHours) {
  if (current.length !== other.length) return current.length < other.length
  const currentOnes = current.filter((value) => value === 1).length
  const otherOnes = other.filter((value) => value === 1).length
  if (currentOnes !== otherOnes) return currentOnes < otherOnes
  const currentPreferred = current.filter((value) => value === preferredHours).length
  const otherPreferred = other.filter((value) => value === preferredHours).length
  if (currentPreferred !== otherPreferred) return currentPreferred > otherPreferred
  return current.join('-') < other.join('-')
}

function buildLocalUnitIssues(unit, splitStrategy, matchedPptFiles) {
  const issues = []
  if (!unit.name) issues.push(issue('unit.nameMissing', 'error', '存在未填写单元名称。'))
  if (!unit.hours) issues.push(issue('unit.hoursMissing', 'error', `单元“${unit.name || unit.code}”未填写有效学时。`))
  if (splitStrategy === STRATEGY_FIXED_TWO_HOURS && unit.hours && unit.hours % 2 !== 0) {
    issues.push(issue('unit.hoursNotDivisible', 'error', `单元“${unit.name || unit.code}”学时为 ${unit.hours}，无法按 2 学时拆分。`))
  }
  if (unit.hours && sum(unit.teachingDesignHours) !== unit.hours) {
    issues.push(issue('unit.splitPlanInvalid', 'error', `单元“${unit.name || unit.code}”的教学设计学时分配与单元学时不一致。`))
  }
  if (!matchedPptFiles.length) issues.push(issue('unit.pptMissing', 'error', `单元“${unit.name || unit.code}”未匹配到任何 PPT/课件。`))
  if (splitStrategy === STRATEGY_FLEXIBLE_HOURS && Array.isArray(unit.teachingCalendarEntries) && unit.teachingCalendarEntries.length) {
    const allocatedHours = sum(unit.teachingCalendarEntries.map((entry) => Number(entry.allocatedHours || entry.periodCount || 0)))
    if (unit.hours && allocatedHours && allocatedHours !== unit.hours) {
      issues.push(issue('unit.calendarHoursMismatch', 'error', `单元“${unit.name || unit.code}”从教学日历分配到 ${allocatedHours} 学时，与单元学时 ${unit.hours} 不一致。`))
    }
  }
  return issues
}

function buildGlobalIssues(base, units, calendarEntries, splitStrategy) {
  const conflicts = (base.conflicts || []).filter((item) => !SCHEDULE_ISSUE_CODES.has(item.code))
  if (!base.basicInfo?.courseName) {
    conflicts.push(issue('basic.courseNameMissing', 'error', '课程名称为空，请先确认课程基本信息。'))
  }
  if (!hasDepartmentName(base.basicInfo?.department)) {
    conflicts.push(issue('basic.departmentMissing', 'error', '课程标准中未识别到明确的系/部信息。'))
  }
  if (!String(base.basicInfo?.department || '').includes('学院')) {
    conflicts.push(issue('basic.collegeMissing', 'error', '课程标准中未识别到明确的学院信息。'))
  }
  if (!base.basicInfo?.totalHours) {
    conflicts.push(issue('basic.totalHoursMissing', 'error', '总学时为空，请先确认课程基本信息。'))
  }
  const summedHours = units.reduce((total, unit) => total + Number(unit.hours || 0), 0)
  if (base.basicInfo?.totalHours && summedHours && Number(base.basicInfo.totalHours) !== summedHours) {
    conflicts.push(issue('hours.totalMismatch', 'error', `单元学时总和为 ${summedHours}，与课程总学时 ${base.basicInfo.totalHours} 不一致。`))
  }
  if (splitStrategy === STRATEGY_FIXED_TWO_HOURS) {
    const expected = units.reduce((total, unit) => total + Number(unit.teachingDesignCount || 0), 0)
    if (expected && calendarEntries.length && expected !== calendarEntries.length) {
      conflicts.push(issue('teachingCalendar.countMismatch', 'error', `教学日历识别到 ${calendarEntries.length} 次课，与课程标准按 2 学时拆分得到的教学设计数 ${expected} 不一致。`))
    }
  } else if (base.basicInfo?.totalHours && calendarEntries.length) {
    const calendarHours = sum(calendarEntries.map((entry) => Number(entry.periodCount || entry.allocatedHours || 0)))
    if (calendarHours && Number(base.basicInfo.totalHours) !== calendarHours) {
      conflicts.push(issue('teachingCalendar.hoursMismatch', 'error', `教学日历累计学时为 ${calendarHours}，与课程总学时 ${base.basicInfo.totalHours} 不一致。`))
    }
  }
  return conflicts
}

function buildWeekRange(entries) {
  const weeks = Array.from(new Set((entries || []).map((entry) => String(entry.week || '').trim()).filter(Boolean)))
  if (!weeks.length) return ''
  if (weeks.length === 1) return weeks[0]
  return `${weeks[0]}-${weeks[weeks.length - 1]}`
}

function dominantPeriodCount(entries) {
  const counter = new Map()
  ;(entries || []).forEach((entry) => {
    const hours = Number(entry.periodCount || 0)
    if (!hours) return
    counter.set(hours, (counter.get(hours) || 0) + 1)
  })
  return Array.from(counter.entries())
    .sort((left, right) => right[1] - left[1] || right[0] - left[0])[0]?.[0] || 0
}

function periodCountOrDefault(entry, fallback) {
  return Number(entry?.periodCount || entry?.allocatedHours || fallback || 2)
}

function sum(values) {
  return (values || []).reduce((total, value) => total + Number(value || 0), 0)
}

function hasDepartmentName(value) {
  const text = String(value || '').trim()
  const collegeIndex = text.lastIndexOf('学院')
  if (collegeIndex >= 0 && text.endsWith('系') && collegeIndex + 2 < text.length) {
    return true
  }
  return text.endsWith('系')
}

function filterBaseIssues(issues) {
  return (issues || []).filter((item) => !SCHEDULE_ISSUE_CODES.has(item.code))
}

function dedupeIssues(items) {
  return (items || []).filter((item, index, source) => {
    const key = `${item.code}-${item.message}`
    return source.findIndex((current) => `${current.code}-${current.message}` === key) === index
  })
}

function issue(code, level, message) {
  return { code, level, message }
}
