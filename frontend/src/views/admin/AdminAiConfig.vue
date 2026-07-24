<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api'
import {
  AI_CONTEXT_PARAMS,
  AI_MODULES,
  buildAiInputSchema,
  getAiModule,
  type AiModuleDefinition,
  type AiParamDefinition,
} from '@/utils/aiModuleContract'
import {
  Bot,
  Check,
  ChevronRight,
  CircleDot,
  Eye,
  KeyRound,
  Loader2,
  Plus,
  RefreshCw,
  Save,
  Settings2,
  SlidersHorizontal,
  Trash2,
  X,
} from 'lucide-vue-next'

interface AgentBinding {
  id: number
  moduleKey: string
  provider: string
  appType: 'workflow' | 'chatflow'
  appId: string
  apiKey: string
  appName: string
  endpoint: string
  inputSchema: string
  botId: string
  botApiKey: string
  botName: string
  apiKeyConfigured: boolean
  enabled: number
}

const defaultEndpoint = 'https://api.dify.ai/v1'
const modules: AiModuleDefinition[] = AI_MODULES
const commonParams: AiParamDefinition[] = AI_CONTEXT_PARAMS
const paramPresets: Record<string, AiParamDefinition[]> = Object.fromEntries(
  AI_MODULES.map(module => [module.key, module.params])
)

const settings = reactive({
  dify_enabled: 'true',
  dify_endpoint: defaultEndpoint,
  dify_timeout_seconds: '180',
  modelscope_api_key: '',
  modelscope_api_key_configured: 'false',
  modelscope_image_model: 'Tongyi-MAI/Z-Image-Turbo',
  modelscope_image_poll_attempts: '30',
  modelscope_image_poll_interval_seconds: '4',
})

const editor = reactive<AgentBinding>({
  id: 0,
  moduleKey: 'brain',
  provider: 'dify',
  appType: 'workflow',
  appId: '',
  apiKey: '',
  appName: '',
  endpoint: '',
  inputSchema: '{}',
  botId: '',
  botApiKey: '',
  botName: '',
  apiKeyConfigured: false,
  enabled: 0,
})

const bindings = ref<AgentBinding[]>([])
const selectedModule = ref('brain')
const toast = ref('')
const loading = ref(false)
const saving = ref(false)
const savingSettings = ref(false)
const validatingId = ref<number | null>(null)
const detailsOpen = ref(false)
const settingsOpen = ref(false)
const editorOpen = ref(false)
const deleteTarget = ref<AgentBinding | null>(null)
const editMode = ref<'create' | 'edit'>('create')

const selectedModuleInfo = computed(() => moduleInfo(selectedModule.value))
const selectedBindings = computed(() => moduleCandidates(selectedModule.value))
const configuredModules = computed(() => modules.filter(item => activeBinding(item.key)).length)
const totalCandidates = computed(() => bindings.value.length)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [settingsRes, bindingsRes] = await Promise.all([
      api.get('/api/admin/settings'),
      api.get('/api/admin/ai-agent-bindings'),
    ])
    const map = settingsRes.data.data?.map || settingsRes.data.data || {}
    settings.dify_enabled = map.dify_enabled || 'true'
    settings.dify_endpoint = map.dify_endpoint || defaultEndpoint
    settings.dify_timeout_seconds = map.dify_timeout_seconds || '180'
    settings.modelscope_api_key = ''
    settings.modelscope_api_key_configured = map.modelscope_api_key_configured || 'false'
    settings.modelscope_image_model = map.modelscope_image_model || 'Tongyi-MAI/Z-Image-Turbo'
    settings.modelscope_image_poll_attempts = map.modelscope_image_poll_attempts || '30'
    settings.modelscope_image_poll_interval_seconds = map.modelscope_image_poll_interval_seconds || '4'
    bindings.value = (bindingsRes.data.data || []).map(normalizeBinding)
  } catch (error: any) {
    flash(error?.response?.data?.message || 'AI 配置加载失败')
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  savingSettings.value = true
  try {
    const res = await api.put('/api/admin/settings', { ...settings })
    assertOk(res.data, '通用配置保存失败')
    settingsOpen.value = false
    flash('通用配置已保存')
  } catch (error: any) {
    flash(error?.response?.data?.message || '通用配置保存失败')
  } finally {
    savingSettings.value = false
  }
}

function openDetails(moduleKey: string) {
  selectedModule.value = moduleKey
  detailsOpen.value = true
}

function createAgent(moduleKey = selectedModule.value) {
  selectedModule.value = moduleKey
  editMode.value = 'create'
  const candidates = moduleCandidates(moduleKey)
  Object.assign(editor, normalizeBinding({
    moduleKey,
    appName: defaultAgentName(moduleKey),
    appType: getAiModule(moduleKey).appType,
    enabled: candidates.length === 0 ? 1 : 0,
    inputSchema: schemaFromModuleDefaults(moduleKey),
  }))
  editorOpen.value = true
}

function editAgent(row: AgentBinding) {
  selectedModule.value = row.moduleKey
  editMode.value = 'edit'
  Object.assign(editor, { ...row })
  editorOpen.value = true
}

async function saveAgent(validate = false) {
  if (!editor.appName.trim()) {
    flash('请填写智能体名称')
    return
  }
  if (!cleanApiKey(editor) && !editor.apiKeyConfigured) {
    flash('请填写 Dify App API Key')
    return
  }
  editor.inputSchema = schemaFromModuleDefaults(editor.moduleKey)
  saving.value = true
  try {
    if (editMode.value === 'create') {
      const res = await api.post('/api/admin/ai-agent-bindings', payload(editor))
      assertOk(res.data, '智能体配置保存失败')
      Object.assign(editor, normalizeBinding(res.data.data || res.data))
    } else {
      const res = await api.put(`/api/admin/ai-agent-bindings/${editor.id}`, payload(editor))
      assertOk(res.data, '智能体配置保存失败')
    }
    if (validate) await validateDify(editor)
    editorOpen.value = false
    flash(validate ? '保存并校验成功' : '智能体配置已保存')
    await load()
  } catch (error: any) {
    flash(error?.response?.data?.message || error?.message || '智能体配置保存失败')
  } finally {
    saving.value = false
  }
}

async function enableAgent(row: AgentBinding) {
  const enabled = row.enabled === 1 ? 0 : 1
  try {
    const res = await api.put(`/api/admin/ai-agent-bindings/${row.id}`, payload({ ...row, enabled }))
    assertOk(res.data, '状态更新失败')
    flash(enabled ? '已启用，当前功能的其他智能体已自动停用' : '智能体已停用')
    await load()
  } catch (error: any) {
    flash(error?.response?.data?.message || '状态更新失败')
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    const res = await api.delete(`/api/admin/ai-agent-bindings/${deleteTarget.value.id}`)
    assertOk(res.data, '删除失败')
    deleteTarget.value = null
    flash('智能体已删除')
    await load()
  } catch (error: any) {
    flash(error?.response?.data?.message || '删除失败')
  }
}

async function validateAgent(row: AgentBinding) {
  validatingId.value = row.id
  try {
    await validateDify(row)
    flash('连接校验成功')
  } catch (error: any) {
    flash(error?.response?.data?.message || error?.message || '连接校验失败')
  } finally {
    validatingId.value = null
  }
}

function payload(row: AgentBinding) {
  const apiKey = cleanApiKey(row)
  const data: Record<string, any> = {
    moduleKey: row.moduleKey,
    provider: 'dify',
    appType: row.appType,
    appId: row.appId,
    appName: row.appName,
    endpoint: row.endpoint,
    inputSchema: row.inputSchema,
    botId: row.botId || '',
    botName: row.appName,
    enabled: row.enabled,
  }
  if (apiKey) {
    data.apiKey = apiKey
    data.botApiKey = apiKey
  }
  return data
}

async function validateDify(row: AgentBinding) {
  const apiKey = cleanApiKey(row)
  if (!apiKey && !row.apiKeyConfigured) throw new Error('请先填写 Dify App API Key')
  const request: Record<string, any> = {
    bindingId: row.id || undefined,
    endpoint: row.endpoint?.trim() || settings.dify_endpoint,
    appType: row.appType,
    moduleKey: row.moduleKey,
  }
  if (apiKey) {
    request.apiKey = apiKey
  }
  const { data } = await api.post('/api/admin/dify/validate', request)
  assertOk(data, '连接校验失败')
}

function cleanApiKey(row: AgentBinding) {
  const value = row.apiKey?.trim() || ''
  if (!value) return ''
  const lower = value.toLowerCase()
  if (lower.includes('dify connection failed') || lower.includes('access token is invalid')) {
    return ''
  }
  return value
}

function normalizeBinding(row: Partial<AgentBinding>): AgentBinding {
  const moduleKey = row.moduleKey || 'brain'
  return {
    id: row.id || 0,
    moduleKey,
    provider: row.provider || 'dify',
    appType: row.appType === 'workflow' ? 'workflow' : 'chatflow',
    appId: row.appId || '',
    apiKey: '',
    appName: row.appName || row.botName || defaultAgentName(moduleKey),
    endpoint: row.endpoint || '',
    inputSchema: row.inputSchema || schemaFromModuleDefaults(moduleKey),
    botId: row.botId || '',
    botApiKey: '',
    botName: row.botName || row.appName || '',
    apiKeyConfigured: row.apiKeyConfigured ?? Boolean(row.apiKey || row.botApiKey),
    enabled: row.enabled ?? 0,
  }
}

function schemaFromModuleDefaults(moduleKey: string) {
  return JSON.stringify(buildAiInputSchema(moduleKey))
}

function defaultAgentName(moduleKey: string) {
  const label = moduleInfo(moduleKey).agentName
  const count = moduleCandidates(moduleKey).length
  return count ? `${label} ${count + 1}` : label
}

function activeBinding(moduleKey: string) {
  return bindings.value.find(row => row.moduleKey === moduleKey && row.enabled === 1) || null
}

function moduleCandidates(moduleKey: string) {
  return bindings.value.filter(row => row.moduleKey === moduleKey)
}

function moduleInfo(moduleKey: string) {
  return getAiModule(moduleKey)
}

function moduleParams(moduleKey: string) {
  return paramPresets[moduleKey] || []
}

function keyStatus(row: AgentBinding) {
  return row.apiKeyConfigured ? 'API Key 已安全配置' : '未填写 API Key'
}

function flash(message: string) {
  toast.value = message
  window.setTimeout(() => { toast.value = '' }, 2400)
}

function assertOk(response: any, fallback: string) {
  if (response?.code !== undefined && response.code !== 200) {
    throw new Error(response.message || fallback)
  }
}
</script>

<template>
  <div class="space-y-5">
    <div v-if="toast" class="fixed right-5 top-5 z-[70] rounded-lg bg-indigo-600 px-4 py-2 text-sm text-white shadow-xl">
      {{ toast }}
    </div>

    <header class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 class="flex items-center gap-2 text-base font-semibold text-gray-100">
          <Bot class="h-5 w-5 text-indigo-400" />
          智能体与功能绑定
        </h1>
        <p class="mt-1 text-xs text-gray-500">按用户端功能管理智能体。每个功能只能启用一个智能体。</p>
      </div>
      <div class="flex gap-2">
        <button class="btn-secondary" @click="load">
          <RefreshCw class="h-4 w-4" />
          刷新
        </button>
        <button class="btn-primary" @click="settingsOpen = true">
          <Settings2 class="h-4 w-4" />
          通用配置
        </button>
      </div>
    </header>

    <section class="grid gap-4 sm:grid-cols-3">
      <div class="metric-card">
        <span>功能模块</span>
        <strong>{{ modules.length }}</strong>
      </div>
      <div class="metric-card">
        <span>已启用</span>
        <strong class="text-emerald-300">{{ configuredModules }}</strong>
      </div>
      <div class="metric-card">
        <span>候选智能体</span>
        <strong>{{ totalCandidates }}</strong>
      </div>
    </section>

    <div v-if="loading" class="flex items-center justify-center py-24">
      <Loader2 class="h-7 w-7 animate-spin text-gray-600" />
    </div>

    <section v-else>
      <div class="mb-3 flex items-center justify-between">
        <h2 class="text-sm font-medium text-gray-300">功能绑定</h2>
        <span class="text-xs text-gray-600">点击详情查看参数和候选智能体</span>
      </div>

      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
        <article
          v-for="item in modules"
          :key="item.key"
          class="group flex min-h-[190px] flex-col rounded-xl border border-gray-800 bg-gray-900 p-4 transition hover:border-gray-700 hover:bg-gray-900/80"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-300">
              <Bot class="h-4 w-4" />
            </div>
            <span
              class="inline-flex items-center gap-1 rounded-full px-2 py-1 text-[11px]"
              :class="activeBinding(item.key) ? 'bg-emerald-500/10 text-emerald-300' : 'bg-gray-800 text-gray-500'"
            >
              <CircleDot class="h-3 w-3" />
              {{ activeBinding(item.key) ? '已启用' : '未配置' }}
            </span>
          </div>

          <div class="mt-4 min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <h3 class="truncate text-sm font-semibold text-gray-100">{{ item.label }}</h3>
              <span class="rounded bg-gray-800 px-1.5 py-0.5 text-[10px] text-gray-500">{{ item.group }}</span>
            </div>
            <p class="mt-1 line-clamp-1 text-xs text-gray-500">{{ item.desc }}</p>
            <div class="mt-3 rounded-lg border border-gray-800 bg-gray-950/60 px-3 py-2">
              <div class="truncate text-xs" :class="activeBinding(item.key) ? 'text-gray-200' : 'text-gray-600'">
                {{ activeBinding(item.key)?.appName || '尚未绑定智能体' }}
              </div>
              <div class="mt-1 text-[11px] text-gray-600">
                {{ moduleCandidates(item.key).length }} 个候选 · {{ moduleParams(item.key).length }} 个业务参数
              </div>
            </div>
          </div>

          <div class="mt-4 flex gap-2 border-t border-gray-800 pt-3">
            <button class="card-action" @click="openDetails(item.key)">
              <Eye class="h-3.5 w-3.5" />
              详情
            </button>
            <button
              class="card-action-primary"
              @click="activeBinding(item.key) ? editAgent(activeBinding(item.key)!) : createAgent(item.key)"
            >
              <SlidersHorizontal class="h-3.5 w-3.5" />
              {{ activeBinding(item.key) ? '配置' : '绑定' }}
            </button>
          </div>
        </article>
      </div>
    </section>

    <div v-if="detailsOpen" class="modal-mask" @click.self="detailsOpen = false">
      <div class="modal-panel max-w-3xl">
        <div class="modal-head">
          <div>
            <div class="flex items-center gap-2">
              <h2>{{ selectedModuleInfo.label }}</h2>
              <span class="rounded bg-gray-800 px-2 py-0.5 text-[11px] text-gray-500">{{ selectedModuleInfo.group }}</span>
            </div>
            <p>{{ selectedModuleInfo.desc }}</p>
          </div>
          <button class="icon-button" @click="detailsOpen = false"><X class="h-4 w-4" /></button>
        </div>

        <div class="max-h-[72vh] space-y-6 overflow-y-auto p-5">
          <section>
            <div class="section-title">
              <div>
                <h3>候选智能体</h3>
                <p>同一功能可以添加多个候选，但只能启用一个。</p>
              </div>
              <button class="btn-primary" @click="createAgent(selectedModule)">
                <Plus class="h-4 w-4" />
                新增智能体
              </button>
            </div>

            <div v-if="selectedBindings.length" class="mt-3 space-y-2">
              <div v-for="row in selectedBindings" :key="row.id" class="candidate-row">
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <CircleDot class="h-3.5 w-3.5" :class="row.enabled === 1 ? 'text-emerald-300' : 'text-gray-600'" />
                    <span class="truncate text-sm font-medium text-gray-200">{{ row.appName }}</span>
                    <span class="rounded bg-gray-800 px-1.5 py-0.5 text-[10px] text-gray-500">{{ row.appType }}</span>
                  </div>
                  <p class="mt-1 truncate text-[11px] text-gray-600">{{ keyStatus(row) }}</p>
                </div>
                <div class="flex flex-wrap justify-end gap-2">
                  <button class="mini-button" @click="enableAgent(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</button>
                  <button class="mini-button" :disabled="validatingId === row.id" @click="validateAgent(row)">
                    {{ validatingId === row.id ? '校验中' : '校验' }}
                  </button>
                  <button class="mini-button text-indigo-300" @click="editAgent(row)">配置</button>
                  <button class="mini-button text-red-300" @click="deleteTarget = row"><Trash2 class="h-3.5 w-3.5" /></button>
                </div>
              </div>
            </div>
            <button v-else class="empty-button mt-3" @click="createAgent(selectedModule)">
              当前功能还没有智能体，点击添加
              <ChevronRight class="h-4 w-4" />
            </button>
          </section>

          <section>
            <div class="section-title">
              <div>
                <h3>用户端固定参数</h3>
                <p>字段由用户端页面决定，管理端只读展示。</p>
              </div>
              <span class="text-xs text-gray-600">{{ moduleParams(selectedModule).length }} 项</span>
            </div>
            <div class="mt-3 grid gap-2 sm:grid-cols-2">
              <div v-for="param in moduleParams(selectedModule)" :key="param.name" class="param-item">
                <div class="flex items-center justify-between gap-2">
                  <code>{{ param.name }}</code>
                  <span v-if="param.required" class="required-tag">必传</span>
                </div>
                <p>{{ param.label }} · {{ param.source }}</p>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>

    <div v-if="settingsOpen" class="modal-mask" @click.self="settingsOpen = false">
      <form class="modal-panel max-w-xl" @submit.prevent="saveSettings">
        <div class="modal-head">
          <div>
            <h2>AI 通用配置</h2>
            <p>配置 Dify 智能体服务与海报图片生成服务。</p>
          </div>
          <button type="button" class="icon-button" @click="settingsOpen = false"><X class="h-4 w-4" /></button>
        </div>
        <div class="space-y-4 p-5">
          <label class="toggle-row">
            <span>
              <strong>启用 Dify 服务</strong>
              <small>关闭后用户端不会调用 Dify 智能体</small>
            </span>
            <input v-model="settings.dify_enabled" true-value="true" false-value="false" type="checkbox" class="h-4 w-4 accent-indigo-600" />
          </label>
          <label class="field">
            <span>Dify Endpoint</span>
            <input v-model="settings.dify_endpoint" class="input" placeholder="https://api.dify.ai/v1" />
          </label>
          <label class="field">
            <span>请求超时（秒）</span>
            <input v-model="settings.dify_timeout_seconds" class="input" min="10" type="number" />
          </label>
          <div class="border-t border-gray-800 pt-4">
            <div class="mb-3 text-xs font-semibold text-gray-300">ModelScope 图片服务</div>
            <div class="space-y-4">
              <label class="field">
                <span>访问令牌 <small v-if="settings.modelscope_api_key_configured === 'true'">已配置，留空表示不修改</small></span>
                <input v-model="settings.modelscope_api_key" class="input font-mono" type="password" autocomplete="new-password" :placeholder="settings.modelscope_api_key_configured === 'true' ? '已安全配置，输入新令牌可替换' : 'ms-...'" />
              </label>
              <label class="field">
                <span>图片模型</span>
                <input v-model="settings.modelscope_image_model" class="input font-mono" />
              </label>
              <div class="grid gap-4 sm:grid-cols-2">
                <label class="field"><span>最多查询次数</span><input v-model="settings.modelscope_image_poll_attempts" class="input" min="1" max="60" type="number" /></label>
                <label class="field"><span>查询间隔（秒）</span><input v-model="settings.modelscope_image_poll_interval_seconds" class="input" min="1" max="15" type="number" /></label>
              </div>
            </div>
          </div>
          <div class="rounded-lg border border-gray-800 bg-gray-950/60 p-3">
            <div class="text-xs font-medium text-gray-400">每次调用自动携带</div>
            <div class="mt-2 flex flex-wrap gap-1.5">
              <code v-for="param in commonParams" :key="param.name" class="common-param">{{ param.name }}</code>
            </div>
          </div>
        </div>
        <div class="modal-actions">
          <button type="button" class="btn-secondary" @click="settingsOpen = false">取消</button>
          <button type="submit" class="btn-primary" :disabled="savingSettings">
            <Loader2 v-if="savingSettings" class="h-4 w-4 animate-spin" />
            <Save v-else class="h-4 w-4" />
            保存配置
          </button>
        </div>
      </form>
    </div>

    <div v-if="editorOpen" class="modal-mask" @click.self="editorOpen = false">
      <form class="modal-panel max-w-xl" @submit.prevent="saveAgent(false)">
        <div class="modal-head">
          <div>
            <h2>{{ editMode === 'create' ? '新增智能体' : '配置智能体' }}</h2>
            <p>{{ moduleInfo(editor.moduleKey).label }} · 每个功能只能启用一个</p>
          </div>
          <button type="button" class="icon-button" @click="editorOpen = false"><X class="h-4 w-4" /></button>
        </div>
        <div class="space-y-4 p-5">
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="field">
              <span>智能体名称</span>
              <input v-model="editor.appName" class="input" />
            </label>
            <label class="field">
              <span>App 类型</span>
              <select v-model="editor.appType" class="input">
                <option value="chatflow">Chatflow</option>
                <option value="workflow">Workflow</option>
              </select>
            </label>
          </div>
          <label class="field">
            <span>Dify App API Key <small v-if="editor.apiKeyConfigured">已配置，留空表示不修改</small></span>
            <input v-model="editor.apiKey" class="input font-mono" :placeholder="editor.apiKeyConfigured ? '已安全配置，输入新 Key 可替换' : 'app-...'" type="password" autocomplete="new-password" />
            <small class="text-[11px] text-amber-300/80">切换 Dify Endpoint 时，需要填写该 Endpoint 所属应用的新 API Key。</small>
          </label>
          <label class="field">
            <span>独立 Endpoint <small>留空使用通用配置</small></span>
            <input v-model="editor.endpoint" class="input font-mono" :placeholder="settings.dify_endpoint" />
          </label>
          <label class="toggle-row">
            <span>
              <strong>启用该智能体</strong>
              <small>启用后会自动停用此功能下的其他智能体</small>
            </span>
            <input v-model="editor.enabled" :true-value="1" :false-value="0" type="checkbox" class="h-4 w-4 accent-indigo-600" />
          </label>
        </div>
        <div class="modal-actions">
          <button type="button" class="btn-secondary" @click="editorOpen = false">取消</button>
          <button type="submit" class="btn-secondary" :disabled="saving">
            <Save class="h-4 w-4" />
            保存
          </button>
          <button type="button" class="btn-primary" :disabled="saving" @click="saveAgent(true)">
            <Loader2 v-if="saving" class="h-4 w-4 animate-spin" />
            <KeyRound v-else class="h-4 w-4" />
            保存并校验
          </button>
        </div>
      </form>
    </div>

    <div v-if="deleteTarget" class="modal-mask" @click.self="deleteTarget = null">
      <div class="modal-panel max-w-sm">
        <div class="p-5">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-red-500/10 text-red-300">
            <Trash2 class="h-5 w-5" />
          </div>
          <h2 class="mt-4 text-base font-semibold text-gray-100">删除智能体</h2>
          <p class="mt-2 text-sm leading-6 text-gray-500">确定删除“{{ deleteTarget.appName }}”吗？删除后该配置无法恢复。</p>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="deleteTarget = null">取消</button>
          <button class="btn-danger" @click="confirmDelete">
            <Trash2 class="h-4 w-4" />
            确认删除
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.btn-primary,
.btn-secondary,
.btn-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border-radius: 0.5rem;
  padding: 0.625rem 0.875rem;
  font-size: 0.75rem;
  font-weight: 500;
  transition: 150ms ease;
}
.btn-primary { background: #4f46e5; color: #fff; }
.btn-primary:hover { background: #6366f1; }
.btn-secondary { border: 1px solid #374151; background: #111827; color: #d1d5db; }
.btn-secondary:hover { background: #1f2937; }
.btn-danger { background: #dc2626; color: #fff; }
.btn-danger:hover { background: #ef4444; }
.btn-primary:disabled,
.btn-secondary:disabled { cursor: not-allowed; opacity: 0.55; }
.metric-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid #1f2937;
  border-radius: 0.75rem;
  background: #111827;
  padding: 1rem 1.125rem;
}
.metric-card span { color: #6b7280; font-size: 0.75rem; }
.metric-card strong { color: #f3f4f6; font-size: 1.25rem; font-weight: 650; }
.card-action,
.card-action-primary {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  border-radius: 0.5rem;
  padding: 0.5rem 0.75rem;
  font-size: 0.75rem;
  transition: 150ms ease;
}
.card-action { border: 1px solid #374151; color: #9ca3af; }
.card-action:hover { background: #1f2937; color: #e5e7eb; }
.card-action-primary { background: #312e81; color: #c7d2fe; }
.card-action-primary:hover { background: #3730a3; color: #fff; }
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(0 0 0 / 0.68);
  padding: 1rem;
  backdrop-filter: blur(2px);
}
.modal-panel {
  width: 100%;
  overflow: hidden;
  border: 1px solid #374151;
  border-radius: 0.75rem;
  background: #111827;
  box-shadow: 0 24px 70px rgb(0 0 0 / 0.45);
}
.modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid #1f2937;
  padding: 1rem 1.25rem;
}
.modal-head h2 { color: #f3f4f6; font-size: 0.875rem; font-weight: 600; }
.modal-head p { margin-top: 0.25rem; color: #6b7280; font-size: 0.75rem; }
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  border-top: 1px solid #1f2937;
  padding: 1rem 1.25rem;
}
.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.5rem;
  padding: 0.4rem;
  color: #6b7280;
}
.icon-button:hover { background: #1f2937; color: #e5e7eb; }
.section-title { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.section-title h3 { color: #e5e7eb; font-size: 0.8125rem; font-weight: 600; }
.section-title p { margin-top: 0.2rem; color: #6b7280; font-size: 0.75rem; }
.candidate-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border: 1px solid #1f2937;
  border-radius: 0.625rem;
  background: rgb(3 7 18 / 0.45);
  padding: 0.75rem;
}
.mini-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #374151;
  border-radius: 0.45rem;
  padding: 0.4rem 0.65rem;
  color: #9ca3af;
  font-size: 0.7rem;
}
.mini-button:hover { background: #1f2937; color: #f3f4f6; }
.empty-button {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border: 1px dashed #374151;
  border-radius: 0.625rem;
  padding: 1.5rem;
  color: #6b7280;
  font-size: 0.75rem;
}
.empty-button:hover { border-color: #4f46e5; color: #a5b4fc; }
.param-item { border: 1px solid #1f2937; border-radius: 0.625rem; background: rgb(3 7 18 / 0.45); padding: 0.75rem; }
.param-item code { color: #c7d2fe; font-size: 0.72rem; }
.param-item p { margin-top: 0.35rem; color: #6b7280; font-size: 0.7rem; }
.required-tag { border-radius: 0.25rem; background: rgb(16 185 129 / 0.1); padding: 0.15rem 0.35rem; color: #6ee7b7; font-size: 0.625rem; }
.common-param { border-radius: 0.3rem; background: #1f2937; padding: 0.25rem 0.4rem; color: #9ca3af; font-size: 0.65rem; }
.field { display: block; }
.field > span { display: block; margin-bottom: 0.4rem; color: #9ca3af; font-size: 0.75rem; }
.field small { color: #4b5563; font-size: 0.68rem; }
.input {
  width: 100%;
  border: 1px solid #374151;
  border-radius: 0.5rem;
  background: #1f2937;
  padding: 0.625rem 0.75rem;
  color: #e5e7eb;
  font-size: 0.8125rem;
  outline: none;
}
.input:focus { border-color: #6366f1; box-shadow: 0 0 0 2px rgb(99 102 241 / 0.12); }
.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border: 1px solid #1f2937;
  border-radius: 0.625rem;
  background: rgb(3 7 18 / 0.45);
  padding: 0.75rem;
}
.toggle-row strong { display: block; color: #d1d5db; font-size: 0.75rem; font-weight: 500; }
.toggle-row small { display: block; margin-top: 0.2rem; color: #6b7280; font-size: 0.68rem; }
@media (max-width: 640px) {
  .candidate-row { align-items: flex-start; flex-direction: column; }
  .modal-actions { flex-wrap: wrap; }
}
</style>
