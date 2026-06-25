<template>
  <section v-if="analysis" class="analyze-panel">
    <div class="panel-head">
      <div>
        <p class="eyebrow">解析结果确认</p>
        <h2>先确认课程信息和单元拆分，再继续生成</h2>
      </div>
      <el-tag :type="analysisReady ? 'success' : 'danger'" effect="dark">
        {{ analysisReady ? '可生成' : '存在冲突' }}
      </el-tag>
    </div>

    <el-alert
      v-if="issues.length"
      class="issues-alert"
      :type="analysisReady ? 'warning' : 'error'"
      :closable="false"
      show-icon
    >
      <template #title>
        {{ analysisReady ? '已识别到提示项，请确认后生成。' : '检测到阻断项，请先修正或重新上传材料。' }}
      </template>
      <ul class="issue-list">
        <li v-for="item in issues" :key="`${item.code}-${item.message}`">
          {{ item.message }}
        </li>
      </ul>
    </el-alert>

    <article class="card-section">
      <div class="section-head">
        <h3>课程基本信息</h3>
        <span>从课程标准和课件中解析，可在此修正。</span>
      </div>
      <div class="basic-grid">
        <label v-for="field in basicFields" :key="field.key" class="basic-field">
          <span>{{ field.label }}</span>
          <el-input
            :model-value="analysis.basicInfo?.[field.key] ?? ''"
            :type="field.type || 'text'"
            @update:model-value="emit('update-basic-info', field.key, $event)"
          />
        </label>
      </div>
    </article>

    <article class="card-section">
      <div class="section-head">
        <h3>模板识别结果</h3>
        <span>{{ analysis.templateFileName }}</span>
      </div>
      <div class="template-checks">
        <div class="check-pill" :class="{ ok: analysis.templateCheck?.courseCoverDetected }">课程首页</div>
        <div class="check-pill" :class="{ ok: analysis.templateCheck?.unitCoverDetected }">单元首页</div>
        <div class="check-pill" :class="{ ok: analysis.templateCheck?.teachingDesignDetected }">教学设计页</div>
      </div>
    </article>

    <article v-if="teachingCalendar" class="card-section">
      <div class="section-head">
        <h3>教学日历</h3>
        <span>{{ teachingCalendar.fileName }} · {{ calendarEntryCount }} 次课</span>
      </div>
      <el-table :data="calendarPreviewRows" border class="calendar-table">
        <el-table-column prop="week" label="周次" width="100" />
        <el-table-column prop="session" label="课次" width="100" />
        <el-table-column prop="periodCount" label="学时" width="100" />
        <el-table-column prop="lessonType" label="课型" width="120" />
        <el-table-column prop="topic" label="基本教学内容" min-width="360" />
      </el-table>
    </article>

    <article class="card-section">
      <div class="section-head">
        <h3>单元拆分预览</h3>
        <span>{{ splitStrategyText }}，课件匹配支持人工修正。</span>
      </div>

      <el-table :data="analysis.units || []" border class="unit-table">
        <el-table-column label="单元" width="90">
          <template #default="{ row }">{{ row.code }}</template>
        </el-table-column>
        <el-table-column label="单元名称" min-width="220">
          <template #default="{ row, $index }">
            <el-input
              :model-value="row.name"
              @update:model-value="emit('update-unit-field', $index, 'name', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="学时" width="120">
          <template #default="{ row, $index }">
            <el-input-number
              :model-value="row.hours"
              :min="0"
              :step="1"
              @update:model-value="emit('update-unit-field', $index, 'hours', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="教学设计" width="160">
          <template #default="{ row }">
            <div class="design-count-cell">
              <el-tag :type="row.teachingDesignCount > 0 ? 'success' : 'danger'">
                {{ row.teachingDesignCount || 0 }} 次
              </el-tag>
              <span v-if="formatDesignHours(row)" class="design-hours">{{ formatDesignHours(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="周次" width="130">
          <template #default="{ row }">
            {{ row.weekRange || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="匹配 PPT / 课件" min-width="300">
          <template #default="{ row, $index }">
            <el-select
              :model-value="row.matchedPptFiles || []"
              multiple
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择课件"
              @update:model-value="emit('update-unit-field', $index, 'matchedPptFiles', $event)"
            >
              <el-option
                v-for="item in pptOptions"
                :key="item.fileName"
                :label="item.title || item.fileName"
                :value="item.fileName"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ready' ? 'success' : 'danger'">
              {{ row.status === 'ready' ? '正常' : '阻断' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <div class="actions">
      <el-button @click="emit('reset')">重新上传材料</el-button>
      <el-button
        v-if="saveAsNewVisible"
        size="large"
        :loading="generating"
        :disabled="!analysisReady"
        @click="emit('generate-save-as-new')"
      >
        另存为新教案
      </el-button>
      <el-button
        type="primary"
        size="large"
        :loading="generating"
        :disabled="!analysisReady"
        @click="emit('generate')"
      >
        {{ generating ? '正在生成课程教案...' : '生成课程教案' }}
      </el-button>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  analysis: { type: Object, default: null },
  generating: { type: Boolean, default: false },
  analysisReady: { type: Boolean, default: false },
  saveAsNewVisible: { type: Boolean, default: false },
})

const emit = defineEmits(['update-basic-info', 'update-unit-field', 'generate', 'generate-save-as-new', 'reset'])

const basicFields = [
  { key: 'school', label: '学校' },
  { key: 'department', label: '学院/系部' },
  { key: 'courseName', label: '课程名称' },
  { key: 'courseCode', label: '课程代码' },
  { key: 'targetStudents', label: '授课对象' },
  { key: 'courseNature', label: '课程性质' },
  { key: 'credits', label: '学分' },
  { key: 'totalHours', label: '总学时' },
  { key: 'theoryHours', label: '理论学时' },
  { key: 'practiceHours', label: '实践学时' },
  { key: 'prerequisites', label: '先修课程' },
  { key: 'followUpCourses', label: '后续课程' },
]

const pptOptions = computed(() => props.analysis?.sourceContext?.pptMaterials || [])
const issues = computed(() => props.analysis?.conflicts || [])
const teachingCalendar = computed(() => props.analysis?.sourceContext?.teachingCalendar || null)
const calendarPreviewRows = computed(() => (teachingCalendar.value?.entries || []).slice(0, 8))
const calendarEntryCount = computed(() => teachingCalendar.value?.entries?.length || teachingCalendar.value?.rowCount || 0)
const splitStrategyText = computed(() => {
  if (props.analysis?.splitStrategy === 'FLEXIBLE_HOURS') {
    return '按教学日历课次分配教学设计'
  }
  return '按 2 学时模式拆分教学设计'
})

function formatDesignHours(row) {
  const hours = Array.isArray(row.teachingDesignHours) ? row.teachingDesignHours.filter(Boolean) : []
  if (!hours.length) return ''
  return hours.map((item) => `${item}学时`).join(' + ')
}
</script>

<style scoped>
.analyze-panel {
  display: grid;
  gap: 20px;
}

.panel-head,
.section-head,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #0f766e;
}

.panel-head h2,
.section-head h3 {
  margin: 0;
  color: #102a43;
}

.card-section {
  padding: 20px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #d9e2ec;
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.05);
}

.section-head span {
  color: #627d98;
}

.design-count-cell {
  display: grid;
  gap: 6px;
  justify-items: start;
}

.design-hours {
  color: #627d98;
  font-size: 12px;
  line-height: 1.4;
}

.issues-alert :deep(.el-alert__content) {
  width: 100%;
}

.issue-list {
  margin: 8px 0 0;
  padding-left: 18px;
  line-height: 1.7;
}

.basic-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.basic-field {
  display: grid;
  gap: 8px;
  color: #243b53;
}

.template-checks {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.check-pill {
  padding: 10px 14px;
  border-radius: 999px;
  background: #fee2e2;
  color: #b91c1c;
  font-weight: 600;
}

.check-pill.ok {
  background: #dcfce7;
  color: #166534;
}

.unit-table {
  margin-top: 18px;
}

.calendar-table {
  margin-top: 18px;
}

@media (max-width: 1080px) {
  .basic-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .panel-head,
  .section-head,
  .actions {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 720px) {
  .basic-grid {
    grid-template-columns: 1fr;
  }
}
</style>
