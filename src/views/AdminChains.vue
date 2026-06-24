<script setup lang="ts">
import { Network, Plus } from 'lucide-vue-next'

function showMsg(msg: string) { alert(msg) }

const chains = [
  { id: 1, name: '松间民宿集团', stores: 3, rooms: 36, balance: 960 },
  { id: 2, name: '清风文旅', stores: 2, rooms: 16, balance: 2150 },
]
</script>

<template>
  <div class="p-5 space-y-4">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs font-medium text-gray-300"><Network class="w-4 h-4 text-indigo-400" />连锁/多门店管理</div>
      <button class="px-3 py-1.5 rounded bg-indigo-600 text-white text-[10px] hover:bg-indigo-500 flex items-center gap-1" @click="showMsg('连锁管理即将上线')"><Plus class="w-3 h-3" />新建连锁</button>
    </div>

    <div class="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
      <table class="w-full text-xs">
        <thead>
          <tr class="border-b border-gray-800 text-gray-500 text-left">
            <th class="py-2.5 px-4">连锁名称</th><th class="py-2.5 px-4">门店数</th><th class="py-2.5 px-4">总房间</th><th class="py-2.5 px-4">总算力</th><th class="py-2.5 px-4"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in chains" :key="c.id" class="border-b border-gray-800/50 hover:bg-gray-800/30">
            <td class="py-2.5 px-4 text-gray-200 font-medium">{{ c.name }}</td>
            <td class="py-2.5 px-4 text-gray-300">{{ c.stores }} 家</td>
            <td class="py-2.5 px-4 text-gray-300">{{ c.rooms }}</td>
            <td class="py-2.5 px-4 text-gray-300">{{ c.balance }}</td>
            <td class="py-2.5 px-4"><button class="text-[10px] text-indigo-400 hover:text-indigo-300" @click="showMsg('连锁管理即将上线')">管理门店 →</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
