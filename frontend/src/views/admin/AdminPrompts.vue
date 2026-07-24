<script setup lang="ts">
import { ref } from 'vue'
import { FileText, Play, GitBranch, History as HistIcon, GitCompare, RotateCcw } from 'lucide-vue-next'

function showMsg(msg: string) { alert(msg) }

interface PromptModule { key: string; name: string; cost: string; version: string; icon: string; icBg: string; icColor: string }

const modules: PromptModule[] = [
  { key: 'xhs', name: '小红书图文生成', cost: '23家使用 · 挂载风格库', version: 'v3', icon: 'IG', icBg: 'bg-pink-500/10', icColor: 'text-pink-400' },
  { key: 'profile_audit', name: '酒店资料完整度检查', cost: '资料驱动', version: 'v1', icon: 'PF', icBg: 'bg-blue-500/10', icColor: 'text-blue-400' },
  { key: 'strategy', name: '周期营销策略生成', cost: '21家使用', version: 'v2', icon: 'LB', icBg: 'bg-amber-500/10', icColor: 'text-amber-400' },
  { key: 'pricing', name: '智能定价建议', cost: '23家使用', version: 'v4', icon: 'CN', icBg: 'bg-amber-500/10', icColor: 'text-amber-400' },
  { key: 'wechat', name: '朋友圈三档文案', cost: '23家使用 · 挂载风格库', version: 'v3', icon: 'WC', icBg: 'bg-emerald-500/10', icColor: 'text-emerald-400' },
  { key: 'review', name: '好评模板/回评话术', cost: '22家使用 · 挂载风格库', version: 'v2', icon: 'ST', icBg: 'bg-amber-500/10', icColor: 'text-amber-400' },
  { key: 'video', name: '抖音口播文案', cost: '19家使用 · 挂载风格库', version: 'v1', icon: 'MC', icBg: 'bg-indigo-500/10', icColor: 'text-indigo-400' },
  { key: 'brain', name: '运营智慧大脑', cost: '16家使用', version: 'v6', icon: 'BR', icBg: 'bg-indigo-500/10', icColor: 'text-indigo-400' },
]

const templates: Record<string, { content: string; model: string; maxTokens: number }> = {
  xhs: {
    content: `你是专业的民宿内容营销专家。请根据酒店主动填写并确认的资料生成小红书图文笔记：\n\n民宿信息：\n- 名称：{{hotel_name}}（{{hotel_type}}）\n- 位置：{{hotel_city}}\n- 核心特色：{{hotel_tags}}\n- 目标客群：{{hotel_target}}\n- 已确认知识：{{confirmed_knowledge}}\n\n内容主题：{{content_theme}}\n写作风格：{{writing_style}}\n\n不得虚构实时入住率、房量、订单、价格或活动。请输出标题备选、正文内容和话题标签。`,
    model: 'claude-sonnet-4-6', maxTokens: 1000,
  },
  profile_audit: {
    content: `请检查酒店基础资料、房型参考信息和已确认知识是否足以支持内容生成与经营建议。输出已完善项、缺失项、建议补充顺序和可直接向酒店方询问的问题。不得推测订单、房态或实时经营数据。`,
    model: 'claude-sonnet-4-6', maxTokens: 800,
  },
}

const selected = ref<PromptModule>(modules[0])
const activeTemplate = ref(templates['xhs'])

const variables = ['{{hotel_name}}', '{{hotel_type}}', '{{hotel_city}}', '{{hotel_tags}}', '{{hotel_target}}', '{{confirmed_knowledge}}', '{{content_theme}}', '{{writing_style}}']

function selectModule(m: PromptModule) {
  selected.value = m
  activeTemplate.value = templates[m.key] || templates['xhs']
}

const versions = ['v4 灰度测试中 · 10%流量', 'v3 当前生产版本 · 100%流量', 'v2', 'v1']
const activeVersion = ref(2)
</script>

<template>
  <div class="p-5 space-y-4">
    <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Template class="w-4 h-4 text-indigo-400" />主模板中心</div>

    <!-- 提示 -->
    <div class="text-xs text-indigo-400 bg-indigo-500/10 border-l-2 border-indigo-500 rounded-r p-3">
      <strong>主模板 vs 风格库：</strong>主模板决定"生成什么结构的内容"；风格库决定"用什么语气写"。两者通过&nbsp;<code v-pre class="bg-gray-800 px-1 rounded text-blue-400 text-[11px]">{{writing_style}}</code>&nbsp;变量动态拼接。
    </div>

    <div class="grid grid-cols-[260px_1fr] gap-4 items-start">
      <!-- 模块列表 -->
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-3 space-y-1.5">
        <div class="text-[10px] text-gray-500 uppercase tracking-wider px-2 mb-1">AI模块主模板</div>
        <button
          v-for="m in modules" :key="m.key"
          @click="selectModule(m)"
          :class="[
            'flex items-center gap-3 p-2.5 rounded-lg w-full text-left transition-colors border',
            selected.key === m.key ? 'bg-indigo-500/10 border-indigo-500/30' : 'border-transparent hover:bg-gray-800/50'
          ]"
        >
          <div :class="[m.icBg, 'w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0']">
            <span :class="[m.icColor, 'text-[10px] font-bold']">{{ m.icon }}</span>
          </div>
          <div class="min-w-0 flex-1">
            <div class="text-xs text-gray-200 font-medium truncate">{{ m.name }}</div>
            <div class="text-[10px] text-gray-600">{{ m.cost }}</div>
          </div>
          <span class="text-[9px] px-1.5 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 flex-shrink-0">{{ m.version }}</span>
        </button>
      </div>

      <!-- 模板编辑 -->
      <div class="space-y-3">
        <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <span class="text-xs font-medium text-gray-300">{{ selected.name }}</span>
              <span class="text-[10px] px-1.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400">{{ selected.version }} · 当前生产</span>
            </div>
            <div class="flex items-center gap-2">
              <button class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500 flex items-center gap-1" @click="showMsg('Prompt 版本管理即将上线')"><GitBranch class="w-3 h-3" />保存为新版本</button>
              <button class="px-3 py-1.5 rounded border border-gray-700 text-gray-400 text-[10px] hover:bg-gray-800 flex items-center gap-1" @click="showMsg('测试运行即将上线')"><Play class="w-3 h-3" />测试运行</button>
            </div>
          </div>

          <div class="mb-3">
            <div class="text-[10px] text-gray-500 mb-1.5">主模板内容（支持变量插值）</div>
            <textarea :value="activeTemplate.content" rows="10" class="w-full text-xs p-3 rounded-lg bg-gray-800 border border-gray-700 text-gray-300 focus:outline-none focus:border-indigo-500 font-mono resize-y"></textarea>
          </div>

          <div class="text-[10px] text-gray-500 uppercase tracking-wider mb-2">可用变量</div>
          <div class="flex flex-wrap gap-1.5 mb-3">
            <span v-for="v in variables" :key="v" class="text-[10px] px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-400 font-mono cursor-pointer">{{ v }}</span>
            <span v-pre class="text-[10px] px-1.5 py-0.5 rounded bg-pink-500/10 text-pink-400 font-mono cursor-pointer" @click="$router.push('/admin/styles')">{{writing_style}} ↗风格库</span>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div><div class="text-[10px] text-gray-500 mb-1">绑定模型</div><select class="w-full text-xs px-3 py-1.5 rounded bg-gray-800 border border-gray-700 text-gray-300"><option>{{ activeTemplate.model }}</option></select></div>
            <div><div class="text-[10px] text-gray-500 mb-1">最大输出长度</div><input :value="activeTemplate.maxTokens" type="number" class="w-full text-xs px-3 py-1.5 rounded bg-gray-800 border border-gray-700 text-gray-300"></div>
          </div>
        </div>

        <!-- 版本历史 -->
        <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
          <div class="text-xs font-medium text-gray-300 mb-3 flex items-center gap-1.5"><HistIcon class="w-3.5 h-3.5" />版本历史与灰度发布</div>
          <div v-for="(v, i) in versions" :key="i" :class="['flex items-center justify-between p-2.5 rounded-lg mb-1.5 border', i === 0 ? 'border-amber-500/30 bg-amber-500/5' : i === 1 ? 'border-emerald-500/30 bg-emerald-500/5' : 'border-gray-800']">
            <div>
              <div class="text-xs text-gray-300">{{ v }}</div>
              <div class="text-[10px] text-gray-600">{{ i === 0 ? '2025-06-10 · 优化标题生成，增加emoji密度控制' : i === 1 ? '2025-06-08 · 增加写作风格变量' : i === 2 ? '2025-05-20 · 增加话题标签数量' : '2025-03-12 · 初始版本' }}</div>
            </div>
            <div class="flex items-center gap-2">
              <span v-if="i === 0" class="text-[9px] px-1.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400">10%流量</span>
              <span v-if="i === 1" class="text-[9px] px-1.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400">生产中</span>
              <button v-if="i === 0" class="text-[9px] text-indigo-400 hover:text-indigo-300 flex items-center gap-0.5"><GitCompare class="w-3 h-3" />对比</button>
              <button v-if="i >= 2" class="text-[9px] text-red-400 hover:text-red-300 flex items-center gap-0.5"><RotateCcw class="w-3 h-3" />回滚</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
