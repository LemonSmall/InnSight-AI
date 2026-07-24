import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import api from '@/api'

const ADMIN_USER = 'admin_user'
const ADMIN_ACCESS_TOKEN = 'admin_access_token'
const ADMIN_REFRESH_TOKEN = 'admin_refresh_token'

export interface AdminUser {
  id: string
  name: string
  email: string
  role: string
}

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const user = ref<AdminUser | null>(loadUser())
  const token = ref<string | null>(loadToken())

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const role = computed(() => user.value?.role || null)

  async function login(email: string, password: string) {
    const { data } = await api.post('/api/admin/auth/login', { email, password })
    const result = data.data || data
    const accessToken = result.accessToken || ''
    const refreshToken = result.refreshToken || ''
    if (!accessToken) {
      throw new Error('登录失败')
    }

    const admin: AdminUser = {
      id: String(result.id || result.adminId || Date.now()),
      name: result.name || email,
      email,
      role: result.role || 'super_admin',
    }

    user.value = admin
    token.value = accessToken
    localStorage.setItem(ADMIN_USER, JSON.stringify(admin))
    localStorage.setItem(ADMIN_ACCESS_TOKEN, accessToken)
    if (refreshToken) {
      localStorage.setItem(ADMIN_REFRESH_TOKEN, refreshToken)
    }
    return admin
  }

  function logout() {
    user.value = null
    token.value = null
    localStorage.removeItem(ADMIN_USER)
    localStorage.removeItem(ADMIN_ACCESS_TOKEN)
    localStorage.removeItem(ADMIN_REFRESH_TOKEN)
  }

  function loadUser(): AdminUser | null {
    try {
      const value = localStorage.getItem(ADMIN_USER)
      return value ? JSON.parse(value) : null
    } catch {
      return null
    }
  }

  function loadToken(): string | null {
    return localStorage.getItem(ADMIN_ACCESS_TOKEN)
  }

  return { user, token, isLoggedIn, role, login, logout }
})
