<script setup lang="ts">
import { Activity } from 'lucide-vue-next'

const logs = [
  { time: '06-09 14:32:15', hotel: '松间·山野民宿', module: 'poster', duration: 2340, status: 'success' },
  { time: '06-09 14:31:08', hotel: '清风客栈', module: 'xhs', duration: 1890, status: 'success' },
  { time: '06-09 14:30:52', hotel: '松间·山野民宿', module: 'poster', duration: 5230, status: 'error', error: 'image model timeout' },
  { time: '06-09 14:28:11', hotel: '古城拾光民宿', module: 'room_status', duration: 320, status: 'success' },
  { time: '06-09 14:25:00', hotel: '观山雅集', module: 'video', duration: 120, status: 'blocked', error: 'balance depleted' },
]
</script>

<template>
  <div class="p-5 space-y-4">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Activity class="w-4 h-4 text-blue-400" />调用日志/监控</div>
    </div>

    <div class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead>
          <tr class="border-b border-gray-800 text-gray-500 text-left">
            <th class="py-2.5 px-4">时间</th><th class="py-2.5 px-4">酒店</th><th class="py-2.5 px-4">模块</th><th class="py-2.5 px-4">耗时(ms)</th><th class="py-2.5 px-4">状态</th><th class="py-2.5 px-4">错误信息</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in logs" :key="l.time" class="border-b border-gray-800/50">
            <td class="py-2.5 px-4 text-gray-500 font-mono">{{ l.time }}</td>
            <td class="py-2.5 px-4 text-gray-300">{{ l.hotel }}</td>
            <td class="py-2.5 px-4 text-gray-400 font-mono">{{ l.module }}</td>
            <td class="py-2.5 px-4 text-gray-400">{{ l.duration }}</td>
            <td class="py-2.5 px-4">
              <span :class="l.status === 'success' ? 'text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded text-[10px]' : l.status === 'error' ? 'text-red-400 bg-red-500/10 px-1.5 py-0.5 rounded text-[10px]' : 'text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded text-[10px]'">
                {{ l.status === 'success' ? '成功' : l.status === 'error' ? '失败' : '已阻断' }}
              </span>
            </td>
            <td class="py-2.5 px-4 text-red-400/70 text-[11px]">{{ l.error || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
