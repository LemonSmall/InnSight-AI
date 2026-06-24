<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import api from '@/api'
import { Wallet, RefreshCw, Loader2, Plus, X, Check } from 'lucide-vue-next'

const packages = ref<any[]>([])
const loading = ref(true)
const toast = ref('')

async function load() {
  loading.value = true
  try { const { data } = await api.get('/api/admin/packages'); packages.value = data.data || [] } catch { /* */ }
  loading.value = false
}
onMounted(load)

const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', credits: 0, price_rmb: 0, applicable_tiers: 'basic,pro' })
const formLoading = ref(false)

function openNew() { editingId.value = null; form.name = ''; form.credits = 0; form.price_rmb = 0; form.applicable_tiers = 'basic,pro'; showModal.value = true }
function openEdit(p: any) { editingId.value = p.id; form.name = p.name || ''; form.credits = p.credits || 0; form.price_rmb = p.price_rmb || 0; form.applicable_tiers = p.applicable_tiers || ''; showModal.value = true }

async function save(e: Event) {
  e.preventDefault()
  if (!form.name.trim()) { flash('请输入套餐名称'); return }
  formLoading.value = true
  try {
    if (editingId.value) {
      await api.put(`/api/admin/packages/${editingId.value}`, { ...form })
      flash('修改成功')
    } else {
      await api.post('/api/admin/packages', { ...form })
      flash('新增成功')
    }
    showModal.value = false
    await load()
  } catch { flash('操作失败') }
  formLoading.value = false
}
async function del(id: number) { if (!confirm('确定删除？')) return; try { await api.delete(`/api/admin/packages/${id}`); flash('已删除'); await load() } catch { flash('删除失败') } }
function flash(msg: string) { toast.value = msg; setTimeout(() => toast.value = '', 2000) }

const tierLabels: Record<string, string> = { trial: '试用版', basic: '基础版', pro: '专业版', flagship: '旗舰版' }
</script>

<template>
  <div class="p-5 space-y-4">
    <div v-if="toast" class="fixed top-4 right-4 z-50 bg-indigo-600 text-white px-4 py-2 rounded text-xs shadow-lg">{{ toast }}</div>

    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Wallet class="w-4 h-4 text-emerald-400" />充值套餐与权限</div>
      <div class="flex items-center gap-2">
        <button @click="load" class="flex items-center gap-1 text-[10px] text-gray-500 hover:text-gray-300"><RefreshCw class="w-3 h-3" />刷新</button>
        <button @click="openNew" class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500 flex items-center gap-1"><Plus class="w-3 h-3" />新增套餐</button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20"><Loader2 class="w-5 h-5 animate-spin text-gray-600" /></div>

    <template v-else>
      <div class="grid grid-cols-4 gap-3">
        <div v-for="p in packages" :key="p.id" class="bg-gray-900 border border-gray-800 rounded-lg p-4 text-center hover:border-indigo-500/30 transition-colors relative group">
          <div class="text-xs font-medium text-gray-300 mb-2">{{ p.name }}</div>
          <div class="text-2xl font-bold text-white mb-0.5">{{ p.credits }}</div>
          <div class="text-[10px] text-gray-500 mb-3">算力</div>
          <div class="text-lg font-semibold text-indigo-400 mb-2">¥{{ p.price_rmb }}</div>
          <span class="text-[10px] px-2 py-0.5 rounded-full bg-gray-800 text-gray-400">{{ p.applicable_tiers || '全部' }}</span>
          <div class="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity flex gap-1">
            <button @click="openEdit(p)" class="text-[9px] text-indigo-400 hover:text-indigo-300 bg-gray-800 px-1.5 py-0.5 rounded">编辑</button>
            <button @click="del(p.id)" class="text-[9px] text-red-400 hover:text-red-300 bg-gray-800 px-1.5 py-0.5 rounded">删除</button>
          </div>
        </div>
      </div>
    </template>

    <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="showModal = false">
      <div class="bg-gray-900 border border-gray-700 rounded-xl p-6 w-full max-w-sm">
        <div class="flex items-center justify-between mb-4">
          <div class="text-sm font-medium text-gray-200">{{ editingId ? '编辑套餐' : '新增充值套餐' }}</div>
          <button @click="showModal = false" class="text-gray-500 hover:text-gray-300"><X class="w-4 h-4" /></button>
        </div>
        <form @submit="save" class="space-y-3">
          <div><label class="text-[10px] text-gray-500 mb-1 block">套餐名称 *</label><input v-model="form.name" required class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div class="grid grid-cols-2 gap-3">
            <div><label class="text-[10px] text-gray-500 mb-1 block">算力数量</label><input v-model.number="form.credits" type="number" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
            <div><label class="text-[10px] text-gray-500 mb-1 block">价格(元)</label><input v-model.number="form.price_rmb" type="number" step="0.01" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          </div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">适用等级</label><select v-model="form.applicable_tiers" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200"><option value="trial">试用版</option><option value="basic,pro">基础版+专业版</option><option value="pro,flagship">专业版+旗舰版</option><option value="basic,pro,flagship">全等级</option></select></div>
          <div class="flex gap-2 pt-2">
            <button type="submit" :disabled="formLoading" class="flex-1 px-4 py-2 rounded bg-indigo-600 text-white text-xs hover:bg-indigo-500 disabled:opacity-50 flex items-center justify-center gap-1">{{ editingId ? '保存' : '创建套餐' }}</button>
            <button type="button" @click="showModal = false" class="px-4 py-2 rounded border border-gray-700 text-gray-400 text-xs hover:bg-gray-800">取消</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
