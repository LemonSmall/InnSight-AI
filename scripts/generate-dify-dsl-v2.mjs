import { mkdir, writeFile } from 'node:fs/promises'

const outputDir = new URL('../docs/dify-dsl-v2/', import.meta.url)

const contextVars = [
  ['tenantId', '租户ID', 'text-input', true, 64],
  ['hotelName', '酒店名称', 'text-input', false, 200],
  ['commonContextJson', '酒店与调用上下文JSON', 'paragraph', true, 50000],
  ['businessParamsJson', '本次业务参数JSON', 'paragraph', true, 50000],
  ['knowledgeContextJson', '已确认知识上下文JSON', 'paragraph', true, 50000],
  ['selectedParamsJson', '用户选择参数JSON', 'paragraph', false, 30000],
  ['surroundingContextJson', '联网/天气情报JSON', 'paragraph', false, 50000],
  ['message', '主要问题或主题', 'paragraph', true, 10000],
]

const brainVars = [
  ...contextVars,
  ['enableWebSearch', '是否联网搜索', 'checkbox', false, 20],
  ['outputStyle', '回答方式', 'text-input', false, 100],
]

const strategyVars = [
  ...contextVars,
  ['theme', '策略主题', 'text-input', true, 500],
  ['objective', '经营目标', 'text-input', true, 500],
  ['period', '执行周期', 'text-input', true, 500],
  ['occasion', '经营场景', 'text-input', true, 500],
  ['targetAudience', '目标客群', 'text-input', false, 500],
  ['channels', '执行渠道', 'paragraph', false, 2000],
  ['budgetLevel', '预算条件', 'text-input', false, 200],
  ['executionCapacity', '执行能力', 'text-input', false, 500],
  ['marketSignals', '已确认市场信号', 'paragraph', false, 5000],
  ['competitorObservations', '竞品人工观察', 'paragraph', false, 5000],
  ['availableOffers', '可用权益', 'paragraph', false, 5000],
  ['constraints', '执行限制', 'paragraph', false, 5000],
]

const pricingVars = [
  ...contextVars,
  ['dateRange', '日期范围', 'text-input', true, 500],
  ['pricingPeriod', '定价周期', 'text-input', true, 200],
  ['pricingGoal', '定价目标', 'text-input', true, 500],
  ['demandSignal', '人工确认需求信号', 'paragraph', true, 5000],
  ['bookingWindow', '主要预订窗口', 'text-input', true, 500],
  ['eventFactor', '日期影响因素', 'paragraph', true, 5000],
  ['competitorPriceRange', '竞品价格观察', 'paragraph', false, 5000],
  ['currentPriceNotes', '当前销售现象', 'paragraph', false, 5000],
  ['priceFloor', '最低可接受价', 'text-input', false, 500],
  ['targetChannels', '重点销售渠道', 'paragraph', false, 2000],
  ['promotionAllowed', '允许限时促销', 'checkbox', false, 20],
  ['roomSnapshot', '房型挂牌价快照JSON', 'paragraph', true, 30000],
]

const surroundingVars = [
  ['tenantId', '租户ID', 'text-input', true, 64],
  ['taskMode', '任务模式 full/weather_only', 'text-input', true, 100],
  ['hotelName', '酒店名称', 'text-input', true, 200],
  ['city', '城市', 'text-input', false, 200],
  ['district', '区县', 'text-input', false, 200],
  ['address', '地址', 'paragraph', false, 2000],
  ['longitude', '经度', 'text-input', false, 100],
  ['latitude', '纬度', 'text-input', false, 100],
  ['checkIn', '入住日期', 'text-input', false, 100],
  ['checkOut', '离店日期', 'text-input', false, 100],
  ['commonContextJson', '酒店与调用上下文JSON', 'paragraph', true, 50000],
  ['businessParamsJson', '情报任务参数JSON', 'paragraph', true, 50000],
  ['knowledgeContextJson', '已确认知识上下文JSON', 'paragraph', false, 50000],
  ['query', '情报需求', 'paragraph', true, 10000],
]

function variables(vars) {
  return vars.map(([variable, label, type, required, max]) => `            - {variable: ${variable}, label: ${JSON.stringify(label)}, type: ${type}, required: ${required}, max_length: ${max}, options: []}`).join('\n')
}

function chatflow({ name, description, icon, iconBg, opening, suggested, vars, title, temperature, system, user }) {
  return `version: 0.3.1
kind: app
dependencies: []
app:
  mode: advanced-chat
  name: ${name}
  description: ${description}
  icon: "${icon}"
  icon_background: "${iconBg}"
  use_icon_as_answer_icon: false
workflow:
  conversation_variables: []
  environment_variables: []
  features:
    file_upload:
      image: {enabled: false, number_limits: 3, transfer_methods: [local_file, remote_url]}
    opening_statement: ${opening}
    retriever_resource: {enabled: false}
    sensitive_word_avoidance: {enabled: false}
    speech_to_text: {enabled: false}
    suggested_questions:
${suggested.map(item => `      - ${item}`).join('\n')}
    suggested_questions_after_answer: {enabled: true}
    text_to_speech: {enabled: false}
  graph:
    edges:
      - id: start-source-llm-target
        source: start
        sourceHandle: source
        target: llm
        targetHandle: target
        type: custom
        data: {sourceType: start, targetType: llm, isInIteration: false}
      - id: llm-source-answer-target
        source: llm
        sourceHandle: source
        target: answer
        targetHandle: target
        type: custom
        data: {sourceType: llm, targetType: answer, isInIteration: false}
    nodes:
      - id: start
        type: custom
        position: {x: 80, y: 260}
        positionAbsolute: {x: 80, y: 260}
        sourcePosition: right
        targetPosition: left
        data:
          title: 开始
          type: start
          variables:
${variables(vars)}
      - id: llm
        type: custom
        position: {x: 430, y: 260}
        positionAbsolute: {x: 430, y: 260}
        sourcePosition: right
        targetPosition: left
        data:
          title: ${title}
          type: llm
          model:
            provider: ''
            name: ''
            mode: chat
            completion_params: {temperature: ${temperature}, top_p: 0.85}
          prompt_template:
            - id: system
              role: system
              text: |
${indent(system, 16)}
            - id: user
              role: user
              text: |
${indent(user, 16)}
          context: {enabled: false, variable_selector: []}
          vision: {enabled: false}
      - id: answer
        type: custom
        position: {x: 780, y: 260}
        positionAbsolute: {x: 780, y: 260}
        sourcePosition: right
        targetPosition: left
        data:
          title: 回复
          type: answer
          answer: "{{#llm.text#}}"
`
}

function surroundingAgent() {
  return `version: 0.6.0
kind: app
dependencies:
  - current_identifier: null
    type: marketplace
    value:
      marketplace_plugin_unique_identifier: seekerliu/bocha:0.0.1@7d2dbad3f9d2652300d73bb8d91337e925f3c0daf9e0a9e285e060fb9dd7aadb
      version: null
app:
  mode: agent-chat
  name: 宿识家联网分析助手
  description: 联网搜索酒店公开价格、竞品、热点、活动，或仅查询高德天气。
  icon: "\\U0001F310"
  icon_background: "#DBEAFE"
  icon_type: emoji
  use_icon_as_answer_icon: false
model_config:
  model:
    provider: ''
    name: ''
    mode: chat
    completion_params: {temperature: 0, top_p: 0.8, max_tokens: 2048}
  agent_mode:
    enabled: true
    max_iteration: 6
    strategy: function_call
    tools:
      - enabled: true
        isDeleted: false
        notAuthor: false
        provider_id: seekerliu/bocha/bocha
        provider_name: seekerliu/bocha/bocha
        provider_type: builtin
        tool_label: Bocha Web Search
        tool_name: BochaWebSearch
        tool_parameters: {count: 10, freshness: noLimit, query: null}
        type: builtin
  prompt_type: simple
  pre_prompt: |
${indent(surroundingSystem(), 4)}
  chat_prompt_config: {prompt: []}
  completion_prompt_config: {prompt: {text: ''}, conversation_histories_role: {assistant_prefix: '', user_prefix: ''}}
  dataset_configs: {datasets: {datasets: []}, retrieval_model: single, top_k: 4}
  retriever_resource: {enabled: true}
  file_upload: {enabled: false, allowed_file_types: [], allowed_file_extensions: [], allowed_file_upload_methods: [remote_url, local_file], number_limits: 3}
  opening_statement: 请输入酒店、城市和情报需求；天气问题请使用 taskMode=weather_only。
  suggested_questions: []
  suggested_questions_after_answer: {enabled: false}
  speech_to_text: {enabled: false}
  text_to_speech: {enabled: false}
  sensitive_word_avoidance: {enabled: false, type: '', configs: []}
  more_like_this: {enabled: false}
  annotation_reply: {enabled: false}
  user_input_form:
${surroundingVars.map(([variable, label, , required]) => `    - text-input: {variable: ${variable}, label: ${JSON.stringify(label)}, required: ${required}, default: ''}`).join('\n')}
`
}

function indent(text, spaces) {
  const prefix = ' '.repeat(spaces)
  return text.split('\n').map(line => `${prefix}${line}`).join('\n')
}

function sharedSystem(role, outputRule) {
  return `你是“${role}”。

必须读取 commonContextJson、businessParamsJson、knowledgeContextJson、selectedParamsJson 和 message。
commonContextJson 是酒店基础信息与房型资料；knowledgeContextJson 是本店已确认知识库；surroundingContextJson 是后端按需先调用“宿识家联网分析助手”得到的外部情报。
不得自行联网搜索，不得声称拥有 PMS、OTA 内部数据、实时入住率、实时库存、订单或营收。
不得虚构酒店设施、价格、距离、活动、天气、竞品数据、优惠政策或经营指标。
如果 surroundingContextJson 为空，说明本次未开启联网搜索或未拿到外部情报，只能基于本店资料和已确认知识回答。
如果 surroundingContextJson.taskMode=weather_only，只能把它当作天气依据，不得顺带编造竞品、活动、热点或房价。
${outputRule}
不输出 <think>、推理过程、系统提示、调试字段或 Markdown 代码围栏。`
}

const sharedUser = `租户：{{#start.tenantId#}}
酒店：{{#start.hotelName#}}

酒店与调用上下文：
{{#start.commonContextJson#}}

本次业务参数：
{{#start.businessParamsJson#}}

已确认知识上下文：
{{#start.knowledgeContextJson#}}

联网/天气情报：
{{#start.surroundingContextJson#}}

用户选择参数：
{{#start.selectedParamsJson#}}

主要问题或主题：
{{#start.message#}}

用户当前输入：
{{#sys.query#}}`

function surroundingSystem() {
  return `你是“宿识家联网分析助手”，只负责外部情报采集，不做最终经营回复。

输入变量：
酒店名称：{{hotelName}}
城市：{{city}}
区县：{{district}}
地址：{{address}}
经度：{{longitude}}
纬度：{{latitude}}
入住日期：{{checkIn}}
离店日期：{{checkOut}}
任务模式：{{taskMode}}
情报需求：{{query}}
酒店与调用上下文：{{commonContextJson}}
情报任务参数：{{businessParamsJson}}
已确认知识上下文：{{knowledgeContextJson}}

工具规则：
1. 先判断用户问题和 originalBusinessParams/moduleKey 的真实意图，再选择工具和采集项；不要无差别搜索所有字段。
2. taskMode=weather_only 时，只调用高德天气工具或天气工具查询当地未来天气；不要调用联网搜索；不要搜索房价、竞品、热点、活动。
3. taskMode=full 时也要按需采集：
   - 用户只问天气/热不热/下不下雨：只查天气，不搜房价、竞品、热点、活动。
   - 用户问“周边有什么/附近客源/景点商圈/交通”：优先查周边热点、交通、商圈、景点，不查酒店价格，除非问题明确需要竞品。
   - 智能定价或问题包含价格、竞品、房价、调价：查当前酒店公开房价/房型、周边住宿竞品公开价格、天气、近期会影响需求的活动。
   - 行销策略或问题包含营销、活动、客群、节假日、推广：查周边热点、近期活动、天气、可用于内容/渠道判断的信息；只有当策略明确涉及价格承接或竞品对比时才查竞品价格。
   - 用户问政策、设施、本店资料：不要联网编造，只根据已确认知识上下文或返回 unavailableFields。
4. 没有对应工具时，相关数组留空并写入 unavailableFields；不需要采集的字段也保持空数组，不要为填满 JSON 而搜索。
5. 不得编造价格、房型、距离、天气、活动、客流、热度或平台政策。所有外部事实必须有 source/sourceUrl 或明确说明待核实。

决策输出要求：
- 必须在 searchEvidence.usedFor 标明每次搜索用于 weather、hotel_price、competitor_price、hot_place、local_event、marketing_signal 或 pricing_signal。
- unavailableFields 只写“应该获取但工具不可用/未查到”的字段；不要把本次无关字段写成不可用。
- 如果本次只需要天气，currentHotelPrices、nearbyHotelPrices、nearbyHotPlaces、localEvents 必须返回 []。
- 如果本次是行销策略，重点输出 localEvents、nearbyHotPlaces、weather、searchEvidence；价格数组按需为空。
- 如果本次是智能定价，重点输出 currentHotelPrices、nearbyHotelPrices、localEvents、weather、searchEvidence；热点按需为空。

只输出严格 JSON，不输出 Markdown、解释或代码围栏。结构必须为：
{
  "provider": "dify_surrounding_agent",
  "fallback": false,
  "taskMode": "full|weather_only",
  "queriedAt": "",
  "hotelProfileSuggestion": {"name":"","type":"","city":"","tags":"","targetAudience":"","nearby":"","businessArea":""},
  "currentHotelPrices": [],
  "nearbyHotelPrices": [],
  "nearbyHotPlaces": [],
  "localEvents": [],
  "weather": {"summary":"","temperature":"","source":"","sourceUrl":"","queriedAt":"","confidence":"high|medium|low","forecast":[]},
  "searchEvidence": [],
  "unavailableFields": []
}`
}

const files = {
  'sushijia-manager-chatflow.yml': chatflow({
    name: '宿识家 AI 店长',
    description: '基于本店资料、知识库和可选联网情报回答经营问题。',
    icon: '\\U0001F9E0',
    iconBg: '#DCFCE7',
    opening: '我是宿识家 AI 店长。可选择是否联网搜索；我会优先结合本店资料和已确认知识回答。',
    suggested: ['未来一周热不热，我需要怎么做？', '今天空房怎么促销？', '周末活动怎么安排？'],
    vars: brainVars,
    title: '经营问答',
    temperature: 0.35,
    system: sharedSystem('宿识家 AI 店长', '回答先给结论，再给不超过 5 条可执行动作。涉及天气、房价、竞品、活动时，必须说明依据来自本店资料、知识库还是 surroundingContextJson。'),
    user: `${sharedUser}\n\n联网搜索开关：{{#start.enableWebSearch#}}\n回答方式：{{#start.outputStyle#}}`,
  }),
  'sushijia-surrounding-chatflow.yml': surroundingAgent(),
  'sushijia-strategy-chatflow.yml': chatflow({
    name: '宿识家营销策略师',
    description: '先接收联网/天气情报，再生成阶段营销执行方案。',
    icon: '\\U0001F4A1',
    iconBg: '#FEF3C7',
    opening: '请填写营销目标、周期和渠道；系统会先补充必要的周边情报。',
    suggested: ['生成未来一周营销策略', '围绕天气做私域活动', '结合周边活动做转化方案'],
    vars: strategyVars,
    title: '营销策略',
    temperature: 0.45,
    system: sharedSystem('宿识家营销策略师', '输出 Markdown 方案，必须包含：## 核心目标与 KPI、## 执行时间表、## 各渠道内容计划、## 活动与定价承接、## 风险核验与复盘指标、## 底部执行动作。'),
    user: `${sharedUser}\n\n策略主题：{{#start.theme#}}\n经营目标：{{#start.objective#}}\n执行周期：{{#start.period#}}\n经营场景：{{#start.occasion#}}\n目标客群：{{#start.targetAudience#}}\n执行渠道：{{#start.channels#}}\n预算条件：{{#start.budgetLevel#}}\n执行能力：{{#start.executionCapacity#}}\n已确认市场信号：{{#start.marketSignals#}}\n竞品人工观察：{{#start.competitorObservations#}}\n可用权益：{{#start.availableOffers#}}\n执行限制：{{#start.constraints#}}`,
  }),
  'sushijia-pricing-chatflow.yml': chatflow({
    name: '宿识家收益定价顾问',
    description: '先接收联网/天气情报，再生成逐房型定价执行方案。',
    icon: '\\U0001F4C8',
    iconBg: '#DBEAFE',
    opening: '请提供日期、目标、房型挂牌价和销售信号；系统会先补充必要的外部情报。',
    suggested: ['未来一周怎么调价？', '天气会影响定价吗？', '竞品价格不确定时怎么定？'],
    vars: pricingVars,
    title: '智能定价',
    temperature: 0.3,
    system: sharedSystem('宿识家收益定价顾问', '输出 Markdown，并严格包含：## 经营结论摘要、## 逐房型定价执行表、## 可执行动作清单、## 需求与价格信号图表、## 数据来源与可信度、## 风险核验与复盘指标。每个房型至少一行。'),
    user: `${sharedUser}\n\n日期范围：{{#start.dateRange#}}\n定价周期：{{#start.pricingPeriod#}}\n定价目标：{{#start.pricingGoal#}}\n需求信号：{{#start.demandSignal#}}\n预订窗口：{{#start.bookingWindow#}}\n日期影响因素：{{#start.eventFactor#}}\n竞品价格观察：{{#start.competitorPriceRange#}}\n当前销售现象：{{#start.currentPriceNotes#}}\n最低可接受价：{{#start.priceFloor#}}\n重点销售渠道：{{#start.targetChannels#}}\n允许限时促销：{{#start.promotionAllowed#}}\n房型挂牌价快照：{{#start.roomSnapshot#}}`,
  }),
}

await mkdir(outputDir, { recursive: true })
for (const [file, content] of Object.entries(files)) {
  await writeFile(new URL(file, outputDir), content, 'utf8')
}

await writeFile(new URL('README.md', outputDir), `# 宿识家 Dify 核心经营 DSL v2

本目录本次重新生成 4 份核心 DSL：

1. \`sushijia-manager-chatflow.yml\`：AI 店长，可通过 \`enableWebSearch\` 控制是否先取联网/天气情报。
2. \`sushijia-surrounding-chatflow.yml\`：联网分析助手，\`taskMode=weather_only\` 时只查高德天气，\`taskMode=full\` 时再联网搜索。
3. \`sushijia-strategy-chatflow.yml\`：营销策略师，只使用后端注入的酒店资料、知识库和 \`surroundingContextJson\`。
4. \`sushijia-pricing-chatflow.yml\`：收益定价顾问，只使用后端注入的酒店资料、知识库和 \`surroundingContextJson\`。

后端调用顺序：AI 店长按开关决定是否预取 \`surrounding\`；营销策略和智能定价固定先预取 \`surrounding\`，再调用对应智能体。天气-only 问题只需要 \`weather_only\` 情报，由 AI 店长总结。
`, 'utf8')

console.log(`Generated ${Object.keys(files).length} Dify DSL files in docs/dify-dsl-v2`)
