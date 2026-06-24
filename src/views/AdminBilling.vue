<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import api from '@/api'
import { Bolt, RefreshCw, Loader2, Plus, X, Check } from 'lucide-vue-next'

const rules = ref<any[]>([])
const loading = ref(true)
const toast = ref('')

async function load() {
  loading.value = true
  try { const { data } = await api.get('/api/admin/billing-rules'); rules.value = data.data || [] } catch { /* */ }
  loading.value = false
}
onMounted(load)

const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ module_key: '', module_name: '', board: '内容发布', cost: 0 })
const formLoading = ref(false)

function openNew() { editingId.value = null; form.module_key = ''; form.module_name = ''; form.board = '内容发布'; form.cost = 0; showModal.value = true }
function openEdit(r: any) { editingId.value = r.id; form.module_key = r.module_key || ''; form.module_name = r.module_name || ''; form.board = r.board || ''; form.cost = r.cost || 0; showModal.value = true }

async function save(e: Event) {
  e.preventDefault()
  if (!form.module_name.trim()) { flash('请输入模块名称'); return }
  formLoading.value = true
  try {
    if (editingId.value) {
      await api.put(`/api/admin/billing-rules/${editingId.value}`, { ...form })
      flash('修改成功')
    } else {
      await api.post('/api/admin/billing-rules', { ...form })
      flash('新增成功')
    }
    showModal.value = false
    await load()
  } catch { flash('操作失败') }
  formLoading.value = false
}
async function del(id: number) { if (!confirm('确定删除？')) return; try { await api.delete(`/api/admin/billing-rules/${id}`); flash('已删除'); await load() } catch { flash('删除失败') } }
function toggleEnable(r: any) { r.enabled = !r.enabled; api.put(`/api/admin/billing-rules/${r.id}`, { enabled: r.enabled ? 1 : 0 }).catch(() => { r.enabled = !r.enabled }) }
function flash(msg: string) { toast.value = msg; setTimeout(() => toast.value = '', 2000) }
</script>

<template>
  <div class="p-5 space-y-4">
    <div v-if="toast" class="fixed top-4 right-4 z-50 bg-indigo-600 text-white px-4 py-2 rounded text-xs shadow-lg">{{ toast }}</div>

    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Bolt class="w-4 h-4 text-amber-400" />模块计费规则</div>
      <div class="flex items-center gap-2">
        <button @click="load" class="flex items-center gap-1 text-[10px] text-gray-500 hover:text-gray-300"><RefreshCw class="w-3 h-3" />刷新</button>
        <button @click="openNew" class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500 flex items-center gap-1"><Plus class="w-3 h-3" />新增规则</button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20"><Loader2 class="w-5 h-5 animate-spin text-gray-600" /></div>

    <div v-else class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead>
          <tr class="border-b border-gray-800 text-gray-500 text-left">
            <th class="py-2.5 px-4 font-medium">模块名称</th><th class="py-2.5 px-4 font-medium">所属看板</th><th class="py-2.5 px-4 font-medium">算力消耗</th><th class="py-2.5 px-4 font-medium">状态</th><th class="py-2.5 px-4 font-medium"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rules" :key="r.id" class="border-b border-gray-800/50 hover:bg-gray-800/30">
            <td class="py-2.5 px-4 text-gray-200 font-medium">{{ r.module_name }}</td>
            <td class="py-2.5 px-4 text-gray-500">{{ r.board }}</td>
            <td class="py-2.5 px-4 text-gray-300 font-mono">{{ r.cost }} 算力</td>
            <td class="py-2.5 px-4"><button @click="toggleEnable(r)" :class="r.enabled ? 'text-emerald-400' : 'text-gray-600'">{{ r.enabled ? '启用' : '禁用' }}</button></td>
            <td class="py-2.5 px-4 flex gap-2">
              <button @click="openEdit(r)" class="text-[10px] text-indigo-400 hover:text-indigo-300">编辑</button>
              <button @click="del(r.id)" class="text-[10px] text-red-400 hover:text-red-300">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="showModal = false">
      <div class="bg-gray-900 border border-gray-700 rounded-xl p-6 w-full max-w-sm">
        <div class="flex items-center justify-between mb-4">
          <div class="text-sm font-medium text-gray-200">{{ editingId ? '编辑计费规则' : '新增计费规则' }}</div>
          <button @click="showModal = false" class="text-gray-500 hover:text-gray-300"><X class="w-4 h-4" /></button>
        </div>
        <form @submit="save" class="space-y-3">
          <div><label class="text-[10px] text-gray-500 mb-1 block">模块Key</label><input v-model="form.module_key" placeholder="如: poster" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">模块名称 *</label><input v-model="form.module_name" required placeholder="如: 营销海报生成" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">所属看板</label><select v-model="form.board" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200"><option>店长看板</option><option>内容发布</option><option>前台客服</option></select></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">算力消耗</label><input v-model.number="form.cost" type="number" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div class="flex gap-2 pt-2">
            <button type="submit" :disabled="formLoading" class="flex-1 px-4 py-2 rounded bg-indigo-600 text-white text-xs hover:bg-indigo-500 disabled:opacity-50 flex items-center justify-center gap-1"><Loader2 v-if="formLoading" class="w-3 h-3 animate-spin" />{{ editingId ? '保存' : '创建规则' }}</button>
            <button type="button" @click="showModal = false" class="px-4 py-2 rounded border border-gray-700 text-gray-400 text-xs hover:bg-gray-800">取消</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
