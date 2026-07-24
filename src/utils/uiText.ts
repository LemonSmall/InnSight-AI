const mojibakePattern = /�|锟|Ã|Â|鉃|鈩|鍙|鍚|鍏|鍒|鍔|鍦|鍩|鍘|鍛|鍝|鍥|鍜|鍟|鍠|瀹|搴|閰|鏈|涓|绠|璧|璇|触|锛|鐨|闂|妗|旂|甯|撴|浼|浣|骇|暱|姝|缃/

export function looksGarbledText(value: unknown): boolean {
  if (typeof value !== 'string') return false
  return mojibakePattern.test(value)
}

export function safeUiText(value: unknown, fallback = ''): string {
  const text = typeof value === 'string' ? value.trim() : ''
  if (!text || looksGarbledText(text)) return fallback
  return text
}
