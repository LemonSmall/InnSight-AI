<script setup lang="ts">
import { computed } from 'vue'
import { Download, Image as ImageIcon } from 'lucide-vue-next'
import AiPlanDocument from '@/components/ai/AiPlanDocument.vue'
import { renderBrainMarkdown } from '@/utils/brainMarkdown'
import { parseOccupancyResultPayload } from '@/utils/occupancyImport'
import { extractArticleDisplayText, extractImageUrl, normalizeImageUrl } from '@/utils/aiContentRender'
import { imageUrl, resultText, type HistoryItem } from '@/utils/generationHistory'

const props = defineProps<{
  item: HistoryItem
}>()

const text = computed(() => resultText(props.item))
const rawText = computed(() => props.item.outputContent || text.value)
const parsed = computed(() => parseJsonLike(rawText.value) || parseJsonLike(text.value))
const assetUrl = computed(() => imageUrl(props.item))
const brainHtml = computed(() => renderBrainMarkdown(text.value))
const occupancyData = computed(() => parseOccupancyResultPayload(rawText.value, 'AI 房态识别结果'))

const xhs = computed(() => {
  const data = parsed.value || {}
  const title = String(data.title || data.titles?.[0] || props.item.title || '小红书图文')
  const body = String(data.body || data.text || data.content || text.value || '')
  const tags = Array.isArray(data.tags) ? data.tags.map(String).filter(Boolean) : []
  return { title, body, tags }
})

const wechatSlots = computed(() => {
  const data = parsed.value
  if (!data) {
    return [{ id: 'copy', label: '朋友圈正文', time: '', content: text.value, imageUrl: assetUrl.value, suggestion: '', schedule: '' }]
  }

  const suggestions = normalizeTextList(data.imageSuggestions || data.image_suggestions || data.imageSuggestion || data.image_suggestion || data.imagePrompts || data.image_prompts)
  const schedules = normalizeTextList(data.publishSchedule || data.publish_schedule || data.schedule || data.schedules)
  const slots = [
    { id: 'morning', label: '早间朋友圈', time: '08:00' },
    { id: 'noon', label: '午间朋友圈', time: '12:00' },
    { id: 'evening', label: '晚间朋友圈', time: '20:30' },
  ]

  return slots
    .map((slot, index) => ({
      ...slot,
      content: slotText(data, slot.id),
      imageUrl: slotImage(data, slot.id, index),
      suggestion: suggestions[index] || (slot.id === 'evening' ? suggestions[0] : '') || '',
      schedule: scheduleForSlot(schedules, slot.id, index),
    }))
    .filter(slot => slot.content.trim())
})

const wechatSuggestions = computed(() => {
  const data = parsed.value
  if (!data) return []
  return normalizeTextList(data.imageSuggestions || data.image_suggestions || data.imageSuggestion || data.image_suggestion || data.imagePrompts || data.image_prompts)
    .map(item => item.replace(/^\d+[:：、.)]\s*/, '').trim())
    .filter(Boolean)
})

const articleText = computed(() => extractArticleDisplayText(rawText.value || text.value))

function parseJsonLike(value: any) {
  const source = typeof value === 'string' ? value.trim() : JSON.stringify(value || '')
  if (!source) return null
  try {
    return JSON.parse(source)
  } catch {
    const match = source.match(/\{[\s\S]*\}/)
    if (!match) return null
    try {
      return JSON.parse(match[0])
    } catch {
      return null
    }
  }
}

function normalizeTextList(value: any) {
  if (Array.isArray(value)) return value.map(item => String(item || '').trim()).filter(Boolean)
  if (typeof value === 'string') {
    return value
      .split(/\n+|(?<=。)\s*|(?<=；)\s*/)
      .map(item => item.trim())
      .filter(Boolean)
  }
  return []
}

function slotText(data: any, key: string) {
  const value = data?.[key]
  if (typeof value === 'string') return value.trim()
  if (value && typeof value === 'object') return String(value.content || value.text || value.body || '').trim()
  return ''
}

function slotImage(data: any, key: string, index: number) {
  const nested = data?.[key]
  const list = data?.imageUrls || data?.image_urls || data?.images || data?.imageList || data?.image_list || []
  const candidate = nested?.imageUrl
    || nested?.image_url
    || nested?.image
    || nested?.url
    || (Array.isArray(list) ? list[index] : list?.[key])
    || assetUrl.value
    || extractImageUrl(JSON.stringify(nested || ''))
    || extractImageUrl(JSON.stringify(data || ''))
  return normalizeImageUrl(candidate || '')
}

function scheduleForSlot(list: string[], key: string, index: number) {
  const patterns: Record<string, RegExp> = {
    morning: /morning|早间|早上|上午|10:00|08:00/i,
    noon: /noon|午间|中午|12:00/i,
    evening: /evening|晚间|晚上|20:30|20:00/i,
  }
  return list.find(item => patterns[key]?.test(item)) || list[index] || ''
}
</script>

<template>
  <div class="generation-preview">
    <section v-if="item.moduleKey === 'poster'" class="poster-preview">
      <div v-if="assetUrl" class="poster-frame">
        <img :src="assetUrl" alt="营销海报" />
        <a :href="assetUrl" target="_blank" download class="download-button" title="下载图片">
          <Download class="h-4 w-4" />
        </a>
      </div>
      <p v-if="text" class="plain-card">{{ text }}</p>
    </section>

    <section v-else-if="item.moduleKey === 'xhs'" class="xhs-preview">
      <div class="xhs-phone">
        <div class="xhs-cover">
          <img v-if="assetUrl" :src="assetUrl" alt="小红书配图" />
          <div v-else class="empty-media">
            <ImageIcon class="h-8 w-8" />
            <span>小红书封面预览</span>
          </div>
        </div>
        <div class="p-5">
          <h2 class="text-lg font-bold leading-7 text-bamboo-950">{{ xhs.title }}</h2>
          <p class="mt-4 whitespace-pre-line text-sm leading-7 text-warm-700">{{ xhs.body }}</p>
          <div v-if="xhs.tags.length" class="mt-4 flex flex-wrap gap-2">
            <span v-for="tag in xhs.tags" :key="tag" class="rounded-full bg-rose-50 px-2.5 py-1 text-xs text-rose-600">#{{ tag }}</span>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="item.moduleKey === 'wechat'" class="wechat-history">
      <article v-for="slot in wechatSlots" :key="slot.id" class="wechat-card">
        <div class="wechat-head">
          <div>
            <span>{{ slot.label }}</span>
            <strong>{{ slot.time }}</strong>
          </div>
          <small v-if="slot.schedule">{{ slot.schedule }}</small>
        </div>
        <div class="wechat-body">
          <div class="wechat-copy whitespace-pre-line">{{ slot.content }}</div>
          <div class="wechat-side">
            <div class="wechat-image">
              <img v-if="slot.imageUrl" :src="slot.imageUrl" alt="朋友圈配图" />
              <div v-else class="empty-media">
                <ImageIcon class="h-7 w-7" />
                <span>暂无配图</span>
              </div>
            </div>
            <a v-if="slot.imageUrl" :href="slot.imageUrl" target="_blank" download class="wechat-download">
              <Download class="h-3.5 w-3.5" />
              下载配图
            </a>
          </div>
        </div>
        <p v-if="slot.suggestion" class="wechat-tip"><b>配图建议：</b>{{ slot.suggestion.replace(/^\d+[:：、.)]\s*/, '') }}</p>
      </article>
      <section v-if="wechatSuggestions.length" class="suggestion-panel">
        <h3>全部配图建议</h3>
        <ol>
          <li v-for="item in wechatSuggestions" :key="item">{{ item }}</li>
        </ol>
      </section>
    </section>

    <section v-else-if="item.moduleKey === 'pricing' || item.moduleKey === 'strategy'" class="plan-preview">
      <AiPlanDocument :content="text" />
    </section>

    <section v-else-if="item.moduleKey === 'brain'" class="plain-card brain-history-preview">
      <div v-html="brainHtml" />
    </section>

    <section v-else-if="item.moduleKey === 'occupancy_image' && occupancyData" class="occupancy-preview">
      <div class="summary-grid">
        <div><span>数据周期</span><strong>{{ occupancyData.dateRange || '-' }}</strong></div>
        <div><span>平均出租率</span><strong>{{ Math.round(occupancyData.averageOccupancyRate * 100) }}%</strong></div>
        <div><span>占用房晚</span><strong>{{ occupancyData.occupiedRoomNights }} / {{ occupancyData.totalRoomNights }}</strong></div>
        <div><span>识别记录</span><strong>{{ occupancyData.records.length }}</strong></div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>日期</th>
              <th>房型</th>
              <th>总房数</th>
              <th>占用</th>
              <th>剩余可售</th>
              <th>出租率</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in occupancyData.records" :key="`${record.date}-${record.roomTypeName}`">
              <td>{{ record.date }}</td>
              <td>{{ record.roomTypeName }}</td>
              <td>{{ record.totalRooms }}</td>
              <td>{{ record.occupiedRooms }}</td>
              <td>{{ record.remainingRooms }}</td>
              <td>{{ Math.round(record.occupancyRate * 100) }}%</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-else-if="item.moduleKey === 'article'" class="plain-card">
      <p class="whitespace-pre-wrap">{{ articleText }}</p>
    </section>

    <section v-else class="plain-card">
      <p class="whitespace-pre-wrap">{{ text }}</p>
    </section>
  </div>
</template>

<style scoped>
.generation-preview {
  min-width: 0;
}

.plain-card,
.occupancy-preview,
.plan-preview {
  border: 1px solid #eadfce;
  border-radius: 1rem;
  background: #fff;
  padding: 1.25rem;
  color: #173826;
  font-size: 0.9rem;
  line-height: 1.8;
}

.poster-frame {
  position: relative;
  margin: 0 auto;
  max-width: 640px;
  overflow: hidden;
  border: 1px solid #eadfce;
  border-radius: 1rem;
  background: #fff;
}

.poster-frame img {
  display: block;
  max-height: 760px;
  width: 100%;
  object-fit: contain;
}

.download-button,
.wechat-download {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  border-radius: 999px;
  background: #234d32;
  color: #fff8e8;
  font-size: 0.75rem;
  font-weight: 700;
}

.download-button {
  position: absolute;
  right: 0.75rem;
  top: 0.75rem;
  height: 2.25rem;
  width: 2.25rem;
}

.xhs-preview {
  display: flex;
  justify-content: center;
}

.xhs-phone {
  width: min(100%, 680px);
  overflow: hidden;
  border: 1px solid #f1d6d9;
  border-radius: 1.25rem;
  background: white;
}

.xhs-cover {
  aspect-ratio: 3 / 4;
  background: #fff1f2;
}

.xhs-cover img,
.wechat-image img {
  height: 100%;
  width: 100%;
  object-fit: cover;
}

.wechat-history {
  display: grid;
  gap: 1rem;
}

.wechat-card {
  border: 1px solid #eadfce;
  border-radius: 1rem;
  background: #fffdfa;
  padding: 1rem;
}

.wechat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid #f0e5d4;
  padding-bottom: 0.75rem;
}

.wechat-head span {
  display: inline-flex;
  border-radius: 999px;
  background: #eef7ea;
  padding: 0.25rem 0.65rem;
  color: #234d32;
  font-size: 0.75rem;
  font-weight: 800;
}

.wechat-head strong {
  margin-left: 0.5rem;
  color: #b3741f;
  font-size: 0.82rem;
}

.wechat-head small {
  max-width: 48%;
  color: #9a7b55;
  font-size: 0.72rem;
  line-height: 1.6;
}

.wechat-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 1rem;
  padding-top: 0.9rem;
}

.wechat-copy {
  min-height: 10rem;
  border-radius: 0.85rem;
  background: #fff;
  padding: 0.95rem;
  color: #173826;
  font-size: 0.9rem;
  line-height: 1.75;
}

.wechat-side {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.wechat-image {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border: 1px solid #eadfce;
  border-radius: 0.85rem;
  background: #faf7f1;
}

.wechat-download {
  height: 2rem;
}

.wechat-tip,
.suggestion-panel {
  margin-top: 0.75rem;
  border-radius: 0.8rem;
  background: #f7faf4;
  padding: 0.75rem 0.85rem;
  color: #5f5143;
  font-size: 0.78rem;
  line-height: 1.7;
}

.suggestion-panel h3 {
  color: #173826;
  font-size: 0.88rem;
  font-weight: 800;
}

.suggestion-panel ol {
  margin-top: 0.5rem;
  padding-left: 1.25rem;
}

.suggestion-panel li + li {
  margin-top: 0.35rem;
}

.empty-media {
  display: flex;
  height: 100%;
  width: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  color: #a9927a;
  font-size: 0.78rem;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
}

.summary-grid div {
  border-radius: 0.85rem;
  background: #f7faf4;
  padding: 0.8rem;
}

.summary-grid span {
  display: block;
  color: #8b7460;
  font-size: 0.72rem;
}

.summary-grid strong {
  display: block;
  margin-top: 0.3rem;
  color: #173826;
}

.table-wrap {
  margin-top: 1rem;
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  font-size: 0.85rem;
}

th,
td {
  border-bottom: 1px solid #f0e5d4;
  padding: 0.7rem 0.6rem;
  text-align: left;
}

th {
  color: #8b7460;
  font-size: 0.75rem;
}

:deep(.brain-markdown) {
  color: #173826;
  font-size: 0.95rem;
  line-height: 1.8;
}

@media (max-width: 900px) {
  .wechat-body {
    grid-template-columns: 1fr;
  }

  .wechat-side {
    max-width: 240px;
  }

  .wechat-head {
    flex-direction: column;
  }

  .wechat-head small {
    max-width: none;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
