<script setup lang="ts">
import { computed } from 'vue'
import { columnIndex, parsePlan, type PlanTable } from '@/utils/planMarkdown'

const props = defineProps<{
  content: string
  hiddenSectionPatterns?: string[]
}>()

const sections = computed(() => parsePlan(props.content || '').filter(section => !isHiddenSection(section.title)))

function cell(row: string[], index: number) {
  return row[index] || '待核实'
}

function hasTextContent(section: (typeof sections.value)[number]) {
  return section.paragraphs.length || section.bullets.length || section.tables.length
}

function isHiddenSection(title: string) {
  return (props.hiddenSectionPatterns || []).some(pattern => {
    try {
      return new RegExp(pattern).test(title)
    } catch {
      return title.includes(pattern)
    }
  })
}

function tableKey(table: PlanTable, index: number) {
  return `${table.headers.join('|')}-${index}`
}

function visibleTables(section: (typeof sections.value)[number]) {
  return section.tables
    .map(table => displayTable(section.title, table))
    .filter(table => table.rows.length)
}

function isUsefulTableRow(headers: string[], row: string[]) {
  const cells = row.map(cell => String(cell || '').trim())
  const filled = cells.filter(Boolean)
  if (!filled.length) return false
  if (filled.every(cell => /^待核实$|^pending$|^[-:：]+$/i.test(cell))) return false
  if (cells.every((cell, index) => !cell || normalizeCell(cell) === normalizeCell(headers[index] || ''))) return false
  if (filled.every(cell => headers.some(header => normalizeCell(header) === normalizeCell(cell)))) return false
  return true
}

function normalizeCell(value: string) {
  return String(value || '').replace(/\s+/g, '').toLowerCase()
}

function isBusinessConclusion(title: string) {
  return /经营结论|结论摘要/.test(title)
}

function conclusionItems(section: (typeof sections.value)[number]) {
  const tableLines = section.tables.flatMap(table => table.rows.flatMap(row => row.filter(Boolean)))
  return [...section.bullets, ...section.paragraphs, ...tableLines]
    .flatMap(line => String(line || '').split(/\n+/))
    .map(parseConclusionLine)
    .filter(item => item.raw)
}

function parseConclusionLine(line: string) {
  const raw = line.replace(/^[-*\d.、)\s]+/, '').trim()
  const match = raw.match(/^(.+?)[：:]\s*(.+?)(?:->|→|至)\s*(.+?)(?:[，,。]\s*(.*))?$/)
  if (!match) {
    return { raw, title: '经营建议', current: '', target: '', action: raw }
  }
  return {
    raw,
    title: match[1].trim(),
    current: normalizePriceLabel(match[2]),
    target: normalizePriceLabel(match[3]),
    action: (match[4] || '').trim() || '待核实执行动作',
  }
}

function normalizePriceLabel(value: string) {
  const text = value.trim()
  const numbers = text.match(/\d{2,5}/g) || []
  if (numbers.length >= 2) return `¥${numbers[0]} - ¥${numbers[1]}`
  if (numbers.length === 1) return `¥${numbers[0]}`
  return text || '待核实'
}

function shouldShowTable(section: (typeof sections.value)[number]) {
  return !isBusinessConclusion(section.title)
}

function isCopyExampleSection(title: string) {
  return /核心文案|文案示例|话术|素材/.test(title)
}

function copyExampleItems(section: (typeof sections.value)[number]) {
  return [...section.bullets, ...section.paragraphs]
    .map(line => cleanDisplayText(line))
    .filter(Boolean)
}

function displayTable(title: string, table: PlanTable) {
  const withoutStatus = /执行时间|时间表|阶段|节奏/.test(title)
    ? removeStatusColumn(table)
    : table
  return {
    headers: withoutStatus.headers,
    rows: withoutStatus.rows.filter(row => isUsefulTableRow(withoutStatus.headers, row)),
  }
}

function removeStatusColumn(table: PlanTable) {
  const statusIndex = table.headers.findIndex(header => /状态|status/i.test(header))
  if (statusIndex < 0) return table
  const actionIndex = columnIndex(table, ['具体动作', '动作', '执行内容'], Math.max(0, statusIndex - 1))
  return {
    headers: table.headers.filter((_, index) => index !== statusIndex),
    rows: table.rows.map(row => {
      const next = row.slice()
      const status = cleanDisplayText(next[statusIndex] || '')
      if (status && !/pending|待执行|进行中|完成|已完成|done/i.test(status)) {
        next[actionIndex] = [next[actionIndex], status].filter(Boolean).join('\n')
      }
      next.splice(statusIndex, 1)
      return next
    }),
  }
}

function cleanDisplayText(value: string) {
  return localizeParams(value)
    .replace(/^[-*]\s*/, '')
    .replace(/^\d+[.)]\s*/, '')
    .trim()
}

function displayCell(row: string[], index: number) {
  return localizeParams(cell(row, index))
}

function cellClass(value: string) {
  const text = localizeParams(value)
  return {
    'cell-price': /[¥￥]\s*\d|\d{2,5}\s*(?:元|~|-|至|—)/.test(text),
    'cell-percent': /\d+(?:\.\d+)?%/.test(text),
    'cell-pending': /待核实|需人工|无法确认|缺失/.test(text),
    'cell-positive': /上浮|上调|提升|增加|偏强|利好|支撑/.test(text),
    'cell-negative': /下调|降低|减少|偏弱|利空|抑制/.test(text),
  }
}

function localizeParams(value: string) {
  const labels: Record<string, string> = {
    eventFactor: '日期影响',
    demandSignal: '需求信号',
    bookingWindow: '预订窗口',
    pricingPeriod: '定价周期',
    pricingGoal: '定价目标',
    targetChannels: '渠道',
    packagePreference: '价格策略',
    riskLevel: '风险偏好',
    normal: '正常',
    'local-event': '本地活动',
    weekend: '周末',
    holiday: '节假日',
    weak: '偏弱',
    strong: '偏强',
    hot: '火热',
    unknown: '暂不确定',
    balance: '平衡价格与成交',
    ota: 'OTA 平台',
  }
  let text = String(value || '')
  Object.entries(labels).forEach(([key, label]) => {
    text = text.replace(new RegExp(`\\b${escapeRegExp(key)}\\b`, 'g'), label)
  })
  text = text.replace(/=/g, '：')
  return text
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
</script>

<template>
  <article class="ai-plan-document">
    <section v-for="section in sections.filter(hasTextContent)" :key="section.title" class="plan-section-block">
      <h2 :class="['plan-heading', `plan-heading-${Math.min(section.level, 3)}`]">{{ section.title }}</h2>

      <template v-if="hasTextContent(section)">
        <div v-if="isBusinessConclusion(section.title)" class="conclusion-grid">
          <article v-for="item in conclusionItems(section)" :key="item.raw" class="conclusion-card">
            <div class="conclusion-card-head">
              <h3>{{ item.title }}</h3>
              <span>{{ item.action }}</span>
            </div>
            <div class="conclusion-price-row">
              <div>
                <small>当前</small>
                <strong>{{ item.current || '待核实' }}</strong>
              </div>
              <div>
                <small>建议</small>
                <strong>{{ item.target || '待核实' }}</strong>
              </div>
            </div>
          </article>
        </div>

        <div v-if="isCopyExampleSection(section.title)" class="copy-example-grid">
          <article v-for="(item, index) in copyExampleItems(section)" :key="`${item}-${index}`" class="copy-example-card">
            <span>{{ index + 1 }}</span>
            <p>{{ item }}</p>
          </article>
        </div>

        <p v-for="paragraph in isBusinessConclusion(section.title) || isCopyExampleSection(section.title) ? [] : section.paragraphs" :key="paragraph" class="plan-paragraph whitespace-pre-line">
          {{ paragraph }}
        </p>

        <ul v-if="section.bullets.length && !isBusinessConclusion(section.title) && !isCopyExampleSection(section.title)" class="plan-list">
          <li v-for="item in section.bullets" :key="item">{{ item }}</li>
        </ul>

        <div v-for="(table, tableIndex) in shouldShowTable(section) ? visibleTables(section) : []" :key="tableKey(table, tableIndex)" class="plan-table-wrap">
          <table class="plan-table">
            <thead>
              <tr>
                <th v-for="header in table.headers" :key="header">{{ header }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rowIndex) in table.rows" :key="rowIndex">
                <td v-for="(_header, cellIndex) in table.headers" :key="cellIndex">
                  <span class="cell-content whitespace-pre-line" :class="cellClass(cell(row, cellIndex))">{{ displayCell(row, cellIndex) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
  </article>
</template>

<style scoped>
.ai-plan-document {
  min-width: 0;
  max-width: 100%;
  color: #4f4338;
  overflow-wrap: anywhere;
}

.plan-section-block + .plan-section-block {
  margin-top: 1.4rem;
}

.plan-heading {
  color: #234d32;
  font-weight: 700;
  letter-spacing: 0;
}

.plan-heading-1 {
  margin: 0 0 1rem;
  font-size: 1.35rem;
}

.plan-heading-2,
.plan-heading-3 {
  margin: 0 0 0.75rem;
  font-size: 1rem;
}

.plan-paragraph {
  margin: 0.85rem 0;
  color: #66594b;
  font-size: 0.94rem;
  line-height: 1.9;
}

.conclusion-grid {
  margin: 0.85rem 0 1.15rem;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 0.85rem;
}

.conclusion-card {
  min-width: 0;
  border: 1px solid #eadfce;
  border-radius: 0.85rem;
  background: #fffdfa;
  padding: 0.95rem;
}

.conclusion-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.7rem;
}

.conclusion-card-head h3 {
  color: #203f2b;
  font-size: 0.92rem;
  font-weight: 800;
  line-height: 1.45;
}

.conclusion-card-head span {
  max-width: 52%;
  border-radius: 999px;
  background: #eef7ea;
  padding: 0.22rem 0.55rem;
  color: #315b37;
  font-size: 0.68rem;
  font-weight: 800;
  line-height: 1.45;
  overflow-wrap: anywhere;
  text-align: right;
}

.conclusion-price-row {
  margin-top: 0.8rem;
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  gap: 0.65rem;
}

.conclusion-price-row > div {
  border-radius: 0.7rem;
  background: #faf7f1;
  padding: 0.65rem 0.75rem;
}

.conclusion-price-row small {
  display: block;
  color: #9a8772;
  font-size: 0.66rem;
  font-weight: 800;
}

.conclusion-price-row strong {
  display: block;
  margin-top: 0.2rem;
  color: #0f5a2a;
  font-size: 1.05rem;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.plan-list {
  margin: 0.75rem 0 1rem;
  display: grid;
  gap: 0.55rem;
}

.plan-list li {
  position: relative;
  border: 1px solid #eadfce;
  border-radius: 0.75rem;
  background: #fffaf2;
  padding: 0.7rem 0.85rem 0.7rem 2rem;
  color: #5f5143;
  font-size: 0.88rem;
  line-height: 1.7;
}

.plan-list li::before {
  content: "";
  position: absolute;
  left: 0.85rem;
  top: 1.18rem;
  height: 0.38rem;
  width: 0.38rem;
  border-radius: 999px;
  background: #4f7a42;
}

.copy-example-grid {
  margin: 0.9rem 0 1.1rem;
  display: grid;
  gap: 0.75rem;
}

.copy-example-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.75rem;
  border: 1px solid #eadfce;
  border-radius: 0.85rem;
  background: #fffdfa;
  padding: 0.9rem 1rem;
}

.copy-example-card span {
  display: inline-flex;
  height: 1.35rem;
  min-width: 1.35rem;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #eef7ea;
  color: #315b37;
  font-size: 0.68rem;
  font-weight: 900;
}

.copy-example-card p {
  min-width: 0;
  margin: 0;
  color: #5f5143;
  font-size: 0.88rem;
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-line;
}

.plan-table-wrap {
  margin: 1rem 0 1.25rem;
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid #eadfce;
  border-radius: 0.9rem;
  background: white;
}

.plan-table {
  width: 100%;
  min-width: 760px;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 0.84rem;
}

.plan-table th {
  background: #f5efe4;
  color: #234d32;
  font-weight: 700;
  text-align: left;
}

.plan-table th,
.plan-table td {
  border-bottom: 1px solid #eadfce;
  padding: 0.85rem 0.9rem;
  vertical-align: top;
  line-height: 1.7;
  overflow-wrap: anywhere;
  word-break: break-word;
  text-align: left;
}

.plan-table tr:last-child td {
  border-bottom: 0;
}

.plan-table td {
  color: #5f5143;
}

.cell-content {
  display: inline;
  border-radius: 0.45rem;
  padding: 0.05rem 0.18rem;
}

.cell-price,
.cell-percent {
  display: inline-block;
  background: #f4f8f0;
  color: #0f5a2a;
  font-weight: 900;
}

.cell-pending {
  display: inline-block;
  background: #faf7f1;
  color: #9a6f21;
  font-weight: 800;
}

.cell-positive {
  color: #166534;
  font-weight: 850;
}

.cell-negative {
  color: #b45309;
  font-weight: 850;
}
</style>
