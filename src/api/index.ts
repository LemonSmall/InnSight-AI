import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// ====== 请求拦截器：自动带 Token ======
api.interceptors.request.use(config => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ====== 响应拦截器：Token 过期自动刷新 ======
let isRefreshing = false
let refreshQueue: Array<{ resolve: (token: string) => void; reject: (err: any) => void }> = []

api.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config

    // 401 且不是刷新 Token 请求本身
    if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.url?.includes('/auth/')) {
      originalRequest._retry = true

      if (!isRefreshing) {
        isRefreshing = true
        try {
          const refreshToken = localStorage.getItem('refresh_token')
          if (!refreshToken) throw new Error('无 refresh_token')

          const { data } = await axios.post(
            `${api.defaults.baseURL}/api/auth/token/refresh`,
            { refreshToken }
          )

          const newAccessToken = data.data?.accessToken || data.accessToken
          localStorage.setItem('access_token', newAccessToken)

          // 重放队列中的所有请求
          refreshQueue.forEach(q => q.resolve(newAccessToken))
          refreshQueue = []

          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
          return api(originalRequest)
        } catch (refreshError) {
          refreshQueue.forEach(q => q.reject(refreshError))
          refreshQueue = []

          // 刷新失败 → 清除状态 → 跳登录
          const auth = useAuthStore()
          auth.logout()
          window.location.href = '/login'
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      } else {
        // 正在刷新中，把请求排入队列等待
        return new Promise<string>((resolve, reject) => {
          refreshQueue.push({ resolve, reject })
        }).then(token => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return api(originalRequest)
        })
      }
    }

    return Promise.reject(error)
  }
)

export default api
