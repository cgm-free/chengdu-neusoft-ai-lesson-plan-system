import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import CoursePlanBuilderView from '../views/CoursePlanBuilderView.vue'
import CoursePlanEditorView from '../views/CoursePlanEditorView.vue'
import CoursePlanMaterialsView from '../views/CoursePlanMaterialsView.vue'
import LessonListView from '../views/LessonListView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => (localStorage.getItem('nsu_maic_token') ? '/new' : '/login'),
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
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
      redirect: () => (localStorage.getItem('nsu_maic_token') ? '/new' : '/login'),
    },
  ],
})

router.beforeEach((to) => {
  if (!to.meta?.requiresAuth) {
    return true
  }
  if (localStorage.getItem('nsu_maic_token')) {
    return true
  }
  return {
    name: 'login',
    query: { redirect: to.fullPath },
  }
})

export default router
