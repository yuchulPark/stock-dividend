import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { searchAssets } from '../api/assetApi.js'
import { errorMessage, isCancelled } from '../api/httpClient.js'
import AssetCard from '../components/AssetCard.jsx'
import DataStatus from '../components/DataStatus.jsx'
import ErrorMessage from '../components/ErrorMessage.jsx'
import LoadingState from '../components/LoadingState.jsx'

export default function StockSearchPage() {
  const [params, setParams] = useSearchParams()
  return <SearchScreen key={params.toString()} submittedMarket={params.get('market') || 'KR'} submittedKeyword={params.get('keyword') || ''} setParams={setParams} />
}

function SearchScreen({ submittedMarket, submittedKeyword, setParams }) {
  const validSubmitted = ['KR', 'US'].includes(submittedMarket) && Boolean(submittedKeyword.trim()) && submittedKeyword.length <= 100
  const [market, setMarket] = useState(submittedMarket === 'US' ? 'US' : 'KR')
  const [keyword, setKeyword] = useState(submittedKeyword)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(Boolean(submittedKeyword) && validSubmitted)
  const [error, setError] = useState(submittedKeyword && !validSubmitted ? '시장과 검색어를 확인해 주세요.' : '')
  const [formError, setFormError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!validSubmitted) return
    const controller = new AbortController()
    searchAssets(submittedMarket, submittedKeyword.trim(), controller.signal)
      .then((data) => { if (!controller.signal.aborted) setResult(data) })
      .catch((failure) => { if (!isCancelled(failure) && !controller.signal.aborted) setError(errorMessage(failure)) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [submittedMarket, submittedKeyword, validSubmitted, attempt])

  function retrySearch() {
    if (!validSubmitted) return
    setLoading(true); setError(''); setAttempt((value) => value + 1)
  }

  function submit(event) {
    event.preventDefault()
    const term = keyword.trim()
    if (!term || term.length > 100) { setFormError('검색어를 1~100자 이내로 입력해 주세요.'); return }
    setFormError('')
    if (market === submittedMarket && term === submittedKeyword) retrySearch()
    else setParams({ market, keyword: term })
  }

  return <>
    <div className="page-heading"><p className="eyebrow">FIND YOUR ASSET</p><h1>종목 검색</h1><p>관심 있는 주식과 ETF를 찾아보세요.</p></div>
    <section className="card search-form-card" aria-label="종목 검색 입력">
      <form onSubmit={submit}>
        <fieldset className="market-fieldset"><legend>시장 선택</legend><div className="market-toggle">
          <button type="button" aria-pressed={market === 'KR'} onClick={() => setMarket('KR')}>국내 <span>KR</span></button>
          <button type="button" aria-pressed={market === 'US'} onClick={() => setMarket('US')}>미국 <span>US</span></button>
        </div></fieldset>
        <label htmlFor="search-keyword">종목명 또는 종목코드</label>
        <div className="search-input-row"><input id="search-keyword" name="keyword" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder={market === 'KR' ? '예: 삼성전자, 005930' : '예: SCHD, KO'} maxLength={100} autoComplete="off" required aria-describedby="search-help" /><button className="button" type="submit" disabled={loading}>{loading ? '검색 중…' : '검색'}<span aria-hidden="true">→</span></button></div>
        <p className="field-help" id="search-help">{market === 'KR' ? '국내 종목코드는 앞자리 0을 포함해 입력하세요.' : '미국 종목은 영문 티커 또는 종목명으로 검색하세요.'}</p>
        <ErrorMessage message={formError} />
      </form>
    </section>
    <section className="search-results" aria-label="종목 검색 결과" aria-busy={loading}>
      {loading ? <LoadingState /> : error ? <ErrorMessage message={error} onRetry={validSubmitted ? retrySearch : undefined} /> : result ? <>
        <div className="section-heading"><h2>검색 결과 <span className="count-label">{result.data.length}</span></h2><span className="subtle">종목을 선택하면 상세정보로 이동합니다.</span></div>
        <DataStatus {...result} />
        {result.data.length ? <div className="asset-grid">{result.data.map((asset) => <AssetCard key={`${asset.market}-${asset.ticker}`} asset={asset} />)}</div> : <div className="state-box">검색 결과가 없습니다.</div>}
      </> : <div className="search-intro"><span className="intro-symbol" aria-hidden="true">⌕</span><h2>어떤 종목이 궁금하신가요?</h2><p>시장을 선택하고 종목명이나 코드를 입력하세요.</p></div>}
    </section>
  </>
}
