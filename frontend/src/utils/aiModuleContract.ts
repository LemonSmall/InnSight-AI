export type AiParamType = 'string' | 'number' | 'boolean' | 'object' | 'array'

export type AiFileParamType = 'file'

export interface AiParamDefinition {
  name: string
  label: string
  type: AiParamType | AiFileParamType
  required?: boolean
  source: string
  description?: string
}

export interface AiModuleDefinition {
  key: string
  label: string
  agentName: string
  group: string
  desc: string
  appType: 'workflow' | 'chatflow'
  params: AiParamDefinition[]
}

export const AI_CONTEXT_PARAMS: AiParamDefinition[] = [
  { name: 'commonContextJson', label: '酒店与调用上下文', type: 'string', required: true, source: '后端鉴权与酒店资料', description: '租户、用户、模块、酒店基础资料、房型参考资料和数据范围。' },
  { name: 'businessParamsJson', label: '本次业务参数', type: 'string', required: true, source: '用户端当前表单', description: '完整保留用户在当前功能中填写和选择的参数。' },
  { name: 'knowledgeContextJson', label: '已确认知识上下文', type: 'string', required: true, source: '后端知识检索', description: '仅包含当前租户有效、已确认且未过期的知识。' },
  { name: 'message', label: '主要问题或主题', type: 'string', required: true, source: '后端从业务参数提取', description: '兼容 Chatflow query 和旧应用的主文本变量。' },
]

const p = (name: string, label: string, type: AiParamType | AiFileParamType, source: string, required = false): AiParamDefinition => ({
  name, label, type, source, required,
})

export const AI_MODULES: AiModuleDefinition[] = [
  {
    key: 'brain', label: 'AI 店长', agentName: '宿识家 AI 店长', group: '经营问答', desc: '基于本店资料与已确认知识回答经营问题', appType: 'chatflow',
    params: [
      p('message', '用户问题', 'string', 'AI 店长输入框', true),
      p('enableWebSearch', '是否联网搜索', 'boolean', 'AI 店长页面'),
      p('outputStyle', '回答方式', 'string', 'AI 店长页面'),
    ],
  },
  {
    key: 'strategy', label: '营销策略', agentName: '宿识家营销策略师', group: '经营增长', desc: '生成有事实边界的阶段营销执行方案', appType: 'chatflow',
    params: [
      p('theme', '策略主题', 'string', '营销策略页', true), p('objective', '经营目标', 'string', '营销策略页', true),
      p('period', '执行周期', 'string', '营销策略页', true), p('occasion', '经营场景', 'string', '营销策略页', true),
      p('targetAudience', '目标客群', 'string', '营销策略页'), p('channels', '执行渠道', 'array', '营销策略页', true),
      p('budgetLevel', '预算条件', 'string', '营销策略页'), p('executionCapacity', '执行能力', 'string', '营销策略页'),
      p('outputDepth', '方案深度', 'string', '营销策略页'), p('marketSignals', '已确认市场信号', 'string', '营销策略页'),
      p('competitorObservations', '竞品人工观察', 'string', '营销策略页'), p('availableOffers', '可使用权益', 'string', '营销策略页'),
      p('constraints', '执行限制', 'string', '营销策略页'), p('evidenceRequirement', '标注待核实信息', 'boolean', '营销策略页'),
    ],
  },
  {
    key: 'pricing', label: '智能定价', agentName: '宿识家收益定价顾问', group: '经营增长', desc: '依据挂牌价与人工确认信号提供定价建议', appType: 'chatflow',
    params: [
      p('dateRange', '日期范围', 'string', '智能定价页', true), p('pricingPeriod', '定价周期', 'string', '智能定价页', true),
      p('pricingGoal', '定价目标', 'string', '智能定价页', true), p('demandSignal', '人工确认需求信号', 'string', '智能定价页', true),
      p('bookingWindow', '主要预订窗口', 'string', '智能定价页', true), p('eventFactor', '日期影响因素', 'string', '智能定价页', true),
      p('competitorPriceRange', '竞品价格观察', 'string', '智能定价页'), p('currentPriceNotes', '当前销售现象', 'string', '智能定价页'),
      p('priceFloor', '最低可接受价', 'string', '智能定价页'), p('maxDiscountPercent', '最大折扣比例', 'number', '智能定价页'),
      p('targetChannels', '重点销售渠道', 'array', '智能定价页'), p('promotionAllowed', '允许限时促销', 'boolean', '智能定价页'),
      p('packagePreference', '价格策略', 'string', '智能定价页'), p('riskLevel', '风险偏好', 'string', '智能定价页'),
      p('constraints', '价格限制', 'string', '智能定价页'), p('roomSnapshot', '房型挂牌价快照', 'array', '酒店房型资料', true),
      p('evidenceRequirement', '区分事实与待核实信息', 'boolean', '智能定价页'),
    ],
  },
  {
    key: 'xhs', label: '小红书图文', agentName: '宿识家小红书创作官', group: '内容发布', desc: '标题、正文、标签和配图建议', appType: 'chatflow',
    params: [p('topics', '内容方向', 'string', '小红书页', true), p('customTopic', '自定义主题', 'string', '小红书页'), p('tone', '内容语气', 'string', '小红书页'), p('style', '写作风格', 'string', '小红书页'), p('note', '补充要求', 'string', '小红书页'), p('withImage', '需要配图', 'boolean', '小红书页'), p('imageSize', '图片比例', 'string', '小红书页'), p('imageCount', '图片数量', 'number', '小红书页')],
  },
  {
    key: 'wechat', label: '朋友圈文案', agentName: '宿识家朋友圈编辑', group: '内容发布', desc: '按时段生成私域朋友圈文案', appType: 'chatflow',
    params: [p('slots', '发布时段', 'array', '朋友圈页', true), p('style', '文案风格', 'string', '朋友圈页'), p('length', '文案长度', 'string', '朋友圈页'), p('note', '补充要求', 'string', '朋友圈页'), p('withImage', '需要配图', 'boolean', '朋友圈页'), p('imageSize', '图片比例', 'string', '朋友圈页')],
  },
  {
    key: 'article', label: '公众号推文', agentName: '宿识家公众号主编', group: '内容发布', desc: '把上传视频转写并重构为微信公众号文章', appType: 'workflow',
    params: [
      p('sourceType', '内容来源类型', 'string', '公众号页', true), p('publishPlatform', '发布平台', 'string', '公众号页', true),
      p('title', '文章主题', 'string', '公众号页'), p('style', '排版预设', 'string', '公众号页'), p('styleLabel', '排版预设名称', 'string', '公众号页'),
      p('length', '文章长度', 'string', '公众号页'), p('lengthLabel', '文章长度名称', 'string', '公众号页'),
      p('withImage', '需要配图', 'boolean', '公众号页'), p('imageCount', '配图数量', 'number', '公众号页'),
      p('fileName', '上传视频文件名', 'string', '公众号页'), p('videoFileName', '上传视频文件名', 'string', '公众号页'),
      p('videoFileType', '上传视频类型', 'string', '公众号页'), p('videoFileSize', '上传视频大小', 'number', '公众号页'),
    ],
  },
  {
    key: 'video', label: '短视频脚本', agentName: '宿识家短视频编导', group: '内容发布', desc: '短视频口播、分镜和发布建议', appType: 'chatflow',
    params: [p('sellingPoints', '核心卖点', 'string', '短视频页', true), p('view', '叙事视角', 'string', '短视频页'), p('style', '视频风格', 'string', '短视频页'), p('goal', '发布目标', 'string', '短视频页'), p('duration', '视频时长', 'string', '短视频页'), p('count', '脚本数量', 'number', '短视频页')],
  },
  {
    key: 'poster', label: '营销海报', agentName: '宿识家营销视觉设计师', group: '图片生成', desc: '生成酒店营销海报和真实图片链接', appType: 'workflow',
    params: [p('mode', '创作模式', 'string', '海报页', true), p('theme', '海报主题', 'string', '海报页', true), p('content', '海报正文', 'string', '海报页'), p('style', '视觉风格', 'string', '海报页'), p('scene', '营销场景', 'string', '海报页'), p('platform', '投放平台', 'string', '海报页'), p('targetAudience', '目标客群', 'string', '海报页'), p('textDensity', '文字密度', 'string', '海报页'), p('cta', '行动号召', 'string', '海报页'), p('sellingPoint', '核心卖点', 'string', '海报页'), p('imageSize', '图片比例', 'string', '海报页', true), p('width', '图片宽度', 'number', '海报页', true), p('height', '图片高度', 'number', '海报页', true), p('imageData', '待美化图片', 'string', '海报美化模式')],
  },
  {
    key: 'occupancy_image', label: '房态导入', agentName: '宿识家房态图片识别员', group: '资料导入', desc: '识别历史房态图片或表格并输出可导入数据', appType: 'workflow',
    params: [
      p('image', '房态表图片', 'file', '基础信息页历史房态上传', true),
      p('sourceFileName', '上传文件名', 'string', '基础信息页历史房态上传'),
      p('sourceFileType', '上传文件类型', 'string', '基础信息页历史房态上传'),
      p('sourceFileSize', '上传文件大小', 'number', '基础信息页历史房态上传'),
    ],
  },
  {
    key: 'polish', label: 'AI 润色', agentName: '宿识家文案润色师', group: '辅助能力', desc: '优化自由文本且不改变事实字段', appType: 'chatflow',
    params: [p('sourceText', '原始文本', 'string', '各业务输入框', true), p('scene', '使用场景', 'string', '调用页面', true), p('field', '目标字段', 'string', '调用页面', true), p('style', '目标风格', 'string', '调用页面'), p('purpose', '润色目标', 'string', '调用页面'), p('immutableFacts', '不可修改事实', 'array', '后端酒店资料')],
  },
  {
    key: 'review', label: '好评引导', agentName: '宿识家好评引导师', group: '口碑管理', desc: '合规的离店感谢和评价邀请', appType: 'chatflow',
    params: [p('guestType', '客群类型', 'string', '好评引导页', true), p('scene', '评价场景', 'string', '好评引导页'), p('additionalNotes', '补充要求', 'string', '好评引导页')],
  },
  {
    key: 'reply', label: '点评回复', agentName: '宿识家点评回复专员', group: '口碑管理', desc: '针对真实评价生成平台回复', appType: 'chatflow',
    params: [p('reviewText', '客人原始评价', 'string', '点评回复页'), p('reviewType', '评价类型', 'string', '点评回复页', true), p('style', '回复语气', 'string', '点评回复页'), p('additionalNotes', '回复要求', 'string', '点评回复页')],
  },
]

export const AI_MODULE_MAP = Object.fromEntries(AI_MODULES.map(module => [module.key, module])) as Record<string, AiModuleDefinition>

export function getAiModule(moduleKey: string) {
  return AI_MODULE_MAP[moduleKey] || AI_MODULES[0]
}

export function buildAiInputSchema(moduleKey: string) {
  const schema: Record<string, AiParamType | AiFileParamType> = {}
  for (const item of AI_CONTEXT_PARAMS) schema[item.name] = item.type
  for (const item of getAiModule(moduleKey).params) schema[item.name] = item.type
  return schema
}

