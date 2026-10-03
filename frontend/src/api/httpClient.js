import axios from 'axios'

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000,
  headers: { Accept: 'application/json' },
})

export function cacheMetadata(response) {
  const freshness = response.headers['x-data-fresh']
  return {
    dataFresh: freshness === 'true' ? true : freshness === 'false' ? false : null,
    syncedAt: response.headers['x-data-synced-at'] || null,
    warningCode: response.headers['x-data-warning'] || null,
  }
}

export function isCancelled(error) { return axios.isCancel(error) || error?.code === 'ERR_CANCELED' }

// Never render raw HTTP bodies, exception text or request URLs.
export function errorMessage(error, fallback = '데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.') {
  const messages = {
    ASSET_NOT_FOUND: '종목을 찾을 수 없습니다. 시장과 종목코드를 확인해 주세요.',
    INVALID_REQUEST: '시장, 종목코드 또는 검색어를 확인해 주세요.',
    EXTERNAL_API_KEY_MISSING: '데이터 제공 서비스의 인증 설정이 필요합니다.',
    EXTERNAL_API_AUTH_FAILED: '데이터 제공 서비스의 인증 또는 이용 승인을 확인해 주세요.',
    EXTERNAL_API_RATE_LIMIT: '데이터 조회 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.',
    EXTERNAL_API_TIMEOUT: '데이터 조회 시간이 초과되었습니다. 다시 시도해 주세요.',
    EXTERNAL_API_UNAVAILABLE: '외부 데이터를 일시적으로 조회할 수 없습니다.',
    EXTERNAL_API_DATA_UNAVAILABLE: '최근 조회 범위에 이용 가능한 거래일 데이터가 없습니다.',
    EXTERNAL_API_INVALID_RESPONSE: '데이터 제공 서비스의 응답을 확인하지 못했습니다.',
    DIVIDEND_DATA_NOT_SUPPORTED: '이 종목의 배당 데이터는 현재 지원하지 않습니다.',
    DIVIDEND_PROVIDER_NOT_IMPLEMENTED: '이 시장의 배당 데이터는 아직 지원하지 않습니다.',
    API_NOT_FOUND: '요청한 기능을 현재 사용할 수 없습니다.',
    DATA_CONFLICT: '데이터를 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    INTERNAL_ERROR: '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  }
  const code = error?.response?.data?.code
  if (Object.hasOwn(messages, code)) return messages[code]
  if (error?.code === 'ECONNABORTED') return messages.EXTERNAL_API_TIMEOUT
  if (error?.code === 'ERR_NETWORK') return '서버에 연결할 수 없습니다. 연결 상태를 확인해 주세요.'
  return fallback
}
