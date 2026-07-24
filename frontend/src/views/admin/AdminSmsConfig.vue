<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import api from '@/api'
import { MessageSquare, Key, Save, Loader2 } from 'lucide-vue-next'

const toast = ref('')
const loading = ref(false)
const saving = ref(false)

const form = reactive({
  sms_provider: '',
  sms_access_key: '',
  sms_secret_key: '',
  sms_sign_name: '',
  sms_template_code: '',
})

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await api.get('/api/admin/settings')
    const map = data.data?.map || data.data || {}
    form.sms_provider = map.sms_provider || ''
    form.sms_access_key = map.sms_access_key || ''
    form.sms_secret_key = map.sms_secret_key || ''
    form.sms_sign_name = map.sms_sign_name || ''
    form.sms_template_code = map.sms_template_code || ''
  } catch { /* */ }
  loading.value = false
})

async function save() {
  saving.value = true
  try {
    await api.put('/api/admin/settings', { ...form })
    toast.value = '短信配置已保存'
    setTimeout(() => { toast.value = '' }, 2000)
  } catch { toast.value = '保存失败'; setTimeout(() => { toast.value = '' }, 2000) }
  saving.value = false
}
</script>

<template>
  <div class="p-6 space-y-6">
    <div v-if="toast" class="fixed top-4 right-4 z-50 bg-indigo-600 text-white px-4 py-2.5 rounded-lg shadow-lg text-xs">{{ toast }}</div>

    <h1 class="text-sm font-semibold text-gray-200 flex items-center gap-2"><MessageSquare class="w-4 h-4 text-indigo-400" />短信服务配置</h1>

    <div v-if="loading" class="flex items-center justify-center py-20"><Loader2 class="w-6 h-6 animate-spin text-gray-600" /></div>

    <div v-else class="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-5 max-w-2xl">
      <div class="grid grid-cols-2 gap-5">
        <div>
          <label class="text-xs text-gray-400 mb-1.5 block">短信服务商</label>
          <select v-model="form.sms_provider" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500">
            <option value="">请选择</option>
            <option value="aliyun">阿里云短信</option>
            <option value="tencent">腾讯云短信</option>
          </select>
        </div>
        <div>
          <label class="text-xs text-gray-400 mb-1.5 block">短信签名</label>
          <input v-model="form.sms_sign_name" placeholder="如: 宿营家" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
        </div>
        <div>
          <label class="text-xs text-gray-400 mb-1.5 block">短信模板ID</label>
          <input v-model="form.sms_template_code" placeholder="SMS_XXXXX" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500" />
        </div>
      </div>

      <div>
        <label class="text-xs text-gray-400 mb-1.5 block flex items-center gap-1.5"><Key class="w-3.5 h-3.5" />AccessKey ID</label>
        <input v-model="form.sms_access_key" placeholder="LTAI5t..." class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500 font-mono" />
      </div>

      <div>
        <label class="text-xs text-gray-400 mb-1.5 block flex items-center gap-1.5"><Key class="w-3.5 h-3.5" />SecretKey</label>
        <input v-model="form.sms_secret_key" type="password" placeholder="输入 SecretKey" class="w-full text-sm px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-200 focus:outline-none focus:border-indigo-500 font-mono" />
      </div>

      <button @click="save" :disabled="saving" class="px-5 py-2 rounded-lg bg-indigo-600 text-white text-xs font-medium hover:bg-indigo-500 disabled:bg-gray-700 disabled:text-gray-500 transition-colors flex items-center gap-2">
        <Loader2 v-if="saving" class="w-3.5 h-3.5 animate-spin" />
        <Save v-else class="w-3.5 h-3.5" />
        {{ saving ? '保存中...' : '保存配置' }}
      </button>
    </div>
  </div>
</template>
