import api from '@/api'

/** 发送短信验证码 */
export function sendSms(phone: string) {
  return api.post('/api/auth/sms/send', { phone })
}

/** 手机号+验证码登录 */
export function loginByPhone(phone: string, code: string) {
  return api.post('/api/auth/login/phone', { phone, code })
}

/** 密码登录（后台） */
export function loginByPassword(phone: string, password: string) {
  return api.post('/api/auth/login/password', { phone, password })
}

// ====== 员工管理 ======

/** 获取员工列表 */
export function getStaffList() {
  return api.get('/api/hotel/staff')
}

/** 新增员工 */
export function createStaff(data: { name: string; phone: string; role: string; password?: string }) {
  return api.post('/api/hotel/staff', data)
}

/** 更新员工 */
export function updateStaff(id: number, data: { name?: string; phone?: string; role?: string }) {
  return api.put(`/api/hotel/staff/${id}`, data)
}

/** 删除员工 */
export function deleteStaff(id: number) {
  return api.delete(`/api/hotel/staff/${id}`)
}

/** 重置员工密码 */
export function resetStaffPassword(id: number, newPassword: string) {
  return api.put(`/api/hotel/staff/${id}/password`, { newPassword })
}

// ====== 个人信息 ======

/** 获取个人信息 */
export function getProfile() {
  return api.get('/api/hotel/profile')
}

/** 修改个人信息 */
export function updateProfile(data: { name?: string; phone?: string; avatar?: string }) {
  return api.put('/api/hotel/profile', data)
}

/** 修改密码 */
export function changePassword(oldPassword: string, newPassword: string) {
  return api.put('/api/hotel/password', { oldPassword, newPassword })
}
