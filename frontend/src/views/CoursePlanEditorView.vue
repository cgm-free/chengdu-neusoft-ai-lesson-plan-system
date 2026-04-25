<template>
  <div class="course-plan-page">
    <AppTopbar
      v-if="user"
      active="lessons"
      :user="user"
      @home="goHome"
      @new="goHome"
      @lessons="goLessons"
      @logout="handleLogout"
    />

    <main class="page-main">
      <el-skeleton v-if="authChecking || loadingDetail" :rows="10" animated />

      <template v-else-if="user && draftAnalysis && draftContent">
        <section class="editor-topbar">
            <div>
              <h1>{{ editorTitle }}</h1>
              <p>左侧目录定位，右侧直接编辑课程首页、单元首页和教学设计。</p>
            </div>
            <div class="topbar-actions">
              <el-button :loading="saving" @click="handleSave">保存</el-button>
              <el-button type="primary" plain @click="handlePreviewPdf">预览PDF</el-button>
              <el-button type="primary" plain @click="handleExportWord">下载Word</el-button>
              <el-button type="primary" @click="handleExportPdf">下载PDF</el-button>
            </div>
          </section>

        <section class="editor-layout">
            <aside class="editor-nav" aria-label="课程教案目录">
              <button
                type="button"
                class="nav-item nav-item--cover"
                :class="{ active: activeAnchor === coverAnchor() }"
                @click="scrollToDocument(coverAnchor())"
              >
                课程首页
              </button>

              <div class="nav-section">
                <h2>单元首页</h2>
                <button
                  v-for="(unit, unitIndex) in draftContent.units || []"
                  :key="`unit-nav-${unit.code || unitIndex}`"
                  type="button"
                  class="nav-item"
                  :class="{ active: activeAnchor === unitAnchor(unitIndex) }"
                  @click="scrollToDocument(unitAnchor(unitIndex))"
                >
                  <strong>{{ unit.code || `CU(${unitIndex + 1})` }}</strong>
                  <span>{{ unit.name }}</span>
                </button>
              </div>

              <div class="nav-section">
                <h2>教学设计</h2>
                <template v-for="(unit, unitIndex) in draftContent.units || []" :key="`design-nav-${unit.code || unitIndex}`">
                  <p class="nav-unit-label">{{ unit.code || `CU(${unitIndex + 1})` }}</p>
                  <button
                    v-for="(design, designIndex) in unit.teachingDesigns || []"
                    :key="`design-nav-${unitIndex}-${designIndex}`"
                    type="button"
                    class="nav-item nav-item--design"
                    :class="{ active: activeAnchor === designAnchor(unitIndex, designIndex) }"
                    @click="scrollToDocument(designAnchor(unitIndex, designIndex))"
                  >
                    <strong>第{{ design.index || designIndex + 1 }}次课</strong>
                    <span>{{ design.title }}</span>
                  </button>
                </template>
              </div>
            </aside>

            <CoursePlanDocumentEditor
              ref="documentEditorRef"
              class="document-editor-pane"
              :analysis="draftAnalysis"
              :content="draftContent"
              @active-anchor-change="handleActiveAnchorChange"
              @update-basic-info="updateBasicInfo"
              @update-source-context-list="updateSourceContextList"
              @update-content-field="updateContentField"
              @update-unit-field="updateUnitField"
              @update-unit-list-field="updateUnitListField"
              @update-design-field="updateDesignField"
              @update-design-list-field="updateDesignListField"
              @update-main-block-field="updateMainBlockField"
              @update-main-block-list-field="updateMainBlockListField"
            />
        </section>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  exportCoursePlanPdf,
  exportCoursePlanWord,
  getCoursePlanDetail,
  previewCoursePlanPdfUrl,
  saveCoursePlan,
} from '../api/http'
import AppTopbar from '../components/AppTopbar.vue'
import CoursePlanDocumentEditor from '../components/course-plan/CoursePlanDocumentEditor.vue'
import { useAuthenticatedPage } from '../composables/useAuthenticatedPage'
import { cloneCoursePlan } from '../utils/coursePlan'

const router = useRouter()
const route = useRoute()

const loadingDetail = ref(false)
const saving = ref(false)
const detail = ref(null)
const draftAnalysis = ref(null)
const draftContent = ref(null)
const documentEditorRef = ref(null)
const activeAnchor = ref('cover')

const {
  user,
  authChecking,
  handleLogout,
} = useAuthenticatedPage(async () => {
  await loadDetail(route.params.id)
})

const editorTitle = computed(() => `${draftContent.value?.basicInfo?.courseName || detail.value?.courseName || '课程教案'}编辑`)

watch(
  () => route.params.id,
  async (id) => {
    if (!user.value || !id) return
    await loadDetail(id)
  },
)

async function loadDetail(id) {
  if (!id) return
  loadingDetail.value = true
  try {
    const current = await getCoursePlanDetail(id)
    detail.value = current
    draftAnalysis.value = cloneCoursePlan(current.analysis)
    draftContent.value = cloneCoursePlan(current.content)
    activeAnchor.value = 'cover'
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取课程教案失败')
    await router.replace({ name: 'lesson-list' })
  } finally {
    loadingDetail.value = false
  }
}

async function handleSave() {
  if (!draftAnalysis.value || !draftContent.value) return
  saving.value = true
  try {
    detail.value = await saveCoursePlan(route.params.id, {
      analysis: draftAnalysis.value,
      content: draftContent.value,
      teacherRequirements: draftContent.value.teacherRequirements || detail.value?.teacherRequirements || '',
      status: detail.value?.status || 'draft',
    })
    draftAnalysis.value = cloneCoursePlan(detail.value.analysis)
    draftContent.value = cloneCoursePlan(detail.value.content)
    ElMessage.success('课程教案已保存')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function handlePreviewPdf() {
  window.open(previewCoursePlanPdfUrl(route.params.id), '_blank', 'noopener')
}

async function handleExportWord() {
  try {
    await exportCoursePlanWord(route.params.id)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '下载 Word 失败')
  }
}

async function handleExportPdf() {
  try {
    await exportCoursePlanPdf(route.params.id)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '下载 PDF 失败')
  }
}

function updateBasicInfo(field, value) {
  if (!draftContent.value?.basicInfo || !draftAnalysis.value?.basicInfo) return
  const nextTitle = field === 'courseName' ? `${value || ''}课程教案` : draftContent.value.title
  draftContent.value = {
    ...draftContent.value,
    title: nextTitle,
    basicInfo: {
      ...draftContent.value.basicInfo,
      [field]: value,
    },
  }
  draftAnalysis.value = {
    ...draftAnalysis.value,
    basicInfo: {
      ...draftAnalysis.value.basicInfo,
      [field]: value,
    },
  }
}

function updateSourceContextList(field, text) {
  if (!draftAnalysis.value?.sourceContext) return
  draftAnalysis.value = {
    ...draftAnalysis.value,
    sourceContext: {
      ...draftAnalysis.value.sourceContext,
      [field]: splitLines(text),
    },
  }
}

function updateContentField(field, value) {
  if (!draftContent.value) return
  draftContent.value = {
    ...draftContent.value,
    [field]: value,
  }
}

function updateUnitField(unitIndex, field, value) {
  const units = cloneCoursePlan(draftContent.value?.units) || []
  if (!units[unitIndex]) return
  units[unitIndex][field] = value
  draftContent.value = {
    ...draftContent.value,
    units,
  }
}

function updateUnitListField(unitIndex, field, text) {
  updateUnitField(unitIndex, field, splitLines(text))
}

function updateDesignField(unitIndex, designIndex, field, value) {
  const units = cloneCoursePlan(draftContent.value?.units) || []
  const design = units[unitIndex]?.teachingDesigns?.[designIndex]
  if (!design) return
  design[field] = value
  draftContent.value = {
    ...draftContent.value,
    units,
  }
}

function updateDesignListField(unitIndex, designIndex, field, text) {
  updateDesignField(unitIndex, designIndex, field, splitLines(text))
}

function updateMainBlockField(unitIndex, designIndex, blockIndex, field, value) {
  const units = cloneCoursePlan(draftContent.value?.units) || []
  const block = units[unitIndex]?.teachingDesigns?.[designIndex]?.mainContentBlocks?.[blockIndex]
  if (!block) return
  block[field] = value
  draftContent.value = {
    ...draftContent.value,
    units,
  }
}

function updateMainBlockListField(unitIndex, designIndex, blockIndex, field, text) {
  updateMainBlockField(unitIndex, designIndex, blockIndex, field, splitLines(text))
}

function splitLines(text) {
  return String(text || '')
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
}

function scrollToDocument(anchor) {
  activeAnchor.value = anchor
  documentEditorRef.value?.scrollToAnchor?.(anchor)
}

function coverAnchor(section = '') {
  return section ? `cover:${section}` : 'cover'
}

function unitAnchor(unitIndex, section = '') {
  return section ? `unit:${unitIndex}:${section}` : `unit:${unitIndex}`
}

function designAnchor(unitIndex, designIndex, section = '') {
  return section ? `design:${unitIndex}:${designIndex}:${section}` : `design:${unitIndex}:${designIndex}`
}

function handleActiveAnchorChange(anchor) {
  const key = String(anchor || '')
  if (!key) return
  if (key.startsWith('cover:')) {
    activeAnchor.value = coverAnchor()
    return
  }
  if (key.startsWith('unit:')) {
    const [, unitIndex] = key.split(':')
    activeAnchor.value = unitAnchor(Number(unitIndex))
    return
  }
  if (key.startsWith('design:')) {
    const [, unitIndex, designIndex] = key.split(':')
    activeAnchor.value = designAnchor(Number(unitIndex), Number(designIndex))
    return
  }
  activeAnchor.value = key
}

function goHome() {
  router.push({ name: 'course-plan-new' })
}

function goLessons() {
  router.push({ name: 'lesson-list' })
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
  width: min(100%, 1680px);
  margin: 0 auto;
  padding: 26px 24px 40px;
  display: grid;
  gap: 20px;
}

.editor-topbar,
.topbar-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.editor-topbar h1 {
  margin: 0;
  color: #102a43;
}

.editor-topbar p {
  margin: 6px 0 0;
  color: #627d98;
}

.topbar-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.editor-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.editor-nav {
  position: sticky;
  top: 104px;
  max-height: calc(100vh - 128px);
  overflow: auto;
  padding: 16px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #d9e2ec;
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.06);
}

.nav-section {
  display: grid;
  gap: 8px;
  margin-top: 18px;
}

.nav-section h2 {
  margin: 0 0 4px;
  color: #102a43;
  font-size: 14px;
}

.nav-unit-label {
  margin: 8px 0 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.nav-item {
  width: 100%;
  border: 0;
  border-radius: 14px;
  padding: 11px 12px;
  background: transparent;
  color: #334e68;
  display: grid;
  gap: 4px;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.nav-item strong {
  font-size: 14px;
}

.nav-item span {
  overflow: hidden;
  color: inherit;
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-item:hover,
.nav-item.active {
  background: #eff6ff;
  color: #0b5ed7;
  box-shadow: inset 3px 0 0 #2f80ed;
}

.nav-item--cover {
  color: #102a43;
  font-weight: 800;
  background: #f8fafc;
}

.nav-item--design {
  padding-left: 18px;
}

.document-editor-pane {
  min-width: 0;
}

@media (max-width: 1320px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }

  .editor-nav {
    position: static;
    max-height: none;
  }
}

@media (max-width: 820px) {
  .editor-topbar,
  .topbar-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
