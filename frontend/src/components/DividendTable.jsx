import { formatCurrency, formatDate } from '../utils/formatCurrency.js'
const reportNames = { '11011': '사업보고서', '11012': '반기보고서', '11013': '1분기보고서', '11014': '3분기보고서' }
const sources = { ALPHA_VANTAGE: 'Alpha Vantage', OPEN_DART: 'OpenDART', KRX: 'KRX' }
export default function DividendTable({ dividends, market }) {
  if (!dividends.length) return <div className="state-box">등록된 배당 정보가 없습니다.</div>
  const reportData = market === 'KR' || dividends.some((row) => row.dataType === 'REPORT')
  return <>
    {reportData && <p className="info-note">사업연도별 정기보고서의 주당 현금배당금입니다. 개별 지급 이벤트나 월 예상 배당금이 아니며, 확인되지 않은 날짜는 -로 표시합니다.</p>}
    <div className="table-scroll" tabIndex={0} role="region" aria-label="배당 이력 표"><table>
      <caption className="sr-only">{reportData ? '사업연도별 배당 공시' : '배당 지급 이벤트'} 목록</caption>
      <thead><tr>
        {reportData && <><th scope="col">사업연도</th><th scope="col">보고서</th></>}
        <th scope="col">배당락일</th><th scope="col" className="numeric">주당 배당금</th><th scope="col">기준일</th><th scope="col">지급일</th><th scope="col">통화</th>
        {reportData && <><th scope="col">주식 종류</th><th scope="col">공시번호</th></>}<th scope="col">출처</th>
      </tr></thead>
      <tbody>{dividends.map((row, index) => <tr key={`${row.source}-${row.businessYear ?? row.exDividendDate}-${row.reportCode ?? ''}-${row.shareClass ?? ''}-${index}`}>
        {reportData && <><td>{row.businessYear ?? '-'}</td><td>{reportNames[row.reportCode] || row.reportCode || '-'}</td></>}
        <td>{formatDate(row.exDividendDate)}</td><td className="numeric amount">{formatCurrency(row.dividendPerShare, row.currency, { dividend: true })}</td><td>{formatDate(row.recordDate)}</td><td>{formatDate(row.paymentDate)}</td><td>{row.currency || '-'}</td>
        {reportData && <><td>{row.shareClass || '-'}</td><td>{row.filingNumber || '-'}</td></>}<td>{sources[row.source] || '-'}</td>
      </tr>)}</tbody>
    </table></div>
  </>
}
