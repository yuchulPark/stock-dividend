import { Route, Routes } from 'react-router-dom'
import HomePage from '../pages/HomePage.jsx'
import StockSearchPage from '../pages/StockSearchPage.jsx'
import AssetDetailPage from '../pages/AssetDetailPage.jsx'
import DividendCalculatorPage from '../pages/DividendCalculatorPage.jsx'
import NotFoundPage from '../pages/NotFoundPage.jsx'

export default function AppRouter() {
  return <Routes>
    <Route path="/" element={<HomePage />} />
    <Route path="/search" element={<StockSearchPage />} />
    <Route path="/asset/:market/:ticker" element={<AssetDetailPage />} />
    <Route path="/calculator" element={<DividendCalculatorPage />} />
    <Route path="*" element={<NotFoundPage />} />
  </Routes>
}
