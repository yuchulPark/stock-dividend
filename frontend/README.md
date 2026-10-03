# Stock Dividend · React 프론트엔드

기존 Spring Boot REST API를 사용하는 React + Vite / JavaScript 화면입니다.
백엔드와 같은 저장소의 `frontend/`에서 관리하며 개발 실행은 별도 프로세스입니다.

## 로컬 실행

**Backend**

Eclipse에서 기존 `StockApplication` 실행 → <http://localhost:8080>.
DB 비밀번호와 Alpha Vantage / KRX / OpenDART 인증키는 기존 백엔드 실행 환경변수에만 설정합니다.

**Frontend**

Node.js가 설치된 터미널에서:

```powershell
cd C:\dev\stock\frontend
npm install
npm run dev
```

<http://localhost:5173> 접속. Backend와 Frontend를 동시에 실행합니다.
현재 검증 환경은 Node.js 24.21.0 / npm 11.19.0입니다.
`npm`이 인식되지 않으면 VS Code/터미널을 완전히 종료 후 다시 열고 `node --version`, `npm --version`을 확인하세요.
이 컴퓨터의 Node.js 설치 위치는 `C:\Program Files\nodejs`입니다.

기본 API 주소는 `/api`이므로 `.env` 없이도 개발 실행이 가능합니다.
변경할 경우 `.env.example`을 `frontend/.env`로 복사하여 공개 설정만 입력합니다.

```properties
VITE_API_BASE_URL=/api
VITE_ENABLE_REFRESH=false
```

`VITE_` 값은 브라우저에 노출됩니다. 금융 API 인증키, DB 접속 정보 등 Secret을 넣지 않습니다.
개발용 갱신 버튼은 프론트엔드 `VITE_ENABLE_REFRESH=true`와 백엔드 `STOCK_REFRESH_ENABLED=true`를 모두 설정한 경우 사용하세요.
프론트엔드 설정 변경 후 Vite를 재시작합니다. 백엔드 갱신 API가 비활성화되어 있으면 404 안내를 표시합니다.

## 구현 보고

### 1. 확인한 Backend API 목록

| Controller | 메서드 / 경로 | 용도 |
|---|---|---|
| StockController | GET `/` | 서버 실행 확인 |
| StockController | GET `/api/hello` | 서버 응답 확인 |
| StockAssetController | GET `/api/assets/search?market=KR&keyword=삼성전자` | 종목 검색. market 기본값 US, keyword 필수 |
| StockAssetController | GET `/api/assets/{market}/{ticker}` | 종목 상세 |
| StockAssetController | GET `/api/assets/{market}/{ticker}/dividends` | 배당 이력 |
| AssetRefreshController | POST `/api/assets/{market}/{ticker}/refresh` | 설정으로 활성화하는 개발용 갱신 |

배당 계산 API와 Request DTO는 현재 존재하지 않습니다.
ErrorResponse는 `{code,message}`이며 GlobalExceptionHandler가 이를 반환합니다.
Enum: Market=KR/US, AssetType=STOCK/ETF, Currency=KRW/USD.
검색/배당은 배열 본문과 `X-Data-Fresh`, `X-Data-Synced-At`, `X-Data-Warning` 헤더를 사용합니다.

### 2. 생성한 React 파일 목록

```text
frontend/
├─ .env.example
├─ .gitignore
├─ .oxlintrc.json
├─ index.html
├─ package.json
├─ package-lock.json
├─ README.md
├─ vite.config.js
├─ public/favicon.svg
└─ src/
   ├─ main.jsx
   ├─ App.jsx
   ├─ api/
   │  ├─ httpClient.js
   │  ├─ assetApi.js
   │  └─ dividendApi.js
   ├─ components/
   │  ├─ Header.jsx
   │  ├─ LoadingState.jsx
   │  ├─ ErrorMessage.jsx
   │  ├─ DataStatus.jsx
   │  ├─ AssetCard.jsx
   │  └─ DividendTable.jsx
   ├─ pages/
   │  ├─ HomePage.jsx
   │  ├─ StockSearchPage.jsx
   │  ├─ AssetDetailPage.jsx
   │  ├─ DividendCalculatorPage.jsx
   │  └─ NotFoundPage.jsx
   ├─ router/AppRouter.jsx
   ├─ utils/formatCurrency.js
   └─ styles/global.css
```

작업 전에 frontend 폴더와 Vue 파일은 없었습니다.
이번에 생성한 Vite 템플릿의 데모 화면·이미지·CSS만 실제 화면으로 교체했습니다.

### 3. 수정한 기존 Backend 파일

없음. Java 소스, 기존 테스트, application.properties, pom.xml을 변경하지 않았습니다.
작업 전후 src 전체와 pom.xml의 SHA-256 해시가 일치합니다. DB Schema 변경도 없습니다.

### 4. 루트 .gitignore 변경

기존 내용을 보존하고 다음만 추가했습니다.

```gitignore
frontend/node_modules/
frontend/dist/
frontend/.env
frontend/.env.local
frontend/.env.*.local
!frontend/.env.example
```

package-lock.json을 ignore하지 않습니다. Git commit/push와 브랜치/설정 변경은 하지 않았습니다.

### 5. package.json 의존성

| 종류 | package.json 선언 | 실제 설치 |
|---|---|---|
| Runtime | react `^19.2.8` | 19.3.0 |
| Runtime | react-dom `^19.2.8` | 19.3.0 |
| Runtime | react-router-dom `^7.18.4` | 7.18.4 |
| Runtime | axios `^1.20.0` | 1.20.0 |
| Dev | vite `^8.3.0` | 8.3.2 |
| Dev | @vitejs/plugin-react `^6.1.1` | 6.1.1 |
| Dev | oxlint `^1.81.0` | 1.86.0 |
| Dev | @types/react `^19.2.18` | 19.3.0 |
| Dev | @types/react-dom `^19.2.7` | 19.3.0 |

Vite 공식 JavaScript React 템플릿의 기본 패키지와 요청한 Router/Axios만 설치했습니다.
TypeScript 소스는 없습니다. @types 패키지는 템플릿 기본 개발 도구입니다.
설치 결과는 package-lock.json에 기록했습니다. Redux, Zustand, UI 프레임워크, React Query, Form Library는 추가하지 않았습니다.

### 6. Router

| Route | 화면 |
|---|---|
| `/` | HomePage |
| `/search` | StockSearchPage |
| `/asset/:market/:ticker` | AssetDetailPage |
| `/calculator` | DividendCalculatorPage |
| `*` | NotFoundPage |

Link/NavLink로 페이지를 이동합니다. 검색 시장/키워드는 URL 쿼리에 보존하여 뒤로 가기와 링크 공유에 사용할 수 있습니다.

### 7. Axios 구조

httpClient: 환경변수 baseURL, JSON 응답, 60초 timeout, 오류/캐시 헤더 처리.
assetApi: searchAssets / getAsset / refreshAsset.
dividendApi: getDividends.
Page에 Axios 호출을 반복하지 않습니다. 종목코드를 URL 인코딩하고, 이동/검색 변경 시 AbortController로 이전 요청을 취소합니다.
계산 API가 없으므로 calculatorApi.js나 가짜 HTTP 함수는 만들지 않았습니다.

### 8. Vite Proxy

개발 서버는 localhost:5173을 사용하고 이미 사용 중이면 다른 포트로 자동 이동하지 않습니다.

```js
server: {
  host: 'localhost', port: 5173, strictPort: true,
  proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
}
```

`/api` 경로를 그대로 전달합니다. Axios에 백엔드 호스트를 하드코딩하지 않았습니다.
Spring Boot CORS/설정은 변경하지 않았습니다. 이 프록시는 `npm run dev`의 개발용이며 `dist`를 배포하는 설정은 아닙니다.

### 9. 각 화면

Home: 서비스 소개, 국내/미국 안내, 검색/계산기 메뉴, 향후 기능 안내. 가격/수익률을 임의로 생성하지 않습니다.
Search: KR/US 선택, 종목명/티커 입력, DTO의 ticker/name/market/assetType/currency를 카드로 표시.
Detail: 기본정보, 통화별 가격, 가격 기준일, 수집 시각, 최신 여부, 별도 배당 영역. 개발용 갱신은 선택 설정입니다.
Calculator: 시장/티커/투자금 입력과 유효성 확인, API 연결 예정 안내.
404: 홈으로 돌아가는 링크.
모바일에서는 카드가 세로 배치되고 넓은 배당 표만 내부에서 가로 스크롤합니다.

### 10. 실제 Backend에 연결된 기능

검색 GET, 종목 상세 GET, 배당 이력 GET, 설정으로 켠 개발용 refresh POST.
React는 Spring Boot만 호출하고 외부 금융 API에 직접 접속하지 않습니다.

### 11. 연결하지 않은 기능

배당 계산 API 없음 → 입력 UI만 제공하며 “배당 계산 API 구현 후 연결 예정” 표시.
프론트엔드에서 매수 가능 수량, 예상 배당, 환율 등을 계산하지 않습니다.
포트폴리오/목표 월배당/캘린더는 향후 안내만 있고 가짜 화면/API를 만들지 않았습니다.

### 12. 국내 KR

종목코드는 문자열로 다루어 앞자리 0을 보존합니다.
가격은 “거래일 종가”로 표시하고 priceAsOfDate를 그대로 보여줍니다. 실시간 문구를 사용하지 않습니다.
KRW 85000 → 85,000원. dataFresh는 캐시 기준 최신 여부이며 오늘의 거래일을 의미하지 않습니다.

### 13. 미국 US

가격은 “조회 가격”으로 표시합니다. 미국 데이터도 실시간이라고 표시하지 않습니다.
USD 31.5 → $31.50. 검색/상세/배당은 기존 US endpoint 그대로 연결합니다.

### 14. 배당 데이터

US EVENT: 배당락일 / 주당 배당금 / 기준일 / 지급일 / 통화 / 출처.
KR REPORT: 사업연도 / 보고서 / 주식 종류 / 공시번호를 추가하고 지급 이벤트와 다르다는 설명을 표시합니다.
null 날짜는 -입니다. 결산기준일을 임의 지급일로 만들거나 연간/월간 배당을 합산하지 않습니다.
주당 배당 표시에서는 최대 소수점 8자리를 유지합니다. Intl.NumberFormat은 표시용이며 비즈니스 계산을 하지 않습니다.
국내 ETF 미지원은 배당 영역에만 안내하고 가격 상세는 유지합니다.

### 15. Error / Loading / Empty / Stale

LoadingState로 요청 중 안내. ErrorMessage에 알려진 Backend 오류 코드별 한국어 메시지와 재시도 버튼을 제공합니다.
원본 JSON, 알 수 없는 서버 메시지, Stack Trace는 렌더링하지 않습니다.
검색/배당의 빈 결과 안내를 제공합니다. 미지원 기능에는 재시도 버튼 없이 안내를 표시합니다.
X-Data-Fresh=false 또는 상세 dataFresh=false이면 이전 저장 데이터와 갱신 실패 안내를 표시합니다.
배당 오류가 종목 가격 영역을 숨기지 않습니다. 수집 시각은 Backend UTC를 한국시간으로 표시합니다.

### 16. Production build

`npm run build` 성공. Vite 8.3.2 / 95 modules.
결과는 frontend/dist에만 생성했습니다. Spring static 복사/JAR 통합/배포는 하지 않았습니다.

### 17. Lint

`npm run lint` 성공, 경고 없음.
최신 생성 템플릿의 기본 lint는 Oxlint입니다. ESLint를 따로 추가하지 않았습니다.

### 18. Maven test

기존 테스트를 수정하지 않고 실행했습니다. 60개, 실패 0 / 오류 0 / 건너뜀 0, BUILD SUCCESS.
PostgreSQL 통합 테스트는 기존 분리된 테스트 스키마에서 실행했습니다. public 데이터를 초기화하지 않았습니다.
검증 로그: 루트 target/frontend-maven-test.log.

### 19. 기존 Backend 유지 및 연결 검증

소스/설정/테스트와 pom.xml 변경 없음. 기존 Alpha Vantage / KRX / OpenDART 기능은 그대로입니다.
모의 API 기반 Edge 브라우저 검증: 검색 파라미터·라우팅·오류·빈 값·stale·KR REPORT·US EVENT·ETF 미지원·refresh POST·계산기 API 미호출·404·375px 화면 통과.
임시 Playwright 검증 도구는 루트 target에만 설치했으며 앱 의존성에 추가하지 않았습니다.
실제 Spring Boot ↔ Vite /api 프록시의 정상 응답 및 인증키 누락 오류 응답도 확인했습니다.
검증용 로컬 프로세스는 종료했습니다. 금융 API의 실응답 검증은 기존 백엔드 인증 설정에 따라 사용자 실행에서 진행하세요.

### 20. 실행/확인 주소

Backend: Eclipse StockApplication → http://localhost:8080.
Frontend: `cd frontend` → `npm install` → `npm run dev` → http://localhost:5173.

```text
http://localhost:5173/
http://localhost:5173/search
http://localhost:5173/asset/KR/005930
http://localhost:5173/asset/US/SCHD
http://localhost:5173/calculator
```

Backend를 실행하지 않으면 홈/계산기 입력 UI는 표시되지만 API 조회는 실패합니다.
인증키/이용 승인이 없으면 조회 오류 또는 기존 Backend 캐시 fallback이 표시될 수 있습니다.
`npm run preview`는 빌드 확인용입니다. 배포 설정은 이번 작업 범위가 아닙니다.

## 확인한 공식 문서

- [Vite 프로젝트 생성](https://vite.dev/guide/)
- [Vite server.proxy](https://vite.dev/config/server-options.html#server-proxy)
- [React Router Declarative](https://reactrouter.com/start/declarative/installation)
- [Axios instance](https://axios-http.com/docs/instance)
