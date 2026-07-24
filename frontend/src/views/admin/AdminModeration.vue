<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ShieldCheck, Plus, X, Check, ToggleLeft, ToggleRight, Loader2 } from 'lucide-vue-next'

const rules = ref([
  { id: 1, name: '违反广告法用词', keywords: '最低价、全网最低、绝无仅有、史上最低', action: '拦截', hits: 432, enabled: true },
  { id: 2, name: '价格表述规范', keywords: '免费赠送、限时免费', action: '标记复审', hits: 126, enabled: true },
  { id: 3, name: '地域歧视性表述', keywords: '', action: '拦截', hits: 3, enabled: true },
])

function toggle(id: number) { const r = rules.value.find(r => r.id === id); if (r) r.enabled = !r.enabled }

const toast = ref('')
const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', keywords: '', action: '拦截', type: '关键词过滤' })
const formLoading = ref(false)

function openNew() { editingId.value = null; form.name = ''; form.keywords = ''; form.action = '拦截'; form.type = '关键词过滤'; showModal.value = true }
function openEdit(r: any) { editingId.value = r.id; form.name = r.name; form.keywords = r.keywords; form.action = r.action; form.type = '关键词过滤'; showModal.value = true }

function save(e: Event) {
  e.preventDefault()
  if (!form.name.trim()) { flash('请输入规则名称'); return }
  formLoading.value = true
  setTimeout(() => {
    if (editingId.value) {
      const r = rules.value.find(r => r.id === editingId.value)
      if (r) { r.name = form.name; r.keywords = form.keywords; r.action = form.action }
      flash('修改成功')
    } else {
      rules.value.push({ id: Date.now(), name: form.name, keywords: form.keywords, action: form.action, hits: 0, enabled: true })
      flash('新增成功')
    }
    showModal.value = false
    formLoading.value = false
  }, 400)
}
function del(id: number) { if (!confirm('确定删除？')) return; rules.value = rules.value.filter(r => r.id !== id); flash('已删除') }
function flash(msg: string) { toast.value = msg; setTimeout(() => toast.value = '', 2000) }
</script>

<template>
  <div class="p-5 space-y-4">
    <div v-if="toast" class="fixed top-4 right-4 z-50 bg-indigo-600 text-white px-4 py-2 rounded text-xs shadow-lg">{{ toast }}</div>

    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><ShieldCheck class="w-4 h-4 text-blue-400" />内容合规审查</div>
      <button @click="openNew" class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500 flex items-center gap-1"><Plus class="w-3 h-3" />新增规则</button>
    </div>

    <div class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead><tr class="border-b border-gray-800 text-gray-500 text-left"><th class="py-2.5 px-4">规则名称</th><th class="py-2.5 px-4">关键词</th><th class="py-2.5 px-4">动作</th><th class="py-2.5 px-4">30日命中</th><th class="py-2.5 px-4">状态</th><th class="py-2.5 px-4"></th></tr></thead>
        <tbody>
          <tr v-for="r in rules" :key="r.id" class="border-b border-gray-800/50 hover:bg-gray-800/30">
            <td class="py-2.5 px-4 text-gray-200 font-medium">{{ r.name }}</td><td class="py-2.5 px-4 text-gray-500 text-[11px]">{{ r.keywords || '正则匹配' }}</td>
            <td class="py-2.5 px-4"><span :class="r.action === '拦截' ? 'text-red-400 bg-red-500/10 px-1.5 py-0.5 rounded text-[10px]' : 'text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded text-[10px]'">{{ r.action }}</span></td>
            <td class="py-2.5 px-4 text-gray-400">{{ r.hits }}</td>
            <td class="py-2.5 px-4"><button @click="toggle(r.id)"><ToggleRight v-if="r.enabled" class="w-5 h-5 text-emerald-500" /><ToggleLeft v-else class="w-5 h-5 text-gray-600" /></button></td>
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
        <div class="flex items-center justify-between mb-4"><div class="text-sm font-medium text-gray-200">{{ editingId ? '编辑合规规则' : '新增合规规则' }}</div><button @click="showModal = false" class="text-gray-500 hover:text-gray-300"><X class="w-4 h-4" /></button></div>
        <form @submit="save" class="space-y-3">
          <div><label class="text-[10px] text-gray-500 mb-1 block">规则名称 *</label><input v-model="form.name" required class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">规则类型</label><select v-model="form.type" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200"><option>关键词过滤</option><option>敏感词库</option><option>正则匹配</option></select></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">关键词/规则（每行一个）</label><textarea v-model="form.keywords" rows="3" class="w-full text-xs p-3 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></textarea></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">触发动作</label><select v-model="form.action" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200"><option>拦截并提示重新生成</option><option>标记待人工复核</option><option>自动替换为通用表述</option></select></div>
          <div class="flex gap-2 pt-2">
            <button type="submit" :disabled="formLoading" class="flex-1 px-4 py-2 rounded bg-indigo-600 text-white text-xs hover:bg-indigo-500 flex items-center justify-center gap-1">{{ editingId ? '保存' : '创建规则' }}</button>
            <button type="button" @click="showModal = false" class="px-4 py-2 rounded border border-gray-700 text-gray-400 text-xs hover:bg-gray-800">取消</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
