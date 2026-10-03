import { Link, NavLink } from 'react-router-dom'
export default function Header() {
  return <header className="site-header"><div className="container header-inner">
    <Link to="/" className="brand" aria-label="Stock Dividend 홈"><span className="brand-mark" aria-hidden="true">sd<span>.</span></span><span>Stock Dividend</span></Link>
    <nav aria-label="주요 메뉴"><NavLink to="/" end>홈</NavLink><NavLink to="/search">종목 검색</NavLink><NavLink to="/calculator">배당 계산기</NavLink></nav>
  </div></header>
}
