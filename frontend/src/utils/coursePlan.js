export function normalizeCoursePlanAnalysis(analysis, teacherRequirementText) {
  if (!analysis) return null
  const base = JSON.parse(JSON.stringify(analysis))
  const pptMap = new Map((base.sourceContext?.pptMaterials || []).map((item) => [item.fileName, item]))
  const units = (base.units || []).map((unit) => {
    const hours = Number(unit.hours || 0)
    const teachingDesignCount = hours > 0 && hours % 2 === 0 ? hours / 2 : 0
    const matchedPptFiles = Array.isArray(unit.matchedPptFiles) ? unit.matchedPptFiles.filter(Boolean) : []
    const matchedPptTitles = matchedPptFiles
      .map((fileName) => pptMap.get(fileName))
      .filter(Boolean)
      .map((item) => item.title || item.fileName)
    const slideHeadings = matchedPptFiles
      .map((fileName) => pptMap.get(fileName))
      .filter(Boolean)
      .flatMap((item) => item.headings || [])
    const localIssues = []
    if (!unit.name) localIssues.push(issue('unit.nameMissing', 'error', '存在未填写单元名称。'))
    if (!hours) localIssues.push(issue('unit.hoursMissing', 'error', `单元“${unit.name || unit.code}”未填写有效学时。`))
    if (hours && hours % 2 !== 0) localIssues.push(issue('unit.hoursNotDivisible', 'error', `单元“${unit.name || unit.code}”学时为 ${hours}，无法按 2 学时拆分。`))
    if (!matchedPptFiles.length) localIssues.push(issue('unit.pptMissing', 'error', `单元“${unit.name || unit.code}”未匹配到任何 PPT/课件。`))
    const issues = [...(unit.issues || []), ...localIssues].filter((item, index, source) => {
      const key = `${item.code}-${item.message}`
      return source.findIndex((current) => `${current.code}-${current.message}` === key) === index
    })
    return {
      ...unit,
      hours,
      teachingDesignCount,
      matchedPptFiles,
      matchedPptTitles,
      slideHeadings: Array.from(new Set(slideHeadings)),
      status: issues.some((item) => item.level === 'error') ? 'blocked' : 'ready',
      issues,
    }
  })
  const conflicts = [...(base.conflicts || [])]
  if (!base.templateCheck?.valid) {
    conflicts.push(...(base.templateCheck?.issues || []))
  }
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
  const summedHours = units.reduce((sum, unit) => sum + Number(unit.hours || 0), 0)
  if (base.basicInfo?.totalHours && summedHours && Number(base.basicInfo.totalHours) !== summedHours) {
    conflicts.push(issue('hours.totalMismatch', 'error', `单元学时总和为 ${summedHours}，与课程总学时 ${base.basicInfo.totalHours} 不一致。`))
  }
  conflicts.push(...units.flatMap((unit) => unit.issues))
  const dedupedConflicts = conflicts.filter((item, index, source) => {
    const key = `${item.code}-${item.message}`
    return source.findIndex((current) => `${current.code}-${current.message}` === key) === index
  })
  return {
    ...base,
    teacherRequirements: teacherRequirementText,
    units,
    conflicts: dedupedConflicts,
    valid: !dedupedConflicts.some((item) => item.level === 'error'),
  }
}

export function cloneCoursePlan(value) {
  return value == null ? null : JSON.parse(JSON.stringify(value))
}

function hasDepartmentName(value) {
  const text = String(value || '').trim()
  const collegeIndex = text.lastIndexOf('学院')
  if (collegeIndex >= 0 && text.endsWith('系') && collegeIndex + 2 < text.length) {
    return true
  }
  return text.endsWith('系')
}

function issue(code, level, message) {
  return { code, level, message }
}
