<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Building, ArrowRight } from 'lucide-vue-next'
import { useHotelStore } from '@/stores/hotel'
import { saveConfig as saveConfigApi } from '@/api/hotel'

const router = useRouter()
const store = useHotelStore()

const toast = ref('')

const form = reactive({
  name: store.config.name,
  type: store.config.type,
  city: store.config.city,
  totalRooms: store.config.totalRooms,
  tags: store.config.tags,
  targetAudience: store.config.targetAudience,
  nearby: store.config.nearby,
})

async function handleSave() {
  store.saveConfig({ ...form })
  try { await saveConfigApi({ ...form }) } catch { /* fallback */ }
  toast.value = '已保存'
  setTimeout(() => { toast.value = '' }, 2000)
}

function goRooms() {
  router.push('/rooms')
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

    <!-- Title -->
    <div class="flex items-center gap-3">
      <Building class="w-5 h-5 text-bamboo-700" />
      <h1 class="text-lg font-semibold text-bamboo-900">酒店基础信息</h1>
    </div>

    <!-- Form -->
    <div class="card space-y-5">
      <!-- 2-column grid -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="label">酒店名称</label>
          <input
            v-model="form.name"
            type="text"
            class="input-field"
            placeholder="请输入酒店名称"
          />
        </div>

        <div>
          <label class="label">酒店类型</label>
          <select v-model="form.type" class="input-field">
            <option value="精品民宿">精品民宿</option>
            <option value="度假酒店">度假酒店</option>
            <option value="商务酒店">商务酒店</option>
            <option value="亲子民宿">亲子民宿</option>
          </select>
        </div>

        <div>
          <label class="label">所在城市</label>
          <input
            v-model="form.city"
            type="text"
            class="input-field"
            placeholder="如：浙江·莫干山"
          />
        </div>

        <div>
          <label class="label">客房总数</label>
          <input
            v-model.number="form.totalRooms"
            type="number"
            class="input-field"
            placeholder="请输入客房数量"
            min="0"
          />
        </div>
      </div>

      <!-- Full width fields -->
      <div>
        <label class="label">特色标签</label>
        <input
          v-model="form.tags"
          type="text"
          class="input-field"
          placeholder="用逗号分隔，如：竹林景观、私汤温泉、无边泳池"
        />
      </div>

      <div>
        <label class="label">目标客群</label>
        <textarea
          v-model="form.targetAudience"
          class="input-field"
          rows="3"
          placeholder="描述酒店的目标客群定位"
        />
      </div>

      <div>
        <label class="label">周边信息</label>
        <input
          v-model="form.nearby"
          type="text"
          class="input-field"
          placeholder="如：距莫干山景区5分钟·竹林徒步·茶园采摘"
        />
      </div>
    </div>

    <!-- Buttons -->
    <div class="flex items-center gap-3">
      <button class="btn-primary" @click="handleSave">保存</button>
      <button class="btn-secondary" @click="goRooms">
        下一步：配置房型
        <ArrowRight class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>
