import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import CoursePlanBuilderView from '../views/CoursePlanBuilderView.vue'
import CoursePlanEditorView from '../views/CoursePlanEditorView.vue'
import CoursePlanMaterialsView from '../views/CoursePlanMaterialsView.vue'
import LessonListView from '../views/LessonListView.vue'
import LoginView from '../views/LoginView.vue'
import AdminUserView from '../views/AdminUserView.vue'
import { getCurrentUser } from '../api/http'

function authenticatedHome() {
  return localStorage.getItem('nsu_maic_user_role') === 'admin' ? '/admin/users' : '/new'
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => (localStorage.getItem('nsu_maic_token') ? authenticatedHome() : '/login'),
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: AdminUserView,
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/new',
      name: 'course-plan-new',
      component: CoursePlanBuilderView,
      meta: { requiresAuth: true },
      beforeEnter: (to) => {
        const id = String(to.query.id || '').trim()
        if (!id) {
          return true
        }
        if (String(to.query.mode || '').trim() === 'materials') {
          return { name: 'course-plan-materials', params: { id } }
        }
        return { name: 'course-plan-edit', params: { id } }
      },
    },
    {
      path: '/course-plans/:id/materials',
      name: 'course-plan-materials',
      component: CoursePlanMaterialsView,
      meta: { requiresAuth: true },
    },
    {
      path: '/course-plans/:id/edit',
      name: 'course-plan-edit',
      component: CoursePlanEditorView,
      meta: { requiresAuth: true },
    },
    {
      path: '/lessons',
      name: 'lesson-list',
      component: LessonListView,
      meta: { requiresAuth: true },
    },
    {
      path: '/lessons/:id/edit',
      name: 'lesson-edit',
      component: Dashboard,
      meta: { requiresAuth: true },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: () => (localStorage.getItem('nsu_maic_token') ? authenticatedHome() : '/login'),
    },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta?.requiresAuth) {
    return true
  }
  if (localStorage.getItem('nsu_maic_token')) {
    if (to.meta?.requiresAdmin) {
      try {
        const user = await getCurrentUser()
        localStorage.setItem('nsu_maic_user_role', user?.role || '')
        if (user?.role === 'admin') {
          return true
        }
        return { name: 'course-plan-new' }
      } catch {
        localStorage.removeItem('nsu_maic_token')
        localStorage.removeItem('nsu_maic_user_role')
        return { name: 'login', query: { redirect: to.fullPath } }
      }
    }
    return true
  }
  return {
    name: 'login',
    query: { redirect: to.fullPath },
  }
})

export default router
