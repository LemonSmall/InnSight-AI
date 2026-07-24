export type ExportFormat = 'markdown' | 'html' | 'pdf'

export function downloadTextFile(filename: string, content: string, mime = 'text/plain;charset=utf-8') {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export function exportMarkdown(filename: string, title: string, content: string) {
  const body = content.trim().startsWith('#') ? content.trim() : `# ${title}\n\n${content.trim()}`
  downloadTextFile(ensureExtension(filename, 'md'), body + '\n', 'text/markdown;charset=utf-8')
}

export function exportHtml(filename: string, title: string, contentHtml: string) {
  downloadTextFile(ensureExtension(filename, 'html'), buildPrintableHtml(title, contentHtml), 'text/html;charset=utf-8')
}

export async function exportPdf(title: string, contentHtml: string, filename?: string) {
  if (!contentHtml.trim()) return false
  const content = document.createElement('div')
  content.innerHTML = contentHtml
  return exportPdfElement(title, content, filename)
}

export async function exportPdfElement(title: string, source: Element | null, filename?: string) {
  if (!source) return false
  const clone = source.cloneNode(true) as HTMLElement
  if (!clone.textContent?.trim() && !clone.querySelector('img,svg,canvas,table')) return false

  const [{ default: html2canvas }, { default: JsPDF }] = await Promise.all([
    import('html2canvas'),
    import('jspdf'),
  ])
  const wrapper = buildPdfElement(title, clone)
  document.body.appendChild(wrapper)

  try {
    await waitForPdfLayout()
    const canvas = await html2canvas(wrapper, {
      backgroundColor: '#ffffff',
      scale: Math.min(window.devicePixelRatio || 2, 2),
      useCORS: true,
      scrollX: 0,
      scrollY: -window.scrollY,
      windowWidth: wrapper.scrollWidth,
      windowHeight: wrapper.scrollHeight,
    })
    if (!canvas.width || !canvas.height) return false
    saveCanvasAsPdf(canvas, JsPDF, ensureExtension(filename || safeFilename(title), 'pdf'))
    return true
  } finally {
    wrapper.remove()
  }
}

export function safeFilename(value: string, fallback = 'ai-result') {
  const name = String(value || fallback)
    .replace(/[\\/:*?"<>|]/g, '-')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .slice(0, 80)
    .replace(/^-|-$/g, '')
  return name || fallback
}

export function elementHtml(selector: string) {
  return document.querySelector(selector)?.innerHTML || ''
}

export function elementNode(selector: string) {
  return document.querySelector(selector)
}

function ensureExtension(filename: string, extension: string) {
  const suffix = `.${extension}`
  return filename.toLowerCase().endsWith(suffix) ? filename : `${filename}${suffix}`
}

function buildPrintableHtml(title: string, contentHtml: string) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>${escapeHtml(title)}</title>
  <style>
    * { box-sizing: border-box; }
    body { margin: 0; background: #f6f3ec; color: #4f4338; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif; }
    main { width: min(900px, calc(100vw - 48px)); margin: 32px auto; background: #fff; border: 1px solid #eadfce; border-radius: 12px; padding: 36px; }
    h1, h2, h3 { color: #234d32; letter-spacing: 0; }
    table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 13px; }
    th, td { border: 1px solid #eadfce; padding: 10px 12px; vertical-align: top; text-align: left; }
    th { background: #f5efe4; color: #234d32; }
    p, li { line-height: 1.85; }
    ul, ol { padding-left: 20px; }
    @media print {
      body { background: #fff; }
      main { width: 100%; margin: 0; border: 0; border-radius: 0; padding: 0; }
      button { display: none !important; }
    }
  </style>
</head>
<body>
  <main>
    <h1>${escapeHtml(title)}</h1>
    ${contentHtml}
  </main>
</body>
</html>`
}

function buildPdfContent(title: string, contentHtml: string) {
  return `<main style="width: 860px; padding: 28px; background: #fff; color: #4f4338; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif;">
    <h1 style="margin: 0 0 22px; color: #234d32; font-size: 24px; line-height: 1.35;">${escapeHtml(title)}</h1>
    <div class="pdf-content">${contentHtml}</div>
  </main>`
}

function buildPdfElement(title: string, content: HTMLElement) {
  const wrapper = document.createElement('main')
  wrapper.setAttribute('data-pdf-export-root', 'true')
  wrapper.style.position = 'absolute'
  wrapper.style.left = '0'
  wrapper.style.top = `${Math.max(window.scrollY, 0)}px`
  wrapper.style.zIndex = '-1'
  wrapper.style.width = '860px'
  wrapper.style.minHeight = '1px'
  wrapper.style.padding = '28px'
  wrapper.style.background = '#ffffff'
  wrapper.style.color = '#4f4338'
  wrapper.style.fontFamily = '-apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif'
  wrapper.style.pointerEvents = 'none'
  wrapper.style.boxSizing = 'border-box'

  const heading = document.createElement('h1')
  heading.textContent = title
  heading.style.margin = '0 0 22px'
  heading.style.color = '#234d32'
  heading.style.fontSize = '24px'
  heading.style.lineHeight = '1.35'
  heading.style.fontWeight = '700'

  normalizePdfClone(content)
  wrapper.appendChild(heading)
  wrapper.appendChild(content)
  return wrapper
}

function saveCanvasAsPdf(canvas: HTMLCanvasElement, JsPDF: any, filename: string) {
  const pdf = new JsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait' })
  const pageWidth = pdf.internal.pageSize.getWidth()
  const pageHeight = pdf.internal.pageSize.getHeight()
  const margin = 10
  const contentWidth = pageWidth - margin * 2
  const contentHeight = pageHeight - margin * 2
  const pageCanvasHeight = Math.floor((contentHeight * canvas.width) / contentWidth)
  let renderedHeight = 0
  let pageIndex = 0

  while (renderedHeight < canvas.height) {
    const sliceHeight = Math.min(pageCanvasHeight, canvas.height - renderedHeight)
    const pageCanvas = document.createElement('canvas')
    pageCanvas.width = canvas.width
    pageCanvas.height = sliceHeight
    const context = pageCanvas.getContext('2d')
    if (!context) break
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, pageCanvas.width, pageCanvas.height)
    context.drawImage(canvas, 0, renderedHeight, canvas.width, sliceHeight, 0, 0, canvas.width, sliceHeight)

    if (pageIndex > 0) pdf.addPage()
    const image = pageCanvas.toDataURL('image/jpeg', 0.96)
    const imageHeight = (sliceHeight * contentWidth) / canvas.width
    pdf.addImage(image, 'JPEG', margin, margin, contentWidth, imageHeight)
    renderedHeight += sliceHeight
    pageIndex += 1
  }

  pdf.save(filename)
}

function normalizePdfClone(root: HTMLElement) {
  root.style.background = '#ffffff'
  root.style.minHeight = 'auto'
  root.style.overflow = 'visible'
  root.style.padding = '0'

  root.querySelectorAll<HTMLElement>('*').forEach((node) => {
    node.style.animation = 'none'
    node.style.transition = 'none'
    if (node.classList.contains('plan-table-wrap')) {
      node.style.overflow = 'visible'
      node.style.maxWidth = 'none'
    }
    if (node.tagName === 'TABLE') {
      node.style.width = '100%'
      node.style.maxWidth = '100%'
    }
  })
}

async function waitForPdfLayout() {
  if (document.fonts?.ready) {
    await document.fonts.ready.catch(() => undefined)
  }
  await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
}

function escapeHtml(value: string) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
