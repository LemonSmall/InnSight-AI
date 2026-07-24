import type { RouteRecordRaw } from 'vue-router'

export const adminRoutes: RouteRecordRaw[] = [
  { path: '/admin/login', name: 'adminLogin', component: () => import('@/views/AdminLogin.vue') },
  {
    path: '/admin',
    component: () => import('@/views/AdminLayout.vue'),
    children: [
      { path: '', name: 'admin', component: () => import('@/views/AdminDashboard.vue') },
      { path: 'tenants', name: 'adminTenants', component: () => import('@/views/AdminTenants.vue') },
      { path: 'tenant-detail', name: 'adminTenantDetail', component: () => import('@/views/AdminTenantDetail.vue') },
      { path: 'billing', name: 'adminBilling', component: () => import('@/views/AdminBilling.vue') },
      { path: 'recharge', name: 'adminRecharge', component: () => import('@/views/AdminRecharge.vue') },
      { path: 'ledger', name: 'adminLedger', component: () => import('@/views/AdminLedger.vue') },
      { path: 'ai', name: 'adminAi', component: () => import('@/views/AdminAiConfig.vue') },
      { path: 'sms', name: 'adminSms', component: () => import('@/views/AdminSmsConfig.vue') },
      { path: 'chains', redirect: '/admin' },
      { path: 'prompts', redirect: '/admin' },
      { path: 'styles', redirect: '/admin' },
      { path: 'feedback', redirect: '/admin' },
      { path: 'moderation', redirect: '/admin' },
      { path: 'models', redirect: '/admin/ai' },
      { path: 'logs', name: 'adminLogs', component: () => import('@/views/AdminLogs.vue') },
      { path: 'audit', redirect: '/admin' },
      { path: 'roles', redirect: '/admin' },
    ],
  },
]
