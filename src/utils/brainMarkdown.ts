export function renderBrainMarkdown(content: string) {
  const lines = normalizeBrainMarkdown(content).split('\n')
  const html: string[] = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index].trim()
    if (!line || /^-{3,}$/.test(line)) {
      index += 1
      continue
    }

    if (/^#{1,4}\s*$/.test(line)) {
      index += 1
      continue
    }

    if (/^#{1,4}\s*/.test(line) && line.replace(/^#{1,4}\s*/, '').trim()) {
      const level = Math.min(line.match(/^#+/)?.[0].length || 2, 4)
      const tag = level <= 2 ? 'h2' : level === 3 ? 'h3' : 'h4'
      html.push(`<${tag}>${formatInline(line.replace(/^#{1,4}\s*/, ''))}</${tag}>`)
      index += 1
      continue
    }

    if (isTableStart(lines, index)) {
      const tableLines: string[] = []
      while (index < lines.length && lines[index].trim().startsWith('|')) {
        tableLines.push(lines[index].trim())
        index += 1
      }
      html.push(renderTable(tableLines))
      continue
    }

    if (/^[-*]\s+/.test(line)) {
      const items: string[] = []
      while (index < lines.length && /^[-*]\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^[-*]\s+/, ''))
        index += 1
      }
      html.push(`<ul>${items.map(item => `<li>${formatInline(item)}</li>`).join('')}</ul>`)
      continue
    }

    if (/^\d+[.、](?!\d)\s*/.test(line)) {
      const items: string[] = []
      while (index < lines.length) {
        const current = lines[index].trim()
        const next = lines[index + 1]?.trim() || ''
        if (!current && /^\d+[.、](?!\d)\s*/.test(next)) {
          index += 1
          continue
        }
        if (!/^\d+[.、](?!\d)\s*/.test(current)) break
        items.push(current.replace(/^\d+[.、](?!\d)\s*/, ''))
        index += 1
      }
      html.push(`<ol>${items.map(item => `<li>${formatInline(item)}</li>`).join('')}</ol>`)
      continue
    }

    const paragraph: string[] = []
    while (
      index < lines.length
      && lines[index].trim()
      && !(/^#{1,4}\s*/.test(lines[index].trim()) && lines[index].trim().replace(/^#{1,4}\s*/, '').trim())
      && !isTableStart(lines, index)
      && !/^[-*]\s+/.test(lines[index].trim())
      && !/^\d+[.、](?!\d)\s*/.test(lines[index].trim())
      && !/^-{3,}$/.test(lines[index].trim())
    ) {
      paragraph.push(lines[index].trim())
      index += 1
    }
    html.push(`<p>${paragraph.map(formatInline).join('<br>')}</p>`)
  }

  return `<div class="brain-markdown">${html.join('')}</div>`
}

export function normalizeBrainMarkdown(content: string) {
  return String(content || '')
    .replace(/\r\n/g, '\n')
    .replace(/([^\n])(#{1,4})(?=\S)/g, '$1\n\n$2')
    .replace(/^(#{1,4})(?=\S)/gm, '$1 ')
    .replace(/(#{1,4}\s*[^|\n]+?)\s*(\|[^\n]+\|)/g, '$1\n\n$2')
    .replace(/(\|[^\n]+\|)\s*(#{1,4}\s*)/g, '$1\n\n$2')
    .replace(/([^\n])(\s*\d+[.、](?!\d)\s*)/g, '$1\n$2')
    .replace(/^\s*#{1,6}\s*$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
}

function isTableStart(lines: string[], index: number) {
  const current = lines[index]?.trim() || ''
  const next = lines[index + 1]?.trim() || ''
  const afterNext = lines[index + 2]?.trim() || ''
  if (!isPipeRow(current)) return false
  if (isTableSeparator(next)) return true
  return isPipeRow(next) && isPipeRow(afterNext)
}

function renderTable(tableLines: string[]) {
  const rows = tableLines
    .filter(line => !isTableSeparator(line))
    .map(line => line.replace(/^\||\|$/g, '').split('|').map(cell => cell.trim()))
    .filter(row => row.length > 1)
  const head = rows[0] || []
  const body = rows.slice(1)
  return [
    '<div class="brain-table-wrap"><table>',
    `<thead><tr>${head.map(cell => `<th>${formatInline(cell)}</th>`).join('')}</tr></thead>`,
    `<tbody>${body.map(row => `<tr>${row.map(cell => `<td>${formatInline(cell)}</td>`).join('')}</tr>`).join('')}</tbody>`,
    '</table></div>',
  ].join('')
}

function isPipeRow(value: string) {
  const text = value.trim()
  return text.startsWith('|') && text.endsWith('|') && text.split('|').length >= 3
}

function isTableSeparator(value: string) {
  return /^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?$/.test(value.trim())
}

function formatInline(value: string) {
  return escapeHtml(value)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}

function escapeHtml(value: string) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
