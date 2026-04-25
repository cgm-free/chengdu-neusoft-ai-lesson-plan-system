export function parseCoursePlanGenerationError(error, fallbackMessage) {
  const response = error?.response?.data
  const detail = response?.data
  if (!detail || typeof detail !== 'object') {
    return {
      toast: response?.message || error?.message || fallbackMessage,
      detail: null,
    }
  }

  const position = [
    detail.unitIndex ? `第${detail.unitIndex}单元` : '',
    detail.lessonIndex ? `第${detail.lessonIndex}次课` : '',
  ].filter(Boolean).join(' ')
  const sectionLabel = generationSectionLabel(detail.section)
  const shortMessage = position
    ? `${position}${sectionLabel}生成失败，请查看详情`
    : `课程教案${sectionLabel}生成失败，请查看详情`

  return {
    toast: shortMessage,
    detail: {
      unitIndex: detail.unitIndex,
      lessonIndex: detail.lessonIndex,
      lessonTitle: detail.lessonTitle || '',
      section: detail.section || '',
      blockTitle: detail.blockTitle || '',
      actualChars: detail.actualChars,
      requiredChars: detail.requiredChars,
      message: detail.message || response?.message || fallbackMessage,
    },
  }
}

export function parseCoursePlanGenerationJobError(job, fallbackMessage) {
  const detail = job?.error
  if (!detail || typeof detail !== 'object') {
    return {
      toast: job?.message || fallbackMessage,
      detail: {
        message: job?.message || fallbackMessage,
      },
    }
  }

  const position = [
    detail.unitIndex ? `第${detail.unitIndex}单元` : '',
    detail.lessonIndex ? `第${detail.lessonIndex}次课` : '',
  ].filter(Boolean).join(' ')
  const sectionLabel = generationSectionLabel(detail.section)
  const shortMessage = position
    ? `${position}${sectionLabel}生成失败，请查看详情`
    : `课程教案${sectionLabel}生成失败，请查看详情`

  return {
    toast: shortMessage,
    detail: {
      unitIndex: detail.unitIndex,
      lessonIndex: detail.lessonIndex,
      lessonTitle: detail.lessonTitle || '',
      section: detail.section || '',
      blockTitle: detail.blockTitle || '',
      actualChars: detail.actualChars,
      requiredChars: detail.requiredChars,
      message: detail.message || job?.message || fallbackMessage,
    },
  }
}

function generationSectionLabel(section) {
  if (section === 'assignments') return '课外学习要求'
  if (section === 'mainContent') return '主要内容'
  if (section === 'mainContentOutline') return '主要内容大纲'
  if (section === 'teachingDesign') return '教学设计'
  return '生成内容'
}
