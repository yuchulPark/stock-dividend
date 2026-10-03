import { useState } from 'react'
import { Link } from 'react-router-dom'
import ErrorMessage from '../components/ErrorMessage.jsx'

export default function DividendCalculatorPage() {
  const [market, setMarket] = useState('KR')
  const [ticker, setTicker] = useState('')
  const [investment, setInvestment] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  function submit(event) {
    event.preventDefault()
    const symbol = ticker.trim().toUpperCase()
    const validTicker = market === 'KR' ? /^[0-9A-Z]{6}$/.test(symbol) : /^[A-Z0-9][A-Z0-9.-]{0,31}$/.test(symbol)
    if (!validTicker || !/^[0-9]+(?:\.[0-9]{1,8})?$/.test(investment) || !/[1-9]/.test(investment)) {
      setMessage(''); setError('올바른 종목코드와 0보다 큰 투자금액을 입력해 주세요.'); return
    }
    setError(''); setMessage('배당 계산 API 구현 후 연결 예정')
  }
  return <>
    <div className="page-heading"><p className="eyebrow">PLAN YOUR DIVIDENDS</p><h1>배당 계산기</h1><p>투자금 기준 예상 배당금 계산을 준비하고 있습니다.</p></div>
    <div className="calculator-layout"><section className="card calculator-card">
      <div className="section-heading"><h2>투자 정보</h2><span className="badge badge-neutral">연결 준비 중</span></div>
      <form onSubmit={submit}>
        <label htmlFor="calculator-market">시장</label><select id="calculator-market" value={market} onChange={(event) => { setMarket(event.target.value); setMessage(''); setError('') }}><option value="KR">국내 · KR</option><option value="US">미국 · US</option></select>
        <label htmlFor="calculator-ticker">종목코드 / 티커</label><input id="calculator-ticker" value={ticker} onChange={(event) => { setTicker(event.target.value); setMessage('') }} placeholder={market === 'KR' ? '예: 005930' : '예: SCHD'} maxLength={32} autoComplete="off" required />
        <label htmlFor="calculator-investment">투자금액 <span className="subtle">{market === 'KR' ? 'KRW · 원' : 'USD · 달러'}</span></label>
        <input id="calculator-investment" value={investment} onChange={(event) => { setInvestment(event.target.value); setMessage('') }} placeholder={market === 'KR' ? '예: 30000000' : '예: 10000'} inputMode="decimal" autoComplete="off" maxLength={25} required aria-describedby="investment-help" />
        <p id="investment-help" className="field-help">선택한 시장의 통화로 입력하세요. 환율 변환은 제공하지 않습니다.</p>
        <button className="button button-full" type="submit">계산하기 <span aria-hidden="true">→</span></button>
        <ErrorMessage message={error} />{message && <p className="info-note" role="status">{message}</p>}
      </form>
    </section><aside className="calculator-explanation"><span className="tool-icon" aria-hidden="true">＋</span><h2>배당 계획의 시작</h2><p>시장, 종목, 투자금액을 입력하는 화면입니다. 배당 계산 API 구현 후 연결 예정입니다.</p><p>현재는 계산 결과를 제공하지 않습니다. 종목 상세에서 제공되는 가격과 배당 이력을 먼저 확인해 보세요.</p><Link className="text-link" to="/search">종목 정보 확인하기 →</Link></aside></div>
  </>
}
