<script setup lang="ts">
import { computed, onMounted, ref, reactive, watch } from 'vue'
import { Home, Loader2, Percent, Plus, Save, Trash2, WalletCards } from 'lucide-vue-next'
import { useHotelStore, type RoomType } from '@/stores/hotel'
import { safeUiText } from '@/utils/uiText'

const store = useHotelStore()

const toast = ref('')
const saving = ref(false)
const addDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const pendingDeleteRoomId = ref('')
const pendingDeleteRoomName = ref('')
const addRoomForm = reactive({
  name: '',
  basePrice: 0,
  count: 0,
})

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

function syncRoomsFromStore() {
  editList.splice(0, editList.length, ...store.roomTypes.map(roomToEditable))
}

onMounted(async () => {
  await store.loadFromApi().catch(() => {})
  syncRoomsFromStore()
})

watch(
  () => store.roomTypes,
  () => syncRoomsFromStore(),
  { deep: true }
)

const occupancySummary = computed(() => store.occupancyImport)
const roomOccupancyMap = computed(() => new Map(
  (occupancySummary.value?.roomTypeSummaries || []).map(room => [room.roomTypeName.trim(), room])
))

function roomOccupancyRate(roomName: string) {
  const summary = roomOccupancyMap.value.get(roomName.trim())
  return summary ? `${Math.round(summary.averageOccupancyRate * 100)}%` : '待导入'
}

function stripTrailingParentheses(value: string) {
  let text = value.trim()
  let next = text.replace(/\s*[（(][^（）()]*[）)]\s*$/u, '').trim()
  while (next && next !== text) {
    text = next
    next = text.replace(/\s*[（(][^（）()]*[）)]\s*$/u, '').trim()
  }
  return text
}

function openAddRoomDialog() {
  addRoomForm.name = ''
  addRoomForm.basePrice = 0
  addRoomForm.count = 0
  addDialogOpen.value = true
}

function closeAddRoomDialog() {
  addDialogOpen.value = false
}

function openDeleteRoomDialog(room: EditableRoom) {
  pendingDeleteRoomId.value = room.id
  pendingDeleteRoomName.value = room.name
  deleteDialogOpen.value = true
}

function closeDeleteRoomDialog() {
  deleteDialogOpen.value = false
  pendingDeleteRoomId.value = ''
  pendingDeleteRoomName.value = ''
}

function flash(message: string) {
  toast.value = safeUiText(message, '操作失败，请稍后重试')
  window.setTimeout(() => { toast.value = '' }, 2000)
}

async function persistRooms(successMessage: string, rollback?: EditableRoom[]) {
  const payload: RoomType[] = editList.map(r => ({
    id: r.id,
    name: r.name,
    basePrice: r.basePrice,
    count: r.count,
  }))
  saving.value = true
  try {
    await store.saveRoomTypes(payload)
    flash(successMessage)
  } catch {
    if (rollback) {
      editList.splice(0, editList.length, ...rollback)
    }
    flash(safeUiText(store.error, '房型保存失败，请稍后重试'))
    throw new Error(store.error || '房型保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function confirmAddRoom() {
  const name = stripTrailingParentheses(addRoomForm.name)
  if (!name) {
    flash('请先填写房型名称')
    return
  }
  const rollback = editList.map(item => ({ ...item }))
  editList.push({
    id: genId(),
    name,
    basePrice: Number(addRoomForm.basePrice || 0),
    count: Number(addRoomForm.count || 0),
    isNew: true,
  })
  closeAddRoomDialog()
  try {
    await persistRooms('房型已添加并保存', rollback)
  } catch {
    // rollback handled in persistRooms
  }
}

async function confirmDeleteRoom() {
  const id = pendingDeleteRoomId.value
  const name = pendingDeleteRoomName.value
  closeDeleteRoomDialog()
  if (!id) return
  const rollback = editList.map(item => ({ ...item }))
  const idx = editList.findIndex(r => r.id === id)
  if (idx !== -1) {
    editList.splice(idx, 1)
  }
  saving.value = true
  try {
    await store.removeRoomType(id)
    syncRoomsFromStore()
    flash(`已删除房型「${name || '未命名房型'}」`)
  } catch {
    editList.splice(0, editList.length, ...rollback)
    flash(safeUiText(store.error, '房型删除失败，请稍后重试'))
  } finally {
    saving.value = false
  }
}

async function saveRooms() {
  try {
    await persistRooms('房型已保存')
  } catch {
    // handled in persistRooms
  }
}
</script>

<template>
  <div class="rooms-page mx-auto max-w-[1500px] space-y-4 pb-4">
    <!-- Toast -->
    <div
      v-if="toast"
      class="fixed top-6 right-6 z-50 bg-bamboo-800 text-cream-100 px-5 py-3 rounded-lg shadow-lg text-sm transition-all duration-300"
    >
      {{ toast }}
    </div>

    <div v-if="addDialogOpen" class="fixed inset-0 z-40 flex items-center justify-center bg-bamboo-950/35 px-4">
      <div class="w-full max-w-md rounded-2xl border border-cream-200 bg-white p-5 shadow-2xl">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-base font-semibold text-bamboo-950">添加房型</h2>
            <p class="mt-1 text-xs text-warm-500">填写后点击确认，房型才会加入列表。</p>
          </div>
          <button class="rounded-lg px-2 py-1 text-sm text-warm-500 hover:bg-cream-100" @click="closeAddRoomDialog">×</button>
        </div>

        <div class="mt-5 space-y-4">
          <div>
            <label class="mb-1 block text-xs font-medium text-warm-500">房型名称</label>
            <input
              v-model="addRoomForm.name"
              type="text"
              class="input-field bg-white"
              placeholder="如：山景大床房"
              @keydown.enter="confirmAddRoom"
            />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="mb-1 block text-xs font-medium text-warm-500">基础房价</label>
              <input v-model.number="addRoomForm.basePrice" type="number" class="input-field bg-white" min="0" />
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-warm-500">房量</label>
              <input v-model.number="addRoomForm.count" type="number" class="input-field bg-white" min="0" />
            </div>
          </div>
        </div>

        <div class="mt-5 flex justify-end gap-3">
          <button class="btn-secondary" @click="closeAddRoomDialog">取消</button>
          <button class="btn-primary" @click="confirmAddRoom">
            <Plus class="w-4 h-4" />
            确认添加
          </button>
        </div>
      </div>
    </div>

    <div v-if="deleteDialogOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-bamboo-950/45 px-4">
      <div class="w-full max-w-sm rounded-2xl border border-rose-200 bg-white p-5 shadow-2xl">
        <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
          <Trash2 class="h-4 w-4 text-rose-500" />
          删除房型
        </div>
        <div class="mt-3 rounded-xl border border-rose-100 bg-rose-50 px-3 py-2 text-sm font-semibold text-bamboo-900">
          {{ pendingDeleteRoomName || '未命名房型' }}
        </div>
        <p class="mt-3 text-sm leading-6 text-warm-600">
          删除后会立即保存到后台，刷新页面也不会恢复。此操作不可撤销。
        </p>
        <div class="mt-5 flex justify-end gap-3">
          <button class="btn-secondary" @click="closeDeleteRoomDialog">取消</button>
          <button class="btn-danger" :disabled="saving" @click="confirmDeleteRoom">
            <Loader2 v-if="saving" class="h-4 w-4 animate-spin" />
            <Trash2 v-else class="h-4 w-4" />
            确认删除
          </button>
        </div>
      </div>
    </div>

    <section class="overflow-hidden rounded-3xl border border-cream-300 bg-white shadow-sm">
      <div class="grid lg:grid-cols-[1fr_360px]">
        <div class="p-4 lg:p-5">
          <div class="flex items-center gap-2 text-xs font-semibold text-bamboo-700">
            <Home class="h-4 w-4" />
            房型与定价          </div>
          <h1 class="mt-1 text-lg font-semibold text-bamboo-950 lg:text-xl">把房型、价格和房量整理成 AI 能读懂的数据</h1>
          <p class="mt-1.5 max-w-3xl text-xs leading-5 text-warm-600">
            房型名称、参考价格和卖点会用于智能定价与内容生成。配置越完整，AI 给出的建议越贴近本店。          </p>
        </div>
        <div class="border-t border-cream-200 bg-bamboo-950 p-4 text-bamboo-50 lg:border-l lg:border-t-0 lg:p-5">
          <div class="grid grid-cols-2 gap-3">
            <div class="rounded-2xl bg-white/8 p-3">
              <div class="text-xs text-bamboo-100/60">房型数量</div>
              <div class="mt-1 text-2xl font-bold">{{ editList.length }}</div>
            </div>
            <div class="rounded-2xl bg-white/8 p-3">
              <div class="text-xs text-bamboo-100/60">总房量</div>
              <div class="mt-1 text-2xl font-bold">{{ editList.reduce((sum, item) => sum + Number(item.count || 0), 0) }}</div>
            </div>
          </div>
          <button class="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-bamboo-100 px-3 py-2 text-xs font-semibold text-bamboo-950 hover:bg-white" @click="openAddRoomDialog">
            <Plus class="h-4 w-4" />
            添加一个房型          </button>
        </div>
      </div>
    </section>

    <section class="grid items-stretch gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
      <div class="flex flex-col rounded-3xl border border-cream-300 bg-white shadow-sm">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-cream-200 p-4">
          <div>
            <h2 class="text-sm font-semibold text-bamboo-950">房型列表</h2>
            <p class="mt-1 text-xs text-warm-500">建议按实际对外销售的房型维护，价格作为 AI 分析时的参考基准。</p>
          </div>
          <div class="flex flex-wrap gap-2">
            <button class="btn-primary" :disabled="saving" @click="saveRooms">
              <Save class="w-4 h-4" />
              {{ saving ? '保存中...' : '保存配置' }}
            </button>
            <button class="btn-secondary" @click="openAddRoomDialog">
              <Plus class="w-4 h-4" />
              添加房型
            </button>
          </div>
        </div>

        <div v-if="editList.length === 0" class="px-5 py-16 text-center text-warm-600 text-sm">
          暂无房型，点击“添加房型”开始配置
        </div>

        <div v-else class="flex-1 overflow-x-auto p-4">
          <table class="rooms-edit-table w-full min-w-[900px] border-separate border-spacing-y-2 text-left">
            <thead>
              <tr class="text-xs font-semibold text-warm-500">
                <th class="px-3 pb-1">房型名称</th>
                <th class="w-36 px-3 pb-1">基础房价</th>
                <th class="w-28 px-3 pb-1">房量</th>
                <th class="w-32 px-3 pb-1">历史出租率</th>
                <th class="w-16 px-3 pb-1 text-center">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(room, index) in editList" :key="room.id" class="group">
                <td class="rounded-l-2xl border-y border-l border-cream-200 bg-cream-50 px-3.5 py-2.5 transition group-hover:border-bamboo-300 group-hover:bg-white">
                  <div class="flex items-center gap-3">
                    <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border border-bamboo-100 bg-bamboo-50 text-sm font-bold text-bamboo-800">
                      {{ index + 1 }}
                    </div>
                    <div class="min-w-0 flex-1">
                      <input v-model="room.name" type="text" class="input-field rooms-compact-input bg-white font-semibold text-bamboo-950" placeholder="如：山景大床房" />
                      <div class="mt-1.5 flex flex-wrap gap-1.5 text-[11px] text-warm-500">
                        <span class="rounded-full bg-white px-2 py-0.5">房量 {{ room.count || 0 }} 间</span>
                        <span class="rounded-full bg-white px-2 py-0.5">出租率 {{ roomOccupancyRate(room.name) }}</span>
                      </div>
                    </div>
                  </div>
                </td>
                <td class="border-y border-cream-200 bg-cream-50 px-3 py-2.5 transition group-hover:border-bamboo-300 group-hover:bg-white">
                  <div class="rounded-2xl border border-cream-200 bg-white p-1.5">
                    <div class="mb-1 text-[10px] font-semibold text-warm-400">参考价</div>
                    <input v-model.number="room.basePrice" type="number" class="input-field rooms-compact-input border-0 bg-transparent px-0 font-semibold text-bamboo-950 shadow-none" placeholder="价格" min="0" />
                  </div>
                </td>
                <td class="border-y border-cream-200 bg-cream-50 px-3 py-2.5 transition group-hover:border-bamboo-300 group-hover:bg-white">
                  <div class="rounded-2xl border border-cream-200 bg-white p-1.5">
                    <div class="mb-1 text-[10px] font-semibold text-warm-400">间数</div>
                    <input v-model.number="room.count" type="number" class="input-field rooms-compact-input border-0 bg-transparent px-0 font-semibold text-bamboo-950 shadow-none" placeholder="数量" min="0" />
                  </div>
                </td>
                <td class="border-y border-cream-200 bg-cream-50 px-3 py-2.5 transition group-hover:border-bamboo-300 group-hover:bg-white">
                  <div class="rounded-2xl border border-bamboo-100 bg-white p-2">
                    <div class="text-base font-bold text-bamboo-900">{{ roomOccupancyRate(room.name) }}</div>
                    <div class="mt-0.5 text-[10px] text-warm-400">历史房态</div>
                  </div>
                </td>
                <td class="rounded-r-2xl border-y border-r border-cream-200 bg-cream-50 px-3 py-2.5 text-center transition group-hover:border-bamboo-300 group-hover:bg-white">
                  <button class="inline-flex h-10 w-10 items-center justify-center rounded-2xl border border-rose-200 bg-white text-rose-500 shadow-sm hover:bg-rose-50" @click="openDeleteRoomDialog(room)">
                    <Trash2 class="h-4 w-4" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside class="flex h-full flex-col gap-3">
        <div v-if="occupancySummary" class="rounded-3xl border border-bamboo-200 bg-bamboo-50 p-4">
          <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
            <Percent class="h-4 w-4" />
            历史出租率          </div>
          <div class="mt-3 grid grid-cols-2 gap-2">
            <div class="rounded-2xl bg-white p-3">
              <div class="text-[11px] text-warm-500">平均出租率</div>
              <div class="mt-1 text-xl font-semibold text-bamboo-950">{{ Math.round(occupancySummary.averageOccupancyRate * 100) }}%</div>
            </div>
            <div class="rounded-2xl bg-white p-3">
              <div class="text-[11px] text-warm-500">数据周期</div>
              <div class="mt-1 text-sm font-semibold text-bamboo-950">{{ occupancySummary.dateRange }}</div>
            </div>
          </div>
          <p class="mt-3 text-xs leading-5 text-warm-600">{{ store.occupancySummaryText }}</p>
        </div>

        <div class="flex flex-1 flex-col rounded-3xl border border-cream-300 bg-white p-4 shadow-sm">
          <h2 class="text-sm font-semibold text-bamboo-950">价格结构预览</h2>
          <div class="mt-3 min-h-0 flex-1 space-y-2 overflow-y-auto pr-1">
            <div v-for="room in editList" :key="room.id" class="rounded-2xl bg-cream-50 p-3">
              <div class="flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <div class="truncate text-sm font-semibold text-bamboo-950">{{ room.name || '未命名房型' }}</div>
                  <div class="mt-0.5 text-xs text-warm-500">已填 {{ room.count || 0 }} 间，作为酒店基础资料</div>
                </div>
                <div class="text-right">
                  <div class="font-mono text-sm font-semibold text-bamboo-800">¥{{ room.basePrice || 0 }}</div>
                  <div class="text-[10px] text-warm-400">基础价</div>
                </div>
              </div>
            </div>
            <div v-if="editList.length === 0" class="rounded-2xl border border-dashed border-cream-300 bg-cream-50 p-5 text-center text-xs text-warm-500">
              添加房型后这里会显示价格结构
            </div>
          </div>
        </div>

        <div class="rounded-3xl border border-bamboo-200 bg-bamboo-50 p-4">
          <div class="flex items-center gap-2 text-sm font-semibold text-bamboo-950">
            <WalletCards class="h-4 w-4" />
            保存后会影响
          </div>
          <p class="mt-2 text-xs leading-5 text-warm-600">智能定价、营销文案里的房型卖点，都会基于这份房型资料生成。</p>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.rooms-page .rooms-compact-input {
  min-height: 38px;
  padding-top: 0.42rem;
  padding-bottom: 0.42rem;
}

.rooms-edit-table th {
  white-space: nowrap;
}

.rooms-page .rooms-compact-input.border-0 {
  min-height: 30px;
  box-shadow: none;
}

.btn-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border-radius: 0.75rem;
  background: #b91c1c;
  padding: 0.5rem 1rem;
  color: #fff;
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.25rem;
  transition: background-color 150ms ease, transform 150ms ease, opacity 150ms ease;
}

.btn-danger:hover {
  background: #991b1b;
}

.btn-danger:active {
  transform: scale(0.98);
}

.btn-danger:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>

