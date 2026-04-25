<!--
 * @Author: cgmm 11317560+cgmmm@user.noreply.gitee.com
 * @Date: 2026-04-17 22:56:43
 * @LastEditors: cgmm 11317560+cgmmm@user.noreply.gitee.com
 * @LastEditTime: 2026-04-23 12:47:01
 * @FilePath: \nsu-edu-maic\frontend\src\App.vue
 * @Description: 
 * 
 * Copyright (c) 2026 by ${git_name_email}, All Rights Reserved. 
-->
<template>
  <router-view />
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

function handleAuthExpired() {
  const currentRoute = router.currentRoute.value
  if (currentRoute.name === 'login') {
    return
  }
  router.replace({
    name: 'login',
    query: { redirect: currentRoute.fullPath },
  })
}

onMounted(() => {
  window.addEventListener('nsu-auth-expired', handleAuthExpired)
})

onUnmounted(() => {
  window.removeEventListener('nsu-auth-expired', handleAuthExpired)
})
</script>
