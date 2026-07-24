export function extractImageUrl(text?: string): string {
  const value = String(text || '').trim()
  if (!value) return ''

  const fromJson = extractImageUrlFromValue(parseJsonLike(value))
  if (fromJson) return fromJson

  return extractImageUrlFromText(value)
}

export function extractDisplayText(text?: string): string {
  const value = String(text || '').trim()
  if (!value) return ''

  const parsed = parseJsonLike(value)
  const structured = extractDisplayTextFromValue(parsed)
  const fallback = stripPresentationNoise(value)

  return stripPresentationNoise(structured || fallback)
}

export function extractArticleDisplayText(text?: string): string {
  const value = String(text || '').trim()
  if (!value) return ''

  const parsed = parseJsonLike(value) || parseJsonLike(wrapJsonObjectFragment(value))
  const structured = extractArticleTextFromValue(parsed)
  if (structured) return cleanArticleMarkup(structured)

  return cleanArticleMarkup(value)
}

function extractImageUrlFromText(value: string): string {
  const markdownImage = value.match(/!\[[^\]]*]\(((?:https?:\/\/|\/api\/public\/ai-images\/)[^)\s"'<>]+)(?:\s+["'][^"']*["'])?\)/i)
  if (markdownImage?.[1]) return normalizeImageUrl(markdownImage[1])

  const directMatches = value.match(/(?:https?:\/\/[^\s"'<>\\]+|\/api\/public\/ai-images\/[^\s"'<>\\]+)/gi) || []
  for (const match of directMatches) {
    const normalized = normalizeImageUrl(match)
    if (normalized && isLikelyImageUrl(normalized)) return normalized
  }

  return ''
}

function wrapJsonObjectFragment(value: string): string {
  const trimmed = String(value || '').trim()
  if (/^"[\w\u4e00-\u9fa5]+"\s*:/.test(trimmed)) {
    return `{${trimmed}}`
  }
  return trimmed
}

export function parseJsonLike(value: string): unknown {
  const trimmed = String(value || '').trim()
  if (!trimmed) return null

  try {
    return JSON.parse(trimmed)
  } catch {
    const match = trimmed.match(/\{[\s\S]*\}/)
    if (!match) return null
    try {
      return JSON.parse(match[0])
    } catch {
      return null
    }
  }
}

function extractArticleTextFromValue(value: unknown, seen = new Set<unknown>()): string {
  if (!value) return ''

  if (typeof value === 'string') {
    const parsed = parseJsonLike(value) || parseJsonLike(wrapJsonObjectFragment(value))
    if (parsed && parsed !== value) {
      const nested = extractArticleTextFromValue(parsed, seen)
      if (nested) return nested
    }
    return cleanArticleMarkup(value)
  }

  if (typeof value !== 'object') return ''
  if (seen.has(value)) return ''
  seen.add(value)

  if (Array.isArray(value)) {
    return value
      .map(item => extractArticleTextFromValue(item, seen))
      .filter(Boolean)
      .join('\n\n')
      .trim()
  }

  const record = value as Record<string, unknown>
  const bodyKeys = [
    'content',
    'body',
    'article',
    'articleContent',
    'article_content',
    '姝ｆ枃',
    'text',
    'markdown',
    'html',
  ]
  for (const key of bodyKeys) {
    if (!(key in record)) continue
    const text = extractArticleTextFromValue(record[key], seen)
    if (text) return text
  }

  const sectionsValue = record.sections || record.paragraphs || record.blocks
  if (Array.isArray(sectionsValue)) {
    return sectionsValue
      .map(item => {
        if (!item || typeof item !== 'object') return extractArticleTextFromValue(item, seen)
        const section = item as Record<string, unknown>
        const heading = String(section.heading || section.title || '').trim()
        const body = extractArticleTextFromValue(section.paragraphs || section.content || section.body || section.text, seen)
        return [heading, body].filter(Boolean).join('\n')
      })
      .filter(Boolean)
      .join('\n\n')
  }

  const outputKeys = ['output', 'outputs', 'answer', 'result', 'data', 'payload']
  for (const key of outputKeys) {
    if (!(key in record)) continue
    const text = extractArticleTextFromValue(record[key], seen)
    if (text) return text
  }

  return ''
}

function cleanArticleMarkup(value: string): string {
  return stripPresentationNoise(String(value || ''))
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|section|div|h[1-6]|li|blockquote)>/gi, '\n\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function extractDisplayTextFromValue(value: unknown, seen = new Set<unknown>()): string {
  if (!value) return ''

  if (typeof value === 'string') {
    const parsed = parseJsonLike(value)
    if (parsed && parsed !== value) {
      const nested = extractDisplayTextFromValue(parsed, seen)
      if (nested) return nested
    }
    return stripPresentationNoise(value)
  }

  if (typeof value !== 'object') return ''
  if (seen.has(value)) return ''
  seen.add(value)

  if (Array.isArray(value)) {
    return value
      .map(item => extractDisplayTextFromValue(item, seen))
      .filter(Boolean)
      .join('\n\n')
      .trim()
  }

  const record = value as Record<string, unknown>
  const preferredKeys = [
    'body',
    'content',
    'article',
    'articleContent',
    'article_content',
    '姝ｆ枃',
    'text',
    'answer',
    'output',
    'outputs',
    'message',
    'result',
    'data',
    'payload',
    'script',
    'spokenScript',
    'publishTips',
    'summary',
  ]

  const preferredValues = preferredKeys
    .map(key => extractDisplayTextFromValue(record[key], seen))
    .filter(Boolean)

  if (preferredValues.length) {
    return preferredValues.join('\n\n').trim()
  }

  return Object.entries(record)
    .filter(([key]) => !/image|url|cover|title|tag|suggestion|bgm|time|date|schedule/i.test(key))
    .map(([, item]) => extractDisplayTextFromValue(item, seen))
    .filter(Boolean)
    .join('\n\n')
    .trim()
}

function extractImageUrlFromValue(value: unknown, seen = new Set<unknown>()): string {
  if (!value) return ''

  if (typeof value === 'string') {
    const parsed = parseJsonLike(value)
    if (parsed && parsed !== value) {
      const nested = extractImageUrlFromValue(parsed, seen)
      if (nested) return nested
    }
    return extractImageUrlFromText(value)
  }

  if (typeof value !== 'object') return ''
  if (seen.has(value)) return ''
  seen.add(value)

  if (Array.isArray(value)) {
    for (const item of value) {
      const found = extractImageUrlFromValue(item, seen)
      if (found) return found
    }
    return ''
  }

  const record = value as Record<string, unknown>
  const preferredKeys = [
    'imageUrl',
    'imageUrls',
    'image_url',
    'image_urls',
    'outputImage',
    'output_image',
    'posterUrl',
    'poster_url',
    'outputAssets',
    'output_assets',
    'assets',
    'files',
    'images',
    'artifacts',
    'url',
    'image',
    'markdown',
    'content',
    'body',
    'answer',
    'text',
    'output',
    'data',
    'result',
  ]

  for (const key of preferredKeys) {
    if (!(key in record)) continue
    const found = extractImageUrlFromValue(record[key], seen)
    if (found) return found
  }

  for (const item of Object.values(record)) {
    const found = extractImageUrlFromValue(item, seen)
    if (found) return found
  }

  return ''
}

export function normalizeImageUrl(url: string): string {
  return String(url || '')
    .trim()
    .replace(/&amp;/gi, '&')
    .replace(/\\+/g, '')
    .replace(/[)\]}锛屻€傦紱;]+$/g, '')
}

export function isLikelyImageUrl(url: string): boolean {
  return /\.(png|jpe?g|webp|gif|bmp|svg)(\?|#|$)/i.test(url)
    || /s3\.siliconflow\.cn\/(?:temporary|t)\/outputs\//i.test(url)
    || /\/api\/public\/ai-images\//i.test(url)
    || /upload\.dify\.ai\/files/i.test(url)
    || /output_images?/i.test(url)
    || /image|img|poster|material|asset|file/i.test(url)
}

export function toPlainTextWithoutImageMarkdown(text?: string): string {
  const value = String(text || '').trim()
  const parsedText = extractTextPayload(parseJsonLike(value) || parseJsonLike(wrapJsonObjectFragment(value)))
  return stripPresentationNoise(String(parsedText || value))
}

function extractTextPayload(value: unknown, seen = new Set<unknown>()): string {
  if (!value) return ''
  if (typeof value === 'string') return value
  if (typeof value !== 'object') return ''
  if (seen.has(value)) return ''
  seen.add(value)

  if (Array.isArray(value)) {
    return value.map(item => extractTextPayload(item, seen)).filter(Boolean).join('\n')
  }

  const record = value as Record<string, unknown>
  const preferredKeys = ['content', 'body', 'answer', 'text', 'output', 'message', 'result']
  for (const key of preferredKeys) {
    const text = extractTextPayload(record[key], seen)
    if (text) return text
  }
  return ''
}

function stripPresentationNoise(value: string): string {
  return String(value || '')
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/^\s*```(?:json)?\s*/i, '')
    .replace(/\s*```\s*$/i, '')
    .replace(/!\[[^\]]*]\(https?:\/\/[^)\s"'<>]+(?:\s+["'][^"']*["'])?\)/gi, '')
    .replace(/https?:\/\/[^\s"'<>\\]+/gi, '')
    .trim()
}
