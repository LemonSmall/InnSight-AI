<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Cpu, Plus, X, Check, Loader2 } from 'lucide-vue-next'

const models = ref([
  { id: 1, type: '文本生成', provider: 'Anthropic', model: 'claude-sonnet-4-6', status: true, key: 'sk-ant-***' },
  { id: 2, type: '文本生成', provider: 'DeepSeek', model: 'deepseek-chat', status: false, key: '' },
  { id: 3, type: '图像生成', provider: 'OpenAI', model: 'dall-e-3', status: true, key: 'sk-***' },
  { id: 4, type: '图像理解', provider: 'Anthropic', model: 'claude-haiku-4-5', status: true, key: 'sk-ant-***' },
])

function toggleModel(m: any) { m.status = !m.status }

const toast = ref('')
const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ type: '文本生成', provider: '', model: '', key: '' })
const formLoading = ref(false)

function openNew() { editingId.value = null; form.type = '文本生成'; form.provider = ''; form.model = ''; form.key = ''; showModal.value = true }
function openEdit(m: any) { editingId.value = m.id; form.type = m.type; form.provider = m.provider; form.model = m.model; form.key = ''; showModal.value = true }

function save(e: Event) {
  e.preventDefault()
  if (!form.provider.trim() || !form.model.trim()) { flash('请填写完整信息'); return }
  formLoading.value = true
  setTimeout(() => {
    if (editingId.value) {
      const m = models.value.find(m => m.id === editingId.value)
      if (m) { Object.assign(m, { type: form.type, provider: form.provider, model: form.model, key: form.key ? form.key.slice(0, 6) + '***' : '' }) }
      flash('修改成功')
    } else {
      models.value.push({ id: Date.now(), type: form.type, provider: form.provider, model: form.model, status: true, key: form.key ? form.key.slice(0, 6) + '***' : '' })
      flash('新增成功')
    }
    showModal.value = false
    formLoading.value = false
  }, 400)
}
function del(id: number) { if (!confirm('确定删除？')) return; models.value = models.value.filter(m => m.id !== id); flash('已删除') }
function flash(msg: string) { toast.value = msg; setTimeout(() => toast.value = '', 2000) }
</script>

<template>
  <div class="p-5 space-y-4">
    <div v-if="toast" class="fixed top-4 right-4 z-50 bg-indigo-600 text-white px-4 py-2 rounded text-xs shadow-lg">{{ toast }}</div>

    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Cpu class="w-4 h-4 text-indigo-400" />模型与渠道配置</div>
      <button @click="openNew" class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500 flex items-center gap-1"><Plus class="w-3 h-3" />新增配置</button>
    </div>

    <div class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead><tr class="border-b border-gray-800 text-gray-500 text-left"><th class="py-2.5 px-4">能力类型</th><th class="py-2.5 px-4">供应商</th><th class="py-2.5 px-4">模型</th><th class="py-2.5 px-4">API Key</th><th class="py-2.5 px-4">状态</th><th class="py-2.5 px-4"></th></tr></thead>
        <tbody>
          <tr v-for="m in models" :key="m.id" class="border-b border-gray-800/50 hover:bg-gray-800/30">
            <td class="py-2.5 px-4 text-gray-200">{{ m.type }}</td><td class="py-2.5 px-4 text-gray-400">{{ m.provider }}</td><td class="py-2.5 px-4 text-gray-300 font-mono">{{ m.model }}</td><td class="py-2.5 px-4 text-gray-600 font-mono text-[11px]">{{ m.key || '未配置' }}</td>
            <td class="py-2.5 px-4"><button @click="toggleModel(m)" :class="m.status ? 'text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded text-[10px]' : 'text-gray-600 bg-gray-800 px-1.5 py-0.5 rounded text-[10px]'">{{ m.status ? '启用' : '禁用' }}</button></td>
            <td class="py-2.5 px-4 flex gap-2">
              <button @click="openEdit(m)" class="text-[10px] text-indigo-400 hover:text-indigo-300">编辑</button>
              <button @click="del(m.id)" class="text-[10px] text-red-400 hover:text-red-300">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="showModal = false">
      <div class="bg-gray-900 border border-gray-700 rounded-xl p-6 w-full max-w-sm">
        <div class="flex items-center justify-between mb-4"><div class="text-sm font-medium text-gray-200">{{ editingId ? '编辑模型配置' : '新增模型配置' }}</div><button @click="showModal = false" class="text-gray-500 hover:text-gray-300"><X class="w-4 h-4" /></button></div>
        <form @submit="save" class="space-y-3">
          <div><label class="text-[10px] text-gray-500 mb-1 block">能力类型</label><select v-model="form.type" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200"><option>文本生成</option><option>图像生成</option><option>图像理解</option><option>视频生成</option></select></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">供应商 *</label><input v-model="form.provider" required placeholder="如: OpenAI" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">模型名称 *</label><input v-model="form.model" required placeholder="如: gpt-4o" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">API Key</label><input v-model="form.key" type="password" placeholder="sk-..." class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500 font-mono"></div>
          <div class="flex gap-2 pt-2">
            <button type="submit" :disabled="formLoading" class="flex-1 px-4 py-2 rounded bg-indigo-600 text-white text-xs hover:bg-indigo-500 flex items-center justify-center gap-1"><Loader2 v-if="formLoading" class="w-3 h-3 animate-spin" />{{ editingId ? '保存' : '新增' }}</button>
            <button type="button" @click="showModal = false" class="px-4 py-2 rounded border border-gray-700 text-gray-400 text-xs hover:bg-gray-800">取消</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
