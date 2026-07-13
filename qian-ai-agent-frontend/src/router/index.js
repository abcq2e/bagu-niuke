import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../utils/auth'

const routes = [
  // ===== 登录注册（不需要登录就能访问）=====
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录 - AI 技术面试官' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { title: '注册 - AI 技术面试官' }
  },
  // ===== 功能页面（需要登录）=====
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: 'AI 技术面试官', requiresAuth: true }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('../views/ChatView.vue'),
    meta: { title: 'AI 技术面试官 - 对话', requiresAuth: true }
  },
  // 🔴 404 兜底
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.title) document.title = to.meta.title

  const loggedIn = isLoggedIn()

  // 需要登录但未登录 → 跳转登录页
  if (to.meta.requiresAuth && !loggedIn) {
    return next('/login')
  }

  // 已登录访问登录/注册页 → 跳转首页
  if (loggedIn && (to.path === '/login' || to.path === '/register')) {
    return next('/')
  }

  next()
})

export default router
