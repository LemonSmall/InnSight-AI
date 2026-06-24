import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'

// ---- 数据接口 ----

export interface PlanKpi {
  value: string
  label: string
  color: string
}

export interface PlanTask {
  channel: string
  channelColor: string
  content: string
}

export interface PlanPhase {
  id: string
  emoji: string
  dotBg: string
  title: string
  dateRange: string
  badgeLabel: string
  badgeClass: 'green' | 'amber' | 'rose' | 'blue' | 'purple'
  tasks: PlanTask[]
}

export interface PlanChannel {
  icon: string
  iconBg: string
  iconColor: string
  name: string
  sub: string
  items: string[]
  tags: { label: string; badgeClass: string }[]
}

export interface PlanPricing {
  stage: string
  stageBadge: 'amber' | 'rose' | 'blue' | 'green'
  prices: string[]
  logic: string
}

export interface PlanActivity {
  icon: string
  iconColor: string
  title: string
  desc: string
  goal: string
  tag: string
  badgeClass: string
}

export interface PlanCopy {
  label: string
  content: string
}

export interface PlanAlert {
  html: string
  bgClass: 'bamboo' | 'amber' | 'purple'
}

export interface MarketingPlan {
  id: string
  name: string
  festival: string
  status: 'draft' | 'active' | 'completed'
  hotelName: string
  period: string
  target: string
  tags: string[]
  kpis: PlanKpi[]
  phases: PlanPhase[]
  channels: PlanChannel[]
  pricings: PlanPricing[]
  activities: PlanActivity[]
  copyExamples: PlanCopy[]
  alertNote: string
  alerts: PlanAlert[]
  createdAt: string
  updatedAt: string
}

// ---- 工具 ----

function genId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7)
}

function now(): string {
  return new Date().toISOString().slice(0, 10)
}

function persist(key: string, data: unknown) {
  try { localStorage.setItem(key, JSON.stringify(data)) } catch {}
}

function load<T>(key: string, fallback: T): T {
  try {
    const d = localStorage.getItem(key)
    return d ? JSON.parse(d) : fallback
  } catch { return fallback }
}

// ---- 初始种子数据 ----

const seedPlan: MarketingPlan = {
  id: 'duanwu2025',
  name: '端午节完整营销方案',
  festival: '端午节',
  status: 'active',
  hotelName: '松间·山野民宿',
  period: 'D-7（5月25日）→ 假期末（6月2日）→ 收尾（6月5日）',
  target: '端午3天出租率 ≥ 90%，RevPAR +20%',
  tags: ['竹林景观', '私汤温泉', '有机早餐', '情侣/亲子', '莫干山·浙江'],
  kpis: [
    { value: '≥ 90%', label: '端午3天目标出租率', color: '#27500A' },
    { value: '+20%', label: 'RevPAR 同比提升', color: '#633806' },
    { value: '+5 条', label: '端午新增好评', color: '#185FA5' },
    { value: '3万+', label: '内容矩阵总曝光', color: '#3C3489' },
  ],
  phases: [
    {
      id: 'p1',
      emoji: '🌱',
      dotBg: '#FAEEDA',
      title: '第一阶段：蓄水种草期',
      dateRange: '5月25–28日（D-7 → D-4）',
      badgeLabel: '内容先行',
      badgeClass: 'amber',
      tasks: [
        { channel: '小红书', channelColor: '#D4537E', content: '「竹林雨夜·端午前夜」情绪种草图文，3条/天，情侣线+亲子线并行；标题嵌「端午去哪儿」「莫干山民宿」高搜词，12:00 / 20:00发布' },
        { channel: '抖音', channelColor: '#3C3489', content: '宋风竹林ASMR短视频，前3秒竹叶雨声钩子，配国风BGM；文案强调「连住早鸟价仅剩X间」，制造稀缺感，18–21点发布' },
        { channel: '朋友圈', channelColor: '#27500A', content: '早08:00种草型（情绪+景色）+ 晚20:30报名型（限额倒计时）；全员±15分钟同步发布，形成刷屏感' },
        { channel: '定价动作', channelColor: '#633806', content: '端午3天房价提前上调10%挂出；设「早鸟8.5折」限量20张，截止D-3；制造价格锚点，触发占便宜心理' },
      ],
    },
    {
      id: 'p2',
      emoji: '📣',
      dotBg: '#EAF3DE',
      title: '第二阶段：爆发冲刺期',
      dateRange: '5月29–30日（D-3 → D-1）',
      badgeLabel: '冲量关键',
      badgeClass: 'rose',
      tasks: [
        { channel: '小红书', channelColor: '#D4537E', content: '「仅剩3间！端午莫干山这家竹林民宿」紧迫感图文；评论区置顶预订方式；号召已订客人晒行程，形成裂变' },
        { channel: '抖音', channelColor: '#3C3489', content: '快闪口播「最后2间，私信粉丝价」；开直播间随机抽住宿优惠券，边直播边实时售房' },
        { channel: '朋友圈', channelColor: '#27500A', content: '日发3条，晚间发「今日已订X间 · 还剩Y间」进度实播，制造紧迫感；切忌捏造数字' },
        { channel: '美团/携程', channelColor: '#712B13', content: '开启「限时抢购」，上架预售定金产品（定金减50元）；主图换端午主题海报；假期前2天恢复正价' },
      ],
    },
    {
      id: 'p3',
      emoji: '🎉',
      dotBg: '#E6F1FB',
      title: '第三阶段：假期服务期',
      dateRange: '5月31日–6月2日（D0 → D+2）',
      badgeLabel: '口碑沉淀',
      badgeClass: 'blue',
      tasks: [
        { channel: '在住体验', channelColor: '#27500A', content: '前台早上送「端午礼包」（手工香包+竹叶粽+节气卡，成本约30元）；每日16:00组织竹林黄昏拍照打卡活动' },
        { channel: 'UGC引导', channelColor: '#D4537E', content: '在住客发小红书/抖音@松间官号，可获下次入住8.8折；前台主动协助拍摄，提供推荐机位与滤镜' },
        { channel: '好评引导', channelColor: '#BA7517', content: '离店递NFC好评卡+手写感谢信；碰一碰跳转携程/美团评价页；引导添加微信沉淀私域' },
        { channel: '实时内容', channelColor: '#3C3489', content: '获授权后发布在住客打卡视频，「端午的莫干山竹林是这样的」，为暑假持续种草' },
      ],
    },
    {
      id: 'p4',
      emoji: '☀️',
      dotBg: '#EAF3DE',
      title: '第四阶段：收尾蓄暑期',
      dateRange: '6月3–5日（D+3 → D+5）',
      badgeLabel: '以端午蓄暑假',
      badgeClass: 'green',
      tasks: [
        { channel: '内容复盘', channelColor: '#27500A', content: '精选端午最美UGC，整理「端午Vlog」发小红书+抖音，吃流量长尾；48小时内回复全部平台评论' },
        { channel: '私域转化', channelColor: '#3C3489', content: '向端午入私域的全部客人发「暑假早鸟7折专属码」，此72小时是最高转化率黄金窗口' },
        { channel: '公众号', channelColor: '#185FA5', content: '发布《端午·莫干山竹林的那三天》精美图文，沉淀品牌内容资产，为暑假旺季引流预热' },
      ],
    },
  ],
  channels: [
    { icon: 'Instagram', iconBg: '#FBEAF0', iconColor: '#D4537E', name: '小红书', sub: '核心引流 · 情绪+攻略双线', items: ['蓄水期3条/天，封面竖版大字标题，12:00/20:00发','冲刺期「仅剩X间」稀缺笔记，评论区置顶预订方式','假期实时打卡（获授权），以在住客第一视角发布','收尾端午复盘Vlog + 暑假攻略预告，延续长尾流量'], tags: [{ label:'3条/天', badgeClass:'rose' },{ label:'情侣+亲子双线', badgeClass:'purple' }] },
    { icon: 'Music', iconBg: '#EEEDFE', iconColor: '#3C3489', name: '抖音', sub: '视频+口播 · 冲刺主战场', items: ['蓄水：竹林ASMR视频，前3秒雨声钩子，国风BGM','冲刺：口播「最后2间粉丝价」+ 直播间抽优惠券','假期：在住打卡合集，「端午竹林是这样的」系列','收尾：端午民宿Vlog完整版，暑假话题提前埋点'], tags: [{ label:'1条/天', badgeClass:'rose' },{ label:'18–21点发布', badgeClass:'blue' }] },
    { icon: 'MessageCircleHeart', iconBg: '#EAF3DE', iconColor: '#27500A', name: '朋友圈 + 私域', sub: '全员发车 · 三档话术', items: ['早08:00 种草型：情绪+景色，引发向往','午12:00 互动型：名额倒计时，引发参与','晚20:30 凡尔赛型：已售进度，引发紧迫','私域群直发专属折扣码，专项转化老客复购'], tags: [{ label:'3条/天', badgeClass:'green' },{ label:'全员±15分钟同步', badgeClass:'amber' }] },
    { icon: 'Building', iconBg: '#FAECE7', iconColor: '#712B13', name: '美团 / 携程 / OTA', sub: '平台流量 · 转化承接', items: ['D-7 上架「端午预售」定金产品（定金减50元）','主图换端午主题海报，标题加关键词','开「限时抢购」活动，假期前2天恢复正价','假期结束72小时内处理全部评价，回复率100%'], tags: [{ label:'转化承接', badgeClass:'blue' },{ label:'好评目标+5条', badgeClass:'rose' }] },
  ],
  pricings: [
    { stage: 'D-7至D-4<br>蓄水期', stageBadge: 'amber', prices: ['¥888<br>早鸟减80元','¥1388<br>早鸟减100元','¥1688<br>早鸟减150元'], logic: '保基础价+早鸟优惠，触发占便宜心理，促提前锁单' },
    { stage: 'D-3至D-1<br>冲刺期', stageBadge: 'rose', prices: ['¥1068<br>↑ +20%','¥1666<br>↑ +20%','¥2026<br>↑ +20%'], logic: '早鸟结束正式涨价；与早鸟价差形成对比，强化早订价值' },
    { stage: 'D0–D+2<br>假期3天', stageBadge: 'blue', prices: ['¥1158<br>↑ +30%','¥1804<br>↑ +30%','¥2194<br>↑ +30%'], logic: '旺季满房逻辑，持高位不降价，保护品牌价值' },
    { stage: 'D+3<br>收尾恢复', stageBadge: 'green', prices: ['¥888','¥1388','¥1688'], logic: '平稳回落，搭配「暑假早鸟」引导下一周期预订' },
  ],
  activities: [
    { icon: 'Clock', iconColor: '#854F0B', title: '早鸟连住特惠', desc: 'D-7开启预售，连住2晚8.5折/连住3晚8折；早鸟名额限20张，截止D-3。前台专线+小程序均可预订。', goal: '目标：提前锁定70%入住率，减少临时空置风险', tag: '引流+锁单', badgeClass: 'amber' },
    { icon: 'Package', iconColor: '#185FA5', title: '端午礼包惊喜', desc: '房间预置「端午礼包」：手工香包+竹叶粽2个+节气卡。成本约30元，溢价感极强，自发晒单率高。', goal: '目标：提升入住体验感，触发社媒自传播', tag: '口碑传播', badgeClass: 'blue' },
    { icon: 'Camera', iconColor: '#27500A', title: '竹林打卡挑战赛', desc: '假期每日16:00组织竹林黄昏拍照打卡，前台协助拍摄。客人发小红书/抖音@官号享8.8折优惠。', goal: '目标：沉淀优质UGC内容资产，持续为账号引流', tag: '内容资产', badgeClass: 'green' },
    { icon: 'Star', iconColor: '#BA7517', title: '离店好评礼遇', desc: '离店赠莫干山龙井茶礼+手写感谢信+NFC好评卡；扫码评价送暑假早鸟专属码。', goal: '目标：端午新增好评5条+，沉淀私域复购客户', tag: '口碑+复购', badgeClass: 'rose' },
  ],
  copyExamples: [],
  alertNote: '若D-1仍有房间未售，推「入住赠端午6折续住券」——实物优惠截流散客，不破挂牌价，保护公开价格体系。连住2晚以上可私下给8.8折。',
  alerts: [
    { html: '<strong>执行关键：</strong>全员朋友圈须在同一时段发布（±15分钟），形成刷屏感；「仅剩X间」数字须与实时房态保持一致，前台每2小时更新一次，切忌捏造。', bgClass: 'bamboo' },
    { html: '<strong>风险预案：</strong>若D-1仍有3间以上未售，立即启动「当日快闪」——朋友圈发「今日特惠私信减100元」，不降挂牌价，以私下优惠截流最后散客。', bgClass: 'amber' },
    { html: '<strong>暑假衔接：</strong>端午假期结束后的72小时是转化暑假订单的黄金窗口，向全部入私域的端午客人发送「暑假早鸟7折码」，以端午好体验背书，转化率最高。', bgClass: 'purple' },
  ],
  createdAt: '2025-05-20',
  updatedAt: '2025-05-25',
}

// ---- Store ----

export const usePlanStore = defineStore('plan', () => {
  const plans = ref<MarketingPlan[]>(load('plan_list', []))
  const loading = ref(false)

  const activePlans = computed(() => plans.value.filter(p => p.status === 'active'))
  const completedPlans = computed(() => plans.value.filter(p => p.status === 'completed'))
  const draftPlans = computed(() => plans.value.filter(p => p.status === 'draft'))

  /** 从后端加载方案列表 */
  async function loadFromApi() {
    loading.value = true
    try {
      const { data: res } = await api.get('/api/hotel/plans')
      const list = res.data || res
      if (Array.isArray(list) && list.length > 0) {
        plans.value = list
        persist('plan_list', list)
      }
    } catch {
      // 静默回退到本地数据
      if (plans.value.length === 0) {
        plans.value = [seedPlan]
      }
    } finally {
      loading.value = false
    }
  }

  /** 创建方案 */
  async function create(name: string, festival: string): Promise<MarketingPlan> {
    const { data: res } = await api.post('/api/hotel/plans', {
      name, festival,
      hotelName: '松间·山野民宿',
      status: 'draft',
      tags: JSON.stringify(['竹林景观', '私汤温泉']),
    })
    const p: MarketingPlan = res.data || res
    plans.value.unshift(p)
    persist('plan_list', plans.value)
    return p
  }

  function getById(id: string): MarketingPlan | undefined {
    return plans.value.find(p => p.id === id)
  }

  async function update(id: string, patch: Partial<MarketingPlan>) {
    const idx = plans.value.findIndex(p => p.id === id)
    if (idx < 0) return
    plans.value[idx] = { ...plans.value[idx], ...patch, updatedAt: now() }
    persist('plan_list', plans.value)
    try { await api.put(`/api/hotel/plans/${id}`, patch) } catch { /* API 失败时本地已保存 */ }
  }

  async function remove(id: string) {
    plans.value = plans.value.filter(p => p.id !== id)
    persist('plan_list', plans.value)
    try { await api.delete(`/api/hotel/plans/${id}`) } catch { /* API 失败时本地已保存 */ }
  }

  async function duplicate(id: string): Promise<MarketingPlan | undefined> {
    const src = plans.value.find(p => p.id === id)
    if (!src) return
    const clonePayload = {
      name: src.name + '（副本）',
      festival: src.festival,
      hotelName: src.hotelName,
      status: 'draft' as const,
      tags: JSON.stringify(src.tags),
    }
    try {
      const { data: res } = await api.post('/api/hotel/plans', clonePayload)
      const p: MarketingPlan = res.data || res
      plans.value.unshift(p)
      persist('plan_list', plans.value)
      return p
    } catch {
      // 后端不可用时本地复制
      const clone: MarketingPlan = {
        ...JSON.parse(JSON.stringify(src)),
        id: genId(),
        name: src.name + '（副本）',
        status: 'draft',
        createdAt: now(),
        updatedAt: now(),
      }
      plans.value.unshift(clone)
      persist('plan_list', plans.value)
      return clone
    }
  }

  return {
    plans, activePlans, completedPlans, draftPlans, loading,
    loadFromApi, getById, create, update, remove, duplicate,
  }
})
