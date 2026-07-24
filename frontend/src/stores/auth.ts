import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  sendSms as sendSmsApi,
  loginByPhone as loginByPhoneApi,
  loginByPassword as loginByPasswordApi,
  getStaffList as getStaffListApi,
  createStaff as createStaffApi,
  updateStaff as updateStaffApi,
  deleteStaff as deleteStaffApi,
  resetStaffPassword as resetStaffPasswordApi,
  updateProfile as updateProfileApi,
  changePassword as changePasswordApi,
} from '@/api/auth'

const HOTEL_USER = 'hotel_user'
const HOTEL_ACCESS_TOKEN = 'hotel_access_token'
const HOTEL_REFRESH_TOKEN = 'hotel_refresh_token'

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

const rolePermissions: Record<Role, string[]> = {
  admin: ['/setup', '/rooms', '/dashboard', '/pricing', '/strategy', '/brain', '/knowledge', '/history', '/create', '/wechat', '/xhs', '/poster', '/video', '/article', '/review', '/reply', '/credits', '/profile'],
  manager: ['/dashboard', '/pricing', '/strategy', '/brain', '/knowledge', '/history', '/create', '/wechat', '/xhs', '/poster', '/video', '/article', '/review', '/reply', '/credits', '/profile'],
  front_desk: ['/knowledge', '/history', '/review', '/reply', '/credits', '/profile'],
  marketing: ['/knowledge', '/history', '/create', '/wechat', '/xhs', '/poster', '/video', '/article', '/credits', '/profile'],
}

export const roleLabels: Record<Role, string> = {
  admin: '超级管理员',
  manager: '店长',
  front_desk: '前台客服',
  marketing: '营销专员',
}

function normalizeRole(value: unknown): Role {
  return value === 'manager' || value === 'front_desk' || value === 'marketing' ? value : 'admin'
}

function mapEmployee(row: any): Employee {
  return {
    id: String(row.id),
    name: row.name || '',
    phone: row.phone || '',
    role: normalizeRole(row.role),
    avatar: row.avatar || '',
    createdAt: row.createdAt ? String(row.createdAt).slice(0, 10) : '',
  }
}

function persistUser(user: UserInfo) {
  localStorage.setItem(HOTEL_USER, JSON.stringify(user))
}

function loadUser(): UserInfo | null {
  try {
    const token = localStorage.getItem(HOTEL_ACCESS_TOKEN)
    if (!isUsableToken(token)) {
      clearStoredAuth()
      return null
    }
    const data = localStorage.getItem(HOTEL_USER)
    return data ? JSON.parse(data) : null
  } catch {
    return null
  }
}

function loadToken(): string | null {
  const token = localStorage.getItem(HOTEL_ACCESS_TOKEN)
  if (!isUsableToken(token)) {
    clearStoredAuth()
    return null
  }
  return token
}

function clearStoredAuth() {
  localStorage.removeItem(HOTEL_USER)
  localStorage.removeItem(HOTEL_ACCESS_TOKEN)
  localStorage.removeItem(HOTEL_REFRESH_TOKEN)
  localStorage.removeItem('auth_user')
  localStorage.removeItem('auth_token')
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
}

function isUsableToken(token: string | null): token is string {
  if (!token) return false
  const parts = token.split('.')
  if (parts.length !== 3) return true
  try {
    const payload = JSON.parse(decodeURIComponent(escape(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))))
    const exp = Number(payload?.exp || 0)
    return !exp || exp * 1000 > Date.now() + 30000
  } catch {
    return false
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(loadUser())
  const token = ref<string | null>(loadToken())
  const employees = ref<Employee[]>([])
  const employeeLoading = ref(false)
  const error = ref('')

  const isLoggedIn = computed(() => Boolean(token.value && user.value && isUsableToken(token.value)))
  const role = computed(() => user.value?.role || null)
  const roleName = computed(() => role.value ? roleLabels[role.value] : '')
  const allowedRoutes = computed(() => role.value ? rolePermissions[role.value] : [])

  function canAccess(path: string): boolean {
    if (!role.value) return false
    return rolePermissions[role.value].some(route => path.startsWith(route))
  }

  function canManageEmployees(): boolean {
    return role.value === 'admin' || role.value === 'manager'
  }

  function saveLoginResult(result: any, phone: string) {
    const userInfo: UserInfo = {
      id: String(result.staffId || result.id || `user-${Date.now()}`),
      name: result.name || '',
      phone: result.phone || phone,
      role: normalizeRole(result.role),
      avatar: result.avatar || '',
    }
    const accessToken = result.accessToken || ''

    user.value = userInfo
    token.value = accessToken
    localStorage.setItem(HOTEL_ACCESS_TOKEN, accessToken)
    if (result.refreshToken) {
      localStorage.setItem(HOTEL_REFRESH_TOKEN, result.refreshToken)
    }
    persistUser(userInfo)
    return userInfo
  }

  async function sendSms(phone: string) {
    await sendSmsApi(phone)
  }

  async function login(phone: string, code: string) {
    return loginByPhone(phone, code)
  }

  async function loginByPhone(phone: string, code: string) {
    const { data } = await loginByPhoneApi(phone, code)
    const result = data.data || data
    return saveLoginResult(result, phone)
  }

  async function loginByPassword(phone: string, password: string) {
    const { data } = await loginByPasswordApi(phone, password)
    const result = data.data || data
    return saveLoginResult(result, phone)
  }

  function logout() {
    user.value = null
    token.value = null
    employees.value = []
    clearStoredAuth()
  }

  function setAccessToken(nextToken: string) {
    token.value = nextToken
    localStorage.setItem(HOTEL_ACCESS_TOKEN, nextToken)
  }

  async function updateProfile(data: { name?: string; phone?: string; avatar?: string }) {
    if (!user.value) return
    error.value = ''
    try {
      await updateProfileApi(data)
      user.value = {
        ...user.value,
        ...(data.name ? { name: data.name } : {}),
        ...(data.phone ? { phone: data.phone } : {}),
        ...(data.avatar !== undefined ? { avatar: data.avatar } : {}),
      }
      persistUser(user.value)
    } catch {
      error.value = '个人资料保存失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function changePassword(oldPwd: string, newPwd: string): Promise<boolean> {
    try {
      await changePasswordApi(oldPwd, newPwd)
      return true
    } catch {
      return false
    }
  }

  async function loadEmployeesFromApi() {
    employeeLoading.value = true
    error.value = ''
    try {
      const { data: res } = await getStaffListApi()
      const list = res.data || res
      employees.value = Array.isArray(list) ? list.map(mapEmployee) : []
    } catch {
      employees.value = []
      error.value = '员工列表加载失败，请稍后重试'
      throw new Error(error.value)
    } finally {
      employeeLoading.value = false
    }
  }

  async function addEmployee(emp: Omit<Employee, 'id' | 'createdAt'> & { password?: string }) {
    error.value = ''
    try {
      const { data: res } = await createStaffApi({
        name: emp.name,
        phone: emp.phone,
        role: emp.role,
        password: emp.password,
      })
      const created = res.data || res
      employees.value.push(mapEmployee({ ...emp, ...created }))
    } catch {
      error.value = '员工创建失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function removeEmployee(id: string) {
    error.value = ''
    try {
      await deleteStaffApi(Number(id))
      employees.value = employees.value.filter(employee => employee.id !== id)
    } catch {
      error.value = '员工删除失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function updateEmployee(id: string, data: Partial<Omit<Employee, 'id' | 'createdAt'>>) {
    const index = employees.value.findIndex(employee => employee.id === id)
    if (index === -1) return
    error.value = ''
    try {
      await updateStaffApi(Number(id), { name: data.name, phone: data.phone, role: data.role })
      employees.value[index] = { ...employees.value[index], ...data }
    } catch {
      error.value = '员工保存失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  async function resetEmployeePassword(id: string, newPassword: string) {
    error.value = ''
    try {
      await resetStaffPasswordApi(Number(id), newPassword)
    } catch {
      error.value = '员工密码重置失败，请稍后重试'
      throw new Error(error.value)
    }
  }

  return {
    user,
    token,
    employees,
    employeeLoading,
    error,
    isLoggedIn,
    role,
    roleName,
    allowedRoutes,
    canAccess,
    canManageEmployees,
    sendSms,
    login,
    loginByPhone,
    loginByPassword,
    logout,
    setAccessToken,
    updateProfile,
    changePassword,
    addEmployee,
    removeEmployee,
    updateEmployee,
    resetEmployeePassword,
    loadEmployeesFromApi,
  }
})
