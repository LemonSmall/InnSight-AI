<script setup lang="ts">
import { ThumbsUp, Star } from 'lucide-vue-next'

const feedbacks = [
  { hotel: '松间·山野民宿', module: '小红书图文', rating: '满意', snippet: '标题很有吸引力，正文带入感强', time: '06-09 14:32', style: '治愈温暖' },
  { hotel: '清风客栈', module: '朋友圈文案', rating: '满意', snippet: '三段式省心好用', time: '06-09 11:08', style: '活泼元气' },
  { hotel: '松间·山野民宿', module: '海报生成', rating: '不满意', snippet: '视觉元素太年轻化，不符合禅意风格', time: '06-09 09:45', style: '轻奢精致' },
]

const stats = { total: 1280, good: 912, bad: 48, neutral: 320 }
</script>

<template>
  <div class="p-5 space-y-4">
    <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><ThumbsUp class="w-4 h-4 text-indigo-400" />内容反馈闭环</div>

    <div class="grid grid-cols-4 gap-3">
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4 text-center">
        <div class="text-2xl font-bold text-white">{{ stats.total }}</div>
        <div class="text-[11px] text-gray-500">总反馈量</div>
      </div>
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4 text-center">
        <div class="text-2xl font-bold text-emerald-400">{{ stats.good }}</div>
        <div class="text-[11px] text-gray-500">满意</div>
      </div>
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4 text-center">
        <div class="text-2xl font-bold text-red-400">{{ stats.bad }}</div>
        <div class="text-[11px] text-gray-500">不满意</div>
      </div>
      <div class="bg-gray-900 border border-gray-800 rounded-lg p-4 text-center">
        <div class="text-2xl font-bold text-amber-400">{{ stats.neutral }}</div>
        <div class="text-[11px] text-gray-500">中性</div>
      </div>
    </div>

    <div class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead>
          <tr class="border-b border-gray-800 text-gray-500 text-left">
            <th class="py-2.5 px-4">酒店</th><th class="py-2.5 px-4">模块</th><th class="py-2.5 px-4">评价</th><th class="py-2.5 px-4">反馈内容</th><th class="py-2.5 px-4">使用风格</th><th class="py-2.5 px-4">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="f in feedbacks" :key="f.time" class="border-b border-gray-800/50 hover:bg-gray-800/30">
            <td class="py-2.5 px-4 text-gray-200 font-medium">{{ f.hotel }}</td>
            <td class="py-2.5 px-4 text-gray-400">{{ f.module }}</td>
            <td class="py-2.5 px-4"><span :class="f.rating === '满意' ? 'text-emerald-400' : f.rating === '不满意' ? 'text-red-400' : 'text-gray-400'">{{ f.rating }}</span></td>
            <td class="py-2.5 px-4 text-gray-500">{{ f.snippet }}</td>
            <td class="py-2.5 px-4 text-gray-600">{{ f.style }}</td>
            <td class="py-2.5 px-4 text-gray-600">{{ f.time }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
