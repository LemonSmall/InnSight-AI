const PREFIX = 'sushijia_ai_page_state:'
const HOTEL_USER = 'hotel_user'
const HOTEL_ACCESS_TOKEN = 'hotel_access_token'
const STATE_EVENT = 'sushijia-ai-page-state'

interface StoredPageState<T> {
  version: 1
  savedAt: number
  state: T
}

export function saveAiPageState<T>(key: string, state: T) {
  if (typeof window === 'undefined') return
  try {
    const payload: StoredPageState<T> = {
      version: 1,
      savedAt: Date.now(),
      state,
    }
    window.localStorage.setItem(storageKey(key), JSON.stringify(payload))
    window.dispatchEvent(new CustomEvent(STATE_EVENT, {
      detail: {
        key,
        scopedKey: storageKey(key),
        state,
      },
    }))
  } catch {
    // localStorage can fail in private mode or when quota is full.
  }
}

export function loadAiPageState<T>(key: string): T | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(storageKey(key))
    if (!raw) return null
    const payload = JSON.parse(raw) as StoredPageState<T>
    return payload?.state || null
  } catch {
    return null
  }
}

export function clearAiPageState(key: string) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(storageKey(key))
  } catch {
    // best effort
  }
}

export function watchAiPageState<T>(key: string, handler: (state: T) => void) {
  if (typeof window === 'undefined') return () => {}
  const scopedKey = storageKey(key)
  const onCustomEvent = (event: Event) => {
    const detail = (event as CustomEvent).detail
    if (detail?.scopedKey === scopedKey || detail?.key === key) {
      handler(detail.state as T)
    }
  }
  const onStorage = (event: StorageEvent) => {
    if (event.key !== scopedKey || !event.newValue) return
    try {
      const payload = JSON.parse(event.newValue) as StoredPageState<T>
      if (payload?.state) handler(payload.state)
    } catch {
      // Ignore malformed external storage events.
    }
  }
  window.addEventListener(STATE_EVENT, onCustomEvent as EventListener)
  window.addEventListener('storage', onStorage)
  return () => {
    window.removeEventListener(STATE_EVENT, onCustomEvent as EventListener)
    window.removeEventListener('storage', onStorage)
  }
}

function storageKey(key: string) {
  return `${PREFIX}${identityScope()}:${key}`
}

function identityScope() {
  try {
    const userRaw = window.localStorage.getItem(HOTEL_USER)
    const user = userRaw ? JSON.parse(userRaw) : null
    const userId = user?.id || user?.phone || ''
    const token = window.localStorage.getItem(HOTEL_ACCESS_TOKEN) || ''
    const tenantId = extractJwtNumber(token, 'tenantId') || extractJwtNumber(token, 'tenant_id') || ''
    if (tenantId || userId) {
      return `${tenantId || 'tenant'}:${userId || 'user'}`
    }
    return token ? `token:${simpleHash(token)}` : 'anonymous'
  } catch {
    return 'anonymous'
  }
}

function extractJwtNumber(token: string, field: string) {
  try {
    const payload = token.split('.')[1]
    if (!payload) return ''
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      Array.from(atob(normalized), c => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`).join('')
    )
    const data = JSON.parse(json)
    return data?.[field] == null ? '' : String(data[field])
  } catch {
    return ''
  }
}

function simpleHash(value: string) {
  let hash = 0
  for (let i = 0; i < value.length; i++) {
    hash = ((hash << 5) - hash + value.charCodeAt(i)) | 0
  }
  return Math.abs(hash).toString(36)
}
