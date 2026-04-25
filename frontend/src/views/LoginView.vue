<template>
  <div class="login-page">
    <section class="login-stage">
      <header class="login-brand">
        <img class="login-brand-image" :src="nsuBrand" alt="成都东软学院" />
      </header>

      <div class="login-layout">
        <section class="login-intro">
          <div class="intro-copy">
            <h1>智能教案生成系统</h1>
            <p class="intro-kicker">高效备课 · 智能生成 · 专业教学</p>
            <p class="intro-text">
              登录后可上传课程标准、PPT、教学日历与模板，生成整套课程教案并导出 Word / PDF。
            </p>
          </div>
          <img class="intro-illustration" :src="loginIllustration" alt="" />
        </section>

        <section v-loading="checkingSession" class="login-card">
          <div class="login-card-head">
            <h2>欢迎登录</h2>
            <p>登录后可查看、打开并导出教案</p>
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
              <span class="login-meta-note">如需重置账号，请联系管理员</span>
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
        <p>© 2026 成都东软学院 · 智能教案生成系统</p>
        <span>安全登录，保护您的数据</span>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { getCurrentUser, login } from '../api/http'
import loginIllustration from '../assets/login-illustration.png'
import nsuBrand from '../assets/nsu-brand.png'

const router = useRouter()
const route = useRoute()

const form = ref({
  username: localStorage.getItem('nsu_maic_last_username') || 'admin',
  password: 'admin123456',
})
const loggingIn = ref(false)
const checkingSession = ref(Boolean(localStorage.getItem('nsu_maic_token')))
const rememberUser = ref(Boolean(localStorage.getItem('nsu_maic_last_username')))

const redirectTarget = computed(() => {
  const target = String(route.query.redirect || '').trim()
  if (!target || target === '/login' || !target.startsWith('/')) {
    return '/new'
  }
  return target
})

onMounted(async () => {
  const token = localStorage.getItem('nsu_maic_token')
  if (!token) {
    checkingSession.value = false
    return
  }
  try {
    await getCurrentUser()
    await router.replace(redirectTarget.value)
  } catch {
    localStorage.removeItem('nsu_maic_token')
    checkingSession.value = false
  }
})

async function handleSubmit() {
  loggingIn.value = true
  try {
    const result = await login(form.value)
    localStorage.setItem('nsu_maic_token', result.token)
    if (rememberUser.value) {
      localStorage.setItem('nsu_maic_last_username', form.value.username)
    } else {
      localStorage.removeItem('nsu_maic_last_username')
    }
    ElMessage.success('登录成功')
    await router.replace(redirectTarget.value)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  } finally {
    loggingIn.value = false
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
  margin: 0;
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

.login-meta-note {
  color: #3b82f6;
  font-size: 14px;
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
}
</style>
