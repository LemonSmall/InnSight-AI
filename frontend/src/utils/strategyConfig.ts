export interface StrategyForm {
  objective: string
  period: string
  occasion: string
  targetAudience: string
  channels: string[]
  budgetLevel: string
  executionCapacity: string
  outputDepth: string
  marketSignals: string
  competitorObservations: string
  availableOffers: string
  constraints: string
  evidenceRequirement: boolean
}

export const objectiveOptions = [
  { value: 'auto', label: 'AI 智能设定', note: '基于酒店、周边和实时信号自动判断目标' },
  { value: 'conversion', label: '提升预订转化', note: '优化渠道承接与成交动作' },
  { value: 'exposure', label: '扩大内容曝光', note: '建立持续内容和话题节奏' },
  { value: 'direct', label: '增加私域直订', note: '沉淀微信、老客和会员线索' },
  { value: 'reputation', label: '改善口碑评价', note: '提升评价数量与回复质量' },
  { value: 'new-product', label: '推广新房型/套餐', note: '验证卖点和目标客群' },
  { value: 'off-season', label: '淡季获客', note: '控制成本并寻找细分需求' },
]

export const periodOptions = [
  { value: '7d', label: '未来 7 天' },
  { value: '14d', label: '未来 14 天' },
  { value: '30d', label: '未来 30 天' },
  { value: 'event', label: '围绕指定活动' },
]

export const occasionOptions = [
  { value: 'normal', label: '日常经营' },
  { value: 'weekend', label: '周末专项' },
  { value: 'holiday', label: '节假日专项' },
  { value: 'local-event', label: '本地活动/演出/展会' },
  { value: 'off-season', label: '淡季专项' },
  { value: 'opening', label: '开业或焕新推广' },
]

export const channelOptions = [
  { value: 'xhs', label: '小红书' },
  { value: 'douyin', label: '抖音/视频号' },
  { value: 'wechat', label: '朋友圈/社群' },
  { value: 'article', label: '公众号' },
  { value: 'ota', label: 'OTA 平台' },
  { value: 'offline', label: '到店/周边合作' },
]

export const budgetOptions = [
  { value: 'none', label: '无投放预算' },
  { value: 'low', label: '低预算，重内容执行' },
  { value: 'medium', label: '中等预算，可小额投放' },
  { value: 'high', label: '预算充足，可组合投放' },
]

export const capacityOptions = [
  { value: 'solo', label: '1 人兼职执行' },
  { value: 'small-team', label: '2-3 人小团队' },
  { value: 'team', label: '有专职运营团队' },
  { value: 'outsourcing', label: '可使用外包资源' },
]

export const depthOptions = [
  { value: 'quick', label: '快速建议' },
  { value: 'action-plan', label: '可执行方案' },
  { value: 'full-campaign', label: '完整战役方案' },
]

export function createDefaultStrategyForm(): StrategyForm {
  return {
    objective: 'auto',
    period: '14d',
    occasion: 'normal',
    targetAudience: '',
    channels: ['xhs', 'wechat', 'ota'],
    budgetLevel: 'low',
    executionCapacity: 'small-team',
    outputDepth: 'action-plan',
    marketSignals: '',
    competitorObservations: '',
    availableOffers: '',
    constraints: '',
    evidenceRequirement: true,
  }
}

export function optionLabel(options: { value: string; label: string }[], value: string) {
  return options.find(item => item.value === value)?.label || value
}

export function buildStrategyParams(form: StrategyForm) {
  const objectiveLabel = optionLabel(objectiveOptions, form.objective)
  const periodLabel = optionLabel(periodOptions, form.period)
  const occasionLabel = optionLabel(occasionOptions, form.occasion)
  return {
    theme: `${occasionLabel}营销策略`,
    target: objectiveLabel,
    objective: form.objective,
    objectiveLabel,
    period: form.period,
    periodLabel,
    occasion: form.occasion,
    occasionLabel,
    targetAudience: form.targetAudience,
    channels: form.channels,
    channelLabels: form.channels.map(channel => optionLabel(channelOptions, channel)),
    budgetLevel: form.budgetLevel,
    budgetLevelLabel: optionLabel(budgetOptions, form.budgetLevel),
    executionCapacity: form.executionCapacity,
    executionCapacityLabel: optionLabel(capacityOptions, form.executionCapacity),
    outputDepth: form.outputDepth,
    outputDepthLabel: optionLabel(depthOptions, form.outputDepth),
    marketSignals: form.marketSignals,
    competitorObservations: form.competitorObservations,
    marketContext: [form.marketSignals, form.competitorObservations].filter(Boolean).join('\n'),
    availableOffers: form.availableOffers,
    constraints: form.constraints,
    evidenceRequirement: form.evidenceRequirement,
    outputFormat: 'markdown',
    message: `请制定${periodLabel}营销执行方案。若 objective 为 auto 或目标客群为空，必须基于酒店真实资料、房型、天气、周边信息智能体 surroundingContextJson、用户已填写条件和已确认知识，智能设定核心目标、目标客群、阶段 KPI 和执行重点；无法核实的实时信息标注为待核实，不得虚构竞品、热度、价格、距离或活动。

请严格输出结构化 Markdown，便于前端按上下结构展示：
# 方案标题
## 核心目标与 KPI
| 指标 | 目标值 | 依据 | 待核实 |
## 策略标签
- 标签
## 执行时间表
| 阶段 | 时间 | 重点 | 具体动作 | 渠道/负责人 |
## 各渠道内容计划
| 渠道 | 定位 | 发布节奏 | 内容主题 | 承接动作 | 验收指标 |
## 活动与定价承接
| 项目 | 当前依据 | 建议动作 | 执行条件 | 风险 |
## 核心文案示例
- 至少输出 3 条文案；必须覆盖用户选择的重点渠道，结合当前天气、热门事件、周边机会或客群；每条写清适用渠道，不要只给一句空泛标题。
## 底部执行动作
| actionKey | 按钮文案 | 执行内容 | 调用模块 |

所有表格内容必须由 AI 基于实际上下文生成，不要套用固定节日案例。`,
  }
}
