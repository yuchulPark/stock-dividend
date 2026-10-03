import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getAsset, refreshAsset } from '../api/assetApi.js'
import { getDividends } from '../api/dividendApi.js'
import { errorMessage, isCancelled } from '../api/httpClient.js'
import { assetTypeLabel, formatCurrency, formatDate, formatDateTime, marketLabel } from '../utils/formatCurrency.js'
import DataStatus from '../components/DataStatus.jsx'
import DividendTable from '../components/DividendTable.jsx'
import ErrorMessage from '../components/ErrorMessage.jsx'
import LoadingState from '../components/LoadingState.jsx'

export default function AssetDetailPage() {
  const { market, ticker } = useParams()
  if (!['KR', 'US'].includes(market) || !/^[A-Z0-9][A-Z0-9.-]{0,31}$/i.test(ticker || '') || (market === 'KR' && !/^[0-9A-Z]{6}$/i.test(ticker))) {
    return <><div className="page-heading"><h1>종목 상세</h1></div><ErrorMessage message="시장과 종목코드를 확인해 주세요." /><Link className="button button-outline" to="/search">종목 검색으로 이동</Link></>
  }
  return <AssetDetailContent key={`${market}:${ticker}`} market={market} ticker={ticker.toUpperCase()} />
}

function AssetDetailContent({ market, ticker }) {
  const [asset, setAsset] = useState(null)
  const [assetLoading, setAssetLoading] = useState(true)
  const [assetError, setAssetError] = useState('')
  const [dividends, setDividends] = useState(null)
  const [dividendLoading, setDividendLoading] = useState(true)
  const [dividendError, setDividendError] = useState('')
  const [dividendUnsupported, setDividendUnsupported] = useState(false)
  const [assetAttempt, setAssetAttempt] = useState(0)
  const [dividendAttempt, setDividendAttempt] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const [refreshError, setRefreshError] = useState('')
  const refreshController = useRef(null)
  const refreshEnabled = import.meta.env.VITE_ENABLE_REFRESH === 'true'

  useEffect(() => {
    const controller = new AbortController()
    getAsset(market, ticker, controller.signal)
      .then((data) => { if (!controller.signal.aborted) setAsset(data) })
      .catch((failure) => { if (!isCancelled(failure) && !controller.signal.aborted) setAssetError(errorMessage(failure, '종목 정보를 불러오지 못했습니다.')) })
      .finally(() => { if (!controller.signal.aborted) setAssetLoading(false) })
    return () => controller.abort()
  }, [market, ticker, assetAttempt])

  useEffect(() => {
    const controller = new AbortController()
    getDividends(market, ticker, controller.signal)
      .then((data) => { if (!controller.signal.aborted) setDividends(data) })
      .catch((failure) => {
        if (isCancelled(failure) || controller.signal.aborted) return
        const unsupported = failure?.response?.data?.code === 'DIVIDEND_DATA_NOT_SUPPORTED'
        setDividendUnsupported(unsupported)
        setDividendError(unsupported && market === 'KR' ? '국내 ETF 분배금 및 우선주 배당 데이터는 현재 지원하지 않습니다.' : errorMessage(failure, '배당 정보를 불러오지 못했습니다.'))
      })
      .finally(() => { if (!controller.signal.aborted) setDividendLoading(false) })
    return () => controller.abort()
  }, [market, ticker, dividendAttempt])

  useEffect(() => () => refreshController.current?.abort(), [])

  function retryAsset() {
    if (refreshing) return
    setAssetLoading(true); setAssetError(''); setAssetAttempt((value) => value + 1)
  }
  function retryDividends() {
    if (refreshing) return
    setDividendLoading(true); setDividendError(''); setDividendUnsupported(false)
    setDividendAttempt((value) => value + 1)
  }

  async function refresh() {
    if (refreshing || assetLoading || dividendLoading) return
    const controller = new AbortController(); refreshController.current = controller
    setRefreshing(true); setRefreshError('')
    try {
      const data = await refreshAsset(market, ticker, controller.signal)
      if (controller.signal.aborted) return
      setAsset(data.asset)
      if (data.dividendWarningCode === 'DIVIDEND_DATA_NOT_SUPPORTED' || data.dividendWarningCode === 'DIVIDEND_PROVIDER_NOT_IMPLEMENTED') {
        setDividendUnsupported(true)
        setDividendError('이 종목의 배당 데이터는 현재 지원하지 않습니다.')
      } else {
        setDividendUnsupported(false)
        setDividendError('')
        // RefreshResponse does not contain dividendSyncedAt; don't invent a collection timestamp.
        setDividends({ data: data.dividends, dataFresh: data.dividendDataFresh, warningCode: data.dividendWarningCode, syncedAt: null })
      }
    } catch (failure) {
      if (!isCancelled(failure) && !controller.signal.aborted) setRefreshError(errorMessage(failure))
    } finally {
      if (!controller.signal.aborted) setRefreshing(false)
    }
  }

  return <>
    <Link to={`/search?market=${market}`} className="back-link">← 종목 검색</Link>
    <div className="page-heading detail-heading"><div><p className="eyebrow">ASSET OVERVIEW · {market}</p><h1>{asset?.name || ticker}</h1><p>{ticker} · {marketLabel(market)}{asset ? ` · ${assetTypeLabel(asset.assetType)}` : ''}</p></div>
      {refreshEnabled && <button className="button button-outline" type="button" onClick={refresh} disabled={refreshing || assetLoading || dividendLoading}>{refreshing ? '갱신 중…' : '데이터 새로고침'}<span className="badge badge-neutral">개발용</span></button>}
    </div>
    <ErrorMessage message={refreshError} />
    {assetLoading ? <LoadingState /> : assetError ? <ErrorMessage message={assetError} onRetry={retryAsset} /> : asset && <section className="card price-card" aria-label="종목 가격과 기본정보">
      <div className="price-main"><span className="eyebrow">{market === 'KR' || asset.priceType === 'EOD' ? '거래일 종가' : '조회 가격'}</span><p className="asset-price">{formatCurrency(asset.currentPrice, asset.currency)}</p><p className="price-caption">{market === 'KR' ? '최근 이용 가능한 거래일의 종가입니다.' : '제공된 가격 기준일을 확인하세요.'}</p></div>
      <dl className="asset-facts"><div><dt>가격 기준일</dt><dd>{formatDate(asset.priceAsOfDate)}</dd></div><div><dt>수집 시각 · 한국시간</dt><dd>{formatDateTime(asset.priceUpdatedAt)}</dd></div><div><dt>시장 / 자산종류</dt><dd>{marketLabel(asset.market)} / {assetTypeLabel(asset.assetType)}</dd></div><div><dt>통화</dt><dd>{asset.currency || '-'}</dd></div></dl>
      <div className="price-status"><DataStatus dataFresh={asset.dataFresh} warningCode={asset.warningCode} /></div>
    </section>}
    <section className="card dividend-section" aria-labelledby="dividend-title" aria-busy={dividendLoading || refreshing}>
      <div className="section-heading"><div><p className="eyebrow">DIVIDEND HISTORY</p><h2 id="dividend-title">{market === 'KR' ? '배당 공시 이력' : '배당 이력'}</h2></div><span className="subtle">주당 배당금 기준</span></div>
      {dividendLoading ? <LoadingState /> : dividendError ? dividendUnsupported ? <p className="info-note" role="status">{asset?.assetType === 'ETF' && market === 'KR' ? '현재 국내 ETF 분배금 데이터는 지원하지 않습니다.' : dividendError}</p> : <ErrorMessage message={dividendError} onRetry={retryDividends} /> : dividends && <><DataStatus {...dividends} /><DividendTable dividends={dividends.data} market={market} /></>}
    </section>
  </>
}
