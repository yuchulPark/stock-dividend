import { BrowserRouter } from 'react-router-dom'
import Header from './components/Header.jsx'
import AppRouter from './router/AppRouter.jsx'

export default function App() {
  return (
    <BrowserRouter>
      <a className="skip-link" href="#main-content">본문으로 건너뛰기</a>
      <Header />
      <main id="main-content" className="container main-content"><AppRouter /></main>
      <footer className="site-footer container">
        <span>Stock Dividend Development Project</span>
        <span>가격과 배당의 기준일을 확인하세요.</span>
      </footer>
    </BrowserRouter>
  )
}
