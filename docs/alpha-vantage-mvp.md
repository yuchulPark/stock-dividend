# Alpha Vantage 1차 구현 안내

이 문서는 Alpha Vantage 1차 구현 당시의 기록이다. 현재 국내시장 연동과 실행 방법은
[KRX / OpenDART 안내](krx-opendart.md)를 함께 확인한다.

Java 17 / Spring Boot 3.5.16 / Maven / PostgreSQL 유지. 추가 Maven dependency 없음.
기존 StockApplication, StockController, Etf는 변경하지 않는다.
KIS, OpenDART, 국내 외부 연동, 계산 API, 프론트엔드, Scheduler는 구현하지 않았다.

최종 검증(2026-10-02): Java 17 컴파일 및 Maven test 성공. 테스트 24개, 실패 0, 오류 0, 건너뜀 0.
PostgreSQL 17.11의 격리된 테스트 스키마에서 Repository/유니크/배당 정정/호출 예산을 검증했다.
최종 설정으로 API Key 없이 애플리케이션 컨텍스트가 시작됨을 확인했다.
사용자 API Key로 SCHD 등 실제 서비스 종목을 조회하는 검증은 아직 수행하지 않았다.

## 실행과 환경변수

Eclipse에서 Maven > Update Project 후 Project > Clean을 수행한다.
Run Configurations > Java Application의 JRE가 Java 17인지 확인한다.
메인 클래스는 `com.stock.StockApplication`이다.
신규 클래스는 getter/setter와 JPA 기본 생성자를 직접 선언하므로 Eclipse의 Lombok 설치 없이 컴파일된다.
기존 pom.xml의 Lombok dependency는 유지한다.

Run Configurations > Environment:

| 변수 | 필수 여부 | 설명 |
|---|---|---|
| DB_PASSWORD | 필수 | 기존 stock_user의 실제 DB 비밀번호 |
| DB_URL | 선택 | 기본 jdbc:postgresql://localhost:5432/stock |
| DB_USERNAME | 선택 | 기본 stock_user |
| ALPHA_VANTAGE_API_KEY | 실데이터 조회 시 필수 | 없으면 애플리케이션은 시작되며 외부 호출 시 명확한 503 오류 |
| STOCK_REFRESH_ENABLED | 개발 갱신 테스트 시 true | 기본 false, 운영 관리자 제한 필요 |

키를 프로젝트 코드·properties·Git에 저장하지 않는다. `.env`는 자동으로 로드되지 않는다.
실행 설정에 환경변수를 넣어야 한다. Eclipse의 개인 실행 설정도 Git에 추가하지 않는다.
기존 properties의 DB 비밀번호는 환경변수로 대체했다. 기존 Git 이력은 조사/정리하지 않았다.

## 추가 설정

| application.properties | 기본값 |
|---|---|
| external.alpha-vantage.api-key | ${ALPHA_VANTAGE_API_KEY:} |
| external.alpha-vantage.connect-timeout | 5s |
| external.alpha-vantage.read-timeout | 10s |
| external.alpha-vantage.daily-limit | 25 |
| external.alpha-vantage.failure-cooldown | 5m |
| stock.cache.price-ttl | 30m |
| stock.cache.asset-ttl | 24h |
| stock.cache.dividend-ttl | 24h |
| stock.development.refresh-enabled | ${STOCK_REFRESH_ENABLED:false} |
| spring.jpa.open-in-view | false |

`ddl-auto=update`는 기존 개발 설정을 유지한다. 운영 DB 변경 이력 관리 도구는 다음 단계에서 검토한다.
LocalDateTime은 UTC로 저장한다. 응답 날짜/시간에 zone suffix가 없는 필드도 UTC 기준이다.
priceUpdatedAt은 수집 시각이며 실제 가격 기준일은 priceAsOfDate로 구분한다.
dataFresh=true는 캐시 TTL 내 정상 수집이라는 뜻이며 실시간 시세라는 뜻이 아니다.

## 새 테이블과 컬럼

### stock_asset

| 컬럼 | 타입 / 제약 |
|---|---|
| id | bigint PK, identity |
| ticker | varchar(32), not null |
| name | varchar(200), not null |
| market | varchar(8), KR/US, not null |
| asset_type | varchar(8), STOCK/ETF, not null |
| currency | varchar(8), KRW/USD, not null |
| current_price | numeric(24,8), nullable until fetched |
| price_updated_at | timestamp, nullable |
| price_as_of_date | date, nullable |
| metadata_updated_at | timestamp, nullable |
| dividend_synced_at | timestamp, nullable |
| active | boolean, not null, 신규 확인 종목 true |
| created_at, updated_at | timestamp, not null, lifecycle callbacks |

`uk_stock_asset_market_ticker`: UNIQUE(market, ticker).
검색은 종목 기본정보를 검색 캐시에 저장한다. stock_asset은 상세/배당 조회 대상이 된 종목만 생성한다.
active는 자동 상장폐지 추적 기능이 아니다. 장애 발생 시 false로 바꾸지 않는다.

### dividend

| 컬럼 | 타입 / 제약 |
|---|---|
| id | bigint PK, identity |
| stock_asset_id | bigint not null, FK stock_asset(id), LAZY |
| dividend_per_share | numeric(24,8), not null, CHECK > 0 |
| ex_dividend_date | date, not null |
| record_date, payment_date | date, nullable |
| currency | varchar(8), not null |
| source | varchar(24), ALPHA_VANTAGE/KIS/OPEN_DART, not null |
| external_updated_at | timestamp, nullable; Alpha Vantage가 수정시각을 제공하지 않아 null 유지 |
| created_at | timestamp, not null |

`fk_dividend_stock_asset`: FK stock_asset_id → stock_asset.id.
`uk_dividend_asset_source_ex_date`: UNIQUE(stock_asset_id, source, ex_dividend_date).
같은 키의 새 수집 금액·날짜는 UPDATE한다. 금액 정정으로 새 행을 만들지 않는다.
응답 안에 같은 배당락일의 서로 다른 배당이 있으면 구분할 근거가 부족하므로 전체 동기화를 거절한다.
같은 날짜/같은 데이터의 중복 응답은 하나로 정리한다. 0 금액은 양수 배당 도메인에서 제외한다.
누락/None 날짜는 null, 잘못된 필수 필드·음수·과도한 소수 자릿수는 오류다. 임의 반올림은 하지 않는다.
새 응답에 없는 기존 행은 삭제하지 않는다. 취소 배당/분할 전후 보정은 다음 단계의 과제다.

### 보조 테이블

| 테이블 | 컬럼 / 목적 |
|---|---|
| asset_search_cache | cache_key PK, payload text, synced_at: 검색어별 정규화 결과 및 빈 결과 캐시 |
| provider_call_budget | budget_date PK, used_calls: Alpha Vantage 호출 시도 수, UTC 날짜 기준 |

무료 한도는 키 전체에 적용되며 다른 앱의 사용량은 이 서비스가 알 수 없다.
로컬 일일 예산은 UTC 일자 기준 예방장치이며 제공업체의 실제 초기화 시각을 보장하지 않는다.
제공업체가 제한 응답을 보내면 그 응답을 최종 기준으로 차단/Fallback한다.
호출 실패도 예산에 포함한다. 여러 JVM에서의 예산/잠금 동시성은 지원하지 않는다.

## API와 호출 흐름

```text
Controller → AssetSyncService → Repository(DB 확인)
                              → Provider → Client → 공식 API
                              → Provider Mapper → 내부 모델
                              → AssetPersistenceService(짧은 쓰기 트랜잭션)
                              → Response DTO
```

외부 HTTP 통신 동안 DB 쓰기 트랜잭션을 열어 두지 않는다.
RestClient + Java 17 JDK HTTP Client로 connect/read timeout을 적용한다.
외부 DTO는 external.alphavantage.dto에 있고 서비스는 정규화 모델만 사용한다.
실제 호출 경로는 `https://www.alphavantage.co/query`이고 function은 SYMBOL_SEARCH/GLOBAL_QUOTE/DIVIDENDS만 사용한다.
미국·USD·Equity/ETF 결과만 채택하며 모르는 자산 유형은 추정하지 않는다.

| 요청 | 결과 |
|---|---|
| GET /api/assets/search?market=US&keyword=SCHD | 종목 검색 DTO 배열 |
| GET /api/assets/US/SCHD | 종목 상세, 가격 기준일, dataFresh, warningCode |
| GET /api/assets/US/SCHD/dividends | 배당 이벤트 DTO 배열, 배당락일 최신순 |
| POST /api/assets/US/SCHD/refresh | 개발 설정 활성 시 가격/배당 강제 갱신 및 각 결과의 상태 |

시장 KR 요청은 422 MARKET_NOT_SUPPORTED. 알 수 없는 Enum/검색어/티커 입력은 400.
존재하지 않는 확인된 종목은 404. 원문 외부 API 오류·키·인증 URL을 노출하지 않는다.
키 없음/잘못된 키/한도/Timeout/서버 장애/잘못된 JSON을 자체 code/message로 구분한다.
공급자의 모호한 `Invalid API call`을 무조건 잘못된 키나 종목 없음으로 단정하지 않는다.

상세 조회의 최초 요청은 이름/유형을 위해 검색 후 GLOBAL_QUOTE로 가격을 받는다.
가격이 없으면 null을 임의 가격으로 대체하지 않고 오류를 반환한다.
배당 조회는 가격 조회를 요구하지 않으며 종목 기본정보와 DIVIDENDS만 조회한다.

## 캐시와 Fallback

동일 종목은 단일 서버의 잠금 안에서 DB를 다시 읽어 정상 GET의 동시 갱신을 중복 실행하지 않는다.
검색 잠금은 별도 그룹을 사용한다. 제한된 수의 잠금으로 메모리 사용량을 제한한다.
강제 refresh는 TTL을 무시하지만 호출 예산과 장애 cooldown은 무시하지 않는다.
강제 refresh를 반복 요청하면 각각 갱신 시도하므로 개발 단계에서만 사용한다.

배당의 마지막 성공 동기화 시각은 dividend_synced_at에 별도로 기록한다.
성공한 빈 배당 이력도 캐시된다. 실패 시 성공 시각을 변경하지 않는다.
외부 오류 시 Client가 5분 동안 전체 Provider 호출을 억제한다. 이는 단일 서버 상태이며 재시작 시 초기화된다.

기존 가격/배당/검색 캐시가 있으면 실패 시 반환하고 dataFresh=false로 표시한다.
배열 형식인 검색/배당 응답은 X-Data-Fresh, X-Data-Synced-At, X-Data-Warning 헤더를 사용한다.
빈 배당 배열의 Fallback도 헤더로 구별된다. Fallback 데이터의 최대 경과 시간 제한은 현재 없다.
검색어 캐시가 없을 때 기존 stock_asset의 부분 검색 결과로 Fallback할 수 있으나 전체 검색 결과를 보장하지 않는다.

## 실제 요청 테스트

Eclipse 실행 후 PowerShell:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/assets/search?market=US&keyword=SCHD'
Invoke-RestMethod 'http://localhost:8080/api/assets/US/SCHD'
Invoke-WebRequest 'http://localhost:8080/api/assets/US/SCHD/dividends' -UseBasicParsing
Invoke-RestMethod -Method Post 'http://localhost:8080/api/assets/US/SCHD/refresh'
```

refresh는 STOCK_REFRESH_ENABLED=true일 때만 존재하며 기본 설정에서는 404다.
조회 반복 시 price_updated_at/dividend_synced_at과 provider_call_budget.used_calls를 비교한다.
캐시를 오래된 시각으로 변경한 뒤 앱의 API Key를 제거하고 재시작하면 Fallback을 확인할 수 있다.
이는 개발 DB에서만 수행하고 기존 실제 값을 보존한다.

## DBeaver 확인

stock DB/public 스키마에서 새 네 테이블과 기존 etf를 확인한다.
앱을 정상 실행하면 ddl-auto=update로 신규 테이블이 생성된다.
stock_asset의 시장/티커 유니크, dividend의 FK·유니크·양수 CHECK를 확인한다.

```sql
SELECT market, ticker, current_price, price_updated_at, price_as_of_date,
       metadata_updated_at, dividend_synced_at FROM stock_asset;
SELECT stock_asset_id, ex_dividend_date, dividend_per_share, payment_date
FROM dividend ORDER BY stock_asset_id, ex_dividend_date DESC;
SELECT stock_asset_id, source, ex_dividend_date, count(*) FROM dividend
GROUP BY stock_asset_id, source, ex_dividend_date HAVING count(*) > 1;
SELECT * FROM provider_call_budget ORDER BY budget_date DESC;
```

## 테스트

기존 Test dependency만 사용하며 외부 API 호출은 MockRestServiceServer/Mockito로 대체한다.
일반 `mvnw.cmd test`는 DB 환경변수 없이 Unit Test를 실행하고 DB 테스트는 건너뛴다.
DB 테스트는 TEST_DB_URL이 있을 때만 실행한다. create-drop 설정이므로 반드시 별도 테스트 DB/스키마를 지정한다.

```powershell
$env:TEST_DB_URL='jdbc:postgresql://localhost:5432/stock?currentSchema=stock_mvp_test'
$env:TEST_DB_USERNAME='stock_user'
# TEST_DB_PASSWORD는 실제 비밀번호를 환경변수로 설정한다.
.\mvnw.cmd test
```

테스트 스키마는 DBeaver에서 미리 생성하고 stock_user의 사용/생성 권한을 확인한다.
작업 중 실제 PostgreSQL 17.11의 codex_mvp_test_20261002 스키마를 사용했다. 테스트 후 이 스키마는 비어 있다.
public.etf는 테스트에서 삭제/수정하지 않았다. 컨텍스트 테스트도 테스트 환경에서만 실행한다.

## 기존 Etf 처리

Etf.java와 public.etf는 유지했다. 신규 업무 코드는 사용하지 않으므로 데이터/참조가 없음을 확인한 뒤 삭제할 수 있다.
필요 데이터는 먼저 stock_asset에 적절한 시장·유형·통화와 함께 이전해야 한다.
사용자가 삭제를 결정하면 먼저 Etf.java를 제거하고 애플리케이션을 중지한 뒤 DBeaver에서
`DROP TABLE public.etf;`를 실행한다. 파일을 남겨두면 ddl-auto=update가 테이블을 다시 만들 수 있다.
참조 제약이 발견되면 원인을 먼저 확인하고 CASCADE로 강제 제거하지 않는다. 이번 작업에서는 삭제하지 않았다.

## 공식 명세와 이용 제한

2026-10-02에 공식 서버의 demo 키로 IBM 배당/시세, BA 검색의 실제 JSON 필드를 확인했다.
공식 배당 응답의 amount/ex_dividend_date/record_date/payment_date와 None 날짜 처리에 맞춰 Mapper를 구현했다.
DTO/캐시/동기화 테스트의 SCHD 데이터는 mock이다. 사용자의 실제 키로 SCHD와 다른 ETF 지원 범위는 별도 확인해야 한다.
키 없는 환경에서 실데이터 종목별 커버리지를 보장하지 않는다.

- [공식 API 문서](https://www.alphavantage.co/documentation/)
- [공식 배당 데모](https://www.alphavantage.co/query?function=DIVIDENDS&symbol=IBM&apikey=demo)
- [공식 시세 데모](https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=IBM&apikey=demo)
- [공식 검색 데모](https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords=BA&apikey=demo)
- [무료 기본 25회/일](https://www.alphavantage.co/premium/)
- [이용약관: 기본 개인·비상업 이용, 상업 이용은 별도 서면 합의](https://www.alphavantage.co/terms_of_service/)

무료 GLOBAL_QUOTE는 기본적으로 거래일 종료 후 갱신된다. 상업 서비스 공개 전 데이터 이용·재배포 계약이 필요하다.

## 다음 단계

사용자 키로 미국 ETF 배당 지원 범위 검증, KIS 토큰·시세 Provider, OpenDART 별도 공시 도메인을 순차 구현한다.
국내 ETF 분배금 공식 무료 API는 확인 전까지 TODO다. 계산/연간/캘린더는 데이터의 의미·완전성을 검증한 뒤 구현한다.
다중 서버 잠금·예산, 공식 수정/취소 이벤트 식별, 운영 DB migration, 관리자 갱신 제한은 후속 과제다.

## 파일 변경 목록

pom.xml 및 기존 main Java 3개는 유지. 수정: application.properties, .gitignore, StockApplicationTests.java.
신규 테스트 설정 application-test.properties와 이 안내 문서를 추가했다.
신규 Java 파일 목록은 아래에 기록한다.

- `src/main/java/com/stock/config/AlphaVantageProperties.java`
- `src/main/java/com/stock/config/CacheProperties.java`
- `src/main/java/com/stock/config/RestClientConfig.java`
- `src/main/java/com/stock/controller/AssetRefreshController.java`
- `src/main/java/com/stock/controller/StockAssetController.java`
- `src/main/java/com/stock/dto/response/AssetSearchResponse.java`
- `src/main/java/com/stock/dto/response/DividendResponse.java`
- `src/main/java/com/stock/dto/response/ErrorResponse.java`
- `src/main/java/com/stock/dto/response/RefreshResponse.java`
- `src/main/java/com/stock/dto/response/StockAssetResponse.java`
- `src/main/java/com/stock/entity/AssetSearchCache.java`
- `src/main/java/com/stock/entity/AssetType.java`
- `src/main/java/com/stock/entity/Currency.java`
- `src/main/java/com/stock/entity/DataSourceType.java`
- `src/main/java/com/stock/entity/Dividend.java`
- `src/main/java/com/stock/entity/Market.java`
- `src/main/java/com/stock/entity/ProviderCallBudget.java`
- `src/main/java/com/stock/entity/StockAsset.java`
- `src/main/java/com/stock/exception/ApiException.java`
- `src/main/java/com/stock/exception/GlobalExceptionHandler.java`
- `src/main/java/com/stock/external/alphavantage/AlphaVantageClient.java`
- `src/main/java/com/stock/external/alphavantage/AlphaVantageMapper.java`
- `src/main/java/com/stock/external/alphavantage/dto/AlphaVantageResponse.java`
- `src/main/java/com/stock/external/dividend/AlphaVantageDividendProvider.java`
- `src/main/java/com/stock/external/dividend/DividendDataProvider.java`
- `src/main/java/com/stock/external/market/AlphaVantageMarketDataProvider.java`
- `src/main/java/com/stock/external/market/MarketDataProvider.java`
- `src/main/java/com/stock/external/model/AssetData.java`
- `src/main/java/com/stock/external/model/DividendData.java`
- `src/main/java/com/stock/external/model/QuoteData.java`
- `src/main/java/com/stock/repository/AssetSearchCacheRepository.java`
- `src/main/java/com/stock/repository/DividendRepository.java`
- `src/main/java/com/stock/repository/ProviderCallBudgetRepository.java`
- `src/main/java/com/stock/repository/StockAssetRepository.java`
- `src/main/java/com/stock/service/AssetPersistenceService.java`
- `src/main/java/com/stock/service/AssetSyncService.java`
- `src/main/java/com/stock/service/CacheResult.java`
- `src/main/java/com/stock/service/ProviderCallBudgetService.java`
- `src/main/java/com/stock/service/RefreshCoordinator.java`
- `src/test/java/com/stock/controller/StockAssetControllerTests.java`
- `src/test/java/com/stock/external/AlphaVantageClientTests.java`
- `src/test/java/com/stock/external/AlphaVantageMapperTests.java`
- `src/test/java/com/stock/repository/PostgresRepositoryTests.java`
- `src/test/java/com/stock/service/AssetSyncServiceTests.java`
