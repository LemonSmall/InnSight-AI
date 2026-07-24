import { readdir, readFile } from 'node:fs/promises'
import { parse } from 'yaml'

const dslDir = new URL('../docs/dify-dsl/', import.meta.url)
const expected = {
  'article-editor-workflow.yml': ['workflow', 'title', 'style', 'length', 'withImage', 'imageCount', 'fileName'],
  'brain-manager-chatflow.yml': ['advanced-chat', 'message', 'outputStyle'],
  'knowledge-organizer-workflow.yml': ['workflow', 'sourceType', 'sourceText', 'fileName', 'extractionMode', 'effectiveHint'],
  'polish-copy-workflow.yml': ['workflow', 'sourceText', 'scene', 'field', 'style', 'purpose', 'immutableFacts'],
  'poster-visual-workflow.yml': ['workflow', 'mode', 'theme', 'content', 'style', 'scene', 'platform', 'targetAudience', 'textDensity', 'cta', 'sellingPoint', 'imageSize', 'width', 'height', 'imageData'],
  'pricing-revenue-workflow.yml': ['workflow', 'dateRange', 'pricingPeriod', 'pricingGoal', 'demandSignal', 'bookingWindow', 'eventFactor', 'competitorPriceRange', 'currentPriceNotes', 'priceFloor', 'maxDiscountPercent', 'targetChannels', 'promotionAllowed', 'packagePreference', 'riskLevel', 'constraints', 'roomSnapshot', 'evidenceRequirement'],
  'reply-specialist-workflow.yml': ['workflow', 'reviewText', 'reviewType', 'style', 'additionalNotes'],
  'review-guide-workflow.yml': ['workflow', 'guestType', 'scene', 'additionalNotes'],
  'strategy-marketing-workflow.yml': ['workflow', 'theme', 'objective', 'period', 'occasion', 'targetAudience', 'channels', 'budgetLevel', 'executionCapacity', 'outputDepth', 'marketSignals', 'competitorObservations', 'availableOffers', 'constraints', 'evidenceRequirement'],
  'video-director-workflow.yml': ['workflow', 'sellingPoints', 'view', 'style', 'goal', 'duration', 'count'],
  'wechat-editor-workflow.yml': ['workflow', 'slots', 'style', 'length', 'note', 'withImage', 'imageSize'],
  'xhs-content-workflow.yml': ['workflow', 'topics', 'customTopic', 'tone', 'style', 'note', 'withImage', 'imageSize', 'imageCount'],
}

const contextInputs = ['commonContextJson', 'businessParamsJson', 'knowledgeContextJson', 'message']
const secretPattern = /(?:app|ms|sk)-[A-Za-z0-9_-]{12,}/

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

const files = (await readdir(dslDir)).filter(file => file.endsWith('.yml')).sort()
assert(files.length === Object.keys(expected).length, `DSL 数量错误：期望 ${Object.keys(expected).length}，实际 ${files.length}`)

for (const file of files) {
  const contract = expected[file]
  assert(contract, `存在未登记 DSL：${file}`)

  const source = await readFile(new URL(file, dslDir), 'utf8')
  assert(!secretPattern.test(source), `${file} 包含疑似明文 Token`)
  const root = parse(source)
  const [expectedMode, ...businessParams] = contract
  const expectedInputs = [...contextInputs, ...businessParams.filter(param => param !== 'message')]

  assert(root?.kind === 'app', `${file}: kind 必须为 app`)
  assert(root?.version === '0.3.1', `${file}: version 必须为 0.3.1`)
  assert(Array.isArray(root?.dependencies), `${file}: dependencies 缺失`)
  assert(root?.app?.mode === expectedMode, `${file}: app.mode 应为 ${expectedMode}`)
  assert(Array.isArray(root?.workflow?.conversation_variables), `${file}: conversation_variables 缺失`)
  assert(Array.isArray(root?.workflow?.environment_variables), `${file}: environment_variables 缺失`)

  const nodes = root?.workflow?.graph?.nodes
  const edges = root?.workflow?.graph?.edges
  const isPoster = file === 'poster-visual-workflow.yml'
  assert(Array.isArray(nodes) && nodes.length === (isPoster ? 6 : 3), `${file}: 节点数量不符合预期`)
  assert(Array.isArray(edges) && edges.length === (isPoster ? 5 : 2), `${file}: 连线数量不符合预期`)

  const start = nodes.find(node => node?.data?.type === 'start')
  const llm = nodes.find(node => node?.data?.type === 'llm')
  const terminalType = expectedMode === 'advanced-chat' ? 'answer' : 'end'
  const terminal = nodes.find(node => node?.data?.type === terminalType)
  assert(start && llm && terminal, `${file}: 节点类型不完整`)
  if (isPoster) {
    assert(nodes.some(node => node?.data?.type === 'code'), `${file}: 海报 DSL 必须包含 Python 代码节点`)
    assert(nodes.some(node => node?.data?.type === 'http-request'), `${file}: 海报 DSL 必须包含 HTTP 请求节点`)
    assert(source.includes('https://api-inference.modelscope.cn/v1/images/generations'), `${file}: 海报 DSL 必须请求 ModelScope 生图接口`)
    assert(source.includes('{{MODELSCOPE_TOKEN}}'), `${file}: 海报 DSL 必须使用 ModelScope Token 占位符`)
  }

  const inputs = start.data.variables.map(variable => variable.variable)
  assert(JSON.stringify(inputs) === JSON.stringify(expectedInputs), `${file}: Start 输入必须包含上下文和所有业务字段：${expectedInputs.join(', ')}`)
  for (const input of contextInputs) {
    const variable = start.data.variables.find(item => item.variable === input)
    assert(variable?.required === true, `${file}: ${input} 必须为必填`)
  }

  const prompt = JSON.stringify(llm.data.prompt_template || [])
  for (const input of expectedInputs) {
    assert(prompt.includes(input), `${file}: Prompt 未引用 ${input}`)
  }
  for (const param of businessParams) {
    assert(prompt.includes(param), `${file}: System Prompt 未声明业务参数 ${param}`)
  }

  assert(llm.data.model?.provider === '', `${file}: DSL 不得硬编码模型供应商`)
  assert(llm.data.model?.name === '', `${file}: DSL 不得硬编码模型名称`)
  if (terminalType === 'answer') {
    assert(terminal.data.answer === '{{#llm.text#}}', `${file}: Answer 未输出 LLM 文本`)
  } else {
    const output = terminal.data.outputs?.find(item => item.variable === 'output')
    const expectedSelector = isPoster ? 'format_result.output' : 'llm.text'
    assert(output?.value_selector?.join('.') === expectedSelector, `${file}: End.output 未绑定 ${expectedSelector}`)
  }

  console.log(`OK ${file} (${expectedMode})`)
}

console.log(`Dify DSL validated: ${files.length}`)
