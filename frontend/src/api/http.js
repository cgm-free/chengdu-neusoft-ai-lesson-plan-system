import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 130000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('nsu_maic_token')
  if (token) {
    config.headers['X-Auth-Token'] = token
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('nsu_maic_token')
      localStorage.removeItem('nsu_maic_user_role')
      window.dispatchEvent(new Event('nsu-auth-expired'))
    }
    return Promise.reject(error)
  },
)

export async function login(payload) {
  const { data } = await http.post('/auth/login', payload)
  return data.data
}

export async function logout() {
  const { data } = await http.post('/auth/logout')
  return data.data
}

export async function getCurrentUser() {
  const { data } = await http.get('/auth/me')
  return data.data
}

export async function submitAccountRequest(payload) {
  const { data } = await http.post('/account-requests', payload)
  return data.data
}

export async function getAccountRequests(status = '') {
  const params = status ? { status } : undefined
  const { data } = await http.get('/admin/account-requests', { params })
  return data.data
}

export async function approveAccountRequest(id, payload = {}) {
  const { data } = await http.post(`/admin/account-requests/${id}/approve`, payload)
  return data.data
}

export async function rejectAccountRequest(id, payload = {}) {
  const { data } = await http.post(`/admin/account-requests/${id}/reject`, payload)
  return data.data
}

export async function getAdminUsers() {
  const { data } = await http.get('/admin/users')
  return data.data
}

export async function createAdminUser(payload) {
  const { data } = await http.post('/admin/users', payload)
  return data.data
}

export async function updateAdminUser(id, payload) {
  const { data } = await http.put(`/admin/users/${id}`, payload)
  return data.data
}

export async function resetAdminUserPassword(id, payload) {
  const { data } = await http.patch(`/admin/users/${id}/password`, payload)
  return data.data
}

export async function updateAdminUserEnabled(id, payload) {
  const { data } = await http.patch(`/admin/users/${id}/enabled`, payload)
  return data.data
}

export async function disableAdminUser(id) {
  const { data } = await http.delete(`/admin/users/${id}`)
  return data.data
}

export async function deleteAdminUserPermanently(id) {
  const { data } = await http.delete(`/admin/users/${id}/permanent`)
  return data.data
}

export async function getOptions() {
  const { data } = await http.get('/config/options')
  return data.data
}

export async function getLessonPlans() {
  const { data } = await http.get('/lesson-plans')
  return data.data
}

export async function getCoursePlans() {
  const { data } = await http.get('/course-plans')
  return data.data
}

export async function createLessonPlan(payload) {
  const { data } = await http.post('/lesson-plans', payload)
  return data.data
}

export async function generateLessonPlan(payload) {
  const { data } = await http.post('/lesson-plans/generate', payload, {
    timeout: 240000,
  })
  return data.data
}

export async function getLessonPlanDetail(id) {
  const { data } = await http.get(`/lesson-plans/${id}`)
  return data.data
}

export async function getGenerationRecords() {
  const { data } = await http.get('/generation-records')
  return data.data
}

export async function getGenerationRecordDetail(id) {
  const { data } = await http.get(`/generation-records/${id}`)
  return data.data
}

export async function optimizeText(payload) {
  const { data } = await http.post('/ai/optimize-text', payload)
  return data.data
}

export async function extractResource(file) {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post('/ai/extract-resource', formData, {
    timeout: 480000,
  })
  return data.data
}

export async function parseTeachingCalendar(file) {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post('/ai/parse-teaching-calendar', formData, {
    timeout: 180000,
  })
  return data.data
}

export async function saveLessonPlan(id, payload) {
  const { data } = await http.put(`/lesson-plans/${id}`, payload)
  return data.data
}

export async function deleteLessonPlan(id) {
  const { data } = await http.delete(`/lesson-plans/${id}`)
  return data.data
}

export async function exportLessonPlanWord(plan) {
  const token = localStorage.getItem('nsu_maic_token')
  const query = token ? `?token=${encodeURIComponent(token)}` : ''
  const url = `/api/lesson-plans/${plan.id}/export-word${query}`
  const link = document.createElement('a')
  link.href = url
  document.body.appendChild(link)
  link.click()
  link.remove()
}

export async function analyzeCoursePlan({ template = null, courseStandard, ppts = [], references = [], teacherRequirements }) {
  const formData = new FormData()
  if (template) {
    formData.append('template', template)
  }
  formData.append('courseStandard', courseStandard)
  ppts.forEach((file) => formData.append('ppts', file))
  references.forEach((file) => formData.append('references', file))
  formData.append('teacherRequirements', teacherRequirements || '')
  const { data } = await http.post('/course-plans/analyze', formData, {
    timeout: 240000,
  })
  return data.data
}

export async function analyzeExistingCoursePlan(id, { template = null, courseStandard, ppts = [], references = [], teacherRequirements }) {
  const formData = new FormData()
  if (template) {
    formData.append('template', template)
  }
  if (courseStandard) {
    formData.append('courseStandard', courseStandard)
  }
  ppts.forEach((file) => formData.append('ppts', file))
  references.forEach((file) => formData.append('references', file))
  formData.append('teacherRequirements', teacherRequirements || '')
  const { data } = await http.post(`/course-plans/${id}/analyze`, formData, {
    timeout: 240000,
  })
  return data.data
}

export async function generateCoursePlan({ template = null, courseStandard, ppts = [], references = [], payload }) {
  const formData = new FormData()
  if (template) {
    formData.append('template', template)
  }
  formData.append('courseStandard', courseStandard)
  ppts.forEach((file) => formData.append('ppts', file))
  references.forEach((file) => formData.append('references', file))
  formData.append('payload', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  const { data } = await http.post('/course-plans/generate', formData, {
    timeout: 1800000,
  })
  return data.data
}

export async function regenerateCoursePlan(id, { template = null, courseStandard, ppts = [], references = [], payload }) {
  const formData = new FormData()
  if (template) {
    formData.append('template', template)
  }
  if (courseStandard) {
    formData.append('courseStandard', courseStandard)
  }
  ppts.forEach((file) => formData.append('ppts', file))
  references.forEach((file) => formData.append('references', file))
  formData.append('payload', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  const { data } = await http.post(`/course-plans/${id}/regenerate`, formData, {
    timeout: 1800000,
  })
  return data.data
}

export async function createCoursePlanGenerationJob({ coursePlanId = null, sourceCoursePlanId = null, template = null, courseStandard = null, ppts = [], references = [], payload }) {
  const formData = new FormData()
  if (coursePlanId) {
    formData.append('coursePlanId', String(coursePlanId))
  }
  if (sourceCoursePlanId) {
    formData.append('sourceCoursePlanId', String(sourceCoursePlanId))
  }
  if (template) {
    formData.append('template', template)
  }
  if (courseStandard) {
    formData.append('courseStandard', courseStandard)
  }
  ppts.forEach((file) => formData.append('ppts', file))
  references.forEach((file) => formData.append('references', file))
  formData.append('payload', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  const { data } = await http.post('/course-plans/generation-jobs', formData, {
    timeout: 240000,
  })
  return data.data
}

export async function getCoursePlanGenerationJob(jobId) {
  const { data } = await http.get(`/course-plans/generation-jobs/${jobId}`)
  return data.data
}

export async function reanalyzeCoursePlan(id, teacherRequirements) {
  const { data } = await http.post(`/course-plans/${id}/reanalyze`, { teacherRequirements }, {
    timeout: 1800000,
  })
  return data.data
}

export async function getCoursePlanDetail(id) {
  const { data } = await http.get(`/course-plans/${id}`)
  return data.data
}

export async function saveCoursePlan(id, payload) {
  const { data } = await http.put(`/course-plans/${id}`, payload)
  return data.data
}

export async function deleteCoursePlan(id) {
  const { data } = await http.delete(`/course-plans/${id}`)
  return data.data
}

function triggerDownload(url, fileName = '') {
  const link = document.createElement('a')
  link.href = url
  if (fileName) {
    link.download = fileName
  }
  document.body.appendChild(link)
  link.click()
  link.remove()
}

export async function exportCoursePlanWord(id) {
  await downloadCoursePlanFile(`/course-plans/${id}/export-word`)
}

export async function exportCoursePlanPdf(id) {
  await downloadCoursePlanFile(`/course-plans/${id}/export-pdf`)
}

async function downloadCoursePlanFile(path) {
  let response
  try {
    response = await http.get(path, {
      responseType: 'blob',
      timeout: 240000,
    })
  } catch (error) {
    if (error.response?.data instanceof Blob) {
      throw new Error(await parseBlobError(error.response.data))
    }
    throw error
  }
  const fileName = parseDownloadFileName(
    response.headers?.['x-course-plan-filename'],
    response.headers?.['content-disposition'],
  )
  const blobUrl = URL.createObjectURL(response.data)
  try {
    triggerDownload(blobUrl, fileName)
  } finally {
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
  }
}

function parseDownloadFileName(explicitFileName, disposition) {
  if (explicitFileName) {
    return decodeHeaderFileName(explicitFileName)
  }
  const header = String(disposition || '')
  const encoded = header.match(/filename\*=UTF-8''([^;]+)/i)
  if (encoded?.[1]) {
    return decodeURIComponent(encoded[1])
  }
  const quoted = header.match(/filename="([^"]+)"/i)
  if (quoted?.[1]) {
    return decodeHeaderFileName(quoted[1])
  }
  const plain = header.match(/filename=([^;]+)/i)
  if (plain?.[1]) {
    return decodeHeaderFileName(plain[1].trim())
  }
  throw new Error('导出响应缺少文件名')
}

function decodeHeaderFileName(value) {
  const text = String(value || '').trim()
  if (/%[0-9A-Fa-f]{2}/.test(text)) {
    try {
      return decodeURIComponent(text)
    } catch {
      // 继续尝试兼容旧的 encoded-word 响应头。
    }
  }
  const standardEncodedWord = text.match(/^=\?UTF-8\?Q\?(.+)\?=$/i)
  if (standardEncodedWord?.[1]) {
    return decodeQEncodedWord(standardEncodedWord[1])
  }
  const malformedEncodedWord = text.match(/^=_UTF-8_Q_(.+)_=$/i)
  if (malformedEncodedWord?.[1]) {
    return decodeQEncodedWord(malformedEncodedWord[1])
  }
  return text
}

function decodeQEncodedWord(value) {
  const percentText = String(value)
    .replace(/_/g, ' ')
    .replace(/=([0-9A-Fa-f]{2})/g, '%$1')
  try {
    return decodeURIComponent(percentText)
  } catch {
    return String(value)
  }
}

export function downloadDefaultCoursePlanTemplate() {
  const token = localStorage.getItem('nsu_maic_token')
  const query = token ? `?token=${encodeURIComponent(token)}` : ''
  triggerDownload(`/api/course-plans/default-template${query}`)
}

export function previewCoursePlanPdfUrl(id) {
  const token = localStorage.getItem('nsu_maic_token')
  const query = token ? `?token=${encodeURIComponent(token)}` : ''
  return `/api/course-plans/${id}/preview-pdf${query}`
}

export async function fetchCoursePlanPreviewPdf(id) {
  const response = await http.get(`/course-plans/${id}/preview-pdf`, {
    responseType: 'blob',
    timeout: 240000,
  })
  const contentType = response.headers?.['content-type'] || ''
  if (!contentType.includes('application/pdf')) {
    throw new Error(await parseBlobError(response.data))
  }
  return response.data
}

async function parseBlobError(blob) {
  if (!(blob instanceof Blob)) {
    return 'PDF 预览加载失败'
  }
  const text = await blob.text()
  if (!text) {
    return 'PDF 预览加载失败'
  }
  try {
    const payload = JSON.parse(text)
    return payload.message || 'PDF 预览加载失败'
  } catch {
    return text
  }
}
