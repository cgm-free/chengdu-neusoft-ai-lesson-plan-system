import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCurrentUser, logout } from '../api/http'

export function useAuthenticatedPage(onAuthorized) {
  const router = useRouter()
  const route = useRoute()
  const user = ref(null)
  const authChecking = ref(true)

  onMounted(async () => {
    window.addEventListener('nsu-auth-expired', handleAuthExpired)
    await bootstrap()
  })

  onUnmounted(() => {
    window.removeEventListener('nsu-auth-expired', handleAuthExpired)
  })

  async function bootstrap() {
    const token = localStorage.getItem('nsu_maic_token')
    if (!token) {
      authChecking.value = false
      await redirectToLogin()
      return
    }

    try {
      user.value = await getCurrentUser()
      if (typeof onAuthorized === 'function') {
        await onAuthorized(user.value)
      }
    } catch {
      localStorage.removeItem('nsu_maic_token')
      user.value = null
      await redirectToLogin()
    } finally {
      authChecking.value = false
    }
  }

  async function handleLogout() {
    try {
      await logout()
    } catch {
      // ignore
    }
    localStorage.removeItem('nsu_maic_token')
    user.value = null
    await router.replace({ name: 'login' })
  }

  async function handleAuthExpired() {
    localStorage.removeItem('nsu_maic_token')
    authChecking.value = false
    user.value = null
    await redirectToLogin()
  }

  async function redirectToLogin() {
    if (route.name === 'login') {
      return
    }
    await router.replace({
      name: 'login',
      query: { redirect: route.fullPath },
    })
  }

  return {
    user,
    authChecking,
    handleLogout,
  }
}
