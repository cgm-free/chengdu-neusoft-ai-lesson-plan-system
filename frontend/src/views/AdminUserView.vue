<template>
  <div class="admin-users-page">
    <AppTopbar
      v-if="user"
      active="admin-users"
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
        <div class="panel-head">
          <div>
            <h1>用户管理</h1>
            <p>审核教师账号申请，维护用户状态，必要时重置密码。</p>
          </div>
        </div>

        <el-tabs v-model="activeAdminTab" class="admin-tabs" @tab-change="handleAdminTabChange">
          <el-tab-pane label="账号申请管理" name="requests">
            <div class="filter-bar request-filter-bar">
              <el-input v-model="requestKeyword" clearable placeholder="搜索姓名、用户名、系部、专业或课程" />
              <el-select v-model="requestStatusFilter" placeholder="按审核状态筛选" @change="loadRequests">
                <el-option label="全部" value="" />
                <el-option label="待审核" value="pending" />
                <el-option label="已通过" value="approved" />
                <el-option label="已拒绝" value="rejected" />
              </el-select>
              <el-button :loading="requestLoading" @click="loadRequests">刷新</el-button>
            </div>

            <el-table v-loading="requestLoading" :data="filteredRequests" empty-text="暂无账号申请">
              <el-table-column prop="realName" label="教师姓名" min-width="120" />
              <el-table-column prop="username" label="申请用户名" min-width="130" />
              <el-table-column prop="employeeNo" label="工号/手机号" min-width="130" />
              <el-table-column prop="phone" label="联系电话" min-width="130" />
              <el-table-column prop="department" label="系部" min-width="130" />
              <el-table-column prop="major" label="专业" min-width="170" />
              <el-table-column prop="courseName" label="课程名称" min-width="160" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="requestStatusType(row.status)">{{ requestStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="申请时间" width="180">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="210" fixed="right">
                <template #default="{ row }">
                  <div class="actions">
                    <template v-if="row.status === 'pending'">
                      <el-button size="small" type="success" plain round @click="approveRequest(row)">通过</el-button>
                      <el-button size="small" type="danger" plain round @click="rejectRequest(row)">拒绝</el-button>
                    </template>
                    <span v-else class="review-note">{{ row.reviewNote || '-' }}</span>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="用户管理" name="users">
            <div class="panel-toolbar">
              <div class="filter-bar">
                <el-input v-model="keyword" clearable placeholder="搜索账号、姓名或院系" />
                <el-select v-model="roleFilter" clearable placeholder="按角色筛选">
                  <el-option label="管理员" value="admin" />
                  <el-option label="教师" value="teacher" />
                </el-select>
                <el-select v-model="enabledFilter" clearable placeholder="按状态筛选">
                  <el-option label="启用" value="enabled" />
                  <el-option label="禁用" value="disabled" />
                </el-select>
                <el-button :loading="loading" @click="loadUsers">刷新</el-button>
              </div>
              <el-button type="primary" @click="openCreateDialog">新建用户</el-button>
            </div>

            <el-table v-loading="loading" :data="filteredUsers" empty-text="暂无用户">
              <el-table-column prop="username" label="账号" min-width="140" />
              <el-table-column prop="realName" label="姓名" min-width="140" />
              <el-table-column label="角色" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.role === 'admin' ? 'danger' : 'primary'">{{ roleLabel(row.role) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="department" label="院系" min-width="180" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="最后登录" width="180">
                <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
              </el-table-column>
              <el-table-column label="创建时间" width="180">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="300" fixed="right">
                <template #default="{ row }">
                  <div class="actions">
                    <el-button size="small" type="primary" plain round @click="openEditDialog(row)">编辑</el-button>
                    <el-button size="small" type="warning" plain round @click="openPasswordDialog(row)">重置密码</el-button>
                    <el-button
                      size="small"
                      :type="row.enabled ? 'danger' : 'success'"
                      plain
                      round
                      :disabled="isCurrentUser(row)"
                      @click="toggleUserEnabled(row)"
                    >
                      {{ row.enabled ? '禁用' : '启用' }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </section>
    </main>

    <el-dialog v-model="userDialogVisible" :title="dialogMode === 'create' ? '新建用户' : '编辑用户'" width="520px">
      <el-form :model="userForm" label-position="top">
        <el-form-item v-if="dialogMode === 'create'" label="账号">
          <el-input v-model="userForm.username" maxlength="64" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="初始密码">
          <el-input v-model="userForm.password" type="password" show-password maxlength="72" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="userForm.realName" maxlength="64" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="userForm.role">
            <el-radio-button label="teacher">教师</el-radio-button>
            <el-radio-button label="admin">管理员</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="院系">
          <el-input v-model="userForm.department" maxlength="128" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit'" label="状态">
          <el-switch
            v-model="userForm.enabled"
            active-text="启用"
            inactive-text="禁用"
            :disabled="isEditingCurrentUser"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="460px">
      <el-form label-position="top">
        <el-form-item label="用户">
          <el-input :model-value="passwordTargetLabel" disabled />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.password" type="password" show-password maxlength="72" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPassword" @click="savePassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveAccountRequest,
  createAdminUser,
  disableAdminUser,
  getAccountRequests,
  getAdminUsers,
  rejectAccountRequest,
  resetAdminUserPassword,
  updateAdminUser,
  updateAdminUserEnabled,
} from '../api/http'
import AppTopbar from '../components/AppTopbar.vue'
import { useAuthenticatedPage } from '../composables/useAuthenticatedPage'

const router = useRouter()
const activeAdminTab = ref('requests')
const users = ref([])
const requests = ref([])
const loading = ref(false)
const requestLoading = ref(false)
const saving = ref(false)
const savingPassword = ref(false)
const keyword = ref('')
const roleFilter = ref('')
const enabledFilter = ref('')
const requestKeyword = ref('')
const requestStatusFilter = ref('pending')
const userDialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const dialogMode = ref('create')
const editingUserId = ref(null)
const passwordTarget = ref(null)

const userForm = ref(emptyUserForm())
const passwordForm = ref({ password: '' })

const { user, authChecking, handleLogout } = useAuthenticatedPage(async () => {
  await Promise.all([loadUsers(), loadRequests()])
})

const filteredUsers = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return users.value.filter((item) => {
    const matchesRole = !roleFilter.value || item.role === roleFilter.value
    const matchesEnabled = !enabledFilter.value
      || (enabledFilter.value === 'enabled' ? item.enabled : !item.enabled)
    const haystack = [item.username, item.realName, item.department, roleLabel(item.role)].join(' ').toLowerCase()
    return matchesRole && matchesEnabled && (!word || haystack.includes(word))
  })
})

const filteredRequests = computed(() => {
  const word = requestKeyword.value.trim().toLowerCase()
  return requests.value.filter((item) => {
    const haystack = [
      item.realName,
      item.username,
      item.employeeNo,
      item.phone,
      item.college,
      item.department,
      item.major,
      item.courseName,
      requestStatusLabel(item.status),
    ].join(' ').toLowerCase()
    return !word || haystack.includes(word)
  })
})

const isEditingCurrentUser = computed(() => editingUserId.value && user.value?.id === editingUserId.value)
const passwordTargetLabel = computed(() => {
  if (!passwordTarget.value) return ''
  return `${passwordTarget.value.realName}（${passwordTarget.value.username}）`
})

async function loadUsers() {
  loading.value = true
  try {
    users.value = await getAdminUsers()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取用户列表失败')
  } finally {
    loading.value = false
  }
}

async function loadRequests() {
  requestLoading.value = true
  try {
    requests.value = await getAccountRequests(requestStatusFilter.value)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取账号申请失败')
  } finally {
    requestLoading.value = false
  }
}

async function handleAdminTabChange(tabName) {
  if (tabName === 'requests') {
    await loadRequests()
  } else {
    await loadUsers()
  }
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingUserId.value = null
  userForm.value = emptyUserForm()
  userDialogVisible.value = true
}

function openEditDialog(row) {
  dialogMode.value = 'edit'
  editingUserId.value = row.id
  userForm.value = {
    username: row.username,
    password: '',
    realName: row.realName || '',
    role: row.role || 'teacher',
    department: row.department || '',
    enabled: Boolean(row.enabled),
  }
  userDialogVisible.value = true
}

function openPasswordDialog(row) {
  passwordTarget.value = row
  passwordForm.value = { password: '' }
  passwordDialogVisible.value = true
}

async function saveUser() {
  const payload = normalizeUserPayload()
  if (!payload) return
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await createAdminUser(payload)
      ElMessage.success('用户已创建')
    } else {
      await updateAdminUser(editingUserId.value, payload)
      ElMessage.success('用户已更新')
    }
    userDialogVisible.value = false
    await loadUsers()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存用户失败')
  } finally {
    saving.value = false
  }
}

async function savePassword() {
  const password = passwordForm.value.password.trim()
  if (password.length < 6) {
    ElMessage.warning('新密码至少6个字符')
    return
  }
  savingPassword.value = true
  try {
    await resetAdminUserPassword(passwordTarget.value.id, { password })
    passwordDialogVisible.value = false
    ElMessage.success('密码已重置')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '重置密码失败')
  } finally {
    savingPassword.value = false
  }
}

async function toggleUserEnabled(row) {
  const nextEnabled = !row.enabled
  if (nextEnabled) {
    await ElMessageBox.confirm(`确定启用用户“${row.realName || row.username}”吗？启用后该用户可以重新登录。`, '启用确认', {
      type: 'warning',
    })
  } else {
    await ElMessageBox.confirm(`确定禁用用户“${row.realName || row.username}”吗？禁用后该用户无法登录。`, '禁用确认', {
      type: 'warning',
    })
  }
  try {
    if (nextEnabled) {
      await updateAdminUserEnabled(row.id, { enabled: true })
      ElMessage.success('用户已启用')
    } else {
      await disableAdminUser(row.id)
      ElMessage.success('用户已禁用')
    }
    await loadUsers()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '更新用户状态失败')
  }
}

async function approveRequest(row) {
  await ElMessageBox.confirm(`确定通过“${row.realName}”的账号申请吗？通过后会创建教师账号“${row.username}”。`, '通过申请', {
    type: 'warning',
  })
  try {
    await approveAccountRequest(row.id, { reviewNote: '审核通过' })
    ElMessage.success('账号申请已通过，教师账号已创建')
    await Promise.all([loadRequests(), loadUsers()])
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '通过申请失败')
  }
}

async function rejectRequest(row) {
  try {
    const note = await ElMessageBox.prompt(`请输入拒绝“${row.realName}”账号申请的原因。`, '拒绝申请', {
      inputPlaceholder: '例如：信息不完整，请重新提交',
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await rejectAccountRequest(row.id, { reviewNote: note.value || '审核未通过' })
    ElMessage.success('账号申请已拒绝')
    await loadRequests()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.response?.data?.message || '拒绝申请失败')
  }
}

function normalizeUserPayload() {
  const form = userForm.value
  const realName = form.realName.trim()
  const department = form.department.trim()
  if (!realName) {
    ElMessage.warning('请输入姓名')
    return null
  }
  if (dialogMode.value === 'create') {
    const username = form.username.trim()
    const password = form.password.trim()
    if (!username) {
      ElMessage.warning('请输入账号')
      return null
    }
    if (password.length < 6) {
      ElMessage.warning('初始密码至少6个字符')
      return null
    }
    return {
      username,
      password,
      realName,
      role: form.role,
      department,
    }
  }
  return {
    realName,
    role: form.role,
    department,
    enabled: Boolean(form.enabled),
  }
}

function emptyUserForm() {
  return {
    username: '',
    password: '',
    realName: '',
    role: 'teacher',
    department: '',
    enabled: true,
  }
}

function roleLabel(role) {
  return role === 'admin' ? '管理员' : '教师'
}

function requestStatusLabel(status) {
  if (status === 'approved') return '已通过'
  if (status === 'rejected') return '已拒绝'
  return '待审核'
}

function requestStatusType(status) {
  if (status === 'approved') return 'success'
  if (status === 'rejected') return 'danger'
  return 'warning'
}

function isCurrentUser(row) {
  return user.value?.id === row.id
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
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
</script>

<style scoped>
.admin-users-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.1), transparent 26%),
    radial-gradient(circle at top right, rgba(147, 197, 253, 0.18), transparent 28%),
    #f4f8ff;
}

.page-main {
  width: min(100%, 1560px);
  margin: 0 auto;
  padding: 26px 24px 40px;
}

.panel {
  padding: 22px;
  border: 1px solid #d9e5f7;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.06);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.panel-head h1 {
  margin: 0;
  color: #102a43;
  font-size: 26px;
}

.panel-head p {
  margin: 8px 0 0;
  color: #627d98;
  line-height: 1.6;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) 170px 170px auto;
  gap: 14px;
  margin-bottom: 18px;
}

.admin-tabs {
  margin-top: 4px;
}

.panel-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
}

.request-filter-bar {
  grid-template-columns: minmax(280px, 1fr) 180px auto;
}

.review-note {
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 960px) {
  .page-main {
    padding: 18px 16px 32px;
  }

  .panel-head {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-bar {
    grid-template-columns: 1fr;
  }

  .panel-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
