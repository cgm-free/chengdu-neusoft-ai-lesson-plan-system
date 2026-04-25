<template>
  <section ref="documentRoot" class="document-editor">
    <div class="document-head">
      <h2>{{ title }}</h2>
      <span>右侧为结构化可编辑文档页，保存后用于 Word / PDF 导出。</span>
    </div>

    <article
      class="document-page cover-page"
      data-anchor="cover"
      data-nav-anchor="cover"
      :class="anchorClass('cover')"
      @focusin="focusAnchor('cover')"
    >
      <header class="cover-header">
        <el-input
          class="cover-title-input cover-school"
          :model-value="basicInfo.school || ''"
          placeholder="学校"
          @update:model-value="emitBasic('school', $event)"
        />
        <el-input
          class="cover-title-input cover-college"
          :model-value="basicInfo.department || ''"
          placeholder="学院/系部"
          @update:model-value="emitBasic('department', $event)"
        />
        <div class="course-title-line">
          <span>《</span>
          <el-input
            class="cover-title-input cover-course"
            :model-value="basicInfo.courseName || ''"
            placeholder="课程名称"
            @update:model-value="emitBasic('courseName', $event)"
          />
          <span>》课程教案</span>
        </div>
      </header>

      <div class="cover-grid">
        <label class="cover-item" data-anchor="cover:semester" :class="anchorClass('cover:semester')">
          <span>学年学期</span>
          <el-input
            :model-value="basicInfo.semester || ''"
            placeholder="例如：2025-2026学年第2学期"
            @focus="focusAnchor('cover:semester')"
            @update:model-value="emitBasic('semester', $event)"
          />
        </label>
        <label class="cover-item" data-anchor="cover:courseCode" :class="anchorClass('cover:courseCode')">
          <span>课程代码</span>
          <el-input
            :model-value="basicInfo.courseCode || ''"
            @focus="focusAnchor('cover:courseCode')"
            @update:model-value="emitBasic('courseCode', $event)"
          />
        </label>
        <label class="cover-item" data-anchor="cover:courseNature" :class="anchorClass('cover:courseNature')">
          <span>课程性质</span>
          <el-input
            :model-value="basicInfo.courseNature || ''"
            @focus="focusAnchor('cover:courseNature')"
            @update:model-value="emitBasic('courseNature', $event)"
          />
        </label>
        <label class="cover-item" data-anchor="cover:targetStudents" :class="anchorClass('cover:targetStudents')">
          <span>年级专业 / 授课对象</span>
          <el-input
            :model-value="basicInfo.targetStudents || ''"
            @focus="focusAnchor('cover:targetStudents')"
            @update:model-value="emitBasic('targetStudents', $event)"
          />
        </label>
        <label class="cover-item">
          <span>课程负责人</span>
          <el-input
            :model-value="basicInfo.responsibleTeacher || ''"
            @update:model-value="emitBasic('responsibleTeacher', $event)"
          />
        </label>
        <label class="cover-item">
          <span>任课教师</span>
          <el-input
            :model-value="basicInfo.teacherName || ''"
            @update:model-value="emitBasic('teacherName', $event)"
          />
        </label>
        <div class="cover-item full hours-row">
          <label>
            <span>总学时</span>
            <el-input-number
              :model-value="numberValue(basicInfo.totalHours)"
              :min="0"
              controls-position="right"
              @update:model-value="emitBasic('totalHours', Number($event || 0))"
            />
          </label>
          <label>
            <span>理论学时</span>
            <el-input-number
              :model-value="numberValue(basicInfo.theoryHours)"
              :min="0"
              controls-position="right"
              @update:model-value="emitBasic('theoryHours', Number($event || 0))"
            />
          </label>
          <label>
            <span>实践学时</span>
            <el-input-number
              :model-value="numberValue(basicInfo.practiceHours)"
              :min="0"
              controls-position="right"
              @update:model-value="emitBasic('practiceHours', Number($event || 0))"
            />
          </label>
          <label>
            <span>学分</span>
            <el-input
              :model-value="basicInfo.credits || ''"
              @update:model-value="emitBasic('credits', $event)"
            />
          </label>
        </div>
        <label class="cover-item full" data-anchor="cover:resources" :class="anchorClass('cover:resources')">
          <span>教材及参考资料</span>
          <el-input
            :model-value="content.textbooksAndReferences || ''"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 10 }"
            @focus="focusAnchor('cover:resources')"
            @update:model-value="emit('update-content-field', 'textbooksAndReferences', $event)"
          />
        </label>
        <label class="cover-item full" data-anchor="cover:resources" :class="anchorClass('cover:resources')">
          <span>其他教学资源</span>
          <el-input
            :model-value="content.otherTeachingResources || ''"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 10 }"
            @focus="focusAnchor('cover:resources')"
            @update:model-value="emit('update-content-field', 'otherTeachingResources', $event)"
          />
        </label>
        <label class="cover-item full" data-anchor="cover:environment" :class="anchorClass('cover:environment')">
          <span>教学环境</span>
          <el-input
            :model-value="content.courseEnvironment || ''"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            @focus="focusAnchor('cover:environment')"
            @update:model-value="emit('update-content-field', 'courseEnvironment', $event)"
          />
        </label>
      </div>
    </article>

    <article
      v-for="(unit, unitIndex) in units"
      :key="unit.code || unit.index || unitIndex"
      class="document-page unit-page"
      :data-anchor="unitAnchor(unitIndex)"
      :data-nav-anchor="unitAnchor(unitIndex)"
      :class="anchorClass(unitAnchor(unitIndex))"
      @focusin="focusAnchor(unitAnchor(unitIndex))"
    >
      <header class="unit-header">
        <div>
          <p class="unit-code">{{ unit.code || `CU(${unitIndex + 1})` }}</p>
          <el-input
            class="unit-name-input"
            :model-value="unit.name || ''"
            placeholder="单元名称"
            @update:model-value="emit('update-unit-field', unitIndex, 'name', $event)"
          />
        </div>
        <label class="unit-hours">
          <span>学时</span>
          <el-input-number
            :model-value="numberValue(unit.hours)"
            :min="0"
            :step="2"
            controls-position="right"
            @update:model-value="emit('update-unit-field', unitIndex, 'hours', Number($event || 0))"
          />
        </label>
      </header>

      <div class="unit-grid">
        <label class="unit-item" :data-anchor="unitAnchor(unitIndex, 'environment')" :class="anchorClass(unitAnchor(unitIndex, 'environment'))">
          <span>教学环境设计</span>
          <el-input
            :model-value="unit.environmentDesign || ''"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            @focus="focusAnchor(unitAnchor(unitIndex, 'environment'))"
            @update:model-value="emit('update-unit-field', unitIndex, 'environmentDesign', $event)"
          />
        </label>
        <label class="unit-item" :data-anchor="unitAnchor(unitIndex, 'project')" :class="anchorClass(unitAnchor(unitIndex, 'project'))">
          <span>项目名称（级别）</span>
          <el-input
            :model-value="unit.projectName || ''"
            placeholder="无"
            @focus="focusAnchor(unitAnchor(unitIndex, 'project'))"
            @update:model-value="emit('update-unit-field', unitIndex, 'projectName', $event)"
          />
        </label>
        <label class="unit-item" :data-anchor="unitAnchor(unitIndex, 'objectives')" :class="anchorClass(unitAnchor(unitIndex, 'objectives'))">
          <span>理论知识</span>
          <el-input
            :model-value="joinLines(unit.theoryObjectives)"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            @focus="focusAnchor(unitAnchor(unitIndex, 'objectives'))"
            @update:model-value="emit('update-unit-list-field', unitIndex, 'theoryObjectives', $event)"
          />
        </label>
        <label class="unit-item" :data-anchor="unitAnchor(unitIndex, 'objectives')" :class="anchorClass(unitAnchor(unitIndex, 'objectives'))">
          <span>专业技能</span>
          <el-input
            :model-value="joinLines(unit.skillObjectives)"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            @focus="focusAnchor(unitAnchor(unitIndex, 'objectives'))"
            @update:model-value="emit('update-unit-list-field', unitIndex, 'skillObjectives', $event)"
          />
        </label>
        <label class="unit-item" :data-anchor="unitAnchor(unitIndex, 'objectives')" :class="anchorClass(unitAnchor(unitIndex, 'objectives'))">
          <span>个人素质</span>
          <el-input
            :model-value="joinLines(unit.qualityObjectives)"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            @focus="focusAnchor(unitAnchor(unitIndex, 'objectives'))"
            @update:model-value="emit('update-unit-list-field', unitIndex, 'qualityObjectives', $event)"
          />
        </label>
        <label class="unit-item" :data-anchor="unitAnchor(unitIndex, 'points')" :class="anchorClass(unitAnchor(unitIndex, 'points'))">
          <span>教学重点</span>
          <el-input
            :model-value="joinLines(unit.keyPoints)"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            @focus="focusAnchor(unitAnchor(unitIndex, 'points'))"
            @update:model-value="emit('update-unit-list-field', unitIndex, 'keyPoints', $event)"
          />
        </label>
        <label class="unit-item" :data-anchor="unitAnchor(unitIndex, 'points')" :class="anchorClass(unitAnchor(unitIndex, 'points'))">
          <span>教学难点</span>
          <el-input
            :model-value="joinLines(unit.difficultPoints)"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            @focus="focusAnchor(unitAnchor(unitIndex, 'points'))"
            @update:model-value="emit('update-unit-list-field', unitIndex, 'difficultPoints', $event)"
          />
        </label>
        <label class="unit-item" :data-anchor="unitAnchor(unitIndex, 'methods')" :class="anchorClass(unitAnchor(unitIndex, 'methods'))">
          <span>教学方法手段媒介</span>
          <el-input
            :model-value="unit.teachingMethods || ''"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            @focus="focusAnchor(unitAnchor(unitIndex, 'methods'))"
            @update:model-value="emit('update-unit-field', unitIndex, 'teachingMethods', $event)"
          />
        </label>
        <label class="unit-item" :data-anchor="unitAnchor(unitIndex, 'organization')" :class="anchorClass(unitAnchor(unitIndex, 'organization'))">
          <span>教学组织方式</span>
          <el-input
            :model-value="unit.teachingOrganization || ''"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            @focus="focusAnchor(unitAnchor(unitIndex, 'organization'))"
            @update:model-value="emit('update-unit-field', unitIndex, 'teachingOrganization', $event)"
          />
        </label>
        <label class="unit-item full" :data-anchor="unitAnchor(unitIndex, 'project-intro')" :class="anchorClass(unitAnchor(unitIndex, 'project-intro'))">
          <span>项目简介要求</span>
          <el-input
            :model-value="unit.projectIntroduction || ''"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            @focus="focusAnchor(unitAnchor(unitIndex, 'project-intro'))"
            @update:model-value="emit('update-unit-field', unitIndex, 'projectIntroduction', $event)"
          />
        </label>
      </div>

      <article
        v-for="(design, designIndex) in unit.teachingDesigns || []"
        :key="`${unit.code || unitIndex}-${design.index || designIndex}`"
        class="design-card"
        :data-anchor="designAnchor(unitIndex, designIndex)"
        :data-nav-anchor="designAnchor(unitIndex, designIndex)"
        :class="anchorClass(designAnchor(unitIndex, designIndex))"
        @focusin="focusAnchor(designAnchor(unitIndex, designIndex))"
      >
        <div class="design-main">
          <el-input
            class="design-title-input"
            :model-value="design.title || ''"
            placeholder="课次标题"
            @update:model-value="emit('update-design-field', unitIndex, designIndex, 'title', $event)"
          />
          <div class="time-grid">
            <label>
              <span>讲评</span>
              <el-input-number :model-value="numberValue(design.afterClassReviewMinutes)" :min="0" controls-position="right" @update:model-value="emit('update-design-field', unitIndex, designIndex, 'afterClassReviewMinutes', Number($event || 0))" />
            </label>
            <label>
              <span>导入</span>
              <el-input-number :model-value="numberValue(design.introductionMinutes)" :min="0" controls-position="right" @update:model-value="emit('update-design-field', unitIndex, designIndex, 'introductionMinutes', Number($event || 0))" />
            </label>
            <label>
              <span>总结</span>
              <el-input-number :model-value="numberValue(design.summaryMinutes)" :min="0" controls-position="right" @update:model-value="emit('update-design-field', unitIndex, designIndex, 'summaryMinutes', Number($event || 0))" />
            </label>
            <label>
              <span>课外要求</span>
              <el-input-number :model-value="numberValue(design.assignmentMinutes)" :min="0" controls-position="right" @update:model-value="emit('update-design-field', unitIndex, designIndex, 'assignmentMinutes', Number($event || 0))" />
            </label>
          </div>
          <section class="design-section" :data-anchor="designAnchor(unitIndex, designIndex, 'review')" :class="anchorClass(designAnchor(unitIndex, designIndex, 'review'))">
            <h4>一、课外学习讲评（约{{ sectionMinutes(design.afterClassReviewMinutes, 10) }}分钟）</h4>
            <el-input
              :model-value="design.afterClassReview || ''"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 8 }"
              @focus="focusAnchor(designAnchor(unitIndex, designIndex, 'review'))"
              @update:model-value="emit('update-design-field', unitIndex, designIndex, 'afterClassReview', $event)"
            />
          </section>
          <section class="design-section" :data-anchor="designAnchor(unitIndex, designIndex, 'introduction')" :class="anchorClass(designAnchor(unitIndex, designIndex, 'introduction'))">
            <h4>二、内容导入（约{{ sectionMinutes(design.introductionMinutes, 10) }}分钟）</h4>
            <el-input
              :model-value="design.introduction || ''"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 10 }"
              @focus="focusAnchor(designAnchor(unitIndex, designIndex, 'introduction'))"
              @update:model-value="emit('update-design-field', unitIndex, designIndex, 'introduction', $event)"
            />
          </section>
          <section class="design-section" :data-anchor="designAnchor(unitIndex, designIndex, 'main')" :class="anchorClass(designAnchor(unitIndex, designIndex, 'main'))">
            <h4>三、主要内容设计（约{{ mainMinutes(design) }}分钟）</h4>
            <div class="block-list">
              <div v-for="(block, blockIndex) in design.mainContentBlocks || []" :key="`${block.title || 'block'}-${blockIndex}`" class="block-item">
                <div class="block-title-row">
                  <el-input
                    :model-value="block.title || ''"
                    placeholder="环节标题"
                    @focus="focusAnchor(designAnchor(unitIndex, designIndex, 'main'))"
                    @update:model-value="emit('update-main-block-field', unitIndex, designIndex, blockIndex, 'title', $event)"
                  />
                  <el-input-number
                    :model-value="numberValue(block.minutes)"
                    :min="0"
                    controls-position="right"
                    @focus="focusAnchor(designAnchor(unitIndex, designIndex, 'main'))"
                    @update:model-value="emit('update-main-block-field', unitIndex, designIndex, blockIndex, 'minutes', Number($event || 0))"
                  />
                </div>
                <el-input
                  :model-value="joinLines(block.points)"
                  type="textarea"
                  :autosize="{ minRows: 4, maxRows: 14 }"
                  @focus="focusAnchor(designAnchor(unitIndex, designIndex, 'main'))"
                  @update:model-value="emit('update-main-block-list-field', unitIndex, designIndex, blockIndex, 'points', $event)"
                />
              </div>
            </div>
          </section>
          <section class="design-section" :data-anchor="designAnchor(unitIndex, designIndex, 'summary')" :class="anchorClass(designAnchor(unitIndex, designIndex, 'summary'))">
            <h4>四、归纳总结（约{{ sectionMinutes(design.summaryMinutes, 10) }}分钟）</h4>
            <el-input
              :model-value="design.summary || ''"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 8 }"
              @focus="focusAnchor(designAnchor(unitIndex, designIndex, 'summary'))"
              @update:model-value="emit('update-design-field', unitIndex, designIndex, 'summary', $event)"
            />
          </section>
          <section class="design-section" :data-anchor="designAnchor(unitIndex, designIndex, 'assignments')" :class="anchorClass(designAnchor(unitIndex, designIndex, 'assignments'))">
            <h4>五、课外学习要求（约{{ sectionMinutes(design.assignmentMinutes, 5) }}分钟）</h4>
            <el-input
              :model-value="joinLines(design.assignments)"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 8 }"
              @focus="focusAnchor(designAnchor(unitIndex, designIndex, 'assignments'))"
              @update:model-value="emit('update-design-list-field', unitIndex, designIndex, 'assignments', $event)"
            />
          </section>
        </div>
        <aside class="design-remark" :data-anchor="designAnchor(unitIndex, designIndex, 'remarks')" :class="anchorClass(designAnchor(unitIndex, designIndex, 'remarks'))">
          <h4>注释及备注</h4>
          <el-input
            :model-value="joinLines(design.remarks)"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 16 }"
            @focus="focusAnchor(designAnchor(unitIndex, designIndex, 'remarks'))"
            @update:model-value="emit('update-design-list-field', unitIndex, designIndex, 'remarks', $event)"
          />
        </aside>
      </article>
    </article>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  analysis: { type: Object, default: null },
  content: { type: Object, default: null },
})

const emit = defineEmits([
  'active-anchor-change',
  'update-basic-info',
  'update-source-context-list',
  'update-content-field',
  'update-unit-field',
  'update-unit-list-field',
  'update-design-field',
  'update-design-list-field',
  'update-main-block-field',
  'update-main-block-list-field',
])

const documentRoot = ref(null)
const highlightedAnchor = ref('')
let highlightTimer = null
let observer = null

const basicInfo = computed(() => props.content?.basicInfo || {})
const sourceContext = computed(() => props.analysis?.sourceContext || {})
const content = computed(() => props.content || {})
const units = computed(() => props.content?.units || [])
const title = computed(() => `${basicInfo.value.courseName || '课程教案'}文档编辑`)

watch(
  () => units.value.length,
  () => nextTick(setupObserver),
)

onMounted(() => {
  nextTick(setupObserver)
})

onBeforeUnmount(() => {
  window.clearTimeout(highlightTimer)
  observer?.disconnect()
})

function setupObserver() {
  observer?.disconnect()
  if (!documentRoot.value) return
  observer = new IntersectionObserver((entries) => {
    const visible = entries
      .filter((entry) => entry.isIntersecting)
      .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
    const anchor = visible?.target?.getAttribute('data-nav-anchor')
    if (anchor) {
      emit('active-anchor-change', anchor)
    }
  }, {
    root: null,
    rootMargin: '-150px 0px -55% 0px',
    threshold: [0.01, 0.12, 0.24],
  })
  documentRoot.value.querySelectorAll('[data-nav-anchor]').forEach((element) => observer.observe(element))
}

function scrollToAnchor(anchor) {
  const key = String(anchor || '')
  if (!key || !documentRoot.value) return
  const selector = `[data-anchor="${escapeAttributeValue(key)}"]`
  const target = documentRoot.value.querySelector(selector)
  if (!target) return
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  focusAnchor(key)
}

function focusAnchor(anchor) {
  const key = String(anchor || '')
  if (!key) return
  emit('active-anchor-change', key)
  highlightedAnchor.value = key
  window.clearTimeout(highlightTimer)
  highlightTimer = window.setTimeout(() => {
    highlightedAnchor.value = ''
  }, 1600)
}

function escapeAttributeValue(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

function anchorClass(anchor) {
  return highlightedAnchor.value === anchor ? 'is-document-highlighted' : ''
}

function unitAnchor(unitIndex, section = '') {
  return section ? `unit:${unitIndex}:${section}` : `unit:${unitIndex}`
}

function designAnchor(unitIndex, designIndex, section = '') {
  return section ? `design:${unitIndex}:${designIndex}:${section}` : `design:${unitIndex}:${designIndex}`
}

function emitBasic(field, value) {
  emit('update-basic-info', field, value)
}

function numberValue(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function sectionMinutes(value, defaultValue) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : defaultValue
}

function mainMinutes(design) {
  return (design?.mainContentBlocks || []).reduce((sum, block) => sum + sectionMinutes(block.minutes, 0), 0)
}

function joinLines(values) {
  if (!Array.isArray(values)) return ''
  return values.filter(Boolean).join('\n')
}

defineExpose({ scrollToAnchor })
</script>

<style scoped>
.document-editor {
  display: grid;
  gap: 18px;
}

.document-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
}

.document-head h2 {
  margin: 0;
  color: #102a43;
}

.document-head span {
  color: #627d98;
  font-size: 13px;
}

.document-page {
  padding: 28px;
  border-radius: 22px;
  background: #ffffff;
  border: 1px solid #d9e2ec;
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.06);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
  scroll-margin-top: 128px;
}

.cover-header {
  display: grid;
  gap: 12px;
  justify-items: center;
  text-align: center;
  margin-bottom: 20px;
}

.cover-title-input {
  width: min(720px, 100%);
}

.cover-school :deep(.el-input__wrapper),
.cover-college :deep(.el-input__wrapper),
.cover-course :deep(.el-input__wrapper),
.unit-name-input :deep(.el-input__wrapper),
.design-title-input :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
  padding: 0;
}

.cover-school :deep(.el-input__inner) {
  height: 42px;
  text-align: center;
  color: #102a43;
  font-size: 30px;
  font-weight: 800;
}

.cover-college :deep(.el-input__inner) {
  height: 34px;
  text-align: center;
  color: #102a43;
  font-size: 22px;
  font-weight: 700;
}

.course-title-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #102a43;
  font-size: 30px;
  font-weight: 800;
}

.cover-course {
  width: 320px;
}

.cover-course :deep(.el-input__inner) {
  height: 40px;
  text-align: center;
  color: #102a43;
  font-size: 30px;
  font-weight: 800;
}

.cover-grid,
.unit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.cover-item,
.unit-item {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.cover-item.full,
.unit-item.full {
  grid-column: 1 / -1;
}

.cover-item > span,
.unit-item > span,
.hours-row label > span,
.time-grid label > span,
.unit-hours > span {
  font-weight: 700;
  color: #102a43;
}

.hours-row {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.hours-row,
.time-grid,
.block-title-row,
.unit-header {
  display: grid;
  gap: 12px;
}

.unit-header {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  margin-bottom: 20px;
}

.unit-code {
  margin: 0 0 4px;
  color: #0f766e;
  font-weight: 700;
}

.unit-name-input :deep(.el-input__inner) {
  color: #102a43;
  font-size: 24px;
  font-weight: 800;
}

.unit-hours {
  display: grid;
  gap: 8px;
}

.design-card {
  margin-top: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 18px;
  padding: 20px;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid #d9e2ec;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
  scroll-margin-top: 128px;
}

.design-title-input :deep(.el-input__inner) {
  color: #102a43;
  font-size: 20px;
  font-weight: 800;
}

.design-main {
  display: grid;
  gap: 16px;
}

.time-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 14px;
  border-radius: 14px;
  background: #ffffff;
}

.time-grid label,
.hours-row label {
  display: grid;
  gap: 6px;
}

.design-section {
  display: grid;
  gap: 8px;
  border-radius: 14px;
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
  scroll-margin-top: 128px;
}

.design-section h4,
.design-remark h4 {
  margin: 0;
  color: #102a43;
}

.block-list {
  display: grid;
  gap: 12px;
}

.block-item {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #ffffff;
}

.block-title-row {
  grid-template-columns: minmax(0, 1fr) 150px;
}

.design-remark {
  display: grid;
  align-content: start;
  gap: 10px;
  padding-left: 18px;
  border-left: 1px solid #d9e2ec;
  border-radius: 14px;
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
}

.is-document-highlighted {
  background: #eff6ff;
  border-color: #2f80ed;
  box-shadow: 0 0 0 3px rgba(47, 128, 237, 0.16);
}

@media (max-width: 1200px) {
  .design-card {
    grid-template-columns: 1fr;
  }

  .design-remark {
    padding-left: 0;
    border-left: 0;
    border-top: 1px solid #d9e2ec;
    padding-top: 18px;
  }
}

@media (max-width: 820px) {
  .cover-grid,
  .unit-grid,
  .hours-row,
  .time-grid,
  .block-title-row,
  .unit-header {
    grid-template-columns: 1fr;
  }

  .document-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .course-title-line {
    flex-wrap: wrap;
    font-size: 24px;
  }
}
</style>
