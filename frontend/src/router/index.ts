import { createRouter, createWebHistory } from 'vue-router'
import { adminRoutes } from './adminRoutes'
import { hotelRoutes } from './hotelRoutes'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    ...hotelRoutes,
    ...adminRoutes,
  ],
})

router.beforeEach(async (to, _from, next) => {
  if (to.path === '/login') {
    const { useAuthStore } = await import('@/stores/auth')
    const auth = useAuthStore()
    if (auth.isLoggedIn) {
      next('/dashboard')
      return
    }
    next()
    return
  }

  if (to.path === '/admin/login') {
    const { useAdminAuthStore } = await import('@/stores/adminAuth')
    const adminAuth = useAdminAuthStore()
    if (adminAuth.isLoggedIn) {
      next('/admin')
      return
    }
    next()
    return
  }

  if (to.path.startsWith('/admin')) {
    const { useAdminAuthStore } = await import('@/stores/adminAuth')
    const adminAuth = useAdminAuthStore()
    if (!adminAuth.isLoggedIn) {
      next('/admin/login')
      return
    }
    next()
    return
  }

  const { useAuthStore } = await import('@/stores/auth')
  const auth = useAuthStore()
  if (!auth.isLoggedIn) {
    next('/login')
    return
  }
  next()
})

export default router
