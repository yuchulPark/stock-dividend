import { assetPath } from './assetApi.js'
import { cacheMetadata, httpClient } from './httpClient.js'

export async function getDividends(market, ticker, signal) {
  const response = await httpClient.get(`${assetPath(market, ticker)}/dividends`, { signal })
  if (!Array.isArray(response.data)) throw new Error('Invalid dividend response')
  return { data: response.data, ...cacheMetadata(response) }
}
