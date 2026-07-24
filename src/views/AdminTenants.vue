<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { Building2, Check, Loader2, Plus, RefreshCw, Search, X } from 'lucide-vue-next'

type Tenant = {
  id: number
  name: string
  type?: string
  city?: string
  totalRooms?: number
  tags?: string
  tier?: string
  status?: string
  balance?: number
}

const router = useRouter()
const tenants = ref<Tenant[]>([])
const loading = ref(true)
const toast = ref('')
const keyword = ref('')

const showModal = ref(false)
const editingId = ref<number | null>(null)
const formLoading = ref(false)
const form = reactive({
  name: '',
  type: '精品民宿',
  city: '',
  totalRooms: 0,
  tags: '',
  tier: 'trial',
  status: 'active',
  balance: 0
})

const tierLabels: Record<string, string> = {
  trial: '试用版',
  basic: '基础版',
  pro: '专业版',
  flagship: '旗舰版'
}

const statusLabels: Record<string, string> = {
  active: '正常',
  warning: '预警',
  suspended: '暂停',
  closed: '关闭'
}

const filteredTenants = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return tenants.value
  return tenants.value.filter((tenant) =>
    [tenant.name, tenant.city, tenant.type, tenant.tags].some((item) => String(item || '').toLowerCase().includes(kw))
  )
})

const totalBalance = computed(() => tenants.value.reduce((sum, tenant) => sum + Number(tenant.balance || 0), 0))
const lowBalanceCount = computed(() => tenants.value.filter((tenant) => Number(tenant.balance || 0) < 500).length)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/api/admin/tenants')
    tenants.value = data.data || []
  } catch {
    flash('酒店账户加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    name: '',
    type: '精品民宿',
    city: '',
    totalRooms: 0,
    tags: '',
    tier: 'trial',
    status: 'active',
    balance: 0
  })
}

function openNew() {
  editingId.value = null
  resetForm()
  showModal.value = true
}

function openEdit(tenant: Tenant) {
  editingId.value = tenant.id
  Object.assign(form, {
    name: tenant.name || '',
    type: tenant.type || '精品民宿',
    city: tenant.city || '',
    totalRooms: tenant.totalRooms || 0,
    tags: tenant.tags || '',
    tier: tenant.tier || 'trial',
    status: tenant.status || 'active',
    balance: tenant.balance || 0
  })
  showModal.value = true
}

async function save() {
  if (!form.name.trim()) {
    flash('请输入酒店名称')
    return
  }
  formLoading.value = true
  const payload = {
    ...form,
    type: form.type.trim() || '精品民宿',
    city: form.city.trim() || '未设置城市',
    totalRooms: Number(form.totalRooms || 0),
    balance: Number(form.balance || 0),
    alertThreshold: 500,
    meltThreshold: 0,
    qpsLimit: 5
  }
  try {
    if (editingId.value) {
      await api.put(`/api/admin/tenants/${editingId.value}`, payload)
      flash('酒店账户已保存')
    } else {
      await api.post('/api/admin/tenants', payload)
      flash('酒店账户已创建')
    }
    showModal.value = false
    await load()
  } catch (error: any) {
    flash(error?.response?.data?.message || '保存失败')
  } finally {
    formLoading.value = false
  }
}

async function removeTenant(id: number) {
  if (!confirm('删除后该酒店账户将不再显示在管理端，确定删除吗？')) return
  try {
    await api.delete(`/api/admin/tenants/${id}`)
    flash('酒店账户已删除')
    await load()
  } catch (error: any) {
    flash(error?.response?.data?.message || '删除失败')
  }
}

function viewDetail(id: number) {
  router.push({ path: '/admin/tenant-detail', query: { id } })
}

function tierClass(tier?: string) {
  if (tier === 'flagship') return 'bg-violet-500/15 text-violet-300'
  if (tier === 'pro') return 'bg-indigo-500/15 text-indigo-300'
  if (tier === 'basic') return 'bg-blue-500/15 text-blue-300'
  return 'bg-gray-700 text-gray-300'
}

function statusClass(status?: string) {
  if (status === 'active') return 'bg-emerald-500/10 text-emerald-300'
  if (status === 'warning') return 'bg-amber-500/10 text-amber-300'
  return 'bg-gray-700 text-gray-300'
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

    <section class="rounded-lg border border-gray-800 bg-gray-900 p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2 text-base font-semibold text-gray-100">
            <Building2 class="h-5 w-5 text-indigo-400" />
            酒店账户
          </div>
          <p class="mt-1 text-sm text-gray-500">管理平台内的酒店、民宿与度假门店账户。</p>
        </div>
        <div class="flex items-center gap-2">
          <button class="btn-secondary" @click="load">
            <RefreshCw class="h-4 w-4" />
            刷新
          </button>
          <button class="btn-primary" @click="openNew">
            <Plus class="h-4 w-4" />
            新增酒店
          </button>
        </div>
      </div>

      <div class="mt-5 grid gap-3 md:grid-cols-3">
        <div class="metric">
          <span>酒店账户</span>
          <strong>{{ tenants.length }}</strong>
        </div>
        <div class="metric">
          <span>总算力余额</span>
          <strong>{{ totalBalance.toLocaleString() }}</strong>
        </div>
        <div class="metric">
          <span>低余额账户</span>
          <strong :class="lowBalanceCount > 0 ? 'text-amber-300' : ''">{{ lowBalanceCount }}</strong>
        </div>
      </div>
    </section>

    <section class="rounded-lg border border-gray-800 bg-gray-900">
      <div class="flex items-center justify-between border-b border-gray-800 p-4">
        <div class="relative w-full max-w-sm">
          <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-500" />
          <input v-model="keyword" class="input pl-9" placeholder="搜索酒店名称、城市、标签" />
        </div>
      </div>

      <div v-if="loading" class="flex justify-center py-16">
        <Loader2 class="h-6 w-6 animate-spin text-gray-500" />
      </div>

      <div v-else class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead class="text-xs text-gray-500">
            <tr class="border-b border-gray-800">
              <th class="px-4 py-3 font-medium">酒店</th>
              <th class="px-4 py-3 font-medium">城市</th>
              <th class="px-4 py-3 font-medium">套餐</th>
              <th class="px-4 py-3 font-medium">算力余额</th>
              <th class="px-4 py-3 font-medium">状态</th>
              <th class="px-4 py-3 text-right font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="tenant in filteredTenants" :key="tenant.id" class="border-b border-gray-800/70 hover:bg-gray-800/40">
              <td class="px-4 py-3">
                <button class="text-left" @click="viewDetail(tenant.id)">
                  <div class="font-medium text-gray-100">{{ tenant.name }}</div>
                  <div class="mt-1 text-xs text-gray-500">{{ tenant.type || '未设置类型' }} · {{ tenant.totalRooms || 0 }} 间房</div>
                </button>
              </td>
              <td class="px-4 py-3 text-gray-300">{{ tenant.city || '-' }}</td>
              <td class="px-4 py-3">
                <span :class="['rounded-full px-2 py-1 text-xs', tierClass(tenant.tier)]">{{ tierLabels[tenant.tier || 'trial'] || tenant.tier }}</span>
              </td>
              <td class="px-4 py-3 font-mono" :class="Number(tenant.balance || 0) < 500 ? 'text-amber-300' : 'text-gray-200'">
                {{ Number(tenant.balance || 0).toLocaleString() }}
              </td>
              <td class="px-4 py-3">
                <span :class="['rounded-full px-2 py-1 text-xs', statusClass(tenant.status)]">{{ statusLabels[tenant.status || 'active'] || tenant.status }}</span>
              </td>
              <td class="px-4 py-3">
                <div class="flex justify-end gap-3">
                  <button class="text-indigo-300 hover:text-indigo-200" @click="openEdit(tenant)">编辑</button>
                  <button class="text-gray-400 hover:text-gray-200" @click="viewDetail(tenant.id)">详情</button>
                  <button class="text-red-300 hover:text-red-200" @click="removeTenant(tenant.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="filteredTenants.length === 0" class="py-12 text-center text-sm text-gray-500">暂无酒店账户</div>
      </div>
    </section>

    <div v-if="showModal" class="fixed inset-0 z-40 flex items-center justify-center bg-black/70 p-4" @click.self="showModal = false">
      <form class="w-full max-w-2xl rounded-lg border border-gray-700 bg-gray-900 p-5 shadow-2xl" @submit.prevent="save">
        <div class="mb-5 flex items-center justify-between">
          <h2 class="text-base font-semibold text-gray-100">{{ editingId ? '编辑酒店账户' : '新增酒店账户' }}</h2>
          <button type="button" class="text-gray-500 hover:text-gray-200" @click="showModal = false">
            <X class="h-5 w-5" />
          </button>
        </div>

        <div class="grid gap-4 md:grid-cols-2">
          <label class="field">
            <span>酒店名称</span>
            <input v-model="form.name" class="input" required />
          </label>
          <label class="field">
            <span>酒店类型</span>
            <input v-model="form.type" class="input" placeholder="精品民宿 / 度假酒店 / 商务酒店" />
          </label>
          <label class="field">
            <span>城市</span>
            <input v-model="form.city" class="input" />
          </label>
          <label class="field">
            <span>房间数</span>
            <input v-model.number="form.totalRooms" class="input" min="0" type="number" />
          </label>
          <label class="field">
            <span>套餐</span>
            <select v-model="form.tier" class="input">
              <option value="trial">试用版</option>
              <option value="basic">基础版</option>
              <option value="pro">专业版</option>
              <option value="flagship">旗舰版</option>
            </select>
          </label>
          <label class="field">
            <span>状态</span>
            <select v-model="form.status" class="input">
              <option value="active">正常</option>
              <option value="warning">预警</option>
              <option value="suspended">暂停</option>
              <option value="closed">关闭</option>
            </select>
          </label>
          <label class="field">
            <span>算力余额</span>
            <input v-model.number="form.balance" class="input" min="0" type="number" />
          </label>
          <label class="field">
            <span>特色标签</span>
            <input v-model="form.tags" class="input" placeholder="亲子, 温泉, 山景" />
          </label>
        </div>

        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="btn-secondary" @click="showModal = false">取消</button>
          <button type="submit" class="btn-primary" :disabled="formLoading">
            <Loader2 v-if="formLoading" class="h-4 w-4 animate-spin" />
            <Check v-else class="h-4 w-4" />
            保存
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  border-radius: 0.5rem;
  padding: 0.625rem 0.875rem;
  font-size: 0.875rem;
}
.btn-primary {
  background: #4f46e5;
  color: white;
}
.btn-primary:hover {
  background: #6366f1;
}
.btn-primary:disabled {
  opacity: 0.6;
}
.btn-secondary {
  border: 1px solid #374151;
  color: #d1d5db;
}
.btn-secondary:hover {
  background: #1f2937;
}
.input {
  width: 100%;
  border-radius: 0.5rem;
  border: 1px solid #374151;
  background: #111827;
  padding: 0.625rem 0.75rem;
  color: #f3f4f6;
  outline: none;
}
.input:focus {
  border-color: #6366f1;
}
.field {
  display: grid;
  gap: 0.375rem;
  font-size: 0.875rem;
  color: #9ca3af;
}
.metric {
  border-radius: 0.5rem;
  border: 1px solid #1f2937;
  background: #111827;
  padding: 1rem;
}
.metric span {
  display: block;
  font-size: 0.75rem;
  color: #6b7280;
}
.metric strong {
  margin-top: 0.375rem;
  display: block;
  font-size: 1.5rem;
  color: #f3f4f6;
}
</style>
