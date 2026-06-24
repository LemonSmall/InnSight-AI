<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import api from '@/api'
import { Building, RefreshCw, Loader2, Plus, X, Check } from 'lucide-vue-next'

const tenants = ref<any[]>([])
const loading = ref(true)
const toast = ref('')

async function load() {
  loading.value = true
  try { const { data } = await api.get('/api/admin/tenants'); tenants.value = data.data || [] } catch { /* */ }
  loading.value = false
}
onMounted(load)

const tierLabels: Record<string, string> = { trial: '试用版', basic: '基础版', pro: '专业版', flagship: '旗舰版' }
const tierBadge: Record<string, string> = { trial: 'bg-gray-700 text-gray-400', basic: 'bg-blue-500/10 text-blue-400', pro: 'bg-indigo-500/10 text-indigo-400', flagship: 'bg-purple-500/10 text-purple-400' }

// ====== 新增/编辑弹窗 ======
const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', type: '精品民宿', city: '', totalRooms: 0, tags: '', tier: 'trial', balance: 0 })
const formLoading = ref(false)

function openNew() {
  editingId.value = null
  Object.assign(form, { name: '', type: '精品民宿', city: '', totalRooms: 0, tags: '', tier: 'trial', balance: 0 })
  showModal.value = true
}
function openEdit(t: any) {
  editingId.value = t.id
  Object.assign(form, {
    name: t.name || '', type: t.type || '精品民宿', city: t.city || '',
    totalRooms: t.total_rooms || 0, tags: t.tags || '', tier: t.tier || 'trial', balance: t.balance || 0
  })
  showModal.value = true
}
async function save(e: Event) {
  e.preventDefault()
  if (!form.name.trim()) { flash('请输入酒店名称'); return }
  formLoading.value = true
  try {
    if (editingId.value) {
      await api.put(`/api/admin/tenants/${editingId.value}`, { name: form.name, type: form.type, city: form.city, total_rooms: form.totalRooms, tags: form.tags, tier: form.tier, balance: form.balance })
      flash('修改成功')
    } else {
      await api.post('/api/admin/tenants', { name: form.name, type: form.type, city: form.city, totalRooms: form.totalRooms, tags: form.tags, tier: form.tier, balance: form.balance })
      flash('新增成功')
    }
    showModal.value = false
    await load()
  } catch { flash('操作失败') }
  formLoading.value = false
}
async function del(id: number) {
  if (!confirm('确定删除该租户？')) return
  try { await api.delete(`/api/admin/tenants/${id}`); flash('已删除'); await load() } catch { flash('删除失败') }
}
function flash(msg: string) { toast.value = msg; setTimeout(() => toast.value = '', 2000) }
</script>

<template>
  <div class="p-5 space-y-4">
    <div v-if="toast" class="fixed top-4 right-4 z-50 bg-indigo-600 text-white px-4 py-2 rounded text-xs shadow-lg">{{ toast }}</div>

    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Building class="w-4 h-4 text-indigo-400" />酒店账户列表</div>
      <div class="flex items-center gap-2">
        <button @click="load" class="flex items-center gap-1 text-[10px] text-gray-500 hover:text-gray-300"><RefreshCw class="w-3 h-3" />刷新</button>
        <button @click="openNew" class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500 flex items-center gap-1"><Plus class="w-3 h-3" />新增租户</button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20"><Loader2 class="w-5 h-5 animate-spin text-gray-600" /></div>

    <div v-else class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead>
          <tr class="border-b border-gray-800 text-gray-500 text-left">
            <th class="py-2.5 px-4 font-medium">酒店名称</th><th class="py-2.5 px-4 font-medium">套餐</th><th class="py-2.5 px-4 font-medium">算力余额</th><th class="py-2.5 px-4 font-medium">状态</th><th class="py-2.5 px-4 font-medium"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tenants" :key="t.id" class="border-b border-gray-800/50 hover:bg-gray-800/30">
            <td class="py-2.5 px-4"><div class="font-medium text-gray-200">{{ t.name }}</div><div class="text-[10px] text-gray-600">{{ t.city }}</div></td>
            <td class="py-2.5 px-4"><span :class="['text-[10px] px-2 py-0.5 rounded-full', tierBadge[t.tier] || 'bg-gray-700 text-gray-400']">{{ tierLabels[t.tier] || t.tier }}</span></td>
            <td class="py-2.5 px-4"><span :class="t.balance < 500 ? 'text-red-400 font-medium' : 'text-gray-300'">{{ t.balance }}</span></td>
            <td class="py-2.5 px-4"><span :class="t.status === 'active' ? 'text-emerald-400' : 'text-amber-400'">{{ t.status === 'active' ? '正常' : '预警' }}</span></td>
            <td class="py-2.5 px-4 flex gap-2">
              <button @click="openEdit(t)" class="text-[10px] text-indigo-400 hover:text-indigo-300">编辑</button>
              <button @click="del(t.id)" class="text-[10px] text-red-400 hover:text-red-300">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Modal -->
    <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="showModal = false">
      <div class="bg-gray-900 border border-gray-700 rounded-xl p-6 w-full max-w-md">
        <div class="flex items-center justify-between mb-4">
          <div class="text-sm font-medium text-gray-200">{{ editingId ? '编辑租户' : '新增租户' }}</div>
          <button @click="showModal = false" class="text-gray-500 hover:text-gray-300"><X class="w-4 h-4" /></button>
        </div>
        <form @submit="save" class="space-y-3">
          <div><label class="text-[10px] text-gray-500 mb-1 block">酒店名称 *</label><input v-model="form.name" required class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div class="grid grid-cols-2 gap-3">
            <div><label class="text-[10px] text-gray-500 mb-1 block">类型</label><select v-model="form.type" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200"><option>精品民宿</option><option>度假酒店</option><option>商务酒店</option><option>亲子民宿</option></select></div>
            <div><label class="text-[10px] text-gray-500 mb-1 block">城市</label><input v-model="form.city" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div><label class="text-[10px] text-gray-500 mb-1 block">房间数</label><input v-model.number="form.totalRooms" type="number" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
            <div><label class="text-[10px] text-gray-500 mb-1 block">套餐</label><select v-model="form.tier" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200"><option value="trial">试用版</option><option value="basic">基础版</option><option value="pro">专业版</option><option value="flagship">旗舰版</option></select></div>
          </div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">特色标签</label><input v-model="form.tags" placeholder="用逗号分隔" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">初始算力</label><input v-model.number="form.balance" type="number" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div class="flex gap-2 pt-2">
            <button type="submit" :disabled="formLoading" class="flex-1 px-4 py-2 rounded bg-indigo-600 text-white text-xs hover:bg-indigo-500 disabled:opacity-50 flex items-center justify-center gap-1"><Loader2 v-if="formLoading" class="w-3 h-3 animate-spin" /><Check v-else class="w-3 h-3" />{{ editingId ? '保存修改' : '创建并开通' }}</button>
            <button type="button" @click="showModal = false" class="px-4 py-2 rounded border border-gray-700 text-gray-400 text-xs hover:bg-gray-800">取消</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
