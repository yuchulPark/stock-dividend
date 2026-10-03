import { cacheMetadata, httpClient } from './httpClient.js'

export async function searchAssets(market, keyword, signal) {
  const response = await httpClient.get('/assets/search', { params: { market, keyword }, signal })
  if (!Array.isArray(response.data)) throw new Error('Invalid search response')
  return { data: response.data, ...cacheMetadata(response) }
}

export function assetPath(market, ticker) {
  return `/assets/${encodeURIComponent(market)}/${encodeURIComponent(ticker)}`
}

export async function getAsset(market, ticker, signal) {
  const response = await httpClient.get(assetPath(market, ticker), { signal })
  if (!response.data || typeof response.data.ticker !== 'string') throw new Error('Invalid asset response')
  return response.data
}

export async function refreshAsset(market, ticker, signal) {
  const response = await httpClient.post(`${assetPath(market, ticker)}/refresh`, null, { signal })
  if (!response.data?.asset || !Array.isArray(response.data.dividends)) throw new Error('Invalid refresh response')
  return response.data
}
