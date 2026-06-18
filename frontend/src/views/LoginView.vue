<template>
  <div class="login-page">
    <section class="login-stage">
      <header class="login-brand">
        <img class="login-brand-image" :src="nsuBrand" alt="成都东软学院" />
      </header>

      <div class="login-layout">
        <section class="login-intro">
          <div class="intro-copy">
            <h1>智慧教案生成系统</h1>
            <p class="intro-kicker">高效备课 · 智慧生成 · 专业教学</p>
            <p class="intro-text">
              登录后可上传课程标准、PPT、教学日历与模板，生成整套课程教案并导出 Word / PDF。
            </p>
          </div>
          <img class="intro-illustration" :src="loginIllustration" alt="" />
        </section>

        <section v-loading="checkingSession" class="login-card">
          <div class="login-card-head">
            <h2>账号登录</h2>
          </div>

          <el-form :model="form" label-position="top" @keyup.enter="handleSubmit">
            <el-form-item label="用户名">
              <el-input v-model="form.username" :prefix-icon="User" />
            </el-form-item>

            <el-form-item label="密码">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                :prefix-icon="Lock"
              />
            </el-form-item>

            <div class="login-card-meta">
              <el-checkbox v-model="rememberUser">记住我</el-checkbox>
              <button type="button" class="login-meta-action" @click="openAccountRequestDialog">注册教师账号</button>
            </div>

            <el-button
              class="login-submit"
              type="primary"
              size="large"
              :loading="loggingIn"
              @click="handleSubmit"
            >
              登录
            </el-button>
          </el-form>
        </section>
      </div>

      <footer class="login-footer">
        <p>© 2026 成都东软学院 · 智慧教案生成系统</p>
        <span>安全登录，保护您的数据</span>
      </footer>
    </section>

    <el-dialog v-model="accountRequestVisible" title="注册教师账号" width="680px" class="account-request-dialog">
      <el-form :model="accountRequestForm" label-position="top">
        <div class="request-form-grid">
          <el-form-item label="登录用户名">
            <el-input v-model="accountRequestForm.username" maxlength="64" />
          </el-form-item>
          <el-form-item label="教师姓名">
            <el-input v-model="accountRequestForm.realName" maxlength="64" />
          </el-form-item>
          <el-form-item label="课程名称">
            <el-input v-model="accountRequestForm.courseName" maxlength="255" />
          </el-form-item>
          <el-form-item label="学院">
            <el-input v-model="accountRequestForm.college" disabled />
          </el-form-item>
          <el-form-item label="系部">
            <el-select v-model="accountRequestForm.department">
              <el-option v-for="item in departmentOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="登录密码">
            <el-input v-model="accountRequestForm.password" type="password" show-password maxlength="72" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="accountRequestForm.confirmPassword" type="password" show-password maxlength="72" />
          </el-form-item>
          <el-form-item label="校内邀请码">
            <el-input
              v-model="accountRequestForm.invitationCode"
              type="password"
              show-password
              maxlength="64"
              placeholder="请输入管理员提供的校内邀请码"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="accountRequestVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingAccountRequest" @click="submitTeacherAccountRequest">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { getCurrentUser, login, submitAccountRequest } from '../api/http'
import loginIllustration from '../assets/login-illustration.png'
import nsuBrand from '../assets/nsu-brand.png'

const router = useRouter()
const route = useRoute()
const REMEMBERED_USERNAME_KEY = 'nsu_maic_remembered_username'
const REMEMBERED_PASSWORD_KEY = 'nsu_maic_remembered_password'
const LEGACY_USERNAME_KEY = 'nsu_maic_last_username'

const rememberedUsername = localStorage.getItem(REMEMBERED_USERNAME_KEY)
  || localStorage.getItem(LEGACY_USERNAME_KEY)
  || ''
localStorage.removeItem(REMEMBERED_PASSWORD_KEY)
localStorage.removeItem('nsu_maic_remembered_role')

const form = ref({
  username: rememberedUsername,
  password: '',
})
const loggingIn = ref(false)
const checkingSession = ref(Boolean(localStorage.getItem('nsu_maic_token')))
const rememberUser = ref(Boolean(rememberedUsername))
const accountRequestVisible = ref(false)
const submittingAccountRequest = ref(false)

const departmentOptions = [
  '智能工程系',
  '大数据工程系',
  '电子工程系',
]

const accountRequestForm = ref(createAccountRequestForm())

const redirectTarget = computed(() => {
  const target = String(route.query.redirect || '').trim()
  if (!target || target === '/login' || !target.startsWith('/')) {
    return ''
  }
  return target
})

function homeForRole(role) {
  return role === 'admin' ? '/admin/users' : '/new'
}

function postLoginTarget(user) {
  return redirectTarget.value || homeForRole(user?.role)
}

onMounted(async () => {
  const token = localStorage.getItem('nsu_maic_token')
  if (!token) {
    checkingSession.value = false
    return
  }
  try {
    const user = await getCurrentUser()
    localStorage.setItem('nsu_maic_user_role', user?.role || '')
    await router.replace(postLoginTarget(user))
  } catch {
    localStorage.removeItem('nsu_maic_token')
    localStorage.removeItem('nsu_maic_user_role')
    checkingSession.value = false
  }
})

async function handleSubmit() {
  loggingIn.value = true
  try {
    const result = await login(form.value)
    localStorage.setItem('nsu_maic_token', result.token)
    localStorage.setItem('nsu_maic_user_role', result.user?.role || '')
    if (rememberUser.value) {
      localStorage.setItem(REMEMBERED_USERNAME_KEY, form.value.username)
    } else {
      localStorage.removeItem(REMEMBERED_USERNAME_KEY)
    }
    localStorage.removeItem(REMEMBERED_PASSWORD_KEY)
    localStorage.removeItem(LEGACY_USERNAME_KEY)
    ElMessage.success('登录成功')
    await router.replace(postLoginTarget(result.user))
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  } finally {
    loggingIn.value = false
  }
}

function createAccountRequestForm() {
  return {
    username: '',
    realName: '',
    college: '智能科学与工程学院',
    department: '智能工程系',
    courseName: '',
    password: '',
    confirmPassword: '',
    invitationCode: '',
  }
}

function openAccountRequestDialog() {
  accountRequestForm.value = createAccountRequestForm()
  accountRequestVisible.value = true
}

function normalizeAccountRequestPayload() {
  const formValue = accountRequestForm.value
  const requiredFields = [
    ['username', '请输入登录用户名'],
    ['realName', '请输入教师姓名'],
    ['department', '请选择系部'],
    ['password', '请输入登录密码'],
    ['invitationCode', '请输入校内邀请码'],
  ]
  for (const [field, message] of requiredFields) {
    if (!String(formValue[field] || '').trim()) {
      ElMessage.warning(message)
      return null
    }
  }
  if (!/^[A-Za-z0-9_-]{3,64}$/.test(String(formValue.username).trim())) {
    ElMessage.warning('用户名需为3到64位字母、数字、下划线或短横线')
    return null
  }
  if (String(formValue.password).trim().length < 8) {
    ElMessage.warning('登录密码至少8个字符')
    return null
  }
  if (formValue.password !== formValue.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return null
  }
  return {
    username: formValue.username.trim(),
    realName: formValue.realName.trim(),
    college: formValue.college,
    department: formValue.department,
    courseName: formValue.courseName.trim(),
    password: formValue.password,
    invitationCode: formValue.invitationCode.trim(),
  }
}

async function submitTeacherAccountRequest() {
  const payload = normalizeAccountRequestPayload()
  if (!payload) return
  submittingAccountRequest.value = true
  try {
    const result = await submitAccountRequest(payload)
    form.value.username = payload.username
    form.value.password = payload.password
    accountRequestVisible.value = false
    ElMessage.success(`注册成功，请使用账号 ${payload.username} 登录`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '注册教师账号失败')
  } finally {
    submittingAccountRequest.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at left top, rgba(37, 99, 235, 0.08), transparent 26%),
    radial-gradient(circle at right center, rgba(147, 197, 253, 0.14), transparent 34%),
    linear-gradient(180deg, #f8fbff 0%, #eef4ff 100%);
}

.login-stage {
  min-height: 100vh;
  width: min(100%, 1600px);
  margin: 0 auto;
  padding: 28px 42px 24px;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 16px;
}

.login-brand-image {
  height: 64px;
  width: auto;
  display: block;
}

.login-layout {
  display: grid;
  grid-template-columns: minmax(0, 720px) 520px;
  justify-content: center;
  gap: 72px;
  align-items: center;
}

.login-intro {
  min-height: 600px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 22px;
  padding: 0 12px 20px;
}

.intro-copy {
  width: min(100%, 620px);
}

.intro-kicker {
  margin: 10px 0 0;
  color: #63728b;
  font-size: 17px;
  line-height: 1.5;
}

.intro-copy h1 {
  margin: 0;
  color: #123b73;
  font-size: 42px;
  line-height: 1.2;
}

.intro-text {
  margin: 18px 0 0;
  color: #617594;
  font-size: 16px;
  line-height: 1.75;
}

.intro-illustration {
  width: min(100%, 690px);
  max-height: 360px;
  object-fit: contain;
  display: block;
  opacity: 0.96;
  mix-blend-mode: multiply;
  -webkit-mask-image: radial-gradient(ellipse 74% 70% at 50% 50%, #000 58%, rgba(0, 0, 0, 0.82) 72%, transparent 100%);
  mask-image: radial-gradient(ellipse 74% 70% at 50% 50%, #000 58%, rgba(0, 0, 0, 0.82) 72%, transparent 100%);
}

.login-card {
  width: 100%;
  max-width: 520px;
  justify-self: start;
  padding: 46px 40px 38px;
  border: 1px solid rgba(205, 220, 244, 0.95);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 28px 60px rgba(73, 111, 185, 0.12);
}

.login-card-head h2 {
  margin: 0 0 28px;
  color: #182a45;
  font-size: 28px;
}

.login-card-head p {
  margin: 10px 0 28px;
  color: #6a7890;
  font-size: 16px;
}

.login-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 6px 0 28px;
}

.login-meta-action {
  padding: 0;
  border: 0;
  color: #3b82f6;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
}

.login-meta-action:hover {
  color: #0f5ad3;
  text-decoration: underline;
}

.request-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px 16px;
}

.login-submit {
  width: 100%;
  height: 58px;
  border-radius: 18px;
  font-size: 20px;
  font-weight: 700;
}

.login-footer {
  display: grid;
  gap: 10px;
  justify-items: center;
  color: #7a879d;
  font-size: 14px;
}

.login-footer p {
  margin: 0;
}

.login-footer span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.login-footer span::before {
  content: '';
  width: 16px;
  height: 16px;
  border-radius: 999px;
  background: linear-gradient(180deg, #8fb5ff, #5a8bf4);
  box-shadow: inset 0 0 0 4px rgba(255, 255, 255, 0.72);
}

@media (max-width: 1440px) {
  .login-layout {
    grid-template-columns: minmax(0, 650px) 500px;
    gap: 42px;
  }

  .intro-copy h1 {
    font-size: 38px;
  }

  .intro-text {
    font-size: 16px;
  }
}

@media (max-width: 1160px) {
  .login-stage {
    padding: 24px;
  }

  .login-layout {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .login-intro {
    min-height: auto;
    padding: 20px 0 0;
  }

  .login-card {
    justify-self: center;
  }
}

@media (max-width: 720px) {
  .login-stage {
    padding: 20px 16px;
  }

  .login-brand-image {
    height: 52px;
  }

  .intro-kicker {
    font-size: 15px;
  }

  .intro-copy h1 {
    font-size: 32px;
  }

  .intro-text {
    margin-top: 16px;
    font-size: 17px;
  }

  .login-intro {
    min-height: auto;
    padding-top: 16px;
  }

  .intro-illustration {
    max-height: 260px;
  }

  .login-card {
    padding: 34px 22px 28px;
    border-radius: 24px;
  }

  .login-card-meta {
    align-items: flex-start;
    flex-direction: column;
  }

  .request-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
