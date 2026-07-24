import type { RouteRecordRaw } from 'vue-router'

export const adminRoutes: RouteRecordRaw[] = [
  { path: '/admin/login', name: 'adminLogin', component: () => import('@/views/admin/AdminLogin.vue') },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    children: [
      { path: '', name: 'admin', component: () => import('@/views/admin/AdminDashboard.vue') },
      { path: 'tenants', name: 'adminTenants', component: () => import('@/views/admin/AdminTenants.vue') },
      { path: 'tenant-detail', name: 'adminTenantDetail', component: () => import('@/views/admin/AdminTenantDetail.vue') },
      { path: 'billing', name: 'adminBilling', component: () => import('@/views/admin/AdminBilling.vue') },
      { path: 'recharge', name: 'adminRecharge', component: () => import('@/views/admin/AdminRecharge.vue') },
      { path: 'ledger', name: 'adminLedger', component: () => import('@/views/admin/AdminLedger.vue') },
      { path: 'ai', name: 'adminAi', component: () => import('@/views/admin/AdminAiConfig.vue') },
      { path: 'sms', name: 'adminSms', component: () => import('@/views/admin/AdminSmsConfig.vue') },
      { path: 'chains', redirect: '/admin' },
      { path: 'prompts', redirect: '/admin' },
      { path: 'styles', redirect: '/admin' },
      { path: 'feedback', redirect: '/admin' },
      { path: 'moderation', redirect: '/admin' },
      { path: 'models', redirect: '/admin/ai' },
      { path: 'logs', name: 'adminLogs', component: () => import('@/views/admin/AdminLogs.vue') },
      { path: 'audit', redirect: '/admin' },
      { path: 'roles', redirect: '/admin' },
    ],
  },
]
