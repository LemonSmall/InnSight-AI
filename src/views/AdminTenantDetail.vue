<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { ArrowLeft, IdCard, KeyRound, Loader2, RefreshCw, Trash2, UserPlus } from 'lucide-vue-next'

type Tenant = {
  id: number
  name: string
  type?: string
  city?: string
  totalRooms?: number
  tags?: string
  targetAudience?: string
  nearby?: string
  tier?: string
  status?: string
  balance?: number
  alertThreshold?: number
  qpsLimit?: number
}

type Ledger = {
  id: number
  tenantId?: number
  type?: string
  amount?: number
  balanceAfter?: number
  moduleName?: string
  detail?: string
  status?: string
  createdAt?: string
}

type Subscription = {
  planCode?: string
  status?: string
  monthlyCredits?: number
  startAt?: string
  endAt?: string
}

type TenantPlan = {
  code?: string
  name?: string
  priceRmb?: number
  monthlyCredits?: number
  maxBranches?: number
  enabledModules?: string
}

type Staff = {
  id: number
  tenantId?: number
  name: string
  phone: string
  role: string
  avatar?: string
  createdAt?: string
}

const route = useRoute()
const tenant = ref<Tenant | null>(null)
const subscription = ref<Subscription | null>(null)
const tenantPlan = ref<TenantPlan | null>(null)
const ledger = ref<Ledger[]>([])
const staff = ref<Staff[]>([])
const loading = ref(true)
const toast = ref('')
const savingStaff = ref(false)
const staffActionLoading = ref(false)
const activeStaff = ref<Staff | null>(null)
const modal = ref<'phone' | 'password' | 'delete' | null>(null)
const modalForm = ref({
  phone: '',
  password: '123456'
})
const newStaff = ref({
  name: '',
  phone: '',
  role: 'manager',
  password: '123456'
})

const tenantLedger = computed(() => {
  if (!tenant.value?.id) return []
  return ledger.value.filter((item) => Number(item.tenantId) === Number(tenant.value?.id)).slice(0, 12)
})

const tierLabels: Record<string, string> = {
  trial: '试用版',
  basic: '基础版',
  pro: '专业版',
  flagship: '旗舰版'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const tenantId = route.query.id || 1
    const [{ data: tenantData }, { data: ledgerData }, { data: staffData }] = await Promise.all([
      api.get(`/api/admin/tenants/${tenantId}`),
      api.get('/api/admin/ledger'),
      api.get(`/api/admin/tenants/${tenantId}/staff`)
    ])
    const detail = tenantData.data || {}
    tenant.value = detail.tenant || detail
    subscription.value = detail.subscription || null
    tenantPlan.value = detail.plan || null
    ledger.value = ledgerData.data || []
    staff.value = staffData.data || []
  } catch {
    flash('租户详情加载失败')
  } finally {
    loading.value = false
  }
}

async function createStaff() {
  if (!tenant.value?.id || savingStaff.value) return
  if (!newStaff.value.name.trim() || !newStaff.value.phone.trim()) {
    flash('姓名和手机号不能为空')
    return
  }
  savingStaff.value = true
  try {
    await api.post(`/api/admin/tenants/${tenant.value.id}/staff`, newStaff.value)
    flash('酒店账号已注册')
    newStaff.value = { name: '', phone: '', role: 'manager', password: '123456' }
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '注册失败')
  } finally {
    savingStaff.value = false
  }
}

async function changeStaffRole(item: Staff) {
  if (!tenant.value?.id) return
  try {
    await api.put(`/api/admin/tenants/${tenant.value.id}/staff/${item.id}`, {
      name: item.name,
      role: item.role,
      avatar: item.avatar || ''
    })
    flash('账号信息已更新')
  } catch (error: any) {
    flash(error.response?.data?.message || '更新失败')
  }
}

async function rebindPhone(item: Staff) {
  activeStaff.value = item
  modalForm.value.phone = item.phone
  modal.value = 'phone'
}

async function submitRebindPhone() {
  if (!tenant.value?.id || !activeStaff.value) return
  const phone = modalForm.value.phone.trim()
  if (!phone) {
    flash('手机号不能为空')
    return
  }
  staffActionLoading.value = true
  try {
    await api.put(`/api/admin/tenants/${tenant.value.id}/staff/${activeStaff.value.id}/phone`, { phone })
    flash('手机号已换绑')
    closeModal()
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '换绑失败')
  } finally {
    staffActionLoading.value = false
  }
}

async function resetPassword(item: Staff) {
  activeStaff.value = item
  modalForm.value.password = '123456'
  modal.value = 'password'
}

async function submitResetPassword() {
  if (!tenant.value?.id || !activeStaff.value) return
  const password = modalForm.value.password
  if (!password || password.length < 6) {
    flash('密码至少 6 位')
    return
  }
  staffActionLoading.value = true
  try {
    await api.put(`/api/admin/tenants/${tenant.value.id}/staff/${activeStaff.value.id}/password`, { password })
    flash('密码已重置')
    closeModal()
  } catch (error: any) {
    flash(error.response?.data?.message || '重置失败')
  } finally {
    staffActionLoading.value = false
  }
}

async function deleteStaff(item: Staff) {
  activeStaff.value = item
  modal.value = 'delete'
}

async function submitDeleteStaff() {
  if (!tenant.value?.id || !activeStaff.value) return
  staffActionLoading.value = true
  try {
    await api.delete(`/api/admin/tenants/${tenant.value.id}/staff/${activeStaff.value.id}`)
    flash('账号已删除')
    closeModal()
    await load()
  } catch (error: any) {
    flash(error.response?.data?.message || '删除失败')
  } finally {
    staffActionLoading.value = false
  }
}

function closeModal() {
  modal.value = null
  activeStaff.value = null
}

function subscriptionStatusText(value?: string) {
  const map: Record<string, string> = {
    trialing: '试用中',
    active: '正常',
    past_due: '欠费',
    cancelled: '已取消',
    expired: '已过期',
  }
  return value ? (map[value] || value) : '-'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function typeClass(type?: string) {
  return type === 'recharge' ? 'text-emerald-300' : 'text-red-300'
}

function flash(msg: string) {
  toast.value = msg
  setTimeout(() => (toast.value = ''), 2000)
}
</script>

<template>
  <div class="space-y-5">
    <div v-if="toast" class="fixed right-5 top-5 z-50 rounded-md bg-indigo-600 px-4 py-2 text-sm text-white shadow-lg">
      {{ toast }}
    </div>

    <transition name="modal-fade">
      <div v-if="modal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4 backdrop-blur-sm">
        <div class="w-full max-w-md rounded-xl border border-gray-700 bg-gray-900 p-5 shadow-2xl">
          <div class="flex items-start justify-between gap-4">
            <div>
              <h3 class="text-base font-semibold text-gray-100">
                {{ modal === 'phone' ? '换绑手机号' : modal === 'password' ? '重置登录密码' : '删除酒店账号' }}
              </h3>
              <p class="mt-1 text-sm text-gray-500">
                {{ activeStaff?.name }} · {{ activeStaff?.phone }}
              </p>
            </div>
            <button class="rounded-md px-2 py-1 text-gray-500 hover:bg-gray-800 hover:text-gray-200" @click="closeModal">×</button>
          </div>

          <div v-if="modal === 'phone'" class="mt-5 space-y-2">
            <label class="text-xs font-medium text-gray-400">新手机号</label>
            <input v-model="modalForm.phone" class="admin-input" placeholder="请输入新的登录手机号" />
            <p class="text-xs text-gray-500">手机号会作为用户端短信登录和密码登录账号，不能与其他酒店账号重复。</p>
          </div>

          <div v-if="modal === 'password'" class="mt-5 space-y-2">
            <label class="text-xs font-medium text-gray-400">新密码</label>
            <input v-model="modalForm.password" class="admin-input" type="password" placeholder="至少 6 位" />
            <p class="text-xs text-gray-500">重置后员工可使用手机号 + 新密码登录用户端。</p>
          </div>

          <div v-if="modal === 'delete'" class="mt-5 rounded-lg border border-red-900/60 bg-red-950/30 p-4 text-sm text-red-100">
            删除后该员工无法再登录用户端。这个操作只删除员工账号，不会删除酒店资料、生成历史或账单数据。
          </div>

          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-secondary" :disabled="staffActionLoading" @click="closeModal">取消</button>
            <button
              v-if="modal === 'phone'"
              class="btn-primary"
              :disabled="staffActionLoading"
              @click="submitRebindPhone"
            >
              <Loader2 v-if="staffActionLoading" class="h-4 w-4 animate-spin" />
              确认换绑
            </button>
            <button
              v-if="modal === 'password'"
              class="btn-primary"
              :disabled="staffActionLoading"
              @click="submitResetPassword"
            >
              <Loader2 v-if="staffActionLoading" class="h-4 w-4 animate-spin" />
              确认重置
            </button>
            <button
              v-if="modal === 'delete'"
              class="btn-danger"
              :disabled="staffActionLoading"
              @click="submitDeleteStaff"
            >
              <Loader2 v-if="staffActionLoading" class="h-4 w-4 animate-spin" />
              删除账号
            </button>
          </div>
        </div>
      </div>
    </transition>

    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-base font-semibold text-gray-100">
        <IdCard class="h-5 w-5 text-indigo-400" />
        租户详情
      </div>
      <div class="flex gap-2">
        <RouterLink class="btn-secondary" to="/admin/tenants">
          <ArrowLeft class="h-4 w-4" />
          返回列表
        </RouterLink>
        <button class="btn-secondary" @click="load">
          <RefreshCw class="h-4 w-4" />
          刷新
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex justify-center py-16">
      <Loader2 class="h-6 w-6 animate-spin text-gray-500" />
    </div>

    <template v-else-if="tenant">
      <section class="grid gap-4 lg:grid-cols-3">
        <div class="rounded-lg border border-gray-800 bg-gray-900 p-5 lg:col-span-2">
          <h2 class="text-lg font-semibold text-gray-100">{{ tenant.name }}</h2>
          <p class="mt-1 text-sm text-gray-500">{{ tenant.type || '未设置类型' }} · {{ tenant.city || '未设置城市' }}</p>
          <div class="mt-5 grid gap-4 md:grid-cols-2">
            <div class="info-row"><span>房间数</span><strong>{{ tenant.totalRooms || 0 }}</strong></div>
            <div class="info-row"><span>特色标签</span><strong>{{ tenant.tags || '-' }}</strong></div>
            <div class="info-row"><span>目标客群</span><strong>{{ tenant.targetAudience || '-' }}</strong></div>
            <div class="info-row"><span>周边信息</span><strong>{{ tenant.nearby || '-' }}</strong></div>
          </div>
        </div>

        <div class="rounded-lg border border-gray-800 bg-gray-900 p-5">
          <h3 class="text-sm font-semibold text-gray-100">账户状态</h3>
          <div class="mt-4 space-y-3">
            <div class="info-row"><span>套餐</span><strong>{{ tierLabels[tenant.tier || 'trial'] || tenant.tier }}</strong></div>
            <div class="info-row"><span>订阅套餐</span><strong>{{ tenantPlan?.name || subscription?.planCode || '-' }}</strong></div>
            <div class="info-row"><span>订阅状态</span><strong>{{ subscriptionStatusText(subscription?.status) }}</strong></div>
            <div class="info-row"><span>月赠算力</span><strong>{{ Number(subscription?.monthlyCredits || tenantPlan?.monthlyCredits || 0).toLocaleString() }}</strong></div>
            <div class="info-row"><span>到期时间</span><strong>{{ formatTime(subscription?.endAt) }}</strong></div>
            <div class="info-row"><span>算力余额</span><strong :class="Number(tenant.balance || 0) < 500 ? 'text-amber-300' : ''">{{ Number(tenant.balance || 0).toLocaleString() }}</strong></div>
            <div class="info-row"><span>预警阈值</span><strong>{{ tenant.alertThreshold || '-' }}</strong></div>
            <div class="info-row"><span>QPS 限制</span><strong>{{ tenant.qpsLimit || '-' }}</strong></div>
            <div class="info-row"><span>状态</span><strong>{{ tenant.status || 'active' }}</strong></div>
          </div>
        </div>
      </section>

      <section class="rounded-lg border border-gray-800 bg-gray-900">
        <div class="flex items-center justify-between border-b border-gray-800 p-4">
          <div>
            <h3 class="text-sm font-semibold text-gray-100">酒店登录账号</h3>
            <p class="mt-1 text-xs text-gray-500">账号与当前酒店绑定，用户端支持手机号验证码或手机号密码登录。</p>
          </div>
          <span class="rounded-full bg-gray-800 px-3 py-1 text-xs text-gray-400">{{ staff.length }} 个账号</span>
        </div>

        <div class="grid gap-3 border-b border-gray-800 p-4 lg:grid-cols-[1fr_1fr_150px_150px_auto]">
          <input v-model="newStaff.name" class="admin-input" placeholder="姓名，例如：店长" />
          <input v-model="newStaff.phone" class="admin-input" placeholder="手机号" />
          <select v-model="newStaff.role" class="admin-input">
            <option value="admin">管理员</option>
            <option value="manager">店长</option>
            <option value="marketing">运营</option>
            <option value="front_desk">前台</option>
          </select>
          <input v-model="newStaff.password" class="admin-input" placeholder="初始密码" />
          <button class="btn-primary" :disabled="savingStaff" @click="createStaff">
            <Loader2 v-if="savingStaff" class="h-4 w-4 animate-spin" />
            <UserPlus v-else class="h-4 w-4" />
            注册
          </button>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead class="text-xs text-gray-500">
              <tr class="border-b border-gray-800">
                <th class="px-4 py-3 font-medium">姓名</th>
                <th class="px-4 py-3 font-medium">手机号</th>
                <th class="px-4 py-3 font-medium">角色</th>
                <th class="px-4 py-3 font-medium">创建时间</th>
                <th class="px-4 py-3 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in staff" :key="item.id" class="border-b border-gray-800/70">
                <td class="px-4 py-3">
                  <input v-model="item.name" class="table-input max-w-[160px]" @change="changeStaffRole(item)" />
                </td>
                <td class="px-4 py-3 font-mono text-gray-300">{{ item.phone }}</td>
                <td class="px-4 py-3">
                  <select v-model="item.role" class="table-input" @change="changeStaffRole(item)">
                    <option value="admin">管理员</option>
                    <option value="manager">店长</option>
                    <option value="marketing">运营</option>
                    <option value="front_desk">前台</option>
                  </select>
                </td>
                <td class="px-4 py-3 text-gray-500">{{ formatTime(item.createdAt) }}</td>
                <td class="px-4 py-3">
                  <div class="flex justify-end gap-2">
                    <button class="table-action" @click="rebindPhone(item)">换绑</button>
                    <button class="table-action" @click="resetPassword(item)">
                      <KeyRound class="h-3.5 w-3.5" />
                      重置
                    </button>
                    <button class="table-action danger" @click="deleteStaff(item)">
                      <Trash2 class="h-3.5 w-3.5" />
                      删除
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="staff.length === 0" class="py-12 text-center text-sm text-gray-500">暂无酒店登录账号</div>
        </div>
      </section>

      <section class="rounded-lg border border-gray-800 bg-gray-900">
        <div class="border-b border-gray-800 p-4">
          <h3 class="text-sm font-semibold text-gray-100">最近流水</h3>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead class="text-xs text-gray-500">
              <tr class="border-b border-gray-800">
                <th class="px-4 py-3 font-medium">时间</th>
                <th class="px-4 py-3 font-medium">类型</th>
                <th class="px-4 py-3 font-medium">模块</th>
                <th class="px-4 py-3 font-medium">算力变动</th>
                <th class="px-4 py-3 font-medium">余额</th>
                <th class="px-4 py-3 font-medium">备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in tenantLedger" :key="item.id" class="border-b border-gray-800/70">
                <td class="px-4 py-3 text-gray-400">{{ formatTime(item.createdAt) }}</td>
                <td class="px-4 py-3 text-gray-300">{{ item.type === 'recharge' ? '充值' : '消耗' }}</td>
                <td class="px-4 py-3 text-gray-300">{{ item.moduleName || '-' }}</td>
                <td class="px-4 py-3 font-mono" :class="typeClass(item.type)">{{ item.type === 'recharge' ? '+' : '' }}{{ item.amount || 0 }}</td>
                <td class="px-4 py-3 font-mono text-gray-300">{{ Number(item.balanceAfter || 0).toLocaleString() }}</td>
                <td class="px-4 py-3 text-gray-500">{{ item.detail || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="tenantLedger.length === 0" class="py-12 text-center text-sm text-gray-500">暂无该租户流水</div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  border-radius: 0.5rem;
  border: 1px solid #374151;
  padding: 0.625rem 0.875rem;
  color: #d1d5db;
  font-size: 0.875rem;
}
.btn-secondary:hover {
  background: #1f2937;
}
.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border-radius: 0.5rem;
  background: #4f46e5;
  padding: 0.625rem 0.875rem;
  color: white;
  font-size: 0.875rem;
  font-weight: 600;
}
.btn-primary:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.btn-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border-radius: 0.5rem;
  background: #dc2626;
  padding: 0.625rem 0.875rem;
  color: white;
  font-size: 0.875rem;
  font-weight: 600;
}
.btn-danger:disabled,
.btn-secondary:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.admin-input,
.table-input {
  width: 100%;
  border-radius: 0.5rem;
  border: 1px solid #374151;
  background: #111827;
  padding: 0.625rem 0.75rem;
  color: #e5e7eb;
  outline: none;
  font-size: 0.875rem;
}
.table-input {
  padding: 0.375rem 0.5rem;
}
.admin-input:focus,
.table-input:focus {
  border-color: #6366f1;
}
.table-action {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  border-radius: 0.375rem;
  border: 1px solid #374151;
  padding: 0.375rem 0.5rem;
  color: #c7d2fe;
  font-size: 0.75rem;
}
.table-action:hover {
  background: #1f2937;
}
.table-action.danger {
  color: #fca5a5;
}
.info-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid #1f2937;
  padding-bottom: 0.75rem;
  color: #9ca3af;
  font-size: 0.875rem;
}
.info-row strong {
  color: #f3f4f6;
  text-align: right;
  font-weight: 600;
}
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 160ms ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
</style>
