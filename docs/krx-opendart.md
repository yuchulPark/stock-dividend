# KRX / OpenDART 국내시장 구현 및 실행 안내

2026-10-03 구현. Java 17 / Spring Boot 3.5.16 / Maven / PostgreSQL 17 유지.
국내 가격은 **최근 이용 가능한 거래일의 종가**이며 실시간 현재가가 아니다.

운영/상업 서비스 전환 시 시장 데이터 이용약관 및 재배포 권한을 다시 확인해야 함

## 공식 명세 확인

구현 전에 KRX 공식 서비스 목록 및 각 서비스 페이지의 개발 명세서와 OpenDART 개발가이드를 확인했다.
KRX 공개 개발 명세서에 기재된 API ID와 요청/출력 필드를 사용했다.

- [KRX 유가증권 일별매매정보](https://openapi.krx.co.kr/contents/OPP/USES/service/OPPUSES002_S2.cmd?BO_ID=JvJFzlAENzZlPBDNGAWC)
- [KRX 유가증권 종목기본정보](https://openapi.krx.co.kr/contents/OPP/USES/service/OPPUSES002_S2.cmd?BO_ID=PiwgMdTwmsenXhmqqxuj)
- [KRX 서비스 이용방법](https://openapi.krx.co.kr/contents/OPP/INFO/OPPINFO003.jsp)
- [OpenDART 배당에 관한 사항](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS002&apiId=2019005)
- [OpenDART 고유번호](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019018)
- [OpenDART 인증키 신청](https://opendart.fss.or.kr/uss/umt/EgovMberInsertView.do)

## 1. 생성한 파일

`src/main/java/com/stock/` 기준:

- `config/KrxProperties.java`, `config/OpenDartProperties.java`
- `external/OfficialApiErrors.java`
- `external/krx/KrxApiClient.java`, `KrxMapper.java`, `KrxCatalog.java`, `dto/KrxResponse.java`
- `external/market/KrxMarketDataProvider.java`
- `external/opendart/OpenDartApiClient.java`, `OpenDartMapper.java`, `OpenDartCorporationCatalog.java`, `dto/OpenDartResponse.java`
- `external/dividend/OpenDartDividendProvider.java`

추가 테스트: `external/krx/KrxTests.java`, `external/opendart/OpenDartTests.java`.
문서: 이 파일 및 `docs/sql/20261003-krx-opendart.sql`.

## 2. 수정한 파일

- `config/RestClientConfig.java`, `CacheProperties.java`
- `external/market/MarketDataProvider.java`, `MarketDataProviderRegistry.java`
- `external/dividend/DividendDataProvider.java`, `external/model/DividendData.java`
- `entity/DataSourceType.java`, `Dividend.java`
- `repository/DividendRepository.java`
- `service/AssetSyncService.java`, `AssetPersistenceService.java`
- `dto/response/StockAssetResponse.java`, `DividendResponse.java`
- `src/main/resources/application.properties`
- 테스트: `StockApplicationTests`, `StockAssetControllerTests`, `AssetSyncServiceTests`, `KoreanAssetSyncServiceTests`, `PostgresRepositoryTests`, `application-test.properties`
- 기존 안내: `docs/alpha-vantage-mvp.md`, `docs/kis-market-data.md`

## 3. 삭제한 파일

KIS 전용 Java 9개:

- `config/KisProperties.java`
- `external/market/KisMarketDataProvider.java`
- `external/kis/KisErrors.java`, `KisTokenService.java`, `KisApiClient.java`
- `external/kis/KisMasterFileClient.java`, `KisMapper.java`, `KisMasterCatalog.java`, `dto/KisResponse.java`

KIS 전용 테스트 6개: `KisTokenServiceTests`, `KisApiClientTests`, `KisMapperTests`, `KisMasterCatalogTests`, `KisMasterFileClientTests`, `KisMarketDataProviderTests`.

## 4. KIS 제거 범위

전용 코드, Spring Bean, 설정, DTO, 토큰/마스터 다운로드 로직과 테스트를 제거했다.
공통 Registry / Service / Entity / Repository와 미국 Alpha Vantage 코드는 보존했다.
기존 KIS 안내는 과거 기록임을 첫머리에 표시했다. `target`의 과거 로그는 실행 코드가 아니다.
과거 `KR:<keyword>` 또는 KIS 전체목록 캐시는 삭제하지 않고 KRX 전용 키로 분리해 재사용을 막았다.
삭제 전 참조를 확인했고 PostgreSQL `dividend`에 KIS 저장값이 없는 것을 확인했다.

## 5. pom.xml

변경 없음. 의존성 추가 없음. Java 17 / Spring Boot 3.5.16 / Maven 유지.
ZIP/XML은 Java 표준 라이브러리, JSON은 기존 Jackson, HTTP는 기존 Spring RestClient를 사용한다.

## 6. application.properties

KIS 설정을 제거하고 다음을 추가했다.

```properties
external.krx.auth-key=${KRX_AUTH_KEY:}
external.krx.lookback-days=14
external.krx.connect-timeout=5s
external.krx.read-timeout=10s
external.opendart.api-key=${OPENDART_API_KEY:}
external.opendart.years=5
external.opendart.report-code=11011
external.opendart.connect-timeout=5s
external.opendart.read-timeout=10s
stock.cache.kr-price-ttl=6h
```

`stock.cache.price-ttl=30m`은 미국시장용으로 유지한다. 국내 TTL을 12시간으로 바꾸려면 `stock.cache.kr-price-ttl=12h`로 설정한다.
API 키 기본값은 빈 문자열이다. 키 없이 서버가 시작되며, 필요한 외부 호출에서만 `EXTERNAL_API_KEY_MISSING`을 반환한다.
DB 접속 환경변수는 여전히 필요하다.

## 7. 필요한 환경변수와 인증키 발급

| 환경변수 | 값의 출처 / 용도 |
|---|---|
| `KRX_AUTH_KEY` | KRX OPEN API에서 발급된 인증키 |
| `OPENDART_API_KEY` | OpenDART에서 발급된 인증키 |
| `ALPHA_VANTAGE_API_KEY` | 기존 미국시장 인증키 유지 |
| `DB_PASSWORD` | 로컬 PostgreSQL `stock_user` 비밀번호 |
| `DB_URL` | 선택. 기본 `jdbc:postgresql://localhost:5432/stock` |
| `DB_USERNAME` | 선택. 기본 `stock_user` |
| `STOCK_REFRESH_ENABLED` | 개발 중 강제 갱신을 사용할 때 `true`. 기본 `false` |

KRX: 공식 홈페이지 회원가입/로그인 → 마이페이지 API 인증키 신청 → 승인 및 발급내역 확인.
인증키 발급과 별도로 아래 5개 **각 API 서비스 이용신청 및 승인**도 확인한다. 공식 이용방법은 두 승인 절차를 구분한다.

OpenDART: 인증키 신청/관리 → 인증키 신청 → 개인 개발 목적과 사용 환경을 입력하여 등록 → 인증키 관리에서 발급값과 상태를 확인한다.
실제 키를 Java 코드, 문서 또는 채팅에 붙여 넣을 필요가 없다.
KIS 환경변수는 더 이상 사용하지 않는다.

## 8. KRX 사용 API 목록

모두 GET. Base URL `https://data-dbg.krx.co.kr`. Query `basDd=YYYYMMDD`.

| 공식 서비스 | API ID / 경로 |
|---|---|
| 유가증권 종목기본정보 | `/svc/apis/sto/stk_isu_base_info` |
| 코스닥 종목기본정보 | `/svc/apis/sto/ksq_isu_base_info` |
| 유가증권 일별매매정보 | `/svc/apis/sto/stk_bydd_trd` |
| 코스닥 일별매매정보 | `/svc/apis/sto/ksq_bydd_trd` |
| ETF 일별매매정보 | `/svc/apis/etp/etf_bydd_trd` |

2010-01-04 이후 데이터 제공. JSON 루트 `OutBlock_1`의 목록을 읽는다.
기본정보: `ISU_SRT_CD`, `ISU_NM`, `MKT_TP_NM`, `KIND_STKCERT_TP_NM`.
시세: `ISU_CD`, `ISU_NM`, `BAS_DD`, `TDD_CLSPRC`.
`ISU_CD`는 기본정보에서 표준코드이므로 기본정보 ticker에는 `ISU_SRT_CD`를 사용한다.
ETF/일별매매정보의 `ISU_CD`는 단축코드다.

## 9. KRX 인증

공식 명세대로 HTTP Header `AUTH_KEY`에 `KRX_AUTH_KEY`를 넣는다.
OAuth 토큰이나 App Secret, 계좌번호는 사용하지 않는다.
빈 키 오류 메시지: `KRX OPEN API 인증키가 설정되어 있지 않습니다.`
요청 URI나 원본 예외를 API 오류 메시지에 노출하지 않는다.

## 10. 국내 주식 검색

KR Registry → KRX KOSPI/KOSDAQ 종목기본정보 전체목록 → 기존 `asset_search_cache`에 JSON 스냅샷 저장 → 서버에서 ticker/name 포함 검색.
KRX에 keyword 요청을 보내지 않는다. 정확한 ticker 일치를 먼저 정렬하고 최대 100건을 반환한다.
기본정보는 24시간 캐시한다. 종목 기본정보의 공식 시장 구분을 보존해 가격 API를 선택한다.

## 11. 국내 ETF 검색

ETF 일별매매정보 전체목록에서 종목 코드와 이름을 얻는다. 해당 공식 서비스에서 제공된 종목만 `ETF`로 분류한다.
이름으로 ETF를 추측하지 않는다. `market=KR`, `assetType=ETF`, `currency=KRW`.
ETF 일별목록도 기존 캐시 테이블을 이용한다.

## 12. 국내 가격 조회

DB 조회 → 기본정보/가격 TTL 확인 → 필요 시 KRX 스냅샷 → KOSPI/KOSDAQ/ETF 가격 API 선택 → 정확한 ticker의 종가 검증 → DB 저장 → DTO 반환.
BigDecimal로 `currentPrice`, LocalDate로 실제 `priceAsOfDate`를 저장한다.
응답 `priceType=EOD`. `priceUpdatedAt`은 수집 시각, `priceAsOfDate`는 실제 거래일로 서로 다르다.
`dataFresh=true`는 캐시 정책상 갱신에 성공했다는 의미이며 오늘 거래가 존재한다는 의미가 아니다.

KRX 명세는 당일 자료를 제외하며 최신 제공 거래일에 다음 날 08시 조건이 있다. 당일 장 종료 직후 종가 제공을 약속하지 않는다.
서울 날짜의 전일부터 최대 14일을 역조회하며 토/일은 건너뛰고 빈 거래일은 더 이전 날짜를 조회한다.
설정 가능한 최대 탐색 범위는 31일. 휴장일 캘린더는 추가하지 않았다. 범위 내 데이터가 없으면 명확한 오류를 반환한다.
과거 KIS 값처럼 거래일이 없는 국내 가격은 EOD로 표시하지 않고 먼저 KRX 가격을 확보한다.

## 13. OpenDART corp_code

GET `https://opendart.fss.or.kr/api/corpCode.xml?crtfc_key=...`의 ZIP에서 `CORPCODE.xml`을 읽는다.
`stock_code` 6자리 → `corp_code` 8자리 매핑. 비상장 빈 stock_code 제외, 선행 0 유지.
XML 외부 엔티티/DTD를 비활성화하고 압축/해제 크기를 제한한다.
전체 매핑은 `asset_search_cache`의 `OPENDART:CORPORATIONS:v1`에 24시간 저장한다.
ticker를 그대로 corp_code에 보내지 않는다. 우선주를 보통주로 치환하지 않는다.

## 14. 국내 일반주식 배당

KR + STOCK → KRX 공식 주식종류가 보통주인지 확인 → corp_code 매핑 → 배당에 관한 사항 → 정기보고서별 주당 현금배당 → DB upsert.

GET `https://opendart.fss.or.kr/api/alotMatter.json`
요청: `crtfc_key`, `corp_code`, `bsns_year`, `reprt_code`.
2015년 이후 데이터. 기본 최근 완료된 5개 사업연도와 사업보고서 `11011` 사용.
2026년 요청이면 기본 범위 2025~2021년이며 현재 연도는 제외한다.
`external.opendart.years`는 1~10, 보고서 코드는 `11011`/`11012`/`11013`/`11014`로 설정 가능하다.
분기/반기로 변경해도 최근 완료된 사업연도 범위를 사용하며 보고서 금액을 월별 지급 이벤트로 환산하지 않는다.

`se=주당 현금배당금(원)`, `stock_knd=보통주`의 `thstrm`만 사용한다.
총액, 수익률, 과거기간 `frmtrm`/`lwfr`는 주당 배당으로 저장하지 않는다.
`stlm_dt`는 결산기준일로 배당락일/지급일에 매핑하지 않는다.
응답 `dataType=REPORT`, `businessYear`, `reportCode`, `filingNumber`, `shareClass`.
배당락일/기준일/지급일은 null. 연도별 보고 금액은 개별 지급 이벤트 목록이나 예상 월배당 금액이 아니다.
자료 없음 `013`은 정상 빈 결과, 장애/인증/한도 실패는 오류다. 전체 연도 조회가 성공한 뒤 한 트랜잭션으로 저장한다.
공시 정정은 같은 연도/보고서/주식종류의 금액과 공시번호를 갱신한다. 명시적인 0원 정정은 이전 양수 금액을 제거하고 0원 행을 저장하지 않는다.
`-`/빈 값은 미확인으로 취급한다. 우선주 배당은 별도 공식 매핑을 구현하지 않아 현재 미지원이다.

## 15. 국내 ETF 분배금

검토한 KRX 증권상품 공식 OPEN API 목록에는 ETF/ETN/ELW 일별매매정보가 있고 ETF 분배금 이력 API는 확인되지 않았다.
따라서 국내 ETF 분배금은 현재 미지원이며 OpenDART를 호출하지 않는다.
`GET /api/assets/KR/490590/dividends` → HTTP 422 `DIVIDEND_DATA_NOT_SUPPORTED`.
ETF 상세 가격은 정상 지원한다. ETF 강제 갱신은 가격을 갱신하고 배당 미지원 경고를 반환한다.

## 16. DB Schema 변경

기존 `stock_asset`, `dividend`, `asset_search_cache`, `provider_call_budget` 및 별도 기존 `etf` 테이블을 보존했다. 신규 테이블 없음.
stock_asset의 `unique(market,ticker)`와 `price_as_of_date` 컬럼을 유지했다.

dividend: `ex_dividend_date` nullable, `business_year integer`, `report_code varchar(5)`, `filing_number varchar(14)`, `share_class varchar(40)` 추가.
기존 미국 이벤트 unique(asset,source,ex_date) 유지. 공시 unique(asset,source,year,report_code,share_class) 추가.
CHECK 제약으로 미국 이벤트의 배당락일 필수와 OpenDART 공시의 보고서 식별자 필수/이벤트 날짜 금지를 구분한다.
양수 배당금 제약도 유지한다. source enum/check를 `ALPHA_VANTAGE`, `KRX`, `OPEN_DART`로 변경했다.
KRX는 가격/종목 Provider이며 이번 단계에서 KRX 배당 행을 생성하지 않는다.

`ddl-auto=update`만으로 기존 CHECK 제약과 NOT NULL 변경을 맡기지 않았다.
`docs/sql/20261003-krx-opendart.sql`을 기존 로컬 `stock.public`에 트랜잭션으로 적용하고 제약을 검증했다.
적용 전 dividend는 0행이었다. 다른 DB/개발 서버에서는 서버 시작 전에 해당 SQL을 실행해야 한다.
SQL은 재실행 가능하며 과거 KIS/OpenDART 이벤트 행이 발견되면 중단한다. 기존 데이터 삭제나 테이블 초기화는 하지 않는다.

## 17. DBeaver 확인

`stock` 연결 → Schemas → public → Tables → Refresh.

| 테이블 | 확인 항목 |
|---|---|
| stock_asset | market/ticker, asset_type, currency, current_price, price_as_of_date, price_updated_at, dividend_synced_at |
| dividend | dividend_per_share, source, ex_dividend_date, business_year, report_code, filing_number, share_class |
| asset_search_cache | cache_key, payload, synced_at: `KRX:v1:*`, `KR:KRX:v1:*`, `OPENDART:CORPORATIONS:v1` |
| provider_call_budget | 기존 미국 호출 예산 유지 |

실제 API 키를 설정하고 조회해야 실제 종목/배당 행이 생긴다. 자동 테스트는 public에 모의 종목을 넣지 않는다.

## 18. Cache / Fallback

US 가격 30분 유지. KR 가격 6시간, 기본정보/검색/corp 매핑 24시간, 배당 24시간.
전체 KRX 일별 스냅샷을 공유해 다른 종목 조회에서도 반복 API 호출을 줄인다.
강제 갱신은 검색과 가격 캐시를 우회한다. 동일 프로세스 동기화로 동시 갱신 중복을 줄인다.
다중 개발 서버 인스턴스의 외부 호출을 분산 잠금으로 조정하는 기능은 추가하지 않았다.

KRX 실패 시 날짜가 검증된 기존 DB 가격과 원래 시각을 반환하며 `dataFresh=false`, `warningCode`를 표시한다.
검색은 키워드 캐시 → DB 저장 종목 → KRX 전체 스냅샷 순으로 오래된 데이터 반환을 시도한다.
OpenDART 실패 시 기존 배당 이력을 반환하고 새 동기화 시각을 기록하지 않는다.
검색/배당 배열 응답은 기존 계약을 유지하며 `X-Data-Fresh`, `X-Data-Synced-At`, `X-Data-Warning` 헤더로 상태를 전달한다.
기존 데이터가 없으면 오류를 반환한다. 미지원 ETF 배당은 오래된 데이터로 성공 처리하지 않는다.

## 19. Eclipse 실행

1. 프로젝트 선택 → F5 Refresh. Project → Clean → stock 선택.
2. Maven → Update Project. Java 실행 JRE가 17인지 확인.
3. Run → Run Configurations → 기존 StockApplication 실행 항목 → Environment.
4. `DB_PASSWORD`, `KRX_AUTH_KEY`, `OPENDART_API_KEY`, 기존 `ALPHA_VANTAGE_API_KEY`를 추가하고 Apply.
5. 개발 강제 갱신을 테스트할 경우에만 `STOCK_REFRESH_ENABLED=true` 추가.
6. 기존 실행을 중지한 후 StockApplication 실행.

Windows 사용자 환경변수에 넣었다면 Eclipse를 완전히 종료 후 다시 열어야 새 값이 전달된다.
Run Configurations에 넣은 환경변수는 해당 Eclipse 실행에만 전달된다. 코드/프로퍼티에 실제 키를 적지 않는다.
현재 로컬 public DB에는 SQL 적용 완료. 다른 DB를 사용하면 16번 SQL을 먼저 적용한다.

## 20. 테스트 URL

```text
GET http://localhost:8080/api/assets/search?market=KR&keyword=삼성전자
GET http://localhost:8080/api/assets/search?market=KR&keyword=005930
GET http://localhost:8080/api/assets/KR/005930
GET http://localhost:8080/api/assets/KR/035900
GET http://localhost:8080/api/assets/KR/490590
GET http://localhost:8080/api/assets/KR/005930/dividends
GET http://localhost:8080/api/assets/KR/490590/dividends
POST http://localhost:8080/api/assets/KR/005930/refresh
GET http://localhost:8080/api/assets/US/SCHD
GET http://localhost:8080/api/assets/US/SCHD/dividends
```

POST는 브라우저 주소창에서 호출할 수 없으므로 Postman 등으로 요청한다. 갱신 설정이 false이면 해당 endpoint는 등록되지 않는다.
KRX 최신 목록에 존재하지 않는 종목은 404일 수 있다. 인증키와 서비스 승인이 완료되어야 외부 조회가 성공한다.

## 21. Maven / Eclipse 검증

Maven `test` 성공: 총 60개, 실패 0, 오류 0, 건너뜀 0.
KRX 9개, OpenDART 9개, 국내 공통 서비스 10개, PostgreSQL 10개 및 기존 미국/Controller/시작 테스트를 실행했다.
PostgreSQL 테스트는 빈 `codex_mvp_test_20261002` 스키마에서 실행했고 public 데이터를 초기화하지 않았다.
KRX / OpenDART 자동 테스트는 Mockito / MockRestServiceServer 응답만 사용했다. 실제 외부 API를 호출하지 않았다.
Eclipse ECJ 컴파일도 Java 17 기준 오류 0. 기존 Etf 미사용 필드와 ApiException serialVersionUID 경고만 있다.
키를 비운 Spring Boot 컨텍스트 시작과 Provider Bean 선택도 통과했다.
변경된 로컬 public 스키마를 사용하는 임시 서버의 Hibernate validate 및 시작을 확인했다.
캐시가 없는 검색어로 KRX 키 누락 시 HTTP 503 / `EXTERNAL_API_KEY_MISSING` 응답도 확인했고 임시 서버는 종료했다.
실제 사용자 키로 인증된 KRX/OpenDART 실응답 검증은 키 설정 후 로컬에서 확인해야 한다.

일반 로컬 실행: `./mvnw.cmd test`. PostgreSQL 통합 테스트는 별도의 빈 테스트 스키마/DB와 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`를 지정한다.
테스트 설정의 create-drop은 테스트 DB 전용이다. `TEST_DB_URL`을 기본 public 스키마로 설정하면 안 된다.
검증 로그: `target/krx-postgres-test.log`, `target/krx-ecj-check.log`.

## 22. 기존 미국시장 유지

AlphaVantage Client / Mapper / Properties / Market Provider / Dividend Provider 및 호출 예산은 수정하지 않았다.
US 검색/가격/배당/강제 갱신 경로와 30분 가격 캐시를 유지했다.
미국 배당은 기존 `ALPHA_VANTAGE` source와 실제 배당락일로 저장되며 응답은 `dataType=EVENT`이다.
기존 미국 테스트를 유지하고 공통 Provider 인터페이스의 기본 메서드에 맞게 Mock 설정만 조정했다.
회원/계좌/주문/실시간 국내 시세/프론트엔드/운영 배포는 이번 작업에 포함하지 않았다.
