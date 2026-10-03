export function formatCurrency(value, currency, { dividend = false } = {}) {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  if (currency === 'KRW') return `${new Intl.NumberFormat('ko-KR', { maximumFractionDigits: dividend ? 8 : 0 }).format(number)}원`
  if (currency === 'USD') return new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: dividend ? 8 : 2,
  }).format(number)
  return `${new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 8 }).format(number)}${currency ? ` ${currency}` : ''}`
}

export function formatDate(value) { return value || '-' }
export function formatDateTime(value) {
  if (!value) return '-'
  // Backend timestamps are UTC even when LocalDateTime serialization omits the offset.
  const date = new Date(/Z$|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}Z`)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Seoul' }).format(date)
}
export function marketLabel(market) { return market === 'KR' ? '국내' : market === 'US' ? '미국' : market }
export function assetTypeLabel(type) { return type === 'STOCK' ? '주식' : type === 'ETF' ? 'ETF' : type || '-' }
