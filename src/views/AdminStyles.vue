<script setup lang="ts">
import { ref } from 'vue'
import { Palette, Plus, Lock } from 'lucide-vue-next'

function showMsg(msg: string) { alert(msg) }

interface Style { name: string; scope: string; usage: string; feedback: string; status: string; prompt: string }

const publicStyles: Style[] = [
  { name: '治愈温暖（主流种草风）', scope: '公共', usage: '23家使用', feedback: '4.6★', status: '启用', prompt: '写作语气：温暖、治愈、有情绪共鸣感。多用感官描写，句子偏短，营造放松氛围。适当使用emoji（🌿☁️🍃），但不泛滥。结尾引导收藏/关注，语气像朋友分享私藏好去处。' },
  { name: '活泼元气（年轻客群）', scope: '公共', usage: '18家使用', feedback: '3.8★', status: '需关注', prompt: '写作语气：轻快、俏皮、有网感。多用网络流行语和梗，节奏快，短句+排比。emoji使用频率较高，标题要有反差感或悬念。适合年轻女性客群，强调"姐妹们必须冲"的氛围。' },
  { name: '轻奢精致（高端调性）', scope: '公共', usage: '9家使用', feedback: '4.7★', status: '启用', prompt: '写作语气：克制、优雅、有质感。少用emoji，强调细节描写（材质、光影、设计感）。句子结构完整，避免口语化网络用语。整体传递"低调的好品质"调性。' },
  { name: '故事叙事（沉浸体验）', scope: '公共', usage: '5家使用', feedback: '4.5★', status: '启用', prompt: '写作语气：第一人称叙事，像在写旅行日记。以时间线或场景切换为结构（清晨/午后/夜晚），细节描写带入感强，结尾留有余韵，不直接喊单，转化更软性。' },
]

const privateStyles: Style[] = [
  { name: '竹林禅意系', scope: '私有', usage: '松间·山野民宿 专属', feedback: '4.8★', status: '私有', prompt: '写作语气：禅意、留白、东方美学。多用自然意象（竹、雾、茶、风），句子有节奏感，类似俳句的留白感。避免过度营销语言，传递"慢下来"的生活方式，结尾引导轻柔，不强推。' },
]

const selected = ref<Style>(publicStyles[0])
const isEditing = ref(false)

function selectStyle(s: Style, idx: number) {
  selected.value = s
  isEditing.value = true
}

const linkedModules = ['小红书图文生成', '朋友圈文案', '抖音口播文案', '好评/回评话术']
</script>

<template>
  <div class="p-5 space-y-4">
    <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Palette class="w-4 h-4 text-indigo-400" />风格库管理</div>

    <!-- 说明 -->
    <div class="text-xs text-indigo-400 bg-indigo-500/10 border-l-2 border-indigo-500 rounded-r p-3">
      <strong>风格库是跨模块复用的资产：</strong>同一风格片段可被小红书、朋友圈、抖音口播、好评话术等多模块引用，改一处全生效。
    </div>

    <!-- 已挂载模块 -->
    <div class="bg-gray-900 border border-gray-800 rounded-lg p-4">
      <div class="text-xs font-medium text-gray-300 mb-3">已挂载风格库的模块</div>
      <div class="grid grid-cols-4 gap-2">
        <div v-for="m in linkedModules" :key="m" class="flex items-center gap-2 p-2 rounded-lg bg-gray-800/50 text-xs text-gray-400">
          <span class="w-6 h-6 rounded bg-gray-800 flex items-center justify-center text-[9px]">AI</span>
          {{ m }}
        </div>
      </div>
    </div>

    <div class="grid grid-cols-2 gap-4 items-start">
      <!-- 风格列表 -->
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-3">
        <div class="flex items-center justify-between">
          <div class="text-xs font-medium text-gray-300">公共风格库（全局）</div>
          <button class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500 flex items-center gap-1"><Plus class="w-3 h-3" />新增风格</button>
        </div>

        <button
          v-for="(s, i) in publicStyles" :key="i"
          @click="selectStyle(s, i)"
          :class="[
            'flex items-center justify-between p-2.5 rounded-lg w-full text-left border transition-colors',
            selected === s ? 'bg-indigo-500/10 border-indigo-500/30' : 'border-transparent hover:bg-gray-800/50'
          ]"
        >
          <div>
            <div class="text-xs text-gray-200 font-medium">{{ s.name }}</div>
            <div class="text-[10px] text-gray-600 mt-0.5">{{ s.usage }} · 反馈{{ s.feedback }}</div>
          </div>
          <span :class="s.status === '启用' ? 'text-[9px] px-1.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400' : 'text-[9px] px-1.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400'">{{ s.status }}</span>
        </button>

        <!-- 私有风格 -->
        <div class="pt-3 border-t border-gray-800">
          <div class="text-xs text-gray-500 mb-2 flex items-center gap-1"><Lock class="w-3 h-3" />租户私有风格</div>
          <button
            v-for="(s, i) in privateStyles" :key="`p${i}`"
            @click="selectStyle(s, i)"
            :class="[
              'flex items-center justify-between p-2.5 rounded-lg w-full text-left border transition-colors',
              selected === s ? 'bg-amber-500/10 border-amber-500/30' : 'border-transparent hover:bg-gray-800/50'
            ]"
          >
            <div>
              <div class="text-xs text-gray-200 font-medium">{{ s.name }}</div>
              <div class="text-[10px] text-gray-600 mt-0.5">{{ s.usage }}</div>
            </div>
            <span class="text-[9px] px-1.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400">私有</span>
          </button>
        </div>
      </div>

      <!-- 编辑区 -->
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-3">
        <div class="flex items-center justify-between">
          <div class="text-xs font-medium text-gray-300">编辑风格片段</div>
          <span :class="selected.scope === '公共' ? 'text-[9px] px-1.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400' : 'text-[9px] px-1.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400'">{{ selected.scope }}</span>
        </div>
        <div>
          <div class="text-[10px] text-gray-500 mb-1">风格名称</div>
          <input :value="selected.name" class="w-full text-xs px-3 py-1.5 rounded bg-gray-800 border border-gray-700 text-gray-300">
        </div>
        <div>
          <div class="text-[10px] text-gray-500 mb-1">提示词片段</div>
          <textarea :value="selected.prompt" rows="6" class="w-full text-xs p-3 rounded-lg bg-gray-800 border border-gray-700 text-gray-300 focus:outline-none focus:border-indigo-500 font-mono resize-y"></textarea>
        </div>
        <div class="flex gap-2">
          <button class="px-4 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500" @click="showMsg('风格保存即将上线')">保存</button>
          <button class="px-4 py-1.5 rounded border border-gray-700 text-gray-400 text-[10px] hover:bg-gray-800" @click="showMsg('风格管理即将上线')">{{ selected.scope === '公共' ? '停用此风格' : '删除该风格' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
