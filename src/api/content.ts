import { fetchEventSource } from '@microsoft/fetch-event-source'
import api from './index'

/**
 * Submit an async AI content generation task.
 * moduleKey: brain/xhs/wechat/video/poster/article/review/reply
 */
export function generateContent(moduleKey: string, params: Record<string, any>) {
  return api.post('/api/content/generate', { moduleKey, params }).then(assertBusinessOk)
}

export interface StreamGenerateHandlers {
  onStatus?: (message: string) => void
  onChunk?: (text: string) => void
  onDone?: (payload: Record<string, any>) => void
  onError?: (message: string) => void
  timeoutMs?: number
}

export interface CollectStreamHandlers extends Omit<StreamGenerateHandlers, 'onChunk'> {
  onChunk?: (chunk: string, content: string) => void
}

export async function collectStreamContent(
  moduleKey: string,
  params: Record<string, any>,
  handlers: CollectStreamHandlers = {}
) {
  return collectStreamContentInternal('/api/ai/generations/stream', moduleKey, params, handlers)
}

export async function collectStreamContentWithFile(
  moduleKey: string,
  params: Record<string, any>,
  file: File,
  handlers: CollectStreamHandlers = {}
) {
  const form = new FormData()
  form.append('moduleKey', moduleKey)
  form.append('params', JSON.stringify(params || {}))
  form.append('file', file)
  return collectStreamContentInternal('/api/ai/generations/stream-file', moduleKey, params, handlers, form)
}

async function collectStreamContentInternal(
  url: string,
  moduleKey: string,
  params: Record<string, any>,
  handlers: CollectStreamHandlers = {},
  body?: BodyInit
) {
  let content = ''
  let doneContent = ''

  await streamGenerateContent(moduleKey, params, {
    url,
    body,
    onStatus: handlers.onStatus,
    timeoutMs: handlers.timeoutMs,
    onChunk(chunk) {
      const nextContent = mergeStreamContent(content, chunk)
      const changed = nextContent !== content
      const delta = nextContent.startsWith(content) ? nextContent.slice(content.length) : nextContent
      content = nextContent
      if (changed) {
        const visibleContent = sanitizeAiContent(content)
        handlers.onChunk?.(delta || chunk, visibleContent)
      }
    },
    onDone(payload) {
      const finalContent = extractFinalContent(payload)
      if (finalContent) {
        doneContent = finalContent
      }
      handlers.onDone?.(payload)
    },
    onError(message) {
      handlers.onError?.(message)
      throw new Error(message || 'AI 调用失败，请稍后重试')
    },
  })

  if (doneContent) {
    const finalContent = sanitizeAiContent(doneContent)
    await revealFinalContent(finalContent, content, handlers)
    return finalContent
  }

  return sanitizeAiContent(content)
}

export async function streamGenerateContent(
  moduleKey: string,
  params: Record<string, any>,
  handlers: StreamGenerateHandlers & { url?: string; body?: BodyInit } = {}
) {
  const token = localStorage.getItem('hotel_access_token')
  const baseURL = import.meta.env.VITE_API_BASE || ''
  const controller = new AbortController()
  let doneReceived = false
  let errorReceived = false
  let timedOut = false
  const timeoutMs = normalizeStreamTimeoutMs(handlers.timeoutMs)
  const timeoutTimer = setTimeout(() => {
    if (!doneReceived && !errorReceived) {
      timedOut = true
      controller.abort()
    }
  }, timeoutMs)

  try {
    const isFormBody = typeof FormData !== 'undefined' && handlers.body instanceof FormData
    await fetchEventSource(`${baseURL}${handlers.url || '/api/ai/generations/stream'}`, {
      method: 'POST',
      signal: controller.signal,
      openWhenHidden: true,
      headers: {
        Accept: 'text/event-stream',
        ...(isFormBody ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: handlers.body || JSON.stringify({ moduleKey, params }),
      async onopen(response) {
        const contentType = response.headers.get('content-type') || ''
        if (!response.ok || !contentType.includes('text/event-stream')) {
          throw new Error(await readErrorMessage(response))
        }
      },
      onmessage(message) {
        if (!message.data) return
        const eventName = message.event || 'message'
        if (eventName === 'error') {
          errorReceived = true
          doneReceived = true
        }
        handleStreamEvent(eventName, message.data, handlers)
        if (eventName === 'error') {
          controller.abort()
          return
        }
        if (isDoneMessage(eventName, message.data)) {
          doneReceived = true
          controller.abort()
        }
      },
      onclose() {
        if (!doneReceived && !errorReceived) {
          throw new Error('AI 连接已中断，请稍后重试')
        }
      },
      onerror(error) {
        throw error
      },
    })
  } catch (error) {
    if (timedOut) {
      throw new Error(`AI 生成时间超过 ${Math.round(timeoutMs / 1000)} 秒，请稍后重试或缩小联网搜索范围`)
    }
    if (!doneReceived && !errorReceived) {
      throw error
    }
  } finally {
    clearTimeout(timeoutTimer)
  }
}

function normalizeStreamTimeoutMs(value?: number) {
  const fallback = 5 * 60 * 1000
  if (!Number.isFinite(value || 0) || !value) return fallback
  return Math.max(60 * 1000, Math.min(15 * 60 * 1000, value))
}

/**
 * Query async generation task status.
 */
export function getTaskResult(taskId: number) {
  return api.get(`/api/content/task/${taskId}`).then(assertBusinessOk).then((response) => {
    const payload = response.data?.data || response.data
    if (payload && typeof payload.content === 'string') {
      payload.content = sanitizeAiContent(payload.content)
    }
    return response
  })
}

function assertBusinessOk<T extends { data?: any }>(response: T): T {
  const payload = response.data
  if (payload && typeof payload.code === 'number' && payload.code !== 200) {
    throw new Error(payload.message || 'AI 调用失败，请稍后重试')
  }
  return response
}

async function readErrorMessage(response: Response) {
  let message = 'AI 调用失败，请稍后重试'
  try {
    const payload = await response.clone().json()
    return payload?.message || payload?.data?.message || message
  } catch {
    try {
      const text = await response.clone().text()
      if (text.trim()) message = text.trim().slice(0, 120)
    } catch {
      // Keep concise default message.
    }
  }
  return message
}

function handleStreamEvent(eventName: string, dataText: string, handlers: StreamGenerateHandlers) {
  if (looksLikeRawSse(dataText)) {
    replayRawSse(dataText, handlers)
    return
  }

  let payload: Record<string, any> = {}
  try {
    payload = JSON.parse(dataText)
  } catch {
    payload = { text: dataText }
  }

  if (eventName === 'message' && typeof payload.event === 'string') {
    handleStreamEvent(payload.event, JSON.stringify(payload), handlers)
    return
  }

  if (eventName === 'status') {
    handlers.onStatus?.(String(payload.message || ''))
    return
  }

  if (['chunk', 'message', 'agent_message', 'text_chunk'].includes(eventName)) {
    const text = extractPayloadText(payload)
    if (text) handlers.onChunk?.(text)
    return
  }

  if (['done', 'workflow_finished', 'message_end'].includes(eventName)) {
    handlers.onDone?.(normalizeDonePayload(payload))
    return
  }

  if (eventName === 'error') {
    handlers.onError?.(String(payload.message || 'AI 调用失败，请稍后重试'))
  }
}

function extractPayloadText(payload: Record<string, any>) {
  const raw = String(
    payload.text
      || payload.answer
      || payload.content
      || payload.delta
      || payload.chunk
      || payload.data?.text
      || payload.data?.answer
      || payload.data?.content
      || payload.data?.delta
      || payload.data?.chunk
      || ''
  )

  if (!raw) return ''
  if (looksLikeRawSse(raw)) {
    let text = ''
    replayRawSse(raw, {
      onChunk(chunk) {
        text = mergeStreamContent(text, chunk)
      },
    })
    return text
  }
  return raw
}

function extractFinalContent(payload: Record<string, any>) {
  const outputs = payload.data?.outputs || payload.outputs || {}
  const raw = String(
    payload.content
      || payload.text
      || payload.answer
      || payload.data?.content
      || payload.data?.text
      || payload.data?.answer
      || outputs.content
      || outputs.text
      || outputs.answer
      || outputs.output
      || ''
  )
  return raw ? sanitizeAiContent(raw) : ''
}

function normalizeDonePayload(payload: Record<string, any>) {
  const content = extractFinalContent(payload)
  if (!content) return payload
  return { ...payload, content }
}

function mergeStreamContent(current: string, chunk: string) {
  const next = String(chunk || '')
  if (!next) return current
  if (!current) return next
  if (next === current) return current
  if (next.startsWith(current)) return next
  if (current.endsWith(next)) return current
  if (next.length >= 8 && current.includes(next)) return current

  const overlap = longestOverlap(current, next)
  if (overlap > 0) {
    return current + next.slice(overlap)
  }

  return current + next
}

function longestOverlap(left: string, right: string) {
  const limit = Math.min(left.length, right.length)
  for (let size = limit; size > 0; size--) {
    if (left.slice(-size) === right.slice(0, size)) {
      return size
    }
  }
  return 0
}

async function revealFinalContent(
  finalContent: string,
  currentContent: string,
  handlers: CollectStreamHandlers
) {
  if (!finalContent) return

  const streamedContent = sanitizeAiContent(currentContent)
  if (finalContent === streamedContent || streamedContent.startsWith(finalContent)) return

  // A completed stream may differ only because final sanitizing removed whitespace
  // or reasoning markers. Never replay an already visible answer from the beginning.
  if (streamedContent && !finalContent.startsWith(streamedContent)) return

  let visible = streamedContent
  const remaining = finalContent.slice(streamedContent.length)

  let index = 0
  while (index < remaining.length) {
    const next = nextRevealBoundary(remaining, index)
    const chunk = remaining.slice(index, next)
    visible += chunk
    handlers.onChunk?.(chunk, visible)
    index = next
    if (index < remaining.length) {
      await sleep(24)
    }
  }
}

function nextRevealBoundary(content: string, start: number) {
  const min = Math.min(content.length, start + 4)
  const max = Math.min(content.length, start + 14)
  for (let i = min; i < max; i++) {
    const ch = content[i]
    if (ch === '\n' || ch === '。' || ch === '，' || ch === '；' || ch === '：' || ch === '!' || ch === '?' || ch === '！' || ch === '？') {
      return i + 1
    }
  }
  return max
}

function sleep(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}

function isDoneMessage(eventName: string, dataText: string) {
  if (eventName === 'done') return true
  try {
    const payload = JSON.parse(dataText)
    return payload.event === 'done'
  } catch {
    return false
  }
}

function looksLikeRawSse(value: string) {
  return /(^|\n)event\s*:/i.test(value) && /(^|\n)data\s*:/i.test(value)
}

function replayRawSse(raw: string, handlers: StreamGenerateHandlers) {
  const normalized = raw.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const frames = normalized.split(/\n\s*\n/).map(item => item.trim()).filter(Boolean)
  for (const frame of frames) {
    const event = parseSseFrame(frame)
    if (event.data) {
      handleStreamEvent(event.eventName, event.data, handlers)
    }
  }
}

function parseSseFrame(frame: string) {
  const lines = frame.split('\n')
  let eventName = 'message'
  const data: string[] = []

  for (const rawLine of lines) {
    const line = rawLine.trimEnd()
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim() || 'message'
      continue
    }
    if (line.startsWith('data:')) {
      data.push(line.slice(5).trimStart())
    }
  }

  return {
    eventName,
    data: data.join('\n'),
  }
}

export function sanitizeAiContent(content: string) {
  const cleaned = String(content || '')
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/<think>[\s\S]*$/gi, '')
    .replace(/^\s*```(?:json)?/i, '')
    .replace(/```\s*$/i, '')
    .trim()
  return stripPromptLeak(stripLeadingReasoning(cleaned))
}

function stripPromptLeak(content: string) {
  const markers = [
    /(?:^|\n)\s*你是[“"]?宿识家(?:收益|营销)[\s\S]*?(?=$)/,
    /(?:^|\n)\s*必须使用这些输入[\s\S]*?(?=$)/,
    /(?:^|\n)\s*规则：[\s\S]*?(?=$)/,
    /(?:^|\n)\s*输出必须严格包含[\s\S]*?(?=$)/,
  ]
  let result = content
  for (const marker of markers) {
    result = result.replace(marker, '').trim()
  }
  return result || content
}

function stripLeadingReasoning(content: string) {
  const lines = content.split(/\r?\n/)
  const kept: string[] = []
  let started = false

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!started && isReasoningLine(line)) {
      continue
    }
    if (isEnglishSuggestion(line)) {
      continue
    }
    if (line) {
      started = true
    }
    if (started) {
      kept.push(rawLine)
    }
  }

  const result = kept.join('\n').trim()
  return result || content
}

function isReasoningLine(line: string) {
  const compact = line.replace(/\s/g, '')
  return compact.startsWith('我们根据')
    || compact.startsWith('好的，我需要')
    || compact.startsWith('嗯，用户')
    || compact.startsWith('用户提供')
    || compact.startsWith('酒店上下文')
    || compact.startsWith('内容构思')
    || compact.startsWith('注意')
    || compact.startsWith('按照')
    || compact.startsWith('作为AI')
    || compact.startsWith('我需要')
    || compact.startsWith('我应该')
    || compact.startsWith('可能是')
    || compact.startsWith('这里')
    || compact.startsWith('首先')
    || compact.startsWith('分析')
    || compact.startsWith('思考')
    || /^I need\b/i.test(line)
    || /^We need\b/i.test(line)
    || /^The user\b/i.test(line)
}

function isEnglishSuggestion(line: string) {
  return /^(Help me|Give me|Write|Create|Generate|Tell me)\b/i.test(line)
}
