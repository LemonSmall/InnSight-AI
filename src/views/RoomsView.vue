<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Home, Plus, Trash2, Save } from 'lucide-vue-next'
import { useHotelStore, type RoomType } from '@/stores/hotel'
import { saveRoomTypes as saveRoomTypesApi } from '@/api/hotel'

const store = useHotelStore()

const toast = ref('')

function genId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7)
}

interface EditableRoom {
  id: string
  name: string
  basePrice: number
  count: number
  isNew: boolean
}

function roomToEditable(r: RoomType): EditableRoom {
  return { ...r, isNew: false }
}

const editList = reactive<EditableRoom[]>(
  store.roomTypes.map(roomToEditable)
)

function addRoom() {
  editList.push({
    id: genId(),
    name: '',
    basePrice: 0,
    count: 0,
    isNew: true,
  })
}

function removeRoom(id: string) {
  const idx = editList.findIndex(r => r.id === id)
  if (idx !== -1) {
    editList.splice(idx, 1)
  }
}

async function saveRooms() {
  const payload: RoomType[] = editList.map(r => ({
    id: r.id,
    name: r.name,
    basePrice: r.basePrice,
    count: r.count,
  }))
  store.saveRoomTypes(payload)
  try { await saveRoomTypesApi(payload as any) } catch { /* fallback */ }
  toast.value = '房型已保存'
  setTimeout(() => { toast.value = '' }, 2000)
}
</script>

<template>
  <div class="max-w-4xl mx-auto p-6 space-y-6">
    <!-- Toast -->
    <div
      v-if="toast"
      class="fixed top-6 right-6 z-50 bg-bamboo-800 text-cream-100 px-5 py-3 rounded-lg shadow-lg text-sm transition-all duration-300"
    >
      {{ toast }}
    </div>

    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <Home class="w-5 h-5 text-bamboo-700" />
        <h1 class="text-lg font-semibold text-bamboo-900">房型管理</h1>
        <span class="badge badge-green">{{ editList.length }} 种房型</span>
      </div>
      <button class="btn-primary" @click="addRoom">
        <Plus class="w-4 h-4" />
        添加房型
      </button>
    </div>

    <!-- Table -->
    <div class="card overflow-hidden !p-0">
      <!-- Table header -->
      <div
        class="grid gap-4 px-5 py-3 bg-cream-50 border-b border-cream-200/60 text-xs font-medium text-warm-600 uppercase tracking-wide"
        :style="{ gridTemplateColumns: '2fr 1fr 1fr 80px' }"
      >
        <span>房型名称</span>
        <span>基础房价(元)</span>
        <span>数量</span>
        <span>操作</span>
      </div>

      <!-- Rows -->
      <div v-if="editList.length === 0" class="px-5 py-12 text-center text-warm-600 text-sm">
        暂无房型，点击"添加房型"开始配置
      </div>

      <div
        v-for="(room, idx) in editList"
        :key="room.id"
        class="grid gap-4 px-5 py-3 border-b border-cream-100 items-center"
        :style="{ gridTemplateColumns: '2fr 1fr 1fr 80px' }"
      >
        <input
          v-model="room.name"
          type="text"
          class="input-field"
          placeholder="房型名称"
        />
        <input
          v-model.number="room.basePrice"
          type="number"
          class="input-field"
          placeholder="价格"
          min="0"
        />
        <input
          v-model.number="room.count"
          type="number"
          class="input-field"
          placeholder="数量"
          min="0"
        />
        <button
          class="btn-ghost text-rose-400 hover:text-rose-500 hover:bg-rose-50"
          @click="removeRoom(room.id)"
        >
          <Trash2 class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- Save -->
    <div class="flex items-center gap-3">
      <button class="btn-primary" @click="saveRooms">
        <Save class="w-4 h-4" />
        保存房型
      </button>
    </div>
  </div>
</template>
