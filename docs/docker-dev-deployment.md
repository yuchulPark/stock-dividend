# Ubuntu 개발서버 Docker Compose 배포

Ubuntu 24.04.5 LTS의 `/srv/stock`에서 사용하는 개발서버 배포 구성이다.
Docker Engine, Docker Compose Plugin, Git은 설치되어 있다고 가정한다.
이번 작업은 배포 파일 작성과 Windows 검증까지 수행했다. Ubuntu에 접속하거나 실제 배포하지 않았다.

## 최초 배포

먼저 Windows에서 변경 파일을 검토하고 사용자가 직접 develop 브랜치에 Commit / Push한다.
아래 Git 명령은 사용자가 Ubuntu에서 실행할 안내이며, Codex가 실행한 명령이 아니다.
`/srv/stock`이 비어 있고 devops 사용자에게 쓰기 권한이 있을 때 실행한다.
이미 clone한 저장소가 있으면 다시 clone하지 않고 재배포 절차를 사용한다.

```bash
cd /srv/stock
git clone -b develop https://github.com/yuchulPark/stock-dividend.git .
cp .env.example .env
nano .env
chmod 600 .env
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

`.env`의 `POSTGRES_PASSWORD`는 비어 있으므로 서버용 비밀번호를 반드시 입력한다.
외부 데이터 조회에는 해당 제공자의 API Key와 이용 승인이 필요하다.
API Key를 비워 두어도 서버는 시작하지만, 인증이 필요한 조회는 실패할 수 있다.
비밀번호에 `$`, `#` 등의 문자가 있으면 Compose dotenv 규칙에 따라 작은따옴표로 감싸 입력한다.
실제 `.env`는 Git에 포함하지 않는다. 이번 작업에서는 실제 `.env`를 만들지 않았다.

빌드와 실행을 나누려면 다음 명령도 사용할 수 있다.

```bash
docker compose build
docker compose up -d
```

`docker compose config`는 해석된 설정 전체를 표시하고 비밀번호/API Key도 출력한다.
출력 내용 없이 설정만 검사하려면 위의 `docker compose config --quiet`를 사용한다.

## 배포 후 확인

브라우저 주소는 `http://<실제 Ubuntu 서버 IP>/`이다. 서버 IP가 예시와 같다면:

```text
http://192.168.0.19/
http://192.168.0.19/search
http://192.168.0.19/asset/US/SCHD
http://192.168.0.19/calculator
http://192.168.0.19/api/hello
```

`/asset/US/SCHD`에 직접 접속하거나 새로고침해도 SPA가 표시되어야 한다.
종목 API 결과는 외부 API 인증과 데이터 제공 상황에 따라 달라진다.
서버에서 다음을 확인한다.

```bash
cd /srv/stock
docker compose ps
docker compose exec stock-frontend nginx -t
curl -fsS http://localhost/ > /dev/null
curl -fsS http://localhost/asset/US/SCHD > /dev/null
curl -fsS http://localhost/api/hello
docker network inspect stock-network
docker volume inspect stock-db-data
```

`/api/hello`의 예상 응답은 `{"message":"hello"}`이다.
`ps`에서 세 서비스가 실행 중이고 healthy인지 확인한다.
외부에서 연결되지 않으면 실제 VM IP, VirtualBox 네트워크, 기존 방화벽 설정을 확인한다.
이 작업에서는 VM 네트워크나 방화벽, 공유기 포트 포워딩을 변경하지 않았다.

## 로그, 종료, 다시 시작

```bash
cd /srv/stock
docker compose logs --tail=100 stock-db
docker compose logs --tail=100 stock-backend
docker compose logs --tail=100 stock-frontend
docker compose logs -f stock-backend
```

실시간 로그 확인은 `Ctrl+C`로 끝낸다. 컨테이너는 계속 실행된다.

```bash
docker compose down
docker compose up -d
```

`docker compose down`은 컨테이너와 Compose 네트워크를 제거하지만 DB Volume은 보존한다.
**`docker compose down -v`는 `stock-db-data`도 삭제하여 PostgreSQL 데이터가 사라진다.**
일반 재배포나 재시작에는 `-v`를 사용하지 않는다.
별도 volume 삭제 명령으로도 데이터가 삭제될 수 있으므로 Volume을 유지한다.

## 코드 갱신 후 수동 재배포

Windows에서 사용자가 직접 Commit / Push한 뒤 Ubuntu에서 실행한다.

```bash
cd /srv/stock
git pull origin develop
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 stock-backend
curl -fsS http://localhost/api/hello
```

DB Volume과 서버 `.env`는 그대로 사용한다. 이 구성은 단일 인스턴스 개발서버이며
컨테이너 교체 중 짧은 중단이 있을 수 있다.
`.env`의 API Key 변경은 `docker compose up -d`로 반영한다.
이미 초기화된 DB의 비밀번호는 `.env` 변경만으로 바뀌지 않는다.
기존 DB 비밀번호를 바꿀 때는 DB 사용자 비밀번호와 서버 설정을 함께 변경해야 한다.

## 구성과 요청 흐름

```text
Browser → Ubuntu :80 → stock-frontend (Nginx)
                         ├─ /, /search, /asset/... → React dist/index.html
                         └─ /api/... → stock-backend:8080/api/...
                                          └─ stock-db:5432
                                               └─ stock-db-data
```

세 서비스는 `stock-network`라는 전용 bridge 네트워크를 공유한다.
서비스 주소는 Docker DNS 이름이며 컨테이너 IP를 하드코딩하지 않는다.
외부 API 통신을 위해 bridge 네트워크의 일반 외부 통신을 유지한다.
외부에 publish하는 포트는 Nginx의 `80:80`뿐이다.
백엔드 8080과 PostgreSQL 5432는 host에 publish하지 않는다.
Dockerfile의 `EXPOSE 8080`은 이미지 메타데이터이며 host 포트 공개 설정이 아니다.

Nginx는 `/api`와 `/api/` 요청을 백엔드로 전달한다.
`proxy_pass http://$backend$request_uri`로 원래 URI와 query string을 보존한다.
예를 들어 `/api/assets/US/SCHD`는 동일한 경로로 Spring Boot에 전달한다.
Host, X-Real-IP, X-Forwarded-For, X-Forwarded-Proto 헤더도 전달한다.
Docker 내장 DNS `127.0.0.11`을 사용하여 백엔드 교체 후 변경된 주소를 다시 조회한다.
DNS 캐시 갱신까지 잠시 연결 오류가 발생할 수 있다.
API 오류 응답은 React index.html로 대체하지 않는다.
React 경로는 `try_files $uri $uri/ /index.html`로 처리한다.

## 이미지 빌드와 환경변수

백엔드 Dockerfile은 `eclipse-temurin:17-jdk-alpine`에서 프로젝트 Maven Wrapper로 빌드한다.
현재 Wrapper는 Maven 3.9.16이며 Java 17 / Spring Boot 3.5.16을 유지한다.
pom.xml과 Wrapper를 먼저 복사하고 `/root/.m2` cache mount로 다운로드를 재사용한다.
빌드 명령은 `clean package -DskipTests`이며 Docker 빌드는 DB 테스트를 실행하지 않는다.
테스트는 로컬에서 별도로 실행해 검증했다.
실행 단계는 `eclipse-temurin:17-jre-alpine`에
`target/stock-0.0.1-SNAPSHOT.jar`만 `app.jar`로 복사하여 일반 사용자 stock으로 실행한다.
소스코드와 Maven은 실행 이미지에 복사하지 않는다.

프론트엔드 Dockerfile은 `node:24-alpine`에서 lock 파일 기반 `npm ci`와 `npm run build`를 실행한다.
`/root/.npm` cache mount를 사용한다. 실행 이미지는 `nginx:alpine`이며
빌드 결과 `dist/`와 nginx.conf를 사용한다. 실행 단계에 Node나 node_modules를 복사하지 않는다.
`VITE_API_BASE_URL=/api`, `VITE_ENABLE_REFRESH=false`를 빌드 시 지정한다.
API Key, DB 비밀번호와 Docker 서비스 이름은 React 빌드에 전달하지 않는다.
로컬 `.env*` 파일도 build context에서 제외한다.

루트 `.env.example`의 항목:

| 환경변수 | 용도 |
|---|---|
| POSTGRES_DB | DB 이름, 기본 예시 stock |
| POSTGRES_USER | DB 사용자, 기본 예시 stock_user |
| POSTGRES_PASSWORD | 서버 DB 비밀번호, 필수 |
| ALPHA_VANTAGE_API_KEY | 미국시장 데이터 인증 |
| KRX_AUTH_KEY | 국내 주식/ETF 시세 인증 |
| OPENDART_API_KEY | 국내 주식 공시 배당 데이터 인증 |

Compose는 앞의 DB 세 값을 PostgreSQL과 백엔드에 연결한다.
백엔드에는 표준 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`를 전달하여 기존 application.properties 값을 덮어쓴다.
서버 연결 주소는 `jdbc:postgresql://stock-db:5432/${POSTGRES_DB}`다.
Docker 배포에 기존 로컬 `DB_PASSWORD` 환경변수는 필요하지 않다.
외부 API 인증 변수명은 현재 Java 설정과 일치한다.
강제 갱신은 기존 기본값에 맞춰 `STOCK_REFRESH_ENABLED=false`로 유지한다.

PostgreSQL healthcheck는 TCP `pg_isready`를 사용한다.
DB가 healthy가 되면 백엔드가 시작되고, 백엔드가 healthy가 되면 Nginx가 시작한다.
백엔드 healthcheck는 기존 안전한 GET `/api/hello`를 사용한다.
Actuator 의존성은 추가하지 않았다. Nginx healthcheck는 `/` 응답을 확인한다.
healthcheck는 외부 데이터 제공자의 정상 동작까지 보장하지 않는다.

## 데이터와 로컬 개발

PostgreSQL 17은 named volume `stock-db-data`를 `/var/lib/postgresql/data`에 연결한다.
Windows 로컬 DB와 Ubuntu DB는 별개다. 기존 Windows 데이터는 자동으로 복사되지 않는다.
빈 Volume의 최초 배포에서는 현재 Entity 기준으로 테이블을 생성한다.
현재 개발서버에서는 기존 `ddl-auto=update`를 사용하며,
운영 전환 시 Flyway/Liquibase 같은 Migration 도구를 검토한다.
기존 데이터를 복원해 사용하는 경우에는 해당 스키마와 기존 변경 SQL의 적용 여부를 별도로 확인한다.

Java 소스, pom.xml, application.properties, React 소스, Vite 설정, package.json과 lock 파일을 변경하지 않았다.
새 Spring Profile도 추가하지 않았다.
Eclipse의 localhost:8080과 `frontend`의 `npm run dev` / localhost:5173 / Vite proxy 사용 방식은 유지된다.
루트 .gitignore는 이미 실제 .env와 frontend/node_modules, dist, 로컬 env 파일을 제외하고
.env.example을 허용하고 있어 수정하지 않았다.

## 검증 결과 (2026-10-03)

- `mvnw.cmd test`: 60개, 실패 0 / 오류 0 / 건너뜀 0, BUILD SUCCESS.
  기존 분리 테스트 스키마 `codex_mvp_test_20261002`를 사용했다.
- `mvnw.cmd package -DskipTests`: 실행 JAR 패키징 성공.
  로컬 캐시에 없던 기존 Maven 플러그인 파일은 다운로드한 뒤 성공했다.
- Java 17에서 실행 JAR을 임시 포트로 실행하여 표준 datasource 환경변수의 DB 연결과
  `/api/hello` 응답을 확인했다. 이 확인에서는 `ddl-auto=none`으로 스키마를 변경하지 않았고,
  임시 프로세스는 종료했다. Compose의 ddl-auto 설정은 기존 update를 유지한다.
- `npm ci`, `npm run build`, `npm run lint`: 모두 성공. build output은 dist.
- Docker Compose v5.5.1의 `config --quiet`: 실제 .env 없이 예제와 임시 검증 값으로 성공.
  세 서비스, 공개 포트 80 하나, DB Volume과 health 의존 관계를 검사했다.
- 현재 Windows Docker CLI는 있으나 Docker Engine 연결이 불가능하다.
  **실제 Docker build / compose 테스트는 Ubuntu 개발서버에서 수행 필요.**
  Linux 이미지 실행, Nginx 구문 검사, SPA 새로고침, proxy와 Volume 재시작 유지 확인은
  위 Ubuntu 명령으로 최종 검증해야 한다.
- 기존 애플리케이션 소스/설정은 작업 전후 SHA-256 비교로 변경 없음을 확인했다.
  실제 .env 생성, Git add / commit / push 및 서버 배포는 수행하지 않았다.

## 작업 보고 22항목

| 항목 | 결과 |
|---|---|
| 1. 생성 | Dockerfile, .dockerignore, compose.yml, .env.example, frontend/Dockerfile, frontend/.dockerignore, frontend/nginx.conf, docs/docker-dev-deployment.md |
| 2. 수정 | frontend/README.md에 배포 문서 링크 추가 |
| 3. 삭제 | 없음 |
| 4. Root Dockerfile | Maven Wrapper + JDK 17 빌드, JRE 17에 실행 JAR만 복사 |
| 5. Frontend Dockerfile | Node 24 + npm ci/build, Nginx에 dist 배치 |
| 6. Reverse Proxy | /api 경로와 query를 보존하여 backend:8080으로 전달 |
| 7. Service | stock-db, stock-backend, stock-frontend |
| 8. Network | 전용 bridge stock-network, 서비스 DNS 사용 |
| 9. Volume | stock-db-data → /var/lib/postgresql/data |
| 10. 공개 Port | 80 |
| 11. 비공개 Port | host에 8080, 5432 publish 안 함 |
| 12. .env | POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD, 세 제공자 API Key |
| 13. React 요청 | Browser /api → Nginx → stock-backend:8080 |
| 14. DB 연결 | Spring 표준 datasource env → stock-db:5432 |
| 15. Local 영향 | 소스/기존 설정 변경 없음, 기존 실행 방식 유지 |
| 16. Maven test | 60개 통과 |
| 17. npm build | ci/build/lint 성공 |
| 18. Docker 검증 | Compose config 통과, 실제 Linux 컨테이너 빌드/실행은 Ubuntu에서 필요 |
| 19. 최초 배포 | 이 문서 최초 배포 명령 참고 |
| 20. 수동 재배포 | 사용자 git pull origin develop 후 docker compose up -d --build |
| 21. 로그 | docker compose logs --tail=100 SERVICE / logs -f stock-backend |
| 22. Jenkins 다음 단계 | 아래 내용 참고 |

## 다음 Jenkins 단계

이번에는 Jenkins, Jenkinsfile, Webhook, Registry를 구현하지 않았다.
다음 단계에서 develop 소스 checkout, 백엔드/프론트엔드 테스트,
서버 접근 권한과 Docker 실행 권한, 인증정보 관리, 배포 동시 실행 제어를 정한다.
실제 서버 .env는 저장소나 빌드 결과에 넣지 않고 서버에서 관리한다.
검증 통과 후 `/srv/stock`에서 `docker compose up -d --build`를 실행하고
health 상태와 `/api/hello`를 확인하는 절차를 자동화할 수 있다.
DB 백업과 실패 시 복구 절차도 자동 배포 전에 정한다.

## 확인한 공식 문서

- [Docker Compose 시작 순서와 service_healthy](https://docs.docker.com/compose/how-tos/startup-order/)
- [Docker build cache](https://docs.docker.com/build/cache/optimize/)
- [Docker 내장 DNS](https://docs.docker.com/engine/network/)
- [PostgreSQL 공식 이미지](https://hub.docker.com/_/postgres)
- [Nginx proxy_pass](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)
- [Spring Boot 3.5 Externalized Configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)
