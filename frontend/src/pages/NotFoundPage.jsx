import { Link } from 'react-router-dom'
export default function NotFoundPage() {
  return <section className="not-found"><p className="eyebrow">404 · PAGE NOT FOUND</p><h1>페이지를 찾을 수 없습니다.</h1><p>주소를 확인하거나 홈에서 다시 시작하세요.</p><Link className="button" to="/">홈으로 이동 →</Link></section>
}
