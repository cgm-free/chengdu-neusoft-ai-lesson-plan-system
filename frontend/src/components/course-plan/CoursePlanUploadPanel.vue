<template>
  <section class="upload-panel">
    <div class="upload-grid">
      <article class="upload-card upload-card--template">
        <div class="upload-card-head">
          <div class="upload-card-title">
            <span class="upload-card-icon">
              <el-icon><Document /></el-icon>
            </span>
            <div>
              <h3>教案模板</h3>
              <p>默认使用系统模板；如需调整格式，可上传自定义 docx 模板</p>
            </div>
          </div>
          <el-button class="template-download" plain round @click="$emit('downloadTemplate')">下载教案模板</el-button>
        </div>

        <el-upload
          class="upload-dropzone"
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept=".docx"
          :limit="1"
          :on-change="handleTemplateChange"
        >
          <div class="dropzone-inner">
            <el-icon class="dropzone-icon"><UploadFilled /></el-icon>
            <strong>点击或拖拽上传自定义模板</strong>
            <span>支持 .docx 格式</span>
          </div>
        </el-upload>

        <div v-if="templateFile" class="file-list">
          <div class="file-item">
            <div class="file-meta">
              <el-icon><Document /></el-icon>
              <span>{{ templateFile.name }}</span>
            </div>
            <div class="file-actions">
              <small>{{ formatFileSize(templateFile.size) }}</small>
              <el-button link type="danger" @click="$emit('update:templateFile', null)">移除</el-button>
            </div>
          </div>
        </div>
        <div v-else-if="savedByRole('template').length" class="file-list">
          <div class="file-item file-item--saved">
            <div class="file-meta">
              <el-icon><Document /></el-icon>
              <span>{{ savedByRole('template')[0].fileName }}</span>
            </div>
            <div class="file-actions">
              <small>已保存</small>
              <el-icon><CircleCheckFilled /></el-icon>
            </div>
          </div>
        </div>
        <div v-else class="file-list">
          <div class="file-item file-item--default">
            <div class="file-meta">
              <el-icon><Document /></el-icon>
              <span>{{ defaultTemplateName }}</span>
            </div>
            <div class="file-actions">
              <small>系统默认模板</small>
              <el-icon><CircleCheckFilled /></el-icon>
            </div>
          </div>
        </div>
      </article>

      <article class="upload-card upload-card--standard">
        <div class="upload-card-head">
          <div class="upload-card-title">
            <span class="upload-card-icon">
              <el-icon><Reading /></el-icon>
            </span>
            <div>
              <h3>课程标准</h3>
              <p>必填，优先 docx，也支持可提取文本的 pdf</p>
            </div>
          </div>
        </div>

        <el-upload
          class="upload-dropzone"
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept=".docx,.pdf"
          :limit="1"
          :on-change="handleStandardChange"
        >
          <div class="dropzone-inner">
            <el-icon class="dropzone-icon"><UploadFilled /></el-icon>
            <strong>点击或拖拽上传课程标准</strong>
            <span>支持 .docx / .pdf 格式</span>
          </div>
        </el-upload>

        <div v-if="courseStandardFile" class="file-list">
          <div class="file-item">
            <div class="file-meta">
              <el-icon><Reading /></el-icon>
              <span>{{ courseStandardFile.name }}</span>
            </div>
            <div class="file-actions">
              <small>{{ formatFileSize(courseStandardFile.size) }}</small>
              <el-button link type="danger" @click="$emit('update:courseStandardFile', null)">移除</el-button>
            </div>
          </div>
        </div>
        <div v-else-if="savedByRole('course-standard').length" class="file-list">
          <div class="file-item file-item--saved">
            <div class="file-meta">
              <el-icon><Reading /></el-icon>
              <span>{{ savedByRole('course-standard')[0].fileName }}</span>
            </div>
            <div class="file-actions">
              <small>已保存</small>
              <el-icon><CircleCheckFilled /></el-icon>
            </div>
          </div>
        </div>
      </article>

      <article class="upload-card upload-card--ppt">
        <div class="upload-card-head">
          <div class="upload-card-title">
            <span class="upload-card-icon">
              <el-icon><PictureRounded /></el-icon>
            </span>
            <div>
              <h3>PPT课件</h3>
              <p>必填，支持 ppt/pptx；可一次选择多份，也可继续追加</p>
            </div>
          </div>
        </div>

        <el-upload
          ref="pptUploadRef"
          class="upload-dropzone"
          drag
          :auto-upload="false"
          :show-file-list="false"
          multiple
          accept=".ppt,.pptx"
          :on-change="handlePptChange"
        >
          <div class="dropzone-inner">
            <el-icon class="dropzone-icon"><UploadFilled /></el-icon>
            <strong>点击或拖拽上传课件</strong>
            <span>支持 .ppt / .pptx 格式</span>
          </div>
        </el-upload>

        <div v-if="pptFiles.length" class="file-list">
          <div class="file-item file-item--summary">
            <div class="file-meta">
              <el-icon><PictureRounded /></el-icon>
              <span>{{ pptSummaryTitle }}</span>
            </div>
            <div class="file-actions">
              <small>{{ formatTotalFileSize(pptFiles) }}</small>
              <el-button link type="primary" @click="pptDialogVisible = true">更多</el-button>
            </div>
          </div>
        </div>
        <div v-else-if="savedPptFiles.length" class="file-list">
          <div class="file-item file-item--summary file-item--saved">
            <div class="file-meta">
              <el-icon><PictureRounded /></el-icon>
              <span>{{ savedPptSummaryTitle }}</span>
            </div>
            <div class="file-actions">
              <small>{{ formatTotalFileSize(savedPptFiles) }} · 已保存</small>
              <el-button link type="primary" @click="pptDialogVisible = true">更多</el-button>
            </div>
          </div>
        </div>
      </article>

      <article class="upload-card upload-card--calendar">
        <div class="upload-card-head">
          <div class="upload-card-title">
            <span class="upload-card-icon">
              <el-icon><Calendar /></el-icon>
            </span>
            <div>
              <h3>教学日历</h3>
              <p>必填，支持 xlsx 格式</p>
            </div>
          </div>
        </div>

        <el-upload
          ref="referenceUploadRef"
          class="upload-dropzone"
          drag
          :auto-upload="false"
          :show-file-list="false"
          :limit="1"
          accept=".xls,.xlsx"
          :on-change="handleReferenceChange"
        >
          <div class="dropzone-inner">
            <el-icon class="dropzone-icon"><UploadFilled /></el-icon>
            <strong>点击或拖拽上传教学日历</strong>
            <span>支持 .xlsx 格式</span>
          </div>
        </el-upload>

        <div v-if="referenceFiles.length" class="file-list">
          <div v-for="file in referenceFiles" :key="file.name + file.size" class="file-item">
            <div class="file-meta">
              <el-icon><Calendar /></el-icon>
              <span>{{ file.name }}</span>
            </div>
            <div class="file-actions">
              <small>{{ formatFileSize(file.size) }}</small>
              <el-button link type="danger" @click="removeFromList('reference', file)">移除</el-button>
            </div>
          </div>
        </div>
        <div v-else-if="savedByRole('reference').length" class="file-list">
          <div v-for="file in savedByRole('reference')" :key="file.id || file.fileName" class="file-item file-item--saved">
            <div class="file-meta">
              <el-icon><Calendar /></el-icon>
              <span>{{ file.fileName }}</span>
            </div>
            <div class="file-actions">
              <small>{{ formatFileSize(file.size) }} · 已保存</small>
              <el-icon><CircleCheckFilled /></el-icon>
            </div>
          </div>
        </div>
      </article>
    </div>

    <el-alert
      v-if="savedMaterials.length && !hasCompleteSavedMaterials"
      type="warning"
      title="该历史记录未保存完整原始上传文件，需重新上传课程标准、PPT/课件和教学日历后才能重新解析。"
      show-icon
      :closable="false"
    />

    <section class="requirements-card">
      <div class="requirements-head">
        <h3>教师补充要求（可选）</h3>
        <p>只写课程级附加约束，例如必须覆盖的项目、成果物、校级格式要求。</p>
      </div>
      <el-input
        class="teacher-requirements-input"
        :model-value="teacherRequirements"
        type="textarea"
        :rows="1"
        placeholder="例如：课程首页需体现学院信息；项目型单元必须写清成果物与验收要求；教学设计语言保持正式书面表达。"
        @update:model-value="$emit('update:teacherRequirements', $event)"
      />
    </section>

    <div class="panel-actions">
      <el-button
        class="analyze-button"
        type="primary"
        size="large"
        :loading="analyzing"
        :disabled="!canAnalyze"
        @click="$emit('analyze')"
      >
        {{ analyzing ? '正在解析材料...' : actionLabel }}
      </el-button>
    </div>

    <el-dialog v-model="pptDialogVisible" title="PPT课件列表" width="720px">
      <div class="dialog-file-list">
        <template v-if="pptFiles.length">
          <div v-for="file in pptFiles" :key="file.name + file.size" class="file-item">
            <div class="file-meta">
              <el-icon><PictureRounded /></el-icon>
              <span>{{ file.name }}</span>
            </div>
            <div class="file-actions">
              <small>{{ formatFileSize(file.size) }}</small>
              <el-button link type="danger" @click="removeFromList('ppt', file)">移除</el-button>
            </div>
          </div>
        </template>
        <template v-else>
          <div v-for="file in savedPptFiles" :key="file.id || file.fileName" class="file-item file-item--saved">
            <div class="file-meta">
              <el-icon><PictureRounded /></el-icon>
              <span>{{ file.fileName }}</span>
            </div>
            <div class="file-actions">
              <small>{{ formatFileSize(file.size) }} · 已保存</small>
              <el-icon><CircleCheckFilled /></el-icon>
            </div>
          </div>
        </template>
      </div>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  Calendar,
  CircleCheckFilled,
  Document,
  PictureRounded,
  Reading,
  UploadFilled,
} from '@element-plus/icons-vue'

const props = defineProps({
  mode: { type: String, default: 'create' },
  templateFile: { type: Object, default: null },
  courseStandardFile: { type: Object, default: null },
  pptFiles: { type: Array, default: () => [] },
  referenceFiles: { type: Array, default: () => [] },
  savedMaterials: { type: Array, default: () => [] },
  defaultTemplateName: { type: String, default: '20XX-20XX学年第X学期《课程名称》-课程教案（模版).docx' },
  teacherRequirements: { type: String, default: '' },
  analyzing: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:templateFile',
  'update:courseStandardFile',
  'update:pptFiles',
  'update:referenceFiles',
  'update:teacherRequirements',
  'downloadTemplate',
  'analyze',
])

const pptUploadRef = ref(null)
const referenceUploadRef = ref(null)
const pptDialogVisible = ref(false)
let pptChangeTimer = null
let referenceChangeTimer = null

const actionLabel = computed(() => (
  props.mode === 'materials' ? '重新解析材料' : '开始解析材料'
))
const savedPptFiles = computed(() => savedByRole('ppt'))
const pptSummaryTitle = computed(() => (
  props.pptFiles.length === 1 ? props.pptFiles[0].name : `已上传 ${props.pptFiles.length} 份课件`
))
const savedPptSummaryTitle = computed(() => (
  savedPptFiles.value.length === 1 ? savedPptFiles.value[0].fileName : `已保存 ${savedPptFiles.value.length} 份课件`
))

const canAnalyze = computed(() => {
  const hasStandard = Boolean(props.courseStandardFile || savedByRole('course-standard').length)
  const hasPpt = Boolean(props.pptFiles.length || savedByRole('ppt').length)
  const hasCalendar = Boolean(props.referenceFiles.length || savedByRole('reference').length)
  return hasStandard && hasPpt && hasCalendar
})

const hasCompleteSavedMaterials = computed(() => Boolean(
  savedByRole('template').length
  && savedByRole('course-standard').length
  && savedByRole('ppt').length
  && savedByRole('reference').length
))

function handleTemplateChange(uploadFile) {
  const rawFile = uploadFile?.raw
  if (rawFile) {
    emit('update:templateFile', rawFile)
  }
}

function handleStandardChange(uploadFile) {
  const rawFile = uploadFile?.raw
  if (rawFile) {
    emit('update:courseStandardFile', rawFile)
  }
}

function handlePptChange(uploadFile, uploadFiles = []) {
  const rawFiles = uploadFiles.length
    ? uploadFiles.map((file) => file.raw).filter(Boolean)
    : [uploadFile?.raw].filter(Boolean)
  if (!rawFiles.length) return
  window.clearTimeout(pptChangeTimer)
  pptChangeTimer = window.setTimeout(() => {
    emit('update:pptFiles', mergeFiles(props.pptFiles, rawFiles))
    pptUploadRef.value?.clearFiles()
  }, 0)
}

function handleReferenceChange(uploadFile, uploadFiles = []) {
  const rawFiles = uploadFiles.length
    ? uploadFiles.map((file) => file.raw).filter(Boolean)
    : [uploadFile?.raw].filter(Boolean)
  if (!rawFiles.length) return
  window.clearTimeout(referenceChangeTimer)
  referenceChangeTimer = window.setTimeout(() => {
    emit('update:referenceFiles', mergeFiles(props.referenceFiles, rawFiles))
    referenceUploadRef.value?.clearFiles()
  }, 0)
}

function removeFromList(type, file) {
  const source = type === 'ppt' ? props.pptFiles : props.referenceFiles
  const next = source.filter((item) => item.name !== file.name || item.size !== file.size)
  emit(type === 'ppt' ? 'update:pptFiles' : 'update:referenceFiles', next)
}

function mergeFiles(source, files) {
  const next = [...source]
  for (const file of files) {
    const index = next.findIndex((item) => item.name === file.name && item.size === file.size)
    if (index >= 0) {
      next.splice(index, 1, file)
    } else {
      next.push(file)
    }
  }
  return next
}

function savedByRole(role) {
  return props.savedMaterials.filter((item) => item.role === role)
}

function formatFileSize(size) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function formatTotalFileSize(files) {
  const total = (files || []).reduce((sum, file) => sum + Number(file.size || 0), 0)
  return formatFileSize(total)
}
</script>

<style scoped>
.upload-panel {
  display: grid;
  gap: 22px;
}

.upload-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.upload-card,
.requirements-card {
  padding: 18px;
  border: 1px solid #dbe7f8;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.05);
}

.upload-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.upload-card-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-card-title h3 {
  margin: 0;
  color: #1e3a6b;
  font-size: 22px;
}

.upload-card-title p {
  margin: 4px 0 0;
  color: #6a7d98;
  font-size: 14px;
  line-height: 1.6;
}

.upload-card-icon {
  width: 48px;
  height: 48px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  font-size: 24px;
  flex: 0 0 auto;
}

.upload-card--template .upload-card-icon {
  color: #ffffff;
  background: linear-gradient(135deg, #2b7dff, #1860e5);
}

.upload-card--standard .upload-card-icon {
  color: #ffffff;
  background: linear-gradient(135deg, #34b16d, #2a9b5c);
}

.upload-card--ppt .upload-card-icon {
  color: #ffffff;
  background: linear-gradient(135deg, #ff9a42, #f97316);
}

.upload-card--calendar .upload-card-icon {
  color: #ffffff;
  background: linear-gradient(135deg, #9a79ff, #7c4dff);
}

.template-download {
  flex: 0 0 auto;
}

.upload-dropzone {
  width: 100%;
}

.upload-dropzone :deep(.el-upload) {
  width: 100%;
}

.upload-dropzone :deep(.el-upload-dragger) {
  width: 100%;
  min-height: 116px;
  padding: 12px 16px;
  border-width: 1px;
  border-radius: 18px;
  background: #fbfdff;
}

.upload-card--template .upload-dropzone :deep(.el-upload-dragger) {
  border-color: #9fc1ff;
  background: #f5f9ff;
}

.upload-card--standard .upload-dropzone :deep(.el-upload-dragger) {
  border-color: #95d9b4;
  background: #f4fcf7;
}

.upload-card--ppt .upload-dropzone :deep(.el-upload-dragger) {
  border-color: #ffc38d;
  background: #fff8f1;
}

.upload-card--calendar .upload-dropzone :deep(.el-upload-dragger) {
  border-color: #c7b5ff;
  background: #f7f4ff;
}

.dropzone-inner {
  display: grid;
  justify-items: center;
  gap: 4px;
  text-align: center;
}

.dropzone-icon {
  color: #4b8dff;
  font-size: 24px;
}

.dropzone-inner strong {
  color: #2b5d9f;
  font-size: 16px;
}

.dropzone-inner span {
  color: #6f86a7;
  font-size: 14px;
}

.file-list {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.file-item {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 14px;
  background: #f5f8ff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.file-item--saved {
  background: #f4fbf7;
}

.file-item--summary {
  background: #eef4ff;
}

.file-item--default {
  background: #eef5ff;
}

.file-meta,
.file-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-meta {
  color: #244775;
  font-weight: 700;
}

.file-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-actions {
  flex: 0 0 auto;
  color: #7890b0;
}

.requirements-card h3 {
  margin: 0;
  color: #1e3a6b;
  font-size: 22px;
}

.requirements-card p {
  margin: 6px 0 14px;
  color: #6d7f98;
  font-size: 14px;
}

.teacher-requirements-input :deep(.el-textarea__inner) {
  min-height: 40px !important;
  padding-top: 8px;
  padding-bottom: 8px;
}

.panel-actions {
  display: flex;
  justify-content: center;
}

.dialog-file-list {
  display: grid;
  gap: 8px;
}

.analyze-button {
  min-width: 280px;
  height: 58px;
  border-radius: 18px;
  font-size: 20px;
  font-weight: 700;
  box-shadow: 0 18px 28px rgba(29, 111, 242, 0.18);
}

@media (max-width: 1380px) {
  .upload-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .upload-card,
  .requirements-card {
    padding: 16px;
    border-radius: 20px;
  }

  .upload-card-head,
  .upload-card-title {
    align-items: flex-start;
    flex-direction: column;
  }

  .upload-card-title h3,
  .requirements-card h3 {
    font-size: 20px;
  }

  .dropzone-inner strong {
    font-size: 15px;
  }

  .dropzone-inner span,
  .upload-card-title p,
  .requirements-card p {
    font-size: 13px;
  }

  .file-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .file-actions {
    width: 100%;
    justify-content: space-between;
  }

  .analyze-button {
    width: 100%;
  }
}
</style>
