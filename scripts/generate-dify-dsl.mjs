import { mkdir, readdir, readFile, unlink, writeFile } from 'node:fs/promises'

const outputDir = new URL('../docs/dify-dsl/', import.meta.url)
const contractFile = new URL('../src/utils/aiModuleContract.ts', import.meta.url)

const modules = [
  { key: 'brain', file: 'brain-manager-chatflow.yml', name: '宿识家 AI 店长', description: '酒店经营问答和行动建议', temperature: 0.35, params: 'message（经营问题）、outputStyle（回答方式）', task: '先给结论，再给不超过 5 条可以直接执行的行动建议。信息不足时只列出必须补充的事实。', output: '自然中文，不输出 JSON。' },
  { key: 'strategy', file: 'strategy-marketing-chatflow.yml', name: '宿识家营销策略师', description: '阶段营销执行方案', temperature: 0.45, params: 'theme、objective、period、occasion、targetAudience、channels、budgetLevel、executionCapacity、outputDepth、marketSignals、competitorObservations、availableOffers、constraints、evidenceRequirement', task: '生成分阶段时间表、渠道任务、内容主题、转化动作、预算安排、检查指标和风险预案。人工市场观察只能标记为用户提供，不能包装成平台实时数据。', output: '使用层级清晰的 Markdown 方案。' },
  { key: 'pricing', file: 'pricing-revenue-chatflow.yml', name: '宿识家收益定价顾问', description: '房型挂牌价和人工信号定价建议', temperature: 0.3, params: 'dateRange、pricingPeriod、pricingGoal、demandSignal、bookingWindow、eventFactor、competitorPriceRange、currentPriceNotes、priceFloor、maxDiscountPercent、targetChannels、promotionAllowed、packagePreference、riskLevel、constraints、roomSnapshot、evidenceRequirement', task: '逐房型给出建议价格区间、适用条件、渠道动作、执行时点和风险。不得声称掌握实时房态、竞品价格或订单。', output: '使用 Markdown 表格和执行清单。' },
  { key: 'xhs', file: 'xhs-content-chatflow.yml', name: '宿识家小红书创作官', description: '小红书标题、正文、标签和配图建议', temperature: 0.65, params: 'topics、customTopic、tone、style、note、withImage、imageSize、imageCount', task: '生成适合小红书的标题、正文、标签、封面文字和配图建议。涉及设施、活动、价格和距离时只能使用已确认事实。', output: '仅输出 JSON：{"title":"","body":"","tags":[],"coverText":"","imageSuggestions":[],"publishTips":""}。' },
  { key: 'wechat', file: 'wechat-editor-chatflow.yml', name: '宿识家朋友圈编辑', description: '分时段朋友圈文案', temperature: 0.6, params: 'slots、style、length、note、withImage、imageSize', task: '只为用户选中的发布时段生成文案，各时段内容不得重复，不虚构当天活动和天气。', output: '仅输出 JSON：{"morning":"","noon":"","evening":"","imageSuggestions":[],"publishSchedule":[]}。未选时段返回空字符串。' },
  { key: 'article', file: 'article-editor-chatflow.yml', name: '宿识家公众号主编', description: '公众号标题、摘要和长文', temperature: 0.5, params: 'title、style、length、withImage、imageCount、fileName', task: '生成可编辑的公众号文章，结构完整，事实来自酒店资料和有效知识。文件名不是文件内容，不能据此虚构文档事实。', output: '仅输出 JSON：{"title":"","summary":"","content":"","imageSuggestions":[]}。' },
  { key: 'video', file: 'video-director-chatflow.yml', name: '宿识家短视频编导', description: '短视频口播和分镜', temperature: 0.65, params: 'sellingPoints、view、style、goal、duration、count', task: '按用户指定视角、目标、时长和数量生成口播脚本、镜头建议和 BGM 方向，卖点必须经过酒店资料验证。', output: '仅输出 JSON：{"scripts":[],"shots":[],"publishTips":"","bgm":""}。' },
  { key: 'poster', file: 'poster-visual-chatflow.yml', name: '宿识家营销视觉设计师', description: '营销海报图片提示词和图片结果', temperature: 0.45, params: 'mode、theme、content、style、scene、platform、targetAudience、textDensity、cta、sellingPoint、imageSize、width、height、imageData', task: '完整保留用户选择的图片比例、宽高和营销参数，先生成适合文生图模型的画面提示词，再由代码节点提纯 prompt 并交给 ModelScope HTTP 节点创建图片任务。不得把比例当作前端画布缩放指令。', output: '仅输出 JSON：{"imageUrl":"","imageStatus":"","imageTaskId":"","prompt":"","imageSize":"","width":0,"height":0,"title":"","content":""}。有真实图片地址时 imageUrl 必须是完整 URL。' },
  { key: 'polish', file: 'polish-copy-chatflow.yml', name: '宿识家文案润色师', description: '业务自由文本润色', temperature: 0.4, params: 'sourceText、scene、field、style、purpose、immutableFacts', task: '只优化表达、结构和可读性。不得修改酒店名、价格、日期、地点、设施、政策、评价原文及 immutableFacts 中的事实。', output: '只输出润色后的中文文本，不解释、不加标题、不输出 JSON。' },
  { key: 'review', file: 'review-guide-chatflow.yml', name: '宿识家好评引导师', description: '合规评价邀请和离店感谢', temperature: 0.45, params: 'guestType、scene、additionalNotes', task: '生成自然的离店感谢和真实评价邀请，不诱导虚假评价，不承诺返现换好评，不代替客人编造入住体验。', output: '只输出可直接使用的中文话术。' },
  { key: 'reply', file: 'reply-specialist-chatflow.yml', name: '宿识家点评回复专员', description: '针对真实评价生成回复', temperature: 0.4, params: 'reviewText、reviewType、style、additionalNotes', task: '有 reviewText 时逐点回应真实评价；没有原文时只能生成场景模板并明确待替换内容。不得否认客人感受或编造补偿。', output: '只输出最终中文回复。' },
  { key: 'knowledge', file: 'knowledge-organizer-chatflow.yml', name: '宿识家知识整理员', description: '酒店资料事实提取和入库建议', temperature: 0.15, params: 'sourceType、sourceText、fileName、extractionMode、effectiveHint', task: '从 sourceText 提取独立、可核验的酒店事实。不得补全原文没有的设施、日期、价格或政策。每项知识都必须等待人工确认后才能生效。', output: '仅输出 JSON：{"summary":"","items":[{"category":"","title":"","content":"","effectiveFrom":"","effectiveTo":"","confidence":0,"needConfirm":true}]}。' },
]

const chatflowModules = new Set(['brain'])
for (const module of modules) {
  module.appType = chatflowModules.has(module.key) ? 'chatflow' : 'workflow'
  if (module.appType === 'workflow') {
    module.file = module.file.replace('-chatflow.yml', '-workflow.yml')
  }
  module.paramNames = parseParamNames(module.params)
}

function parseParamNames(value) {
  return value
    .replace(/（[^）]*）/g, '')
    .split('、')
    .map(item => item.trim())
    .filter(Boolean)
}

function parseContract(source) {
  const params = new Map()
  const moduleParams = new Map()
  const callPatternSource = String.raw`p\('([^']+)'\s*,\s*'([^']+)'\s*,\s*'([^']+)'\s*,\s*'([^']+)'(?:\s*,\s*(true|false))?\)`
  for (const match of source.matchAll(new RegExp(callPatternSource, 'g'))) {
    const [, name, label, type, sourceName, required] = match
    if (!params.has(name) || required === 'true') {
      params.set(name, { name, label, type, source: sourceName, required: required === 'true' })
    }
  }
  for (const module of modules) {
    const pattern = new RegExp(`key:\\s*'${module.key}'[\\s\\S]*?params:\\s*\\[([^\\]]*)\\]`)
    const moduleMatch = source.match(pattern)
    const definitions = new Map()
    if (moduleMatch) {
      for (const match of moduleMatch[1].matchAll(new RegExp(callPatternSource, 'g'))) {
        const [, name, label, type, sourceName, required] = match
        definitions.set(name, { name, label, type, source: sourceName, required: required === 'true' })
      }
    }
    moduleParams.set(module.key, definitions)
  }
  const contextParams = [
    { name: 'commonContextJson', label: '酒店与调用上下文JSON', type: 'string', source: '后端鉴权与酒店资料', required: true, maxLength: 50000 },
    { name: 'businessParamsJson', label: '本次业务参数JSON', type: 'string', source: '用户端当前表单', required: true, maxLength: 50000 },
    { name: 'knowledgeContextJson', label: '已确认知识上下文JSON', type: 'string', source: '后端知识检索', required: true, maxLength: 50000 },
    { name: 'message', label: '主要问题或主题', type: 'string', source: '后端从业务参数提取', required: true, maxLength: 10000 },
  ]
  for (const item of contextParams) params.set(item.name, item)
  return { params, moduleParams }
}

const contractDefinitions = parseContract(await readFile(contractFile, 'utf8'))

function paramDefinition(name, module) {
  return contractDefinitions.moduleParams.get(module?.key)?.get(name)
    || contractDefinitions.params.get(name)
    || { name, label: name, type: 'string', source: '用户端当前表单', required: false }
}

function yamlEscape(value) {
  return String(value).replace(/"/g, '\\"')
}

function difyInputType(field) {
  if (field.type === 'number') return 'number'
  if (field.type === 'boolean') return 'checkbox'
  return 'text-input'
}

function startVariableLines(module) {
  const fields = [
    paramDefinition('commonContextJson'),
    paramDefinition('businessParamsJson'),
    paramDefinition('knowledgeContextJson'),
    paramDefinition('message'),
    ...module.paramNames.filter(name => name !== 'message').map(name => paramDefinition(name, module)),
  ]
  return fields.map((field) => {
    const suffix = field.source ? `；来源：${field.source}` : ''
    const typeLabel = field.type && field.type !== 'string' ? `；类型：${field.type}` : ''
    const label = `${field.label || field.name}${typeLabel}${suffix}`
    return `            - {variable: ${field.name}, label: "${yamlEscape(label)}", type: ${difyInputType(field)}, required: ${field.required ? 'true' : 'false'}, max_length: ${field.maxLength || 20000}, options: []}`
  }).join('\n')
}

function businessParamDetails(module) {
  return module.paramNames.map((name) => {
    const field = paramDefinition(name, module)
    const required = field.required ? '必填' : '选填'
    return `- ${name}：${field.label || name}，${required}，来源：${field.source || '用户端当前表单'}。`
  }).join('\n')
}

function explicitBusinessParamPrompt(module) {
  return module.paramNames
    .filter(name => name !== 'message')
    .map(name => `                - ${name}：{{#start.${name}#}}`)
    .join('\n')
}

function indentBlock(text, spaces) {
  const prefix = ' '.repeat(spaces)
  return text.split('\n').map(line => `${prefix}${line}`).join('\n')
}

function posterExtractCode() {
  return `import json
import re

def _json_from_text(text: str) -> dict:
    text = (text or "").strip()
    fence = chr(96) * 3
    text = re.sub(r"^\\s*" + fence + r"(?:json)?\\s*", "", text)
    text = re.sub(r"\\s*" + fence + r"\\s*$", "", text)
    try:
        return json.loads(text)
    except Exception:
        pass
    match = re.search(r"\\{[\\s\\S]*\\}", text)
    if match:
        try:
            return json.loads(match.group(0))
        except Exception:
            pass
    return {"prompt": text}

def _int_value(value, fallback: int) -> int:
    try:
        parsed = int(float(str(value).strip()))
        return parsed if parsed > 0 else fallback
    except Exception:
        return fallback

def _default_size(image_size: str) -> tuple[int, int]:
    normalized = (image_size or "").replace("：", ":").strip()
    mapping = {
        "1:1": (1024, 1024),
        "3:4": (768, 1024),
        "4:3": (1024, 768),
        "9:16": (768, 1344),
        "16:9": (1344, 768),
    }
    return mapping.get(normalized, (1024, 1024))

def main(text: str, imageSize: str, width: str, height: str) -> dict:
    data = _json_from_text(text)
    default_width, default_height = _default_size(imageSize)
    final_width = _int_value(width, default_width)
    final_height = _int_value(height, default_height)
    prompt = (
        data.get("prompt")
        or data.get("image_prompt")
        or data.get("visualPrompt")
        or data.get("content")
        or ""
    )
    prompt = str(prompt).strip()
    prompt = re.sub(r"<think>[\\s\\S]*?</think>", "", prompt, flags=re.I).strip()
    if not prompt:
        prompt = str(text or "").strip()
    request_body = {
        "model": "Tongyi-MAI/Z-Image-Turbo",
        "prompt": prompt,
        "width": final_width,
        "height": final_height,
    }
    return {
        "prompt": prompt,
        "image_size": imageSize or f"{final_width}:{final_height}",
        "width": final_width,
        "height": final_height,
        "title": str(data.get("title") or ""),
        "content": str(data.get("content") or ""),
        "request_body": json.dumps(request_body, ensure_ascii=False),
    }`
}

function posterFormatCode() {
  return `import json

def main(body: str, prompt: str, image_size: str, width: int, height: int, title: str, content: str) -> dict:
    try:
        data = json.loads(body or "{}")
    except Exception:
        data = {"raw": body or ""}
    task_id = data.get("task_id") or data.get("id") or ""
    output_images = data.get("output_images") or []
    image_url = output_images[0] if output_images else ""
    status = data.get("task_status") or ("SUCCEED" if image_url else "SUBMITTED")
    result = {
        "imageUrl": image_url,
        "imageStatus": status,
        "imageTaskId": task_id,
        "prompt": prompt,
        "imageSize": image_size,
        "width": width,
        "height": height,
        "title": title,
        "content": content,
        "raw": data,
    }
    return {"output": json.dumps(result, ensure_ascii=False)}`
}

function dsl(module) {
  if (module.key === 'poster') {
    return posterDsl(module)
  }
  const isChatflow = module.appType === 'chatflow'
  const appMode = isChatflow ? 'advanced-chat' : 'workflow'
  const terminalId = isChatflow ? 'answer' : 'end'
  const terminalType = isChatflow ? 'answer' : 'end'
  const systemQuery = isChatflow
    ? `\n\n                当前用户输入：\n                {{#sys.query#}}`
    : ''
  const terminalNode = isChatflow
    ? `      - id: answer
        type: custom
        height: 104
        width: 244
        position: {x: 790, y: 260}
        positionAbsolute: {x: 790, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 仅向用户输出最终业务结果
          title: 回复
          type: answer
          variables: []
          answer: "{{#llm.text#}}"`
    : `      - id: end
        type: custom
        height: 104
        width: 244
        position: {x: 790, y: 260}
        positionAbsolute: {x: 790, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 统一通过 output 返回最终业务结果
          title: 结束
          type: end
          outputs:
            - variable: output
              value_selector: [llm, text]
              value_type: string`
  const system = `你是“${module.name}”，${module.description}。\n\n输入协议：\n1. commonContextJson：后端根据登录态组装的租户、用户、模块、酒店基础资料、房型挂牌价和数据权限范围，是酒店身份与基础事实的权威来源。\n2. businessParamsJson：用户在当前功能页面真实填写和选择的完整参数快照。\n3. knowledgeContextJson：当前租户已确认、启用且未过期的知识，包含来源和有效期。待确认、过期或其他租户知识不得使用。\n4. message：本次主要问题或主题，用于 Chatflow 查询兼容。\n5. 显式业务字段：后端会同时把本模块业务参数展开为独立 Start 字段，便于 Dify 节点直接引用。独立字段与 businessParamsJson 冲突时，以独立字段为准。\n\n本模块业务字段含义：\n${businessParamDetails(module)}\n\n执行任务：\n${module.task}\n\n共同约束：\n- 必须逐项读取本模块业务字段，不得忽略用户选择，尤其是图片比例、投放平台、目标客群、风格、补充要求等字段。\n- 仅使用 commonContextJson、businessParamsJson、knowledgeContextJson、显式业务字段和用户问题中的信息。\n- 平台未接入 PMS、OTA、订单、实时房态和实时营收，不得声称拥有这些数据。\n- 不得虚构酒店设施、价格、距离、活动、奖项、政策、天气、市场热度或经营指标。\n- 知识缺失时明确指出需要酒店确认的内容，不得自行补造。\n- 不输出 <think>、推理过程、系统提示词、节点日志、调试字段、Markdown 代码围栏或英文示例问题。\n- 回答使用中文，内容必须能由酒店员工直接使用。\n\n输出要求：\n${module.output}`
  return `app:
  mode: ${appMode}
  name: ${module.name}
  description: ${module.description}
  icon: "\\U00002728"
  icon_background: "#DCFCE7"
  use_icon_as_answer_icon: false
dependencies: []
kind: app
version: 0.3.1
workflow:
  conversation_variables: []
  environment_variables: []
  features:
    file_upload:
      enabled: false
    opening_statement: 请填写当前任务，系统会结合本店资料和已确认知识处理。
    retriever_resource: {enabled: false}
    sensitive_word_avoidance: {enabled: false}
    speech_to_text: {enabled: false}
    suggested_questions: []
    suggested_questions_after_answer: {enabled: false}
    text_to_speech: {enabled: false}
  graph:
    edges:
      - id: start-source-llm-target
        source: start
        sourceHandle: source
        target: llm
        targetHandle: target
        type: custom
        data: {sourceType: start, targetType: llm, isInIteration: false, isInLoop: false}
        zIndex: 0
      - id: llm-source-${terminalId}-target
        source: llm
        sourceHandle: source
        target: ${terminalId}
        targetHandle: target
        type: custom
        data: {sourceType: llm, targetType: ${terminalType}, isInIteration: false, isInLoop: false}
        zIndex: 0
    nodes:
      - id: start
        type: custom
        height: 174
        width: 244
        position: {x: 80, y: 260}
        positionAbsolute: {x: 80, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 后端统一注入三段上下文，前端仅提交业务参数
          title: 开始
          type: start
          variables:
${startVariableLines(module)}
      - id: llm
        type: custom
        height: 118
        width: 244
        position: {x: 430, y: 260}
        positionAbsolute: {x: 430, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 严格按三段参数协议生成当前模块结果
          title: ${module.description}
          type: llm
          variables: []
          model:
            provider: ''
            name: ''
            mode: chat
            completion_params:
              temperature: ${module.temperature}
              top_p: 0.9
          prompt_template:
            - id: system
              role: system
              text: |
${indentBlock(system, 16)}
            - id: user
              role: user
              text: |
                酒店与调用上下文：
                {{#start.commonContextJson#}}

                本次业务参数：
                {{#start.businessParamsJson#}}

                已确认知识上下文：
                {{#start.knowledgeContextJson#}}

                主要问题或主题：
                {{#start.message#}}

                显式业务字段：
${explicitBusinessParamPrompt(module)}${systemQuery}
          context: {enabled: false, variable_selector: []}
          vision: {enabled: false, configs: {variable_selector: []}}
          memory:
            enabled: false
            window: {enabled: false, size: 10}
          structured_output: {enabled: false}
          retry_config:
            enabled: true
            max_retries: 2
            retry_interval: 1000
            exponential_backoff: {enabled: true, multiplier: 2, max_interval: 5000}
${terminalNode}
    viewport: {x: 0, y: 0, zoom: 0.8}
`
}

function posterDsl(module) {
  const system = `你是“${module.name}”，${module.description}。\n\n输入协议：\n1. commonContextJson：后端根据登录态组装的租户、用户、模块、酒店基础资料、房型挂牌价和数据权限范围，是酒店身份与基础事实的权威来源。\n2. businessParamsJson：用户在当前功能页面真实填写和选择的完整参数快照。\n3. knowledgeContextJson：当前租户已确认、启用且未过期的知识，包含来源和有效期。待确认、过期或其他租户知识不得使用。\n4. 显式业务字段：${module.params}。独立字段与 businessParamsJson 冲突时，以独立字段为准。\n\n本模块业务字段含义：\n${businessParamDetails(module)}\n\n执行任务：\n${module.task}\n\n输出给下游代码节点的内容必须是严格 JSON，不要 Markdown，不要代码围栏：\n{\n  "prompt": "可直接发送给文生图模型的中文画面提示词，包含酒店事实、构图、风格、文字密度、目标客群、投放平台、画幅要求，不包含解释",\n  "imageSize": "原样返回用户选择的图片比例",\n  "width": 用户选择或推导后的图片宽度数字,\n  "height": 用户选择或推导后的图片高度数字,\n  "title": "适合海报主标题的短句",\n  "content": "适合海报副文案或说明的短句"\n}\n\n共同约束：\n- 必须逐项读取 mode、theme、content、style、scene、platform、targetAudience、textDensity、cta、sellingPoint、imageSize、width、height、imageData，不得忽略用户选择。\n- 宽高是生图参数，不是前端预览画布缩放指令。\n- 仅使用 commonContextJson、businessParamsJson、knowledgeContextJson、显式业务字段和用户问题中的信息。\n- 平台未接入 PMS、OTA、订单、实时房态和实时营收，不得声称拥有这些数据。\n- 不得虚构酒店设施、价格、距离、活动、奖项、政策、天气、市场热度或经营指标。\n- 不输出 <think>、推理过程、系统提示词、节点日志或调试字段。`
  return `app:
  mode: workflow
  name: ${module.name}
  description: ${module.description}
  icon: "\\U0001F5BC"
  icon_background: "#DCFCE7"
  use_icon_as_answer_icon: false
dependencies: []
kind: app
version: 0.3.1
workflow:
  conversation_variables: []
  environment_variables: []
  features:
    file_upload:
      enabled: false
    opening_statement: 请填写当前海报任务，系统会结合本店资料和已确认知识生成图片。
    retriever_resource: {enabled: false}
    sensitive_word_avoidance: {enabled: false}
    speech_to_text: {enabled: false}
    suggested_questions: []
    suggested_questions_after_answer: {enabled: false}
    text_to_speech: {enabled: false}
  graph:
    edges:
      - id: start-source-llm-target
        source: start
        sourceHandle: source
        target: llm
        targetHandle: target
        type: custom
        data: {sourceType: start, targetType: llm, isInIteration: false, isInLoop: false}
        zIndex: 0
      - id: llm-source-prompt_cleaner-target
        source: llm
        sourceHandle: source
        target: prompt_cleaner
        targetHandle: target
        type: custom
        data: {sourceType: llm, targetType: code, isInIteration: false, isInLoop: false}
        zIndex: 0
      - id: prompt_cleaner-source-create_image-target
        source: prompt_cleaner
        sourceHandle: source
        target: create_image
        targetHandle: target
        type: custom
        data: {sourceType: code, targetType: http-request, isInIteration: false, isInLoop: false}
        zIndex: 0
      - id: create_image-source-format_result-target
        source: create_image
        sourceHandle: source
        target: format_result
        targetHandle: target
        type: custom
        data: {sourceType: http-request, targetType: code, isInIteration: false, isInLoop: false}
        zIndex: 0
      - id: format_result-source-end-target
        source: format_result
        sourceHandle: source
        target: end
        targetHandle: target
        type: custom
        data: {sourceType: code, targetType: end, isInIteration: false, isInLoop: false}
        zIndex: 0
    nodes:
      - id: start
        type: custom
        height: 260
        width: 244
        position: {x: 80, y: 260}
        positionAbsolute: {x: 80, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 后端注入三段上下文，并展开海报业务参数
          title: 开始
          type: start
          variables:
${startVariableLines(module)}
      - id: llm
        type: custom
        height: 118
        width: 244
        position: {x: 380, y: 260}
        positionAbsolute: {x: 380, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 根据海报参数生成结构化视觉方案
          title: 视觉方案
          type: llm
          variables: []
          model:
            provider: ''
            name: ''
            mode: chat
            completion_params:
              temperature: ${module.temperature}
              top_p: 0.9
          prompt_template:
            - id: system
              role: system
              text: |
${indentBlock(system, 16)}
            - id: user
              role: user
              text: |
                酒店与调用上下文：
                {{#start.commonContextJson#}}

                本次业务参数：
                {{#start.businessParamsJson#}}

                已确认知识上下文：
                {{#start.knowledgeContextJson#}}

                主要问题或主题：
                {{#start.message#}}

                显式业务字段：
${explicitBusinessParamPrompt(module)}
          context: {enabled: false, variable_selector: []}
          vision: {enabled: false, configs: {variable_selector: []}}
          memory:
            enabled: false
            window: {enabled: false, size: 10}
          structured_output: {enabled: false}
          retry_config:
            enabled: true
            max_retries: 2
            retry_interval: 1000
            exponential_backoff: {enabled: true, multiplier: 2, max_interval: 5000}
      - id: prompt_cleaner
        type: custom
        height: 54
        width: 244
        position: {x: 680, y: 260}
        positionAbsolute: {x: 680, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 提取干净 prompt，并组装 ModelScope 请求体
          title: Python 提纯 Prompt
          type: code
          code_language: python3
          code: |
${indentBlock(posterExtractCode(), 12)}
          variables:
            - value_selector: [llm, text]
              variable: text
            - value_selector: [start, imageSize]
              variable: imageSize
            - value_selector: [start, width]
              variable: width
            - value_selector: [start, height]
              variable: height
          outputs:
            prompt: {type: string, children: null}
            image_size: {type: string, children: null}
            width: {type: number, children: null}
            height: {type: number, children: null}
            title: {type: string, children: null}
            content: {type: string, children: null}
            request_body: {type: string, children: null}
      - id: create_image
        type: custom
        height: 170
        width: 244
        position: {x: 980, y: 260}
        positionAbsolute: {x: 980, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 创建 ModelScope 文生图异步任务。导入后请把 MODELSCOPE_TOKEN 替换为 Dify 环境变量或真实密钥。
          title: ModelScope 创建图片任务
          type: http-request
          method: POST
          url: https://api-inference.modelscope.cn/v1/images/generations
          authorization:
            type: no-auth
          headers: |-
            Authorization: Bearer {{MODELSCOPE_TOKEN}}
            Content-Type: application/json
            X-ModelScope-Async-Mode: true
          params: ''
          body:
            type: json
            data: '{{#prompt_cleaner.request_body#}}'
          timeout:
            connect: 10
            read: 60
            write: 30
          retry_config:
            enabled: true
            max_retries: 1
            retry_interval: 1000
            exponential_backoff: {enabled: false, multiplier: 2, max_interval: 10000}
          variables: []
      - id: format_result
        type: custom
        height: 54
        width: 244
        position: {x: 1280, y: 260}
        positionAbsolute: {x: 1280, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 合并图片任务响应和提示词参数
          title: Python 格式化结果
          type: code
          code_language: python3
          code: |
${indentBlock(posterFormatCode(), 12)}
          variables:
            - value_selector: [create_image, body]
              variable: body
            - value_selector: [prompt_cleaner, prompt]
              variable: prompt
            - value_selector: [prompt_cleaner, image_size]
              variable: image_size
            - value_selector: [prompt_cleaner, width]
              variable: width
            - value_selector: [prompt_cleaner, height]
              variable: height
            - value_selector: [prompt_cleaner, title]
              variable: title
            - value_selector: [prompt_cleaner, content]
              variable: content
          outputs:
            output: {type: string, children: null}
      - id: end
        type: custom
        height: 104
        width: 244
        position: {x: 1580, y: 260}
        positionAbsolute: {x: 1580, y: 260}
        selected: false
        sourcePosition: right
        targetPosition: left
        data:
          desc: 返回图片任务、prompt、宽高和最终字段
          title: 结束
          type: end
          outputs:
            - variable: output
              value_selector: [format_result, output]
              value_type: string
    viewport: {x: 0, y: 0, zoom: 0.65}
`
}

await mkdir(outputDir, { recursive: true })
for (const file of await readdir(outputDir)) {
  if (file.endsWith('.yml')) {
    await unlink(new URL(file, outputDir))
  }
}
for (const module of modules) {
  await writeFile(new URL(module.file, outputDir), dsl(module), 'utf8')
}

const table = modules.map(module => `| ${module.key} | ${module.name} | ${module.appType} | \`${module.file}\` | ${module.params} |`).join('\n')
const detailSections = modules.map(module => {
  const rows = module.paramNames.map((name) => {
    const field = paramDefinition(name, module)
    return `| ${name} | ${field.label || name} | ${field.type || 'string'} | ${field.required ? '是' : '否'} | ${field.source || ''} |`
  }).join('\n')
  return `## ${module.name}\n\n| 字段 | 含义 | 类型 | 必填 | 来源 |\n| --- | --- | --- | --- | --- |\n${rows}`
}).join('\n\n')
await writeFile(new URL('parameter-contract.md', outputDir), `# Dify 参数合同\n\n所有应用统一接收 \`commonContextJson\`、\`businessParamsJson\`、\`knowledgeContextJson\` 和兼容字段 \`message\`。同时，每个模块会把用户端实际业务字段展开为 Dify Start 独立输入，便于在 Dify 节点里直接引用、分支和调试。\n\n前三项由宿识家后端组装，Dify 不负责识别租户身份。独立业务字段与 \`businessParamsJson\` 冲突时，以独立业务字段为准。\n\n| moduleKey | 智能体 | 应用类型 | DSL 文件 | 显式业务字段 |\n| --- | --- | --- | --- | --- |\n${table}\n\n${detailSections}\n`, 'utf8')
await writeFile(new URL('system-prompts.md', outputDir), `# System Prompt 索引\n\n${modules.map(module => `## ${module.name}\n\n- 模块：\`${module.key}\`\n- 业务字段：${module.params}\n- 任务：${module.task}\n- 输出：${module.output}\n`).join('\n')}`, 'utf8')
await writeFile(new URL('README.md', outputDir), `# 宿识家 Dify 独立智能体 DSL\n\n本目录包含 ${modules.length} 个独立应用。需要连续经营对话的 brain 使用 Chatflow；其余确定性生成、润色、回复、策略、定价、海报和知识整理任务使用 Workflow。每个功能必须导入为独立 Dify App、单独发布、生成独立 App API Key，再在宿识家管理端绑定。\n\n所有 DSL 使用官方仓库当前样例结构 \`version: 0.3.1\`。Chatflow 通过 Answer 节点输出，Workflow 通过 End 节点的 \`output\` 字段输出；宿识家后端均使用 \`response_mode=streaming\` 接收事件。\n\n${modules.map((module, index) => `${index + 1}. \`${module.file}\`：${module.name}（${module.appType}）`).join('\n')}\n\n禁止把一个 App API Key 同时绑定给多个 moduleKey。DSL 故意不绑定具体模型，导入后必须在 Dify 中选择当前工作区真实可用的模型并重新发布。\n`, 'utf8')
await writeFile(new URL('import-guide.md', outputDir), `# 导入与绑定\n\n1. 在 Dify 工作室逐个选择“导入 DSL”。\n2. 打开 LLM 节点，将占位模型替换为工作区已配置模型。\n3. 预览时填写三个 JSON 参数和 message，确认没有变量缺失。\n4. 发布应用，在“访问 API”中创建该 App 独立 API Key。\n5. 在宿识家管理端选择相同 moduleKey，填写名称、Endpoint、App API Key；绑定类型必须与参数合同中的 Chatflow/Workflow 一致。\n6. 保存并校验。校验通过后再启用；同模块其他候选会自动停用。\n7. 通过宿识家用户端调用，确认请求日志中存在三个 JSON 参数且未泄露 API Key。\n\n海报应用的 DSL 会在 Dify Workflow 内部生成结构化视觉提示词，并通过 HTTP 节点调用 ModelScope 创建图片任务；宿识家后端默认只调用 Dify 并接收图片 URL。Token 不得提交到源码或 DSL，请在 Dify 凭据/环境变量中替换 MODELSCOPE_TOKEN 占位符。\n`, 'utf8')
await writeFile(new URL('test-cases.md', outputDir), `# 验收用例\n\n每个智能体至少验证：正常参数、空知识、过期知识、缺少酒店资料、其他租户知识不可见、非法 JSON、第三方超时、流式增量、输出不含 think。\n\n内容模块还需验证所有页面选择均出现在 businessParamsJson。poster 分别验证 1:1、3:4、16:9、9:16，返回比例必须与输入一致。polish 验证价格、日期、酒店名不被修改。knowledge 验证输出仍需人工确认，不能直接成为 active 知识。\n`, 'utf8')
