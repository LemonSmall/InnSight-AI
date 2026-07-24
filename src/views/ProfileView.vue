<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore, roleLabels, type Role, type Employee } from '@/stores/auth'
import {
  User, Lock, Users, Plus, Trash2, Edit, Check, X, LogOut,
  Loader2
} from 'lucide-vue-next'

const auth = useAuthStore()
const router = useRouter()

// 加载员工列表
onMounted(async () => {
  try {
    await auth.loadEmployeesFromApi()
  } catch {
    empError.value = auth.error || '员工列表加载失败，请稍后重试'
  }
})

// ====== Tab ======
const activeTab = ref<'info' | 'password' | 'employees'>('info')

// ====== 个人信息编辑 ======
const editName = ref(auth.user?.name || '')
const editPhone = ref(auth.user?.phone || '')
const infoEditing = ref(false)
const infoSaved = ref(false)
const infoError = ref('')
const infoSaving = ref(false)

async function saveInfo() {
  infoError.value = ''
  infoSaving.value = true
  try {
    await auth.updateProfile({ name: editName.value, phone: editPhone.value })
    infoEditing.value = false
    infoSaved.value = true
    setTimeout(() => { infoSaved.value = false }, 2000)
  } catch {
    infoError.value = auth.error || '个人资料保存失败，请稍后重试'
  } finally {
    infoSaving.value = false
  }
}

function cancelInfo() {
  editName.value = auth.user?.name || ''
  editPhone.value = auth.user?.phone || ''
  infoEditing.value = false
}

// ====== 修改密码 ======
const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const pwdError = ref('')
const pwdSuccess = ref(false)
const pwdLoading = ref(false)

async function changePwd() {
  pwdError.value = ''
  if (!oldPwd.value) { pwdError.value = '请输入旧密码'; return }
  if (newPwd.value.length < 6) { pwdError.value = '新密码至少6位'; return }
  if (newPwd.value !== confirmPwd.value) { pwdError.value = '两次密码不一致'; return }

  pwdLoading.value = true
  try {
    const ok = await auth.changePassword(oldPwd.value, newPwd.value)
    if (ok) {
      pwdSuccess.value = true
      oldPwd.value = ''; newPwd.value = ''; confirmPwd.value = ''
      setTimeout(() => { pwdSuccess.value = false }, 2000)
    } else {
      pwdError.value = '旧密码不正确'
    }
  } catch {
    pwdError.value = '修改失败，请稍后重试'
  } finally {
    pwdLoading.value = false
  }
}

// ====== 员工管理 ======
const showAddEmp = ref(false)
const newEmp = ref({ name: '', phone: '', role: 'front_desk' as Role, password: '123456' })
const empError = ref('')
const empSaving = ref(false)
const editingEmpId = ref<string | null>(null)
const editEmp = ref({ name: '', phone: '', role: 'front_desk' as Role })

async function addEmployee() {
  empError.value = ''
  if (!newEmp.value.name.trim()) { empError.value = '请输入员工姓名'; return }
  if (!/^1[3-9]\d{9}$/.test(newEmp.value.phone)) { empError.value = '请输入正确的手机号'; return }
  if (newEmp.value.password.length < 6) { empError.value = '初始密码至少6位'; return }
  empSaving.value = true
  try {
    await auth.addEmployee({
      name: newEmp.value.name,
      phone: newEmp.value.phone,
      role: newEmp.value.role,
      password: newEmp.value.password,
      avatar: '',
    })
    newEmp.value = { name: '', phone: '', role: 'front_desk', password: '123456' }
    showAddEmp.value = false
  } catch {
    empError.value = auth.error || '员工添加失败，请稍后重试'
  } finally {
    empSaving.value = false
  }
}

function startEditEmp(emp: Employee) {
  editingEmpId.value = emp.id
  editEmp.value = { name: emp.name, phone: emp.phone, role: emp.role }
}

async function saveEditEmp() {
  if (!editingEmpId.value) return
  empError.value = ''
  empSaving.value = true
  try {
    await auth.updateEmployee(editingEmpId.value, { ...editEmp.value })
    editingEmpId.value = null
  } catch {
    empError.value = auth.error || '员工信息保存失败，请稍后重试'
  } finally {
    empSaving.value = false
  }
}

function cancelEditEmp() {
  editingEmpId.value = null
}

async function removeEmployee(id: string) {
  empError.value = ''
  empSaving.value = true
  try {
    await auth.removeEmployee(id)
  } catch {
    empError.value = auth.error || '员工删除失败，请稍后重试'
  } finally {
    empSaving.value = false
  }
}

// ====== 退出 ======
function handleLogout() {
  auth.logout()
  router.push('/login')
}

// 角色颜色
const roleColors: Record<Role, string> = {
  admin: 'bg-red-50 text-red-600 border-red-200',
  manager: 'bg-blue-50 text-blue-600 border-blue-200',
  front_desk: 'bg-green-50 text-green-600 border-green-200',
  marketing: 'bg-purple-50 text-purple-600 border-purple-200',
}

function getInitials(name: string): string {
  return name ? name.slice(0, 1) : '?'
}
</script>

<template>
  <div class="h-full flex flex-col max-w-3xl mx-auto w-full">
    <!-- Header -->
    <div class="flex items-center justify-between mb-5">
      <div>
        <h1 class="text-sm font-semibold text-bamboo-900">个人中心</h1>
        <p class="text-[11px] text-warm-500 mt-0.5">管理账号信息、密码与员工</p>
      </div>
      <button @click="handleLogout" class="text-[11px] px-3 py-1.5 rounded-lg border border-red-200 bg-white text-red-500 hover:bg-red-50 transition-colors flex items-center gap-1.5">
        <LogOut class="w-3.5 h-3.5" />退出登录
      </button>
    </div>

    <!-- 用户信息卡片 -->
    <div class="bg-white border border-cream-200 rounded-xl p-5 mb-4 flex items-center gap-4">
      <div class="w-14 h-14 rounded-full bg-bamboo-800 flex items-center justify-center text-white text-lg font-bold flex-shrink-0">
        {{ getInitials(auth.user?.name || '') }}
      </div>
      <div class="flex-1 min-w-0">
        <div class="text-sm font-semibold text-bamboo-900">{{ auth.user?.name }}</div>
        <div class="text-xs text-warm-500 mt-0.5">{{ auth.user?.phone }}</div>
        <span :class="['text-[10px] px-2 py-0.5 rounded-full border mt-1 inline-block', roleColors[auth.role || 'front_desk']]">
          {{ auth.roleName }}
        </span>
      </div>
    </div>

    <!-- Tab 切换 -->
    <div class="flex bg-cream-100 rounded-lg p-1 gap-1 mb-4">
      <button @click="activeTab = 'info'" :class="['flex-1 py-2 rounded-md text-xs font-medium transition-colors flex items-center justify-center gap-1.5', activeTab === 'info' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-bamboo-700']">
        <User class="w-3.5 h-3.5" />账号信息
      </button>
      <button @click="activeTab = 'password'" :class="['flex-1 py-2 rounded-md text-xs font-medium transition-colors flex items-center justify-center gap-1.5', activeTab === 'password' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-bamboo-700']">
        <Lock class="w-3.5 h-3.5" />修改密码
      </button>
      <button v-if="auth.canManageEmployees()" @click="activeTab = 'employees'" :class="['flex-1 py-2 rounded-md text-xs font-medium transition-colors flex items-center justify-center gap-1.5', activeTab === 'employees' ? 'bg-white text-bamboo-800 shadow-sm' : 'text-warm-500 hover:text-bamboo-700']">
        <Users class="w-3.5 h-3.5" />员工管理
      </button>
    </div>

    <!-- 账号信息 -->
    <div v-if="activeTab === 'info'" class="bg-white border border-cream-200 rounded-xl p-5 space-y-4">
      <div v-if="infoSaved" class="text-xs text-green-600 bg-green-50 rounded-lg px-3 py-2">信息已保存</div>
      <div v-if="infoError" class="text-xs text-red-500 bg-red-50 rounded-lg px-3 py-2">{{ infoError }}</div>

      <div>
        <label class="text-xs font-medium text-warm-700 mb-1 block">姓名</label>
        <input v-model="editName" :disabled="!infoEditing" :class="['w-full text-sm px-3 py-2 rounded-lg border transition-colors', infoEditing ? 'border-bamboo-400 bg-white text-bamboo-950 focus:outline-none' : 'border-cream-200 bg-cream-50 text-warm-600']" />
      </div>
      <div>
        <label class="text-xs font-medium text-warm-700 mb-1 block">手机号</label>
        <input v-model="editPhone" :disabled="!infoEditing" :class="['w-full text-sm px-3 py-2 rounded-lg border transition-colors', infoEditing ? 'border-bamboo-400 bg-white text-bamboo-950 focus:outline-none' : 'border-cream-200 bg-cream-50 text-warm-600']" />
      </div>
      <div class="flex gap-2">
        <button v-if="!infoEditing" @click="infoEditing = true" class="text-xs px-4 py-2 rounded-lg bg-bamboo-800 text-white hover:bg-bamboo-900 transition-colors flex items-center gap-1.5">
          <Edit class="w-3.5 h-3.5" />编辑
        </button>
        <template v-else>
          <button @click="saveInfo" :disabled="infoSaving" class="text-xs px-4 py-2 rounded-lg bg-bamboo-800 text-white hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 transition-colors flex items-center gap-1.5">
            <Loader2 v-if="infoSaving" class="w-3.5 h-3.5 animate-spin" />
            <Check v-else class="w-3.5 h-3.5" />{{ infoSaving ? '保存中...' : '保存' }}
          </button>
          <button @click="cancelInfo" class="text-xs px-4 py-2 rounded-lg border border-cream-300 bg-white text-warm-600 hover:bg-cream-50 transition-colors flex items-center gap-1.5">
            <X class="w-3.5 h-3.5" />取消
          </button>
        </template>
      </div>
    </div>

    <!-- 修改密码 -->
    <div v-if="activeTab === 'password'" class="bg-white border border-cream-200 rounded-xl p-5 space-y-4">
      <div v-if="pwdSuccess" class="text-xs text-green-600 bg-green-50 rounded-lg px-3 py-2">密码修改成功</div>
      <div v-if="pwdError" class="text-xs text-red-500 bg-red-50 rounded-lg px-3 py-2">{{ pwdError }}</div>

      <div>
        <label class="text-xs font-medium text-warm-700 mb-1 block">旧密码</label>
        <input v-model="oldPwd" type="password" placeholder="请输入旧密码" class="w-full text-sm px-3 py-2 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400 transition-colors" />
      </div>
      <div>
        <label class="text-xs font-medium text-warm-700 mb-1 block">新密码</label>
        <input v-model="newPwd" type="password" placeholder="至少6位" class="w-full text-sm px-3 py-2 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400 transition-colors" />
      </div>
      <div>
        <label class="text-xs font-medium text-warm-700 mb-1 block">确认新密码</label>
        <input v-model="confirmPwd" type="password" placeholder="再次输入新密码" class="w-full text-sm px-3 py-2 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400 transition-colors" />
      </div>
      <button @click="changePwd" :disabled="pwdLoading" class="text-xs px-4 py-2 rounded-lg bg-bamboo-800 text-white hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 transition-colors flex items-center gap-1.5">
        <Loader2 v-if="pwdLoading" class="w-3.5 h-3.5 animate-spin" />
        <Lock v-else class="w-3.5 h-3.5" />
        {{ pwdLoading ? '修改中...' : '修改密码' }}
      </button>
    </div>

    <!-- 员工管理 -->
    <div v-if="activeTab === 'employees' && auth.canManageEmployees()" class="bg-white border border-cream-200 rounded-xl p-5 space-y-4">
      <div class="flex items-center justify-between">
        <span class="text-xs font-medium text-warm-700">员工列表 ({{ auth.employees.length }})</span>
        <button @click="showAddEmp = !showAddEmp" :disabled="auth.employeeLoading || empSaving" class="text-xs px-3 py-1.5 rounded-lg bg-bamboo-800 text-white hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 transition-colors flex items-center gap-1.5">
          <Plus class="w-3.5 h-3.5" />添加员工
        </button>
      </div>
      <div v-if="auth.employeeLoading" class="text-xs text-warm-500 bg-cream-50 rounded-lg px-3 py-2 flex items-center gap-2">
        <Loader2 class="w-3.5 h-3.5 animate-spin" />员工列表加载中...
      </div>
      <div v-if="empError && !showAddEmp" class="text-xs text-red-500 bg-red-50 rounded-lg px-3 py-2">{{ empError }}</div>

      <!-- 添加员工表单 -->
      <div v-if="showAddEmp" class="bg-cream-50 border border-cream-200 rounded-lg p-4 space-y-3">
        <div v-if="empError" class="text-xs text-red-500">{{ empError }}</div>
        <div class="grid grid-cols-4 gap-3">
          <input v-model="newEmp.name" placeholder="姓名" class="text-xs px-3 py-2 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400" />
          <input v-model="newEmp.phone" placeholder="手机号" class="text-xs px-3 py-2 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400" />
          <input v-model="newEmp.password" type="password" placeholder="初始密码" class="text-xs px-3 py-2 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400" />
          <select v-model="newEmp.role" class="text-xs px-3 py-2 rounded-lg border border-cream-300 bg-white text-bamboo-950 focus:outline-none focus:border-bamboo-400">
            <option v-for="(label, key) in roleLabels" :key="key" :value="key" v-show="key !== 'admin'">{{ label }}</option>
          </select>
        </div>
        <div class="flex gap-2">
          <button @click="addEmployee" :disabled="empSaving" class="text-xs px-4 py-2 rounded-lg bg-bamboo-800 text-white hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400 transition-colors">{{ empSaving ? '添加中...' : '确认添加' }}</button>
          <button @click="showAddEmp = false" class="text-xs px-4 py-2 rounded-lg border border-cream-300 bg-white text-warm-600 hover:bg-cream-50 transition-colors">取消</button>
        </div>
      </div>

      <!-- 员工列表 -->
      <div class="space-y-2">
        <div v-for="emp in auth.employees" :key="emp.id" class="flex items-center justify-between p-3 border border-cream-200 rounded-lg hover:border-cream-300 transition-colors">
          <template v-if="editingEmpId === emp.id">
            <!-- 编辑模式 -->
            <div class="flex-1 grid grid-cols-3 gap-2 mr-3">
              <input v-model="editEmp.name" class="text-xs px-2 py-1.5 rounded border border-cream-300 focus:outline-none focus:border-bamboo-400" />
              <input v-model="editEmp.phone" class="text-xs px-2 py-1.5 rounded border border-cream-300 focus:outline-none focus:border-bamboo-400" />
              <select v-model="editEmp.role" class="text-xs px-2 py-1.5 rounded border border-cream-300 focus:outline-none focus:border-bamboo-400">
                <option v-for="(label, key) in roleLabels" :key="key" :value="key" v-show="key !== 'admin'">{{ label }}</option>
              </select>
            </div>
            <div class="flex gap-1.5">
              <button @click="saveEditEmp" :disabled="empSaving" class="text-[10px] p-1.5 rounded bg-bamboo-800 text-white hover:bg-bamboo-900 disabled:bg-cream-200 disabled:text-warm-400"><Check class="w-3 h-3" /></button>
              <button @click="cancelEditEmp" class="text-[10px] p-1.5 rounded border border-cream-300 text-warm-500 hover:bg-cream-50"><X class="w-3 h-3" /></button>
            </div>
          </template>
          <template v-else>
            <!-- 展示模式 -->
            <div class="flex items-center gap-3 flex-1 min-w-0">
              <div class="w-8 h-8 rounded-full bg-bamboo-100 text-bamboo-700 flex items-center justify-center text-xs font-bold flex-shrink-0">
                {{ getInitials(emp.name) }}
              </div>
              <div class="min-w-0">
                <div class="text-xs font-medium text-bamboo-950">{{ emp.name }}</div>
                <div class="text-[10px] text-warm-500">{{ emp.phone }}</div>
              </div>
              <span :class="['text-[10px] px-2 py-0.5 rounded-full border', roleColors[emp.role]]">{{ roleLabels[emp.role] }}</span>
            </div>
            <div class="flex gap-1.5">
              <button @click="startEditEmp(emp)" class="text-[10px] p-1.5 rounded border border-cream-300 text-warm-500 hover:bg-bamboo-50 hover:text-bamboo-700"><Edit class="w-3 h-3" /></button>
              <button @click="removeEmployee(emp.id)" :disabled="empSaving" class="text-[10px] p-1.5 rounded border border-red-200 text-red-400 hover:bg-red-50 disabled:bg-cream-100 disabled:text-warm-300"><Trash2 class="w-3 h-3" /></button>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
