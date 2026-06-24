<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import api from '@/api'
import { Bot, Key, Save, Loader2 } from 'lucide-vue-next'

const toast = ref('')
const loading = ref(false)
const saving = ref(false)

const form = reactive({
  ai_provider: 'openai',
  ai_api_key: '',
  ai_model: 'gpt-4o',
  ai_base_url: '',
  ai_max_tokens: '4000',
})

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await api.get('/api/admin/settings')
    const map = data.data?.map || data.data || {}
    form.ai_provider = map.ai_provider || 'openai'
    form.ai_api_key = map.ai_api_key || ''
    form.ai_model = map.ai_model || 'gpt-4o'
    form.ai_base_url = map.ai_base_url || ''
    form.ai_max_tokens = map.ai_max_tokens || '4000'
  } catch { /* */ }
  loading.value = false
})

async function save() {
  saving.value = true
  try {
    await api.put('/api/admin/settings', { ...form })
    toast.value = 'AI配置已保存'
    setTimeout(() => { toast.value = '' }, 2000)
  } catch { toast.value = '保存失败'; setTimeout(() => { toast.value = '' }, 2000) }
  saving.value = false
}
</script>

<template>
  <div class="p-6 space-y-6">
    <div v-if="toast" class="fixed top-4 right-4 z-50 bg-indigo-600 text-white px-4 py-2.5 rounded-lg shadow-lg text-xs">{{ toast }}</div>

    <h1 class="text-sm font-semibold text-gray-200 flex items-center gap-2"><Bot class="w-4 h-4 text-indigo-400" />AI 大模型配置</h1>

    <div v-if="loading" class="flex items-center justify-center py-20"><Loader2 class="w-6 h-6 animate-spin text-gray-600" /></div>

    <div v-else class="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-5 max-w-2xl">
      <div class="grid grid-cols-2 gap-5">
        <div>
          <label class="text-xs text-gray-400 mb-1.5 block">AI服务商</label>
          <select v-model="form.ai_provider" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500">
            <option value="openai">OpenAI</option>
            <option value="deepseek">DeepSeek</option>
            <option value="zhipu">智谱AI (GLM)</option>
            <option value="moonshot">Moonshot (月之暗面)</option>
            <option value="qwen">通义千问</option>
          </select>
        </div>
        <div>
          <label class="text-xs text-gray-400 mb-1.5 block">模型名称</label>
          <input v-model="form.ai_model" placeholder="gpt-4o" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
        </div>
        <div>
          <label class="text-xs text-gray-400 mb-1.5 block">最大Token数</label>
          <input v-model="form.ai_max_tokens" type="number" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
        </div>
        <div>
          <label class="text-xs text-gray-400 mb-1.5 block">自定义 Base URL</label>
          <input v-model="form.ai_base_url" placeholder="https://api.openai.com/v1" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
        </div>
      </div>

      <div>
        <label class="text-xs text-gray-400 mb-1.5 block flex items-center gap-1.5"><Key class="w-3.5 h-3.5" />API Key</label>
        <input v-model="form.ai_api_key" type="password" placeholder="sk-..." class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500 font-mono" />
      </div>

      <button @click="save" :disabled="saving" class="px-5 py-2 rounded-lg bg-indigo-600 text-white text-xs font-medium hover:bg-indigo-500 disabled:bg-gray-700 disabled:text-gray-500 transition-colors flex items-center gap-2">
        <Loader2 v-if="saving" class="w-3.5 h-3.5 animate-spin" />
        <Save v-else class="w-3.5 h-3.5" />
        {{ saving ? '保存中...' : '保存配置' }}
      </button>
    </div>
  </div>
</template>
