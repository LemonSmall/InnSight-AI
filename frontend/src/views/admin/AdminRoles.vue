<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Users, Plus, X, Check, Loader2 } from 'lucide-vue-next'

const admins = ref([
  { id: 1, name: '超级管理员', phone: '138****0000', role: '超级管理员', lastLogin: '06-09 14:20', status: 'active' },
  { id: 2, name: '运维管理员', phone: '139****1234', role: '运维管理', lastLogin: '06-09 09:00', status: 'active' },
  { id: 3, name: '内容审核员', phone: '136****5678', role: '内容审核', lastLogin: '06-08 16:30', status: 'inactive' },
])

const toast = ref('')
const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', phone: '', role: '运维管理' })
const formLoading = ref(false)

function openNew() { editingId.value = null; form.name = ''; form.phone = ''; form.role = '运维管理'; showModal.value = true }
function openEdit(a: any) { editingId.value = a.id; form.name = a.name; form.phone = a.phone; form.role = a.role; showModal.value = true }

function save(e: Event) {
  e.preventDefault()
  if (!form.name.trim() || !form.phone.trim()) { flash('请填写完整信息'); return }
  formLoading.value = true
  setTimeout(() => {
    if (editingId.value) {
      const a = admins.value.find(a => a.id === editingId.value)
      if (a) { a.name = form.name; a.phone = form.phone; a.role = form.role }
      flash('修改成功')
    } else {
      admins.value.push({ id: Date.now(), name: form.name, phone: form.phone, role: form.role, lastLogin: '-', status: 'inactive' })
      flash('新增成功')
    }
    showModal.value = false
    formLoading.value = false
  }, 400)
}
function del(id: number) { if (!confirm('确定删除？')) return; admins.value = admins.value.filter(a => a.id !== id); flash('已删除') }
function flash(msg: string) { toast.value = msg; setTimeout(() => toast.value = '', 2000) }
</script>

<template>
  <div class="p-5 space-y-4">
    <div v-if="toast" class="fixed top-4 right-4 z-50 bg-indigo-600 text-white px-4 py-2 rounded text-xs shadow-lg">{{ toast }}</div>

    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Users class="w-4 h-4 text-indigo-400" />管理员与权限</div>
      <button @click="openNew" class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500 flex items-center gap-1"><Plus class="w-3 h-3" />添加管理员</button>
    </div>

    <div class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead><tr class="border-b border-gray-800 text-gray-500 text-left"><th class="py-2.5 px-4">姓名</th><th class="py-2.5 px-4">手机号</th><th class="py-2.5 px-4">角色</th><th class="py-2.5 px-4">最后登录</th><th class="py-2.5 px-4">状态</th><th class="py-2.5 px-4"></th></tr></thead>
        <tbody>
          <tr v-for="a in admins" :key="a.id" class="border-b border-gray-800/50 hover:bg-gray-800/30">
            <td class="py-2.5 px-4 text-gray-200 font-medium">{{ a.name }}</td><td class="py-2.5 px-4 text-gray-500 font-mono">{{ a.phone }}</td><td class="py-2.5 px-4 text-indigo-400">{{ a.role }}</td><td class="py-2.5 px-4 text-gray-500">{{ a.lastLogin }}</td>
            <td class="py-2.5 px-4"><span :class="a.status === 'active' ? 'text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded text-[10px]' : 'text-gray-600 bg-gray-800 px-1.5 py-0.5 rounded text-[10px]'">{{ a.status === 'active' ? '活跃' : '未活跃' }}</span></td>
            <td class="py-2.5 px-4 flex gap-2">
              <button @click="openEdit(a)" class="text-[10px] text-indigo-400 hover:text-indigo-300">编辑</button>
              <button @click="del(a.id)" class="text-[10px] text-red-400 hover:text-red-300">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" @click.self="showModal = false">
      <div class="bg-gray-900 border border-gray-700 rounded-xl p-6 w-full max-w-sm">
        <div class="flex items-center justify-between mb-4"><div class="text-sm font-medium text-gray-200">{{ editingId ? '编辑管理员' : '添加管理员' }}</div><button @click="showModal = false" class="text-gray-500 hover:text-gray-300"><X class="w-4 h-4" /></button></div>
        <form @submit="save" class="space-y-3">
          <div><label class="text-[10px] text-gray-500 mb-1 block">姓名 *</label><input v-model="form.name" required class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">手机号 *</label><input v-model="form.phone" required class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500"></div>
          <div><label class="text-[10px] text-gray-500 mb-1 block">角色</label><select v-model="form.role" class="w-full text-xs px-3 py-2 rounded bg-gray-800 border border-gray-700 text-gray-200"><option>超级管理员</option><option>运维管理</option><option>内容审核</option></select></div>
          <div class="flex gap-2 pt-2">
            <button type="submit" :disabled="formLoading" class="flex-1 px-4 py-2 rounded bg-indigo-600 text-white text-xs hover:bg-indigo-500 flex items-center justify-center gap-1">{{ editingId ? '保存' : '添加' }}</button>
            <button type="button" @click="showModal = false" class="px-4 py-2 rounded border border-gray-700 text-gray-400 text-xs hover:bg-gray-800">取消</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
