import { formatDateTime } from '../utils/formatCurrency.js'
export default function DataStatus({ dataFresh, syncedAt, warningCode }) {
  return <div className="data-status" role="status">
    <span className={`badge ${dataFresh === false ? 'badge-warning' : 'badge-neutral'}`}>{dataFresh === true ? '캐시 기준 최신' : dataFresh === false ? '이전 저장 데이터' : '갱신 상태 미확인'}</span>
    {syncedAt && <span>수집 시각 {formatDateTime(syncedAt)} · 한국시간</span>}
    {(dataFresh === false || warningCode) && <span className="status-warning">갱신을 완료하지 못해 이전 데이터를 표시합니다.</span>}
  </div>
}
