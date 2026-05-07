<template>
  <div class="course-plan-page">
    <AppTopbar
      v-if="user"
      active="new"
      :user="user"
      @home="goHome"
      @new="goHome"
      @lessons="goLessons"
      @logout="handleLogout"
    />

    <main class="page-main">
      <el-skeleton v-if="authChecking || loadingDetail" :rows="10" animated />

      <template v-else-if="user">
        <CoursePlanUploadPanel
          mode="materials"
          :template-file="templateFile"
          :course-standard-file="courseStandardFile"
          :ppt-files="pptFiles"
          :reference-files="referenceFiles"
          :saved-materials="savedMaterials"
          :default-template-name="defaultTemplateName"
          :teacher-requirements="teacherRequirements"
          :analyzing="analyzing"
          @update:template-file="templateFile = $event"
          @update:course-standard-file="courseStandardFile = $event"
          @update:ppt-files="pptFiles = $event"
          @update:reference-files="referenceFiles = $event"
          @update:teacher-requirements="teacherRequirements = $event"
          @download-template="handleDownloadTemplate"
          @analyze="handleAnalyze"
        />

        <CoursePlanAnalyzeResult
          v-if="preparedAnalysis"
          :analysis="preparedAnalysis"
          :analysis-ready="analysisReady"
          :generating="generating"
          :save-as-new-visible="true"
          @update-basic-info="updateBasicInfo"
          @update-unit-field="updateUnitField"
          @generate="handleGenerate"
          @generate-save-as-new="handleGenerateAsNew"
          @reset="resetMaterialsState"
        />

        <section v-if="generationJob" class="generation-job-card">
          <div class="generation-job-head">
            <div>
              <h3>课程教案正在重新生成</h3>
              <p>{{ generationJob.message || '生成任务已提交，正在处理。' }}</p>
            </div>
            <el-tag :type="generationStatusType">{{ generationStatusText }}</el-tag>
          </div>
          <el-progress
            :percentage="generationProgressPercent"
            :status="generationProgressStatus"
            :stroke-width="12"
          />
          <div class="generation-job-meta">
            <span>阶段：{{ generationJob.stage || 'pending' }}</span>
            <span class="progress-with-help">
              进度：{{ generationJob.current || 0 }} / {{ generationJob.total || 0 }}
              <el-tooltip :content="generationProgressHelp" placement="top">
                <span class="progress-help">?</span>
              </el-tooltip>
            </span>
          </div>
          <div v-if="generationJob.status === 'failed'" class="generation-job-actions">
            <el-button type="primary" @click="goEditor">返回编辑页</el-button>
            <el-button plain @click="goLessons">返回教案管理</el-button>
            <el-button type="danger" plain @click="clearFailedJobState">清除失败状态</el-button>
          </div>
        </section>

        <el-alert
          v-if="generationError"
          class="generation-error-alert"
          title="课程教案生成未通过校验"
          type="error"
          show-icon
          :closable="true"
          @close="generationError = null"
        >
          <div class="generation-error-detail">
            <p>{{ generationError.message }}</p>
            <p v-if="generationError.lessonTitle">课次：{{ generationError.lessonTitle }}</p>
            <p v-if="generationError.blockTitle">内容块：{{ generationError.blockTitle }}</p>
            <p v-if="generationError.section === 'mainContent' && (generationError.actualChars || generationError.requiredChars)">
              字数：{{ generationError.actualChars || 0 }} / {{ generationError.requiredChars || 0 }}
            </p>
            <p v-if="generationError.section === 'assignments'">
              建议检查教学日历课次主题、PPT 内容和教师补充要求，确保能生成调研、习题、开放性项目、提交方式和提交标准。
            </p>
            <p v-else>建议检查教学日历对应课次、PPT 章节内容和课程标准中的单元目标/重点难点。</p>
            <div class="generation-error-actions">
              <el-button type="primary" @click="goEditor">返回编辑页</el-button>
              <el-button plain @click="goLessons">返回教案管理</el-button>
              <el-button type="danger" plain @click="clearFailedJobState">清除失败状态</el-button>
            </div>
          </div>
        </el-alert>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  analyzeExistingCoursePlan,
  createCoursePlanGenerationJob,
  downloadDefaultCoursePlanTemplate,
  getCoursePlanGenerationJob,
  getCoursePlanDetail,
} from '../api/http'
import CoursePlanAnalyzeResult from '../components/course-plan/CoursePlanAnalyzeResult.vue'
import CoursePlanUploadPanel from '../components/course-plan/CoursePlanUploadPanel.vue'
import AppTopbar from '../components/AppTopbar.vue'
import { useAuthenticatedPage } from '../composables/useAuthenticatedPage'
import { normalizeCoursePlanAnalysis } from '../utils/coursePlan'
import {
  parseCoursePlanGenerationError,
  parseCoursePlanGenerationJobError,
} from '../utils/coursePlanGenerationError'

const router = useRouter()
const route = useRoute()
const defaultTemplateName = '20XX-20XX学年第X学期《课程名称》-课程教案（模版).docx'

const loadingDetail = ref(false)
const analyzing = ref(false)
const generating = ref(false)
const templateFile = ref(null)
const courseStandardFile = ref(null)
const pptFiles = ref([])
const referenceFiles = ref([])
const teacherRequirements = ref('')
const analysisState = ref(null)
const savedMaterials = ref([])
const generationError = ref(null)
const generationJob = ref(null)
let generationPollTimer = null

const { user, authChecking, handleLogout } = useAuthenticatedPage(async () => {
  await loadDetail(route.params.id)
  await resumeGenerationJob()
})

const preparedAnalysis = computed(() => normalizeCoursePlanAnalysis(analysisState.value, teacherRequirements.value))
const analysisReady = computed(() => Boolean(preparedAnalysis.value?.valid))
const generationStorageKey = computed(() => `course_plan_generation_job:plan:${route.params.id}`)
const generationProgressPercent = computed(() => {
  const total = Number(generationJob.value?.total || 0)
  if (!total) return 0
  return Math.min(100, Math.round((Number(generationJob.value?.current || 0) / total) * 100))
})
const generationProgressStatus = computed(() => {
  if (generationJob.value?.status === 'failed') return 'exception'
  if (generationJob.value?.status === 'succeeded') return 'success'
  return undefined
})
const generationProgressHelp = computed(() => {
  const units = preparedAnalysis.value?.units || []
  const unitCount = units.length
  const designCount = units.reduce((sum, unit) => sum + Number(unit.teachingDesignCount || 0), 0)
  const total = Number(generationJob.value?.total || (1 + unitCount + designCount))
  if (!unitCount && !designCount) {
    return `总进度 ${total} 步：包含课程首页、单元教案首页和教学设计生成。`
  }
  return `总进度 ${total} 步 = 1 个课程教案首页 + ${unitCount} 个单元教案首页 + ${designCount} 个教学设计。`
})
const generationStatusType = computed(() => {
  if (generationJob.value?.status === 'failed') return 'danger'
  if (generationJob.value?.status === 'succeeded') return 'success'
  return 'primary'
})
const generationStatusText = computed(() => {
  const status = generationJob.value?.status
  if (status === 'pending') return '等待中'
  if (status === 'running') return '生成中'
  if (status === 'succeeded') return '已完成'
  if (status === 'failed') return '失败'
  if (status === 'cancelled') return '已取消'
  return '处理中'
})

onBeforeUnmount(() => {
  stopGenerationPolling()
})

watch(
  () => route.params.id,
  async (id) => {
    if (!user.value || !id) return
    clearGenerationJobState({ removeStorage: false })
    await loadDetail(id)
    await resumeGenerationJob()
  },
)

async function loadDetail(id) {
  if (!id) return
  loadingDetail.value = true
  try {
    const detail = await getCoursePlanDetail(id)
    analysisState.value = detail.analysis
    teacherRequirements.value = detail.teacherRequirements || ''
    savedMaterials.value = detail.materials || []
    resetUploads()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取课程教案失败')
    await router.replace({ name: 'lesson-list' })
  } finally {
    loadingDetail.value = false
  }
}

async function handleAnalyze() {
  if (hasPartialCoreUploads()) {
    ElMessage.warning('重新上传材料时，请同时上传课程标准、PPT/课件和教学日历。')
    return
  }
  analyzing.value = true
  generationError.value = null
  try {
    if (!hasUploadedCoreMaterials() && !hasAnyUploadedMaterials() && !hasCompleteSavedMaterials.value) {
      ElMessage.warning('该历史记录未保存完整原始上传文件，需重新上传课程标准、PPT/课件和教学日历。')
      return
    }
    analysisState.value = await analyzeExistingCoursePlan(route.params.id, {
      template: templateFile.value,
      courseStandard: courseStandardFile.value,
      ppts: pptFiles.value,
      references: referenceFiles.value,
      teacherRequirements: teacherRequirements.value,
    })
    ElMessage.success(hasAnyUploadedMaterials() ? '材料解析完成' : '已使用保存材料重新解析')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '材料解析失败')
  } finally {
    analyzing.value = false
  }
}

async function handleGenerate() {
  await submitGenerationJob({
    coursePlanId: route.params.id,
    sourceCoursePlanId: null,
    successMessage: '课程教案重新生成任务已提交',
  })
}

async function handleGenerateAsNew() {
  await submitGenerationJob({
    coursePlanId: null,
    sourceCoursePlanId: route.params.id,
    successMessage: '课程教案另存为新记录的生成任务已提交',
  })
}

async function submitGenerationJob({ coursePlanId, sourceCoursePlanId, successMessage }) {
  if (!preparedAnalysis.value?.valid) {
    ElMessage.warning('请先完成材料解析并修正冲突项。')
    return
  }
  if (hasPartialCoreUploads()) {
    ElMessage.warning('重新上传材料时，请同时上传课程标准、PPT/课件和教学日历。')
    return
  }
  generating.value = true
  generationError.value = null
  try {
    const job = await createCoursePlanGenerationJob({
      coursePlanId,
      sourceCoursePlanId,
      template: templateFile.value,
      courseStandard: courseStandardFile.value,
      ppts: pptFiles.value,
      references: referenceFiles.value,
      payload: {
        analysis: preparedAnalysis.value,
        teacherRequirements: teacherRequirements.value,
      },
    })
    generationJob.value = job
    localStorage.setItem(generationStorageKey.value, String(job.id))
    ElMessage.success(successMessage)
    scheduleGenerationPoll(0)
  } catch (error) {
    const parsed = parseCoursePlanGenerationError(error, '课程教案更新失败')
    generationError.value = parsed.detail
    ElMessage.error(parsed.toast)
    generating.value = false
  }
}

function scheduleGenerationPoll(delay = 2500) {
  stopGenerationPolling()
  generationPollTimer = window.setTimeout(pollGenerationJob, delay)
}

function stopGenerationPolling() {
  if (generationPollTimer) {
    window.clearTimeout(generationPollTimer)
    generationPollTimer = null
  }
}

async function resumeGenerationJob() {
  const jobId = localStorage.getItem(generationStorageKey.value)
  if (!jobId) return
  generating.value = true
  try {
    generationJob.value = await getCoursePlanGenerationJob(jobId)
    await handleGenerationJobState(generationJob.value)
  } catch (error) {
    localStorage.removeItem(generationStorageKey.value)
    generationJob.value = null
    generating.value = false
  }
}

async function pollGenerationJob() {
  if (!generationJob.value?.id) return
  try {
    generationJob.value = await getCoursePlanGenerationJob(generationJob.value.id)
    await handleGenerationJobState(generationJob.value)
  } catch (error) {
    generationError.value = {
      message: error.response?.data?.message || error.message || '读取课程教案生成进度失败',
    }
    ElMessage.error(generationError.value.message)
    generating.value = false
    stopGenerationPolling()
  }
}

async function handleGenerationJobState(job) {
  if (job?.status === 'succeeded') {
    clearGenerationJobState()
    resetUploads()
    await router.push({ name: 'course-plan-edit', params: { id: job.coursePlanId } })
    ElMessage.success('课程教案已更新，已进入编辑页')
    return
  }
  if (job?.status === 'failed' || job?.status === 'cancelled') {
    stopGenerationPolling()
    const parsed = parseCoursePlanGenerationJobError(job, '课程教案更新失败')
    generationError.value = parsed.detail
    ElMessage.error(parsed.toast)
    generating.value = false
    return
  }
  generating.value = true
  scheduleGenerationPoll()
}

function clearGenerationJobState({ removeStorage = true } = {}) {
  stopGenerationPolling()
  if (removeStorage) {
    localStorage.removeItem(generationStorageKey.value)
  }
  generating.value = false
  generationJob.value = null
}

function updateBasicInfo(field, value) {
  if (!analysisState.value) return
  analysisState.value = {
    ...analysisState.value,
    basicInfo: {
      ...analysisState.value.basicInfo,
      [field]: value,
    },
  }
}

function updateUnitField(index, field, value) {
  if (!analysisState.value) return
  const units = (analysisState.value.units || []).map((unit, unitIndex) => {
    if (unitIndex !== index) return unit
    return {
      ...unit,
      [field]: field === 'hours' ? Number(value || 0) : value,
    }
  })
  analysisState.value = {
    ...analysisState.value,
    units,
  }
}

function resetUploads() {
  templateFile.value = null
  courseStandardFile.value = null
  pptFiles.value = []
  referenceFiles.value = []
}

function resetMaterialsState() {
  clearGenerationJobState()
  resetUploads()
  analysisState.value = null
  generationError.value = null
}

function hasUploadedCoreMaterials() {
  return Boolean(courseStandardFile.value && pptFiles.value.length && referenceFiles.value.length)
}

function hasPartialCoreUploads() {
  return Boolean(courseStandardFile.value || pptFiles.value.length || referenceFiles.value.length) && !hasUploadedCoreMaterials()
}

function hasAnyUploadedMaterials() {
  return Boolean(templateFile.value || courseStandardFile.value || pptFiles.value.length || referenceFiles.value.length)
}

const hasCompleteSavedMaterials = computed(() => (
  savedMaterials.value.some((item) => item.role === 'template')
  && savedMaterials.value.some((item) => item.role === 'course-standard')
  && savedMaterials.value.some((item) => item.role === 'ppt')
  && savedMaterials.value.some((item) => item.role === 'reference')
))

function handleDownloadTemplate() {
  downloadDefaultCoursePlanTemplate()
}

function goHome() {
  router.push({ name: 'course-plan-new' })
}

function goLessons() {
  router.push({ name: 'lesson-list' })
}

function goEditor() {
  router.push({ name: 'course-plan-edit', params: { id: generationJob.value?.coursePlanId || route.params.id } })
}

function clearFailedJobState() {
  clearGenerationJobState()
  generationError.value = null
  ElMessage.success('已清除当前失败任务状态')
}
</script>

<style scoped>
.course-plan-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.1), transparent 26%),
    radial-gradient(circle at top right, rgba(147, 197, 253, 0.18), transparent 28%),
    #f4f8ff;
}

.page-main {
  width: min(100%, 1520px);
  margin: 0 auto;
  padding: 26px 24px 44px;
  display: grid;
  gap: 24px;
}

.generation-job-card {
  display: grid;
  gap: 14px;
  padding: 22px 24px;
  border: 1px solid #bfdbfe;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 16px 38px rgba(37, 99, 235, 0.1);
}

.generation-job-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.generation-job-head h3 {
  margin: 0;
  color: #0b2b5d;
  font-size: 20px;
}

.generation-job-head p {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.6;
}

.generation-job-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #64748b;
  font-size: 14px;
}

.progress-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.progress-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 17px;
  height: 17px;
  border-radius: 999px;
  background: #e0ecff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
  cursor: help;
}

.generation-job-actions,
.generation-error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.generation-error-alert {
  border-radius: 16px;
}

.generation-error-detail {
  display: grid;
  gap: 4px;
  color: #7f1d1d;
  line-height: 1.6;
}

.generation-error-detail p {
  margin: 0;
}

@media (max-width: 720px) {
  .page-main {
    padding: 18px 16px 32px;
  }
}
</style>
