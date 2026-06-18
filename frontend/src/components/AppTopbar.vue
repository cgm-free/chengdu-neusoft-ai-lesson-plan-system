<template>
  <header class="app-topbar">
    <button class="app-brand" type="button" @click="$emit('home')">
      <img class="brand-logo" :src="nsuBrand" alt="成都东软学院" />
      <span class="brand-copy">
        <strong>智慧教案生成系统</strong>
      </span>
    </button>

    <nav class="app-topbar-actions" aria-label="全局导航">
      <button
        type="button"
        class="nav-button"
        :class="{ active: active === 'new' }"
        @click="$emit('new')"
      >
        新建教案
      </button>

      <button
        type="button"
        class="nav-button"
        :class="{ active: active === 'lessons' }"
        @click="$emit('lessons')"
      >
        教案管理
      </button>

      <button
        v-if="isAdmin"
        type="button"
        class="nav-button"
        :class="{ active: active === 'admin-users' }"
        @click="$emit('admin')"
      >
        用户管理
      </button>

      <div v-if="user" class="user-pill">
        <el-icon><UserFilled /></el-icon>
        <span>{{ user.realName }} · {{ roleLabel }}</span>
        <el-icon class="user-pill-arrow"><ArrowDown /></el-icon>
      </div>

      <button type="button" class="nav-button nav-button--ghost" @click="$emit('logout')">
        <el-icon><SwitchButton /></el-icon>
        <span>退出</span>
      </button>
    </nav>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import nsuBrand from '../assets/nsu-brand.png'

const props = defineProps({
  user: { type: Object, default: null },
  active: { type: String, default: '' },
})

defineEmits(['home', 'new', 'lessons', 'admin', 'logout'])

const isAdmin = computed(() => props.user?.role === 'admin')
const roleLabel = computed(() => (props.user?.role === 'admin' ? '管理员' : '教师'))
</script>

<style scoped>
.app-topbar {
  position: sticky;
  top: 0;
  z-index: 40;
  width: 100%;
  min-height: 76px;
  padding: 14px 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  background: rgba(255, 255, 255, 0.94);
  border-bottom: 1px solid #e6edf8;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(10px);
}

.app-brand {
  min-width: 0;
  padding: 0;
  border: 0;
  display: inline-flex;
  align-items: center;
  gap: 16px;
  color: #14396d;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.brand-logo {
  width: auto;
  height: 44px;
  display: block;
  flex: 0 0 auto;
}

.brand-copy {
  display: flex;
  align-items: center;
}

.brand-copy strong {
  color: #113e7c;
  font-size: 22px;
  font-weight: 900;
  line-height: 1;
  white-space: nowrap;
}

.app-topbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  flex-wrap: wrap;
}

.nav-button,
.user-pill {
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid #d7e4fb;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #29466f;
  background: #ffffff;
  font-size: 15px;
  font-weight: 700;
}

.nav-button {
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease, color 0.18s ease;
}

.nav-button:hover {
  transform: translateY(-1px);
  border-color: #8eb9ff;
  color: #0f5ad3;
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.08);
}

.nav-button.active {
  border-color: #1d6ff2;
  color: #ffffff;
  background: linear-gradient(135deg, #2b78ff, #1557d6);
  box-shadow: 0 14px 28px rgba(29, 111, 242, 0.18);
}

.nav-button.active:hover {
  color: #ffffff;
  border-color: #1557d6;
}

.nav-button--ghost {
  border-color: #d9e4f6;
  color: #47607f;
  background: #ffffff;
}

.user-pill {
  color: #1d4f9a;
  background: #f4f8ff;
}

.user-pill-arrow {
  color: #7a91b8;
  font-size: 12px;
}

@media (max-width: 1040px) {
  .app-topbar {
    padding: 14px 18px;
    align-items: flex-start;
    flex-direction: column;
  }

  .brand-copy strong {
    font-size: 20px;
  }

  .app-topbar-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .nav-button,
  .user-pill {
    min-height: 40px;
    padding: 0 14px;
    font-size: 14px;
  }

  .brand-logo {
    height: 38px;
  }

  .brand-copy strong {
    font-size: 18px;
    white-space: normal;
  }
}
</style>
