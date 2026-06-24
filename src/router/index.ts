import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },

    // ====== 酒店管理端 ======
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/setup', name: 'setup', component: () => import('@/views/SetupView.vue') },
    { path: '/rooms', name: 'rooms', component: () => import('@/views/RoomsView.vue') },
    { path: '/dashboard', name: 'dashboard', component: DashboardView },
    { path: '/pricing', name: 'pricing', component: () => import('@/views/PricingView.vue') },
    { path: '/strategy', name: 'strategy', component: () => import('@/views/StrategyView.vue') },
    { path: '/plans', name: 'plans', component: () => import('@/views/PlansView.vue') },
    { path: '/plan', name: 'plan', component: () => import('@/views/PlanView.vue') },
    { path: '/wechat', name: 'wechat', component: () => import('@/views/WechatView.vue') },
    { path: '/xhs', name: 'xhs', component: () => import('@/views/XhsView.vue') },
    { path: '/poster', name: 'poster', component: () => import('@/views/PosterView.vue') },
    { path: '/video', name: 'video', component: () => import('@/views/VideoView.vue') },
    { path: '/article', name: 'article', component: () => import('@/views/ArticleView.vue') },
    { path: '/review', name: 'review', component: () => import('@/views/ReviewView.vue') },
    { path: '/reply', name: 'reply', component: () => import('@/views/ReplyView.vue') },
    { path: '/checkin', name: 'checkin', component: () => import('@/views/CheckinView.vue') },
    { path: '/brain', name: 'brain', component: () => import('@/views/BrainView.vue') },
    { path: '/room-status', name: 'roomStatus', component: () => import('@/views/RoomStatusView.vue') },
    { path: '/credits', name: 'credits', component: () => import('@/views/CreditsView.vue') },
    { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue') },

    // ====== 管理后台登录 ======
    { path: '/admin/login', name: 'adminLogin', component: () => import('@/views/AdminLogin.vue') },

    // ====== 管理后台 ======
    {
      path: '/admin',
      component: () => import('@/views/AdminLayout.vue'),
      children: [
        { path: '', name: 'admin', component: () => import('@/views/AdminDashboard.vue') },
        { path: 'tenants', name: 'adminTenants', component: () => import('@/views/AdminTenants.vue') },
        { path: 'tenant-detail', name: 'adminTenantDetail', component: () => import('@/views/AdminTenantDetail.vue') },
        { path: 'chains', name: 'adminChains', component: () => import('@/views/AdminChains.vue') },
        { path: 'billing', name: 'adminBilling', component: () => import('@/views/AdminBilling.vue') },
        { path: 'recharge', name: 'adminRecharge', component: () => import('@/views/AdminRecharge.vue') },
        { path: 'ledger', name: 'adminLedger', component: () => import('@/views/AdminLedger.vue') },
        { path: 'prompts', name: 'adminPrompts', component: () => import('@/views/AdminPrompts.vue') },
        { path: 'styles', name: 'adminStyles', component: () => import('@/views/AdminStyles.vue') },
        { path: 'feedback', name: 'adminFeedback', component: () => import('@/views/AdminFeedback.vue') },
        { path: 'moderation', name: 'adminModeration', component: () => import('@/views/AdminModeration.vue') },
        { path: 'models', name: 'adminModels', component: () => import('@/views/AdminModels.vue') },
        { path: 'logs', name: 'adminLogs', component: () => import('@/views/AdminLogs.vue') },
        { path: 'audit', name: 'adminAudit', component: () => import('@/views/AdminAudit.vue') },
        { path: 'roles', name: 'adminRoles', component: () => import('@/views/AdminRoles.vue') },
        { path: 'ai', name: 'adminAi', component: () => import('@/views/AdminAiConfig.vue') },
        { path: 'sms', name: 'adminSms', component: () => import('@/views/AdminSmsConfig.vue') },
      ],
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  if (to.path === '/login') { next(); return }
  if (to.path === '/admin/login') { next(); return }

  const { useAuthStore } = await import('@/stores/auth')
  const auth = useAuthStore()

  // 管理后台
  if (to.path.startsWith('/admin')) {
    if (!auth.isLoggedIn) { next('/admin/login'); return }
    if (auth.role !== 'admin') { next('/dashboard'); return }
    next()
    return
  }

  // 酒店端
  if (!auth.isLoggedIn) { next('/login'); return }
  if (!auth.canAccess(to.path)) { next('/dashboard'); return }
  next()
})

export default router
