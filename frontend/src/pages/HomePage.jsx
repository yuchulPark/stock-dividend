import { Link } from 'react-router-dom'

export default function HomePage() {
  return <>
    <section className="home-hero">
      <div className="hero-copy">
        <p className="eyebrow"><span className="tiny-dot" /> STOCKS & DIVIDENDS</p>
        <h1>주식과 배당,<br /><span>한눈에 더 명확하게.</span></h1>
        <p className="hero-description">국내·미국 주식과 ETF의 가격부터 배당 이력까지.<br className="desktop-break" /> 관심 있는 종목의 정보를 차분히 살펴보세요.</p>
        <div className="button-row"><Link className="button" to="/search">종목 검색하기 <span aria-hidden="true">↗</span></Link><Link className="button button-outline" to="/calculator">배당 계산기</Link></div>
        <div className="hero-bottom"><span>국내 / 미국</span><span>주식 / ETF</span><span>기준일이 있는 데이터</span></div>
      </div>
      <div className="market-overview" aria-label="지원 시장 안내">
        <div className="overview-heading"><span>하나의 화면, 두 개의 시장</span><span className="badge badge-neutral">조회 서비스</span></div>
        <Link to="/search?market=KR" className="market-panel"><span className="market-symbol">KR</span><div><h2>국내 주식 · ETF</h2><p>거래일 종가와 일반주식 배당 공시</p></div><span aria-hidden="true">↗</span></Link>
        <Link to="/search?market=US" className="market-panel"><span className="market-symbol market-symbol-us">US</span><div><h2>미국 주식 · ETF</h2><p>가격 정보와 배당 지급 이력</p></div><span aria-hidden="true">↗</span></Link>
        <div className="overview-note"><span aria-hidden="true">ⓘ</span><p>가격은 데이터의 기준일을 따릅니다.<br />국내 가격은 최근 이용 가능한 거래일 종가입니다.</p></div>
      </div>
    </section>
    <section className="home-tools" aria-labelledby="tools-title">
      <div className="section-heading"><div><p className="eyebrow">START EXPLORING</p><h2 id="tools-title">관심 종목부터 시작하세요</h2></div><span className="subtle">배당 정보를 확인하는 첫걸음</span></div>
      <div className="tool-grid">
        <Link className="tool-card" to="/search"><span className="tool-icon" aria-hidden="true">⌕</span><h3>종목 검색</h3><p>종목명이나 티커로 검색하고,<br />가격과 배당 정보를 확인하세요.</p><span className="text-link">검색으로 이동 <span aria-hidden="true">→</span></span></Link>
        <Link className="tool-card" to="/calculator"><span className="tool-icon" aria-hidden="true">＋</span><h3>배당 계산기</h3><p>투자금 기준 배당 계산을 위한<br />입력 화면을 미리 만나보세요.</p><span className="text-link">계산기 보기 <span className="badge badge-neutral">연결 준비 중</span></span></Link>
        <div className="tool-card upcoming-card"><span className="tool-icon" aria-hidden="true">▦</span><h3>다음으로 만날 기능</h3><p>배당 캘린더 · 목표 월배당<br />내 포트폴리오</p><span className="subtle">추후 추가 예정</span></div>
      </div>
    </section>
  </>
}
