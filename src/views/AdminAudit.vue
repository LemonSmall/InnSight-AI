<script setup lang="ts">
import { History as HistIcon } from 'lucide-vue-next'

const audits = [
  { time: '06-09 14:20', operator: 'admin@xxx', action: '保存模板新版本', detail: '小红书图文 → v4 灰度10%', target: '全平台' },
  { time: '06-09 10:15', operator: 'admin@xxx', action: '修改计费规则', detail: '海报生成 20→30 算力', target: '全平台' },
  { time: '06-09 09:00', operator: 'admin@xxx', action: '新增租户', detail: '古城拾光民宿 · 试用版', target: '古城拾光' },
  { time: '06-08 16:30', operator: 'admin@xxx', action: '手动充值', detail: '松间民宿 +2000 算力', target: '松间·山野' },
]
</script>

<template>
  <div class="p-5 space-y-4">
    <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><HistIcon class="w-4 h-4 text-indigo-400" />操作审计日志</div>

    <div class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead>
          <tr class="border-b border-gray-800 text-gray-500 text-left">
            <th class="py-2.5 px-4">时间</th><th class="py-2.5 px-4">操作人</th><th class="py-2.5 px-4">操作</th><th class="py-2.5 px-4">详情</th><th class="py-2.5 px-4">目标对象</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in audits" :key="a.time" class="border-b border-gray-800/50">
            <td class="py-2.5 px-4 text-gray-500 font-mono">{{ a.time }}</td>
            <td class="py-2.5 px-4 text-gray-400 font-mono text-[11px]">{{ a.operator }}</td>
            <td class="py-2.5 px-4 text-gray-300">{{ a.action }}</td>
            <td class="py-2.5 px-4 text-gray-500">{{ a.detail }}</td>
            <td class="py-2.5 px-4 text-gray-600">{{ a.target }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
