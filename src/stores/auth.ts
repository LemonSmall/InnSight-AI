import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'
import {
  getStaffList as getStaffListApi,
  createStaff as createStaffApi,
  updateStaff as updateStaffApi,
  deleteStaff as deleteStaffApi,
  updateProfile as updateProfileApi,
  changePassword as changePasswordApi,
} from '@/api/auth'

// ====== 角色定义 ======
export type Role = 'admin' | 'manager' | 'front_desk' | 'marketing'

export interface Employee {
  id: string
  name: string
  phone: string
  role: Role
  avatar: string
  createdAt: string
}

export interface UserInfo {
  id: string
  name: string
  phone: string
  role: Role
  avatar: string
}

// 角色可访问的模块路由
const rolePermissions: Record<Role, string[]> = {
  admin: ['/setup', '/rooms', '/dashboard', '/pricing', '/strategy', '/brain', '/wechat', '/xhs', '/poster', '/video', '/article', '/review', '/reply', '/checkin', '/room-status', '/credits', '/profile'],
  manager: ['/dashboard', '/pricing', '/strategy', '/brain', '/wechat', '/xhs', '/poster', '/video', '/article', '/review', '/reply', '/checkin', '/room-status', '/credits', '/profile'],
  front_desk: ['/review', '/reply', '/checkin', '/room-status', '/credits', '/profile'],
  marketing: ['/wechat', '/xhs', '/poster', '/video', '/article', '/credits', '/profile'],
}

export const roleLabels: Record<Role, string> = {
  admin: '超级管理员',
  manager: '店长',
  front_desk: '前台客服',
  marketing: '营销专员',
}

function genId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7)
}

// 默认管理员
const defaultAdmin: UserInfo = {
  id: 'admin-001',
  name: '张店长',
  phone: '13800000000',
  role: 'admin',
  avatar: '',
}

const defaultEmployees: Employee[] = [
  { id: genId(), name: '李小明', phone: '13800000001', role: 'manager', avatar: '', createdAt: '2024-06-01' },
  { id: genId(), name: '王小红', phone: '13800000002', role: 'front_desk', avatar: '', createdAt: '2024-06-05' },
  { id: genId(), name: '赵小丽', phone: '13800000003', role: 'marketing', avatar: '', createdAt: '2024-06-10' },
]

export const useAuthStore = defineStore('auth', () => {
  // ====== 状态 ======
  const user = ref<UserInfo | null>(loadUser())
  const token = ref<string | null>(loadToken())
  const employees = ref<Employee[]>(loadEmployees())

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const role = computed(() => user.value?.role || null)
  const roleName = computed(() => role.value ? roleLabels[role.value] : '')
  const allowedRoutes = computed(() => role.value ? rolePermissions[role.value] : [])

  // ====== 权限检查 ======
  function canAccess(path: string): boolean {
    if (!role.value) return false
    return rolePermissions[role.value].some(r => path.startsWith(r))
  }

  function canManageEmployees(): boolean {
    return role.value === 'admin' || role.value === 'manager'
  }

  // ====== 登录（真实API） ======
  async function login(phone: string, code: string) {
    const { data } = await api.post('/api/auth/login/phone', { phone, code })
    const result = data.data || data
    const userInfo: UserInfo = {
      id: 'user-' + Date.now(),
      name: result.name || '',
      phone: phone,
      role: (result.role as Role) || 'admin',
      avatar: '',
    }
    const authToken = result.accessToken || ''

    user.value = userInfo
    token.value = authToken
    localStorage.setItem('access_token', authToken)
    if (result.refreshToken) {
      localStorage.setItem('refresh_token', result.refreshToken)
    }
    persist('auth_user', userInfo)
    persist('auth_token', authToken)
    return userInfo
  }

  // ====== 退出 ======
  function logout() {
    user.value = null
    token.value = null
    localStorage.removeItem('auth_user')
    localStorage.removeItem('auth_token')
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
  }

  // ====== 修改个人信息 ======
  async function updateProfile(data: { name?: string; phone?: string; avatar?: string }) {
    if (!user.value) return
    if (data.name) user.value.name = data.name
    if (data.phone) user.value.phone = data.phone
    if (data.avatar !== undefined) user.value.avatar = data.avatar
    persist('auth_user', user.value)
    try { await updateProfileApi(data) } catch { /* API 失败时本地已保存 */ }
  }

  // ====== 修改密码 ======
  async function changePassword(oldPwd: string, newPwd: string): Promise<boolean> {
    try {
      await changePasswordApi(oldPwd, newPwd)
      return true
    } catch {
      return false
    }
  }

  // ====== 员工管理 ======
  /** 从后端加载员工列表 */
  async function loadEmployeesFromApi() {
    try {
      const { data: res } = await getStaffListApi()
      const list = res.data || res
      if (Array.isArray(list) && list.length > 0) {
        employees.value = list.map((e: any) => ({
          id: String(e.id),
          name: e.name,
          phone: e.phone,
          role: e.role,
          avatar: e.avatar || '',
          createdAt: e.createdAt || '',
        }))
        persist('auth_employees', employees.value)
      }
    } catch { /* 静默回退到本地数据 */ }
  }

  async function addEmployee(emp: Omit<Employee, 'id' | 'createdAt'>) {
    try {
      const { data: res } = await createStaffApi({ name: emp.name, phone: emp.phone, role: emp.role })
      const created = res.data || res
      const newEmp: Employee = {
        id: String(created.id),
        name: created.name || emp.name,
        phone: created.phone || emp.phone,
        role: created.role || emp.role,
        avatar: emp.avatar,
        createdAt: created.createdAt || new Date().toISOString().slice(0, 10),
      }
      employees.value.push(newEmp)
      persist('auth_employees', employees.value)
    } catch {
      // 后端不可用时本地新增
      const newEmp: Employee = {
        ...emp,
        id: genId(),
        createdAt: new Date().toISOString().slice(0, 10),
      }
      employees.value.push(newEmp)
      persist('auth_employees', employees.value)
    }
  }

  async function removeEmployee(id: string) {
    employees.value = employees.value.filter(e => e.id !== id)
    persist('auth_employees', employees.value)
    try { await deleteStaffApi(Number(id)) } catch { /* API 失败时本地已删除 */ }
  }

  async function updateEmployee(id: string, data: Partial<Omit<Employee, 'id' | 'createdAt'>>) {
    const idx = employees.value.findIndex(e => e.id === id)
    if (idx === -1) return
    employees.value[idx] = { ...employees.value[idx], ...data }
    persist('auth_employees', employees.value)
    try {
      await updateStaffApi(Number(id), { name: data.name, phone: data.phone, role: data.role })
    } catch { /* API 失败时本地已更新 */ }
  }

  // ====== 持久化 ======
  function persist(key: string, data: unknown) {
    try { localStorage.setItem(key, JSON.stringify(data)) } catch {}
  }

  function loadUser(): UserInfo | null {
    try {
      const d = localStorage.getItem('auth_user')
      return d ? JSON.parse(d) : null
    } catch { return null }
  }

  function loadToken(): string | null {
    // 优先从 localStorage 读取后端 access_token
    const apiToken = localStorage.getItem('access_token')
    return apiToken || localStorage.getItem('auth_token')
  }

  function loadEmployees(): Employee[] {
    try {
      const d = localStorage.getItem('auth_employees')
      return d ? JSON.parse(d) : defaultEmployees
    } catch { return defaultEmployees }
  }

  return {
    user, token, employees, isLoggedIn, role, roleName, allowedRoutes,
    canAccess, canManageEmployees,
    login, logout, updateProfile, changePassword,
    addEmployee, removeEmployee, updateEmployee, loadEmployeesFromApi,
  }
})
