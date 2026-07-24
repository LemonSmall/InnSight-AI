import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { useAdminAuthStore } from '@/stores/adminAuth'

const HOTEL_ACCESS_TOKEN = 'hotel_access_token'
const HOTEL_REFRESH_TOKEN = 'hotel_refresh_token'
const ADMIN_ACCESS_TOKEN = 'admin_access_token'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

function replaceLocation(path: string) {
  if (window.location.pathname !== path) {
    window.location.replace(path)
  }
}

api.interceptors.request.use(config => {
  const url = config.url || ''
  const token = url.startsWith('/api/admin')
    ? localStorage.getItem(ADMIN_ACCESS_TOKEN)
    : localStorage.getItem(HOTEL_ACCESS_TOKEN)

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let isRefreshing = false
let refreshQueue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = []

api.interceptors.response.use(
  response => {
    const payload = response.data
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      const error = new Error(payload.message || '请求失败') as any
      error.response = response
      error.data = payload
      return Promise.reject(error)
    }
    return response
  },
  async error => {
    const originalRequest = error.config
    const url = originalRequest?.url || ''
    const status = error.response?.status

    if (url.startsWith('/api/admin') && (status === 401 || status === 403) && !url.includes('/auth/')) {
      const adminAuth = useAdminAuthStore()
      adminAuth.logout()
      replaceLocation('/admin/login')
      return Promise.reject(error)
    }

    if (status === 401 && !originalRequest._retry && !url.includes('/auth/')) {
      originalRequest._retry = true

      if (!isRefreshing) {
        isRefreshing = true
        try {
          const refreshToken = localStorage.getItem(HOTEL_REFRESH_TOKEN)
          if (!refreshToken) throw new Error('missing refresh token')

          const { data } = await axios.post(
            `${api.defaults.baseURL}/api/auth/token/refresh`,
            { refreshToken }
          )

          const newAccessToken = data.data?.accessToken || data.accessToken
          localStorage.setItem(HOTEL_ACCESS_TOKEN, newAccessToken)
          const auth = useAuthStore()
          auth.setAccessToken(newAccessToken)

          refreshQueue.forEach(q => q.resolve(newAccessToken))
          refreshQueue = []

          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
          return api(originalRequest)
        } catch (refreshError) {
          refreshQueue.forEach(q => q.reject(refreshError))
          refreshQueue = []

          const auth = useAuthStore()
          auth.logout()
          replaceLocation('/login')
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }

      return new Promise<string>((resolve, reject) => {
        refreshQueue.push({ resolve, reject })
      }).then(token => {
        originalRequest.headers.Authorization = `Bearer ${token}`
        return api(originalRequest)
      })
    }

    return Promise.reject(error)
  }
)

export default api
