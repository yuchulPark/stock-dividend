# KIS 국내 주식·ETF 조회 구현 안내

> 과거 구현 기록입니다. 2026-10-03부터 KIS 코드와 설정은 제거되었습니다.
> 현재 실행 방법은 [KRX / OpenDART 안내](krx-opendart.md)를 확인하세요.

Java 17, Spring Boot 3.5.16, Maven, PostgreSQL 17을 유지했다. 아래 내용은 2026-10-02에 확인한 공식 명세와 이번 변경에 대한 보고다.
미국 Alpha Vantage와 국내 KIS가 기존 REST API와 공통 Entity/Repository/캐시를 함께 사용한다.
계좌번호가 필요 없는 개인 고객의 시장 데이터 조회만 구현했다.

## 공식 명세 확인과 선택

공식 개발자센터의 공개 문서 데이터를 직접 조회해 URL, TR ID, 필수 헤더와 응답 필드를 확인했고,
한국투자증권 공식 GitHub 예제와 교차 확인했다. 이름 부분 검색을 위한 임의 REST endpoint는 추가하지 않았다.

| 용도 | Method / Endpoint | TR ID | 지원 |
|---|---|---|---|
| 토큰 | POST `/oauth2/tokenP` | 없음 | 실전·모의 |
| 주식 현재가 | GET `/uapi/domestic-stock/v1/quotations/inquire-price` | `FHKST01010100` | 실전·모의 동일 ID |
| ETF 현재가 | GET `/uapi/etfetn/v1/quotations/inquire-price` | `FHPST02400000` | 실전, 모의 미지원 |
| 종목명·코드 검색 | 공식 KOSPI/KOSDAQ 마스터 ZIP | 없음 | 공개 다운로드 |

현재가 query는 `FID_COND_MRKT_DIV_CODE=J`(KRX), `FID_INPUT_ISCD=종목코드`를 사용한다.
필수 헤더는 `Authorization: Bearer ...`, `appkey`, `appsecret`, `tr_id`, `custtype: P`, `Content-Type: application/json; charset=utf-8`이다.
응답은 `rt_cd`, `msg_cd`, `msg1`, `output.stck_prpr`를 확인한다. 주식 응답에 종목코드 `stck_shrn_iscd`가 있으면 요청 코드와 일치 여부도 확인한다.
현재가에 필요한 공식 ETF 전용 API가 있어 이 API를 분리 사용했다. 모의투자 ETF를 지원하는 것처럼 다른 TR ID를 임의로 만들지 않았다.

공식 자료:

- [KIS OAuth 문서](https://apiportal.koreainvestment.com/apiservice-apiservice?/oauth2/tokenP)
- [KIS 주식현재가 문서](https://apiportal.koreainvestment.com/apiservice-apiservice?/uapi/domestic-stock/v1/quotations/inquire-price)
- [KIS ETF 현재가 문서](https://apiportal.koreainvestment.com/apiservice-apiservice?/uapi/etfetn/v1/quotations/inquire-price)
- [공식 주식현재가 예제](https://github.com/koreainvestment/open-trading-api/tree/main/examples_llm/domestic_stock/inquire_price)
- [공식 ETF 현재가 예제](https://github.com/koreainvestment/open-trading-api/tree/main/examples_llm/etfetn/inquire_price)
- [공식 인증 예제](https://github.com/koreainvestment/open-trading-api/blob/main/examples_user/kis_auth.py)
- [KIS 종목정보파일 안내](https://apiportal.koreainvestment.com/apiservice-category)
- [마스터 처리 예제와 분류 헤더](https://github.com/koreainvestment/open-trading-api/tree/main/stocks_info)
- [공식 오류 코드](https://apiportal.koreainvestment.com/faq-error-code)

## 1. 생성한 파일

`src/main/java/com/stock/` 아래:

- `config/KisProperties.java`: 실전/모의, URL, 자격증명, HTTP timeout.
- `external/kis/dto/KisResponse.java`: KIS 토큰·현재가 전용 응답 DTO.
- `external/kis/KisTokenService.java`: 토큰 발급·재사용·동시성 제어.
- `external/kis/KisApiClient.java`: 공식 현재가 요청과 KIS 오류 처리.
- `external/kis/KisErrors.java`: 민감정보를 포함하지 않는 공통 오류 변환.
- `external/kis/KisMapper.java`: CP949 마스터와 BigDecimal 가격 매핑.
- `external/kis/KisMasterFileClient.java`: 공식 ZIP 두 개 다운로드.
- `external/kis/KisMasterCatalog.java`: 마스터 스냅샷의 DB/메모리 캐시, 검색.
- `external/market/KisMarketDataProvider.java`: KR 검색·현재가 Provider.
- `external/market/MarketDataProviderRegistry.java`: 시장별 가격·배당 Provider 선택.

`src/test/java/com/stock/` 아래:

- `external/kis/KisTokenServiceTests.java`
- `external/kis/KisApiClientTests.java`
- `external/kis/KisMapperTests.java`
- `external/kis/KisMasterCatalogTests.java`
- `external/kis/KisMasterFileClientTests.java`
- `external/kis/KisMarketDataProviderTests.java`
- `service/KoreanAssetSyncServiceTests.java`

문서: `docs/kis-market-data.md`.
`target/`의 다운로드 파일, 빌드 로그, 수동 마스터 검증 코드는 임시 검증 산출물이며 Git 제외 대상이다.

## 2. 수정한 파일

- `src/main/java/com/stock/config/RestClientConfig.java`: KIS HTTP Client 두 개와 properties 등록.
- `src/main/java/com/stock/external/market/MarketDataProvider.java`: 강제 검색 갱신과 로컬 검색 fallback 확장점.
- `src/main/java/com/stock/external/alphavantage/AlphaVantageClient.java`: HTTP Bean 선택에 명시적 `@Qualifier` 추가.
- `src/main/java/com/stock/service/AssetSyncService.java`: Registry 사용, KR 코드 검증, 미구현 배당 응답, 강제 metadata 갱신.
- `src/main/java/com/stock/exception/ApiException.java`: 종목 없음 메시지를 시장 공통으로 변경.
- `src/main/resources/application.properties`: KIS 환경변수 설정 추가.
- `src/test/java/com/stock/service/AssetSyncServiceTests.java`: Registry 생성자 반영, US 강제 갱신 회귀 테스트 추가.
- `src/test/java/com/stock/controller/StockAssetControllerTests.java`: KR 검색/배당 오류 계약 테스트 추가.
- `src/test/java/com/stock/repository/PostgresRepositoryTests.java`: KR upsert·통화·시장 분리 저장 검증 추가.
- `src/test/java/com/stock/StockApplicationTests.java`: KIS/Alpha HTTP Bean과 Provider 등록 확인.
- `docs/alpha-vantage-mvp.md`: 이후 단계의 안내 문서 링크 추가.

## 3. 삭제한 파일

없음. 기존 Entity, 테이블, Controller와 미국시장 Provider를 유지했다.

## 4. Maven dependency 변경

없음. `pom.xml`을 수정하지 않았다. JDK ZIP/CP949, Spring RestClient, 기존 Jackson을 사용한다.
getter/setter는 명시적으로 선언해 Eclipse의 Lombok 설치에 의존하지 않는다.

## 5. application.properties 변경

```properties
external.kis.environment=${KIS_ENVIRONMENT:REAL}
external.kis.base-url=${KIS_BASE_URL:}
external.kis.app-key=${KIS_APP_KEY:}
external.kis.app-secret=${KIS_APP_SECRET:}
external.kis.connect-timeout=5s
external.kis.read-timeout=10s
```

`KIS_BASE_URL`을 비우면 environment에 따라 공식 URL을 선택한다.
REAL: `https://openapi.koreainvestment.com:9443`, VIRTUAL: `https://openapivts.koreainvestment.com:29443`.
둘 중 하나의 자격증명만 현재 환경에 설정한다. Token에 REAL/VIRTUAL의 Key를 혼용하지 않는다.
Key가 비어도 서버는 시작되며, KIS 현재가를 실제 요청할 때 설정 누락 오류를 반환한다.
공개 마스터 검색에는 Key가 필요 없다.

## 6. Windows 환경변수 설정

| 변수 | 필수 여부 | 값 |
|---|---|---|
| `DB_PASSWORD` | 서버 구동 필수 | 기존 `stock_user`의 실제 비밀번호 |
| `DB_URL` | 선택 | 기본 `jdbc:postgresql://localhost:5432/stock` |
| `DB_USERNAME` | 선택 | 기본 `stock_user` |
| `KIS_APP_KEY` | KIS 현재가 조회 필수 | 해당 실전/모의 환경 App Key |
| `KIS_APP_SECRET` | KIS 현재가 조회 필수 | 해당 실전/모의 환경 App Secret |
| `KIS_ENVIRONMENT` | 선택 | `REAL` 기본, 또는 `VIRTUAL` |
| `KIS_BASE_URL` | 선택 | 보통 미설정, 공식 URL 자동 선택 |
| `ALPHA_VANTAGE_API_KEY` | 미국 API 조회 시 | 기존 값 유지 |
| `STOCK_REFRESH_ENABLED` | 개발 중 강제 갱신 시 | `true`, 기본 `false` |

Windows 검색에서 **환경 변수 편집 → 환경 변수 → 사용자 변수 → 새로 만들기**를 선택해 이름과 실제 값을 입력한다.
Windows 사용자 변수를 설정한 뒤에는 Eclipse를 완전히 종료하고 다시 실행한다. 기존 Eclipse 프로세스에는 새 값이 자동 반영되지 않는다.

Eclipse의 **Run → Run Configurations → 현재 StockApplication 설정 → Environment → New**에 직접 입력해도 된다.
Key/Secret/비밀번호는 따옴표와 앞뒤 공백 없이 넣는다. 같은 변수의 기존 실행 설정 값이 있으면 실제 사용하는 값으로 수정한다.
개인 실행 설정은 Git에 넣지 않는다(`*.launch` 제외 유지). 실제 Secret을 코드·문서·로그에 기록하지 않는다.

## 7. Token 동작

POST JSON에 `grant_type=client_credentials`, `appkey`, `appsecret`를 보낸다.
응답의 `access_token`, `token_type`, `expires_in`, `access_token_token_expired`를 매핑한다.
공식 정책은 일반 고객 유효기간 24시간, 갱신 발급 주기 6시간이며 6시간 이내에는 기존 토큰이 반환될 수 있다.
고정 24시간을 가정하지 않고, 응답의 초 단위 유효기간과 절대 만료시각 중 빠른 시각을 사용한다.
절대 시각은 Asia/Seoul로 해석하고 실제 만료 5분 전에 갱신 대상으로 판단한다.
발급·검증을 synchronized로 처리해 동시에 여러 요청이 도착해도 토큰 발급을 한 번만 수행한다.
발급 실패는 1분 동안 재시도를 억제한다. Redis, Token DB 테이블, Scheduler는 없다.
서버 재시작 시 메모리 토큰은 사라지며 첫 현재가 요청 때 다시 요청한다.
토큰 만료 응답은 캐시를 무효화하고 공통 오류/fallback을 반환한다. 같은 요청에서 무한 재시도하지 않으며 다음 요청에서 새 토큰을 확보한다.
늦게 도착한 이전 토큰 오류가 새 토큰을 무효화하지 않도록 토큰 값을 비교한다.

## 8. Provider 선택

`MarketDataProviderRegistry`가 Spring의 Provider 목록에서 `supports(market)`로 선택한다.
US → `AlphaVantageMarketDataProvider`, KR → `KisMarketDataProvider`.
배당 Provider는 별도 목록이며 현재 `AlphaVantageDividendProvider`만 US를 지원한다.
같은 시장의 Provider가 중복 등록되면 서버 시작 시 오류를 내어 잘못된 선택을 막는다.

## 9. 국내 종목 검색

`GET /api/assets/search?market=KR&keyword=삼성전자` 또는 `keyword=005930`.
검색어 DB 캐시가 유효하면 그대로 반환한다. 없거나 만료되면 공식 마스터 목록을 사용한다.
마스터는 KOSPI/KOSDAQ ZIP을 함께 검증한 후 전체 목록을 기존 `asset_search_cache`의 `KIS:MASTER:KR:v1` 키에 JSON으로 저장한다.
재시작 시 DB 스냅샷을 읽고, 유효기간이 끝났으면 첫 요청이 새 파일을 내려받는다. 시작 시 다운로드하지 않는다.
메모리 목록은 이름 부분 일치/코드 부분 일치를 검색하고 정확한 코드 일치를 우선 정렬한다. 결과는 최대 100개다.
HTTP 검색 응답은 기존 JSON 배열이며 `X-Data-Fresh`, `X-Data-Synced-At`, `X-Data-Warning` 헤더로 캐시 상태를 표시한다.
종목코드는 문자열로 유지해 앞자리 0과 우선주 문자 코드(예: `00088K`)를 보존한다.

마스터 방식의 장점은 빠른 한글 검색과 전체 공식 분류 사용, 조회 API 호출량 감소다.
단점은 초기 ZIP 다운로드와 신규 상장·명칭 변경의 갱신 지연이다. MVP는 기존 24시간 캐시와 개발용 강제 갱신을 사용한다.
KONEX, NXT 별도 목록, ETN, ELW, 권리증권 등은 이번 지원 범위에서 제외했다.
마스터 조회는 StockAsset 전체를 등록하지 않는다. 상세 조회한 종목만 StockAsset으로 저장한다.

## 10. 국내 현재가 조회

`GET /api/assets/KR/005930` → DB `market=KR,ticker=005930` 조회 → metadata/가격 TTL 확인.
metadata가 없거나 오래됐으면 공식 검색에서 정확한 ticker를 찾아 `StockAsset`에 upsert한다.
가격이 없거나 만료됐으면 마스터의 AssetType에 맞는 KIS 현재가를 호출하고 BigDecimal 가격을 갱신한다.
신규 종목은 INSERT, 기존 종목은 UPDATE한다. `unique(market,ticker)`를 그대로 사용한다.
응답은 Entity가 아니라 기존 `StockAssetResponse`다. 국내 통화는 `KRW`, 미국은 `USD`다.
HTTP 조회 실패 시 기존 metadata만 새로 저장되고 가격이 null인 종목이 남을 수 있으며, 다음 요청이 가격 확보를 다시 시도한다.
현 선택 API에서 가격의 거래일을 확인할 필드가 없어 `priceAsOfDate`는 null로 저장한다.
현재 날짜를 가격 거래일로 임의 기록하지 않는다. `priceUpdatedAt`은 서버가 가격을 수신·저장한 UTC 시각이다.

## 11. 국내 ETF 처리

공식 마스터 헤더의 `scrt_grp_cls_code`를 사용한다. `EF`는 ETF이다.
`ST/RT/MF/SC/IF/FS`는 공식 주권·투자회사·외국주권 분류에 따라 STOCK으로 취급한다.
그 외 분류는 이번 MVP에서 제외한다. 종목명에 ETF가 포함되는지를 보고 추정하지 않는다.
ETF는 실전 전용 현재가 API를 호출한다. VIRTUAL 환경의 ETF 요청은
`EXTERNAL_API_ENVIRONMENT_NOT_SUPPORTED`를 반환하고, 기존 가격이 있으면 stale fallback할 수 있다.
종목명은 공식 최신 마스터의 이름을 사용하며 예제에 적힌 이름을 고정하지 않는다.
2026-10-02에 내려받은 파일에서 `490590`의 종목명은 `RISE 미국AI밸류체인데일리고정커버드콜`이었다.
입력 예시의 이름과 달라도 공식 코드에 연결된 이름을 그대로 사용한다.

## 12. Cache 동작

- 가격: 기존 `stock.cache.price-ttl=30m`.
- 종목 metadata, 검색어 결과, 전체 마스터: 기존 `stock.cache.asset-ttl=24h`.
- 배당: 기존 US 정책 유지, KR 배당 캐시 갱신 없음.

유효한 종목·가격 DB 캐시는 KIS Token 발급/현재가 요청도 수행하지 않는다.
한 종목의 동시 요청은 기존 RefreshCoordinator 잠금을 재사용한다.
전체 마스터는 synchronized로 갱신해 다른 검색어의 동시 요청도 ZIP 다운로드를 중복하지 않는다.
실패한 마스터 갱신은 1분 동안 재시도를 억제하고 마지막 정상 스냅샷을 보존한다.
검색어 결과와 마스터는 각각의 캐시 시각을 사용하므로, 검색 결과의 명칭 변경 반영이 두 캐시의 TTL만큼 지연될 수 있다.

`POST /api/assets/KR/005930/refresh`는 검색어 캐시와 전체 마스터 캐시를 우회해 마스터를 다시 내려받고 KIS 가격을 요청한다.
기존처럼 기본 비활성화 상태이며 개발 중 `STOCK_REFRESH_ENABLED=true`로 활성화한다.
응답 형태는 기존 `RefreshResponse`를 유지하며 `asset`에 새 종목/가격, `dividends=[]`,
`dividendDataFresh=false`, `dividendWarningCode=DIVIDEND_PROVIDER_NOT_IMPLEMENTED`를 반환한다.
운영에서는 관리자에게만 허용하도록 다음 인증 단계에서 접근을 제한해야 한다.

## 13. Fallback 동작

가격이 오래됐을 때 KIS 장애/인증 실패/timeout/제한이 발생하고 DB 가격이 있으면 그 가격을 반환한다.
`dataFresh=false`, `warningCode=공통 오류 코드`를 표시하며 기존 가격의 `priceUpdatedAt`은 바꾸지 않는다.
DB 가격이 없으면 성공 응답처럼 꾸미지 않고 공통 오류를 반환한다.
검색은 검색어 DB 캐시 → 저장된 StockAsset 검색 → 마지막 정상 전체 마스터 검색 순서로 fallback한다.
fallback 결과는 DB 검색 캐시의 새 정상 응답으로 저장하지 않는다.
강제 갱신에서도 같은 장애 fallback 정책을 사용하므로 HTTP 200 응답의 `asset.dataFresh`를 반드시 확인한다.

## 14. 에러 처리

| 상황 | 공통 code |
|---|---|
| App Key 없음 | `EXTERNAL_API_KEY_MISSING` |
| App Secret 없음 | `EXTERNAL_API_SECRET_MISSING` |
| Token 발급 실패 | `EXTERNAL_API_TOKEN_ISSUE_FAILED` |
| Token 만료 | `EXTERNAL_API_TOKEN_EXPIRED` |
| 인증/권한 실패 | `EXTERNAL_API_AUTH_FAILED` |
| HTTP 429 / EGW00201 | `EXTERNAL_API_RATE_LIMIT` |
| Timeout | `EXTERNAL_API_TIMEOUT` |
| 공식 마스터에 지원 종목 없음 | `ASSET_NOT_FOUND` / HTTP 404 |
| KR 코드 형식 오류 | `INVALID_REQUEST` / HTTP 400 |
| 서버 연결 장애 | `EXTERNAL_API_UNAVAILABLE` |
| rt_cd 실패, 알 수 없는 업무 오류 | `EXTERNAL_API_REQUEST_REJECTED` |
| 필수 필드 누락/잘못된 JSON/ZIP/가격 | `EXTERNAL_API_INVALID_RESPONSE` |
| 모의투자 ETF | `EXTERNAL_API_ENVIRONMENT_NOT_SUPPORTED` |
| KR 배당 조회 | `DIVIDEND_PROVIDER_NOT_IMPLEMENTED` / HTTP 501 |

`GlobalExceptionHandler`의 `{code,message}`를 그대로 사용한다.
원본 KIS 응답이나 오류 문자열, Token, Key, Secret, 요청 헤더를 메시지/로그에 노출하지 않는다.
알 수 없는 업무 오류를 종목 없음으로 단정하지 않는다. Token 만료/공식 인증 오류만 토큰을 무효화한다.

## 15. DBeaver 확인

테이블과 컬럼 추가/삭제 없음. `StockAsset`, `Dividend`, `AssetSearchCache`, `ProviderCallBudget`의 Entity 정의도 변경하지 않았다.
`ProviderCallBudget`은 기존 Alpha Vantage의 일일 예산에만 사용하며 KIS에 그 제한을 적용하지 않는다.
아래 SQL은 조회만 수행한다.

```sql
SELECT id, market, ticker, name, asset_type, currency,
       current_price, price_updated_at, price_as_of_date, metadata_updated_at
FROM stock_asset
WHERE market IN ('KR', 'US')
ORDER BY market, ticker;

SELECT cache_key, synced_at, length(payload) AS payload_length
FROM asset_search_cache
WHERE cache_key LIKE 'KR:%' OR cache_key = 'KIS:MASTER:KR:v1'
ORDER BY cache_key;

SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid IN ('stock_asset'::regclass, 'dividend'::regclass);
```

삼성전자 `KR/005930`는 STOCK/KRW, ETF는 ETF/KRW인지 확인한다.
동일 상세 조회/refresh를 반복해도 같은 market+ticker의 행은 하나여야 한다.
US/SCHD 기존 데이터와 배당 이력은 유지돼야 한다.
`unique(market,ticker)`, `unique(stock_asset_id,source,ex_dividend_date)`, Dividend FK를 유지한다.
KR 조회/refresh 때문에 Dividend 행 또는 `dividend_synced_at`이 갱신되지 않는다.

## 16. Eclipse 실행

1. `stock` 프로젝트 F5 → Maven → Update Project → Project → Clean.
2. Run Configurations에서 `com.stock.StockApplication`과 Java 17 JRE 선택.
3. Environment에 DB_PASSWORD와 KIS Key/Secret 설정. 미국도 확인하려면 Alpha Key 유지.
4. ETF는 KIS_ENVIRONMENT=REAL. 별도 KIS_BASE_URL을 설정했다면 REAL 주소와 일치하는지 확인.
5. Apply → 기존 실행 종료 → Run. 기본 서버 포트 8080.

DB 비밀번호 인증 실패가 발생하면 현재 Eclipse 실행 설정에 실제 DB_PASSWORD가 전달되는지 확인한다.
환경변수 값 자체를 채팅/문서에 붙여넣을 필요는 없다.

## 17. 실제 API 테스트 URL

브라우저/REST 클라이언트에서:

```text
GET http://localhost:8080/api/assets/search?market=KR&keyword=삼성전자
GET http://localhost:8080/api/assets/search?market=KR&keyword=005930
GET http://localhost:8080/api/assets/search?market=KR&keyword=490590
GET http://localhost:8080/api/assets/KR/005930
GET http://localhost:8080/api/assets/KR/490590
GET http://localhost:8080/api/assets/KR/005930/dividends
GET http://localhost:8080/api/assets/US/SCHD
GET http://localhost:8080/api/assets/US/SCHD/dividends
```

KR 배당 URL은 현재 HTTP 501이 정상 동작이다. 같은 현재가 상세 URL을 즉시 재호출하면 캐시를 사용한다.
강제 갱신은 개발 설정 활성화 후 POST로 호출한다.

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/assets/KR/005930/refresh'
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/assets/US/SCHD/refresh'
```

US refresh는 metadata/가격/배당을 갱신한다. 이번 보완으로 강제 metadata 갱신도 검색어 캐시를 우회한다.
테스트 수치(85000 등)는 Mock 예시이며 실시간 가격을 보장하는 값이 아니다.

## 18. Maven test와 검증

일반 실행: `mvn test` 또는 `.\mvnw.cmd test`.
외부 HTTP는 MockRestServiceServer와 Mockito로 대체하며, 자동 테스트에서 실제 KIS·Alpha API나 마스터 다운로드를 호출하지 않는다.
실제 KIS 자격증명으로 인증·시세 요청을 수행하는 검증은 아직 하지 않았다.
실제 공개 마스터 다운로드를 별도 수동 검증해 3,932개 지원 종목과 코드 중복 없음, STOCK/ETF 분류를 확인했다.

PostgreSQL 통합 테스트는 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`가 별도 설정된 경우에만 실행한다.
TEST_DB_URL은 반드시 비어 있는 테스트 전용 DB/스키마를 가리켜야 한다. test profile은 create-drop이다.
개발 DB의 public 스키마를 테스트 URL로 지정하지 않는다.
이번 검증에는 기존 `stock` DB 안의 `codex_mvp_test_20261002` 전용 스키마를 사용했다.
최종 Maven 결과: 테스트 56개, 실패 0, 오류 0, 건너뜀 0, BUILD SUCCESS.
Java 17 전체 컴파일과 Spring ApplicationContext 시작도 통과했다.
DB 환경변수가 없는 일반 테스트에서는 PostgreSQL/컨텍스트 테스트 7개를 건너뛰고 나머지 49개를 실행한다.
Eclipse 설치본의 ECJ로도 전체 main 소스를 Java 17로 컴파일해 오류 0개를 확인했다.

## 19. 미국시장 기능 유지

Alpha API Endpoint/DTO/가격·배당 매핑/호출 예산/오류 처리를 유지했다.
HTTP Bean이 늘어도 기존 Alpha Client가 정확한 Bean을 사용하도록 Qualifier를 명시했다.
기존 US 단위 테스트와 PostgreSQL 테스트를 함께 실행했다.
US REST 경로와 DTO 응답 형태를 유지했다. 기존 Etf Entity와 간단한 StockController도 유지했다.

## 20. 다음 OpenDART 단계

공식 OpenDART 배당 데이터 제공 범위와 정정 공시 처리, corp_code ↔ KR ticker 매핑을 확인한다.
국내 주식의 현금배당과 ETF 분배금은 제공 출처/필드가 다를 수 있으므로 ETF 데이터의 공식 제공 여부를 별도 확인한다.
`DividendDataProvider`의 KR 구현과 기존 Registry에 등록한다.
배당 기준일, 배당락일, 지급일을 확인 가능한 범위에서 분리하고 알 수 없는 날짜를 임의로 생성하지 않는다.
시장별 통화와 BigDecimal 금액, 기존 Dividend 유니크/FK, 캐시와 fallback을 재사용한다.
OpenDART만으로 ETF 분배금을 제공할 수 없다면 공식 대체 출처를 검토하고 한계를 먼저 설명한다.
이번 단계에는 국내 배당, 배당 계산, 프론트엔드, 매매, 주문, 잔고, 계좌 기능을 추가하지 않았다.
