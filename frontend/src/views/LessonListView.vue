<template>
  <div class="lesson-list-page">
    <AppTopbar
      v-if="user"
      active="lessons"
      :user="user"
      @home="goHome"
      @new="goHome"
      @lessons="goLessons"
      @admin="goAdminUsers"
      @logout="handleLogout"
    />

    <main class="page-main">
      <el-skeleton v-if="authChecking" :rows="8" animated />

      <section v-else-if="user" class="panel">
        <div class="filter-bar">
          <el-select v-model="courseFilter" clearable placeholder="按课程筛选">
            <el-option v-for="item in courseOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-input v-model="keyword" clearable placeholder="搜索标题、课程或章节" />
          <el-button :loading="loading" @click="loadPlans">刷新</el-button>
        </div>

        <el-table v-loading="loading" :data="pagedPlans" empty-text="暂无教案记录">
          <el-table-column type="index" label="序号" width="55" :index="tableIndex" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="row.planKind === 'course-plan' ? 'success' : 'info'">{{ row.typeLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="240" />
          <el-table-column prop="courseName" label="课程名称" min-width="160" />
          <el-table-column prop="teacherName" label="教师姓名" width="120" />
          <el-table-column prop="department" label="院系" min-width="190" />
          <el-table-column prop="topic" label="章节主题" min-width="160" />
          <el-table-column label="更新时间" width="180">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="560" fixed="right">
            <template #default="{ row }">
              <div class="actions">
                <template v-if="row.planKind === 'course-plan'">
                  <el-button size="small" type="primary" plain round @click.stop="editCoursePlan(row)">编辑</el-button>
                  <el-button size="small" type="primary" plain round @click.stop="editCoursePlanMaterials(row)">修改材料</el-button>
                  <el-button size="small" type="success" plain round @click.stop="previewCoursePlan(row)">预览PDF</el-button>
                  <el-button size="small" type="success" plain round :loading="isExporting(row, 'word')" @click.stop="exportPlan(row, 'word')">下载Word</el-button>
                  <el-button size="small" type="success" plain round :loading="isExporting(row, 'pdf')" @click.stop="exportPlan(row, 'pdf')">下载PDF</el-button>
                  <el-button size="small" type="danger" plain round @click.stop="removeCoursePlan(row)">删除</el-button>
                </template>
                <template v-else>
                  <el-button size="small" type="primary" plain round @click.stop="openPlan(row)">编辑</el-button>
                  <el-button size="small" type="success" plain round :loading="isExporting(row, 'word')" @click.stop="exportPlan(row, 'word')">下载Word</el-button>
                  <el-button size="small" type="danger" plain round @click.stop="removeLesson(row)">删除</el-button>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="filteredPlans.length" class="lesson-pagination">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="pageSizes"
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="filteredPlans.length"
            @size-change="handlePageSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteCoursePlan,
  deleteLessonPlan,
  exportCoursePlanPdf,
  exportCoursePlanWord,
  exportLessonPlanWord,
  getCoursePlans,
  getLessonPlans,
  previewCoursePlanPdfUrl,
} from '../api/http'
import AppTopbar from '../components/AppTopbar.vue'
import { useAuthenticatedPage } from '../composables/useAuthenticatedPage'

const router = useRouter()
const loading = ref(false)
const plans = ref([])
const keyword = ref('')
const courseFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const exportingPlanKey = ref('')
const pageSizes = [10, 20, 50, 100]

const filteredPlans = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return plans.value.filter((item) => {
    const matchesCourse = !courseFilter.value || item.courseName === courseFilter.value
    const haystack = [item.title, item.courseName, item.teacherName, item.department, item.topic, item.typeLabel]
      .join(' ')
      .toLowerCase()
    return matchesCourse && (!word || haystack.includes(word))
  })
})

const courseOptions = computed(() => Array.from(new Set(plans.value.map((item) => item.courseName).filter(Boolean))))

const pagedPlans = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredPlans.value.slice(start, start + pageSize.value)
})

watch([keyword, courseFilter], () => {
  currentPage.value = 1
})

watch(filteredPlans, (items) => {
  const maxPage = Math.max(1, Math.ceil(items.length / pageSize.value))
  if (currentPage.value > maxPage) {
    currentPage.value = maxPage
  }
})

const { user, authChecking, handleLogout } = useAuthenticatedPage(async () => {
  await loadPlans()
})

async function loadPlans() {
  loading.value = true
  try {
    const [coursePlans, lessonPlans] = await Promise.all([getCoursePlans(), getLessonPlans()])
    plans.value = [
      ...(coursePlans || []).map(normalizeCoursePlan),
      ...(lessonPlans || []).map(normalizeLessonPlan),
    ].sort((left, right) => String(right.updatedAt || '').localeCompare(String(left.updatedAt || '')))
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取教案列表失败')
  } finally {
    loading.value = false
  }
}

function normalizeCoursePlan(item) {
  return {
    ...item,
    planKind: 'course-plan',
    typeLabel: '课程教案',
    topic: '整门课程教案',
  }
}

function normalizeLessonPlan(item) {
  return {
    ...item,
    planKind: 'lesson-plan',
    typeLabel: '单次课教案',
  }
}

function openPlan(row) {
  router.push({ name: 'lesson-edit', params: { id: row.id } })
}

function editCoursePlan(row) {
  router.push({ name: 'course-plan-edit', params: { id: row.id } })
}

function editCoursePlanMaterials(row) {
  router.push({ name: 'course-plan-materials', params: { id: row.id } })
}

function goHome() {
  router.push({ name: 'course-plan-new' })
}

function goLessons() {
  router.push({ name: 'lesson-list' })
}

function goAdminUsers() {
  router.push({ name: 'admin-users' })
}

async function exportPlan(row, format) {
  const key = exportKey(row, format)
  exportingPlanKey.value = key
  if (row.planKind === 'course-plan') {
    try {
      if (format === 'pdf') {
        await exportCoursePlanPdf(row.id)
      } else {
        await exportCoursePlanWord(row.id)
      }
    } catch (error) {
      ElMessage.error(error.response?.data?.message || error.message || '下载课程教案失败')
    } finally {
      exportingPlanKey.value = ''
    }
    return
  }
  try {
    await exportLessonPlanWord(row)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '下载单次课教案失败')
  } finally {
    exportingPlanKey.value = ''
  }
}

function exportKey(row, format) {
  return `${row.planKind}:${row.id}:${format}`
}

function isExporting(row, format) {
  return exportingPlanKey.value === exportKey(row, format)
}

function previewCoursePlan(row) {
  window.open(previewCoursePlanPdfUrl(row.id), '_blank', 'noopener')
}

async function removeLesson(row) {
  await ElMessageBox.confirm('确定删除这份单次课教案吗？', '删除确认', { type: 'warning' })
  await deleteLessonPlan(row.id)
  ElMessage.success('单次课教案已删除')
  await loadPlans()
}

async function removeCoursePlan(row) {
  await ElMessageBox.confirm('确定删除这份课程教案吗？', '删除确认', { type: 'warning' })
  await deleteCoursePlan(row.id)
  ElMessage.success('课程教案已删除')
  await loadPlans()
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function tableIndex(index) {
  return (currentPage.value - 1) * pageSize.value + index + 1
}

function handlePageSizeChange() {
  currentPage.value = 1
}

function handlePageChange(page) {
  currentPage.value = page
}
</script>

<style scoped>
.lesson-list-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.1), transparent 26%),
    radial-gradient(circle at top right, rgba(147, 197, 253, 0.18), transparent 28%),
    #f4f8ff;
}

.page-main {
  width: min(100%, 1720px);
  margin: 0 auto;
  padding: 26px 24px 40px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.panel {
  padding: 22px;
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #d9e5f7;
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.06);
}

.filter-bar {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) auto;
  gap: 14px;
  margin-bottom: 18px;
}

.lesson-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

@media (max-width: 960px) {
  .page-main {
    padding: 18px 16px 32px;
  }

  .filter-bar {
    grid-template-columns: 1fr;
  }
}
</style>
