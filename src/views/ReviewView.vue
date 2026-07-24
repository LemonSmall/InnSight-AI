<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Copy, FileText, Gift, MessageCircleHeart, Sparkles } from 'lucide-vue-next'
import { collectStreamContent } from '@/api/content'
import { loadAiPageState, saveAiPageState } from '@/utils/aiPageState'
import { copyTextToClipboard } from '@/utils/clipboard'
import { useHotelStore } from '@/stores/hotel'
import { buildContentAiParams } from '@/utils/aiContextParams'

type GuestType = 'couple' | 'family' | 'biz'

const hotel = useHotelStore()
const router = useRouter()
const pageStateKey = 'review'

const selectedType = ref<GuestType | null>(null)
const selectedIncentive = ref<number | null>(null)
const copied = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const reviews = ref<Record<string, string>>({})

const guestTypes: Array<{ type: GuestType; label: string; hint: string }> = [
  { type: 'couple', label: '情侣客群', hint: '适合浪漫度假、纪念日入住后的温和邀请' },
  { type: 'family', label: '亲子家庭', hint: '突出孩子体验、服务细节和安心感受' },
  { type: 'biz', label: '商务出行', hint: '强调效率、安静、服务稳定和复住体验' },
]

const incentives = [
  {
    title: '离店感谢 + 真实评价邀请',
    desc: '感谢本次入住，并邀请客人如实评价我们酒店的环境、服务、房间和建议，不和好评内容直接挂钩。',
    badge: '优先推荐',
    badgeClass: 'badge-green',
  },
  {
    title: '前台口头邀请 + 评价卡片',
    desc: '退房时由前台自然提醒，并把评价入口放在房卡套、台卡或小卡片上，降低客人操作成本。',
    badge: '轻触达',
    badgeClass: 'badge-amber',
  },
  {
    title: '服务回访 + 问题闭环',
    desc: '离店后做一次简短回访，先确认入住体验是否顺畅，再邀请客人留下真实反馈。',
    badge: '提升体验',
    badgeClass: 'badge-rose',
  },
  {
    title: '老客关怀 + 复住礼遇',
    desc: '对所有离店客人统一提供复住礼遇或会员权益提示，适合沉淀老客，不以评价好坏为条件。',
    badge: '促进复住',
    badgeClass: 'badge-purple',
  },
  {
    title: '短信 / 企微评价提醒',
    desc: '离店后自动发送感谢短信或企微消息，附上评价入口和酒店改进承诺，适合标准化执行。',
    badge: '自动化',
    badgeClass: 'badge-slate',
  },
  {
    title: '会员积分 / 小礼遇',
    desc: '把积分、咖啡券、延迟退房等小礼遇放入会员体系统一发放，避免和好评结果直接绑定。',
    badge: '长期运营',
    badgeClass: 'badge-bamboo',
  },
]

const currentReview = computed(() => {
  return selectedType.value ? reviews.value[selectedType.value] || '' : ''
})

function persistState() {
  saveAiPageState(pageStateKey, {
    selectedType: selectedType.value,
    selectedIncentive: selectedIncentive.value,
    reviews: reviews.value,
  })
}

function restoreState() {
  const restored = loadAiPageState<any>(pageStateKey)
  if (!restored) return
  selectedType.value = restored.selectedType || null
  selectedIncentive.value = restored.selectedIncentive ?? null
  reviews.value = restored.reviews && typeof restored.reviews === 'object' ? restored.reviews : {}
}

function selectType(type: GuestType) {
  selectedType.value = type
  copied.value = false
  errorMessage.value = ''
  persistState()
}

async function generateReview() {
  const type = selectedType.value
  if (!type || loading.value) return

  loading.value = true
  copied.value = false
  errorMessage.value = ''
  reviews.value[type] = ''
  persistState()

  try {
    reviews.value[type] = await collectStreamContent('review', buildContentAiParams(hotel, 'review', {
      guestType: type,
      theme: `${type}客群评价邀请`,
      message: `生成${type}客群的离店感谢与邀请客人评价我们酒店的话术，要求真诚、合规、不用利益诱导好评`,
      outputFormat: 'text',
    }), {
      onChunk(_chunk, content) {
        reviews.value[type] = content
        persistState()
      },
    })

    persistState()
    if (!reviews.value[type]) throw new Error('AI 调用失败')
  } catch {
    reviews.value[type] = ''
    errorMessage.value = 'AI 调用失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function copyReview() {
  if (!currentReview.value) return
  try {
    const ok = await copyTextToClipboard(currentReview.value)
    if (!ok) throw new Error('copy failed')
    copied.value = true
    window.setTimeout(() => { copied.value = false }, 2000)
  } catch {
    errorMessage.value = '复制失败，请稍后重试'
  }
}

function selectIncentive(index: number) {
  selectedIncentive.value = selectedIncentive.value === index ? null : index
  persistState()
}

onMounted(() => {
  restoreState()
})
</script>

<template>
  <div class="review-layout">
    <section class="card">
      <div class="card-head">
        <div class="title-wrap">
          <MessageCircleHeart class="h-5 w-5 text-bamboo-700" />
          <div>
            <h2>好评引导</h2>
            <p>给客人发送评价邀请，让客人评价我们酒店的真实入住体验。</p>
          </div>
        </div>
        <button
          class="mini-action"
          @click="router.push('/history/review')"
        >
          <FileText class="h-3 w-3" />生成记录
        </button>
      </div>

      <div class="nfc-panel">
        <div class="nfc-icon">
          <MessageCircleHeart class="h-7 w-7 text-cream-100" />
        </div>
        <div class="nfc-copy">
          <strong>碰一碰评价邀请</strong>
          <span>NFC 贴片可放在前台、房卡套或离店台卡上，方便客人给酒店评价。</span>
        </div>
      </div>

      <div class="section-title">选择客群</div>
      <div class="guest-grid">
        <button
          v-for="item in guestTypes"
          :key="item.type"
          :class="['guest-card', selectedType === item.type ? 'guest-card-active' : '']"
          @click="selectType(item.type)"
        >
          <strong>{{ item.label }}</strong>
          <span>{{ item.hint }}</span>
        </button>
      </div>

      <div class="action-row">
        <button
          class="primary-button"
          :disabled="!selectedType || loading"
          @click="generateReview"
        >
          <Sparkles class="h-4 w-4" />
          {{ loading ? 'AI生成中...' : 'AI生成评价邀请' }}
        </button>
      </div>

      <div v-if="selectedType" class="result-panel">
        <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
        <div class="result-copy">{{ currentReview || '生成结果会显示在这里。' }}</div>
        <button class="ghost-button" :disabled="!currentReview" @click="copyReview">
          <Copy class="h-3.5 w-3.5" />
          {{ copied ? '已复制' : '复制给客人' }}
        </button>
      </div>
    </section>

    <section class="card">
      <div class="card-head">
        <div class="title-wrap">
          <Gift class="h-5 w-5 text-bamboo-700" />
          <div>
            <h2>激励方案</h2>
            <p>礼遇和服务回访要分开设计，避免和评价内容、星级结果直接绑定。</p>
          </div>
        </div>
      </div>

      <div class="incentive-list">
        <button
          v-for="(item, index) in incentives"
          :key="item.title"
          :class="['incentive-card', selectedIncentive === index ? 'incentive-card-active' : '']"
          @click="selectIncentive(index)"
        >
          <div class="incentive-head">
            <strong>{{ item.title }}</strong>
            <span :class="['badge', item.badgeClass]">{{ item.badge }}</span>
          </div>
          <p>{{ item.desc }}</p>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.review-layout {
  display: grid;
  gap: 1.25rem;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
}

.card {
  border-radius: 1rem;
  border: 1px solid #e7dccb;
  background: #fff;
  padding: 1.25rem;
  box-shadow: 0 10px 24px rgb(64 49 35 / 5%);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.title-wrap {
  display: flex;
  gap: 0.75rem;
}

.title-wrap h2 {
  color: #1d3c29;
  font-size: 1rem;
  font-weight: 700;
}

.title-wrap p {
  margin-top: 0.2rem;
  color: #8b7460;
  font-size: 0.75rem;
  line-height: 1.6;
}

.mini-action,
.primary-button,
.ghost-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  border-radius: 0.7rem;
  transition: 150ms ease;
}

.mini-action {
  flex-shrink: 0;
  border: 1px solid #e7dccb;
  background: #fff;
  padding: 0.45rem 0.7rem;
  color: #7b6654;
  font-size: 0.7rem;
  font-weight: 600;
}

.primary-button {
  border: 0;
  background: #234d32;
  padding: 0.7rem 1rem;
  color: #fff7e7;
  font-size: 0.82rem;
  font-weight: 700;
}

.ghost-button {
  border: 1px solid #e7dccb;
  background: #fff;
  padding: 0.55rem 0.85rem;
  color: #6b5848;
  font-size: 0.76rem;
  font-weight: 600;
}

.primary-button:disabled,
.ghost-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.nfc-panel {
  margin-top: 1rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  border-radius: 0.95rem;
  border: 1px solid #e9dfd2;
  background: linear-gradient(135deg, #f8f5ef 0%, #fefcf8 100%);
  padding: 1rem;
}

.nfc-icon {
  display: grid;
  height: 3.3rem;
  width: 3.3rem;
  flex-shrink: 0;
  place-items: center;
  border-radius: 999px;
  background: #234d32;
}

.nfc-copy {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.nfc-copy strong {
  color: #1d3c29;
  font-size: 0.88rem;
}

.nfc-copy span,
.section-title {
  color: #8b7460;
  font-size: 0.72rem;
  line-height: 1.6;
}

.section-title {
  margin-top: 1rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.guest-grid {
  margin-top: 0.75rem;
  display: grid;
  gap: 0.75rem;
}

.guest-card,
.incentive-card {
  width: 100%;
  border-radius: 0.9rem;
  border: 1px solid #e7dccb;
  background: #fff;
  padding: 0.95rem 1rem;
  text-align: left;
  transition: 150ms ease;
}

.guest-card strong,
.incentive-card strong {
  display: block;
  color: #1d3c29;
  font-size: 0.86rem;
  font-weight: 700;
  line-height: 1.45;
}

.guest-card span,
.incentive-card p {
  display: block;
  margin-top: 0.35rem;
  color: #7d6958;
  font-size: 0.75rem;
  line-height: 1.7;
}

.guest-card-active,
.incentive-card-active {
  border-color: #82a56b;
  background: #f5fbf3;
}

.action-row {
  margin-top: 1rem;
}

.result-panel {
  margin-top: 1rem;
  display: grid;
  gap: 0.75rem;
}

.result-copy {
  min-height: 12rem;
  white-space: pre-line;
  border-radius: 0.95rem;
  border: 1px solid #d9ead4;
  background: #f4faf2;
  padding: 1rem;
  color: #29513a;
  font-size: 0.84rem;
  line-height: 1.85;
}

.error-text {
  color: #dc2626;
  font-size: 0.8rem;
}

.incentive-list {
  margin-top: 1rem;
  display: grid;
  gap: 0.85rem;
}

.incentive-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.badge {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 0.25rem 0.55rem;
  font-size: 0.66rem;
  font-weight: 700;
}

.badge-green {
  background: #eaf7e8;
  color: #2f7a36;
}

.badge-amber {
  background: #fff7e6;
  color: #b26b00;
}

.badge-rose {
  background: #fff1f2;
  color: #be5168;
}

.badge-purple {
  background: #f3f0ff;
  color: #6d4cc2;
}

.badge-slate {
  background: #eef3f2;
  color: #47615b;
}

.badge-bamboo {
  background: #e7f2dc;
  color: #234d32;
}

@media (max-width: 1080px) {
  .review-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .card {
    padding: 1rem;
  }

  .card-head,
  .incentive-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .mini-action,
  .primary-button,
  .ghost-button {
    width: 100%;
  }
}
</style>
