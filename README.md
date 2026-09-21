# srrrg-k3s

Java 21·Spring Boot 4 기반의 새 앱이다. 현재는 상태 검사와 최소 OAuth2·JWT 인증을 제공한다. 프로젝트 기능은 아직 없다.

## 로컬 실행

1. `docker compose up -d postgres`로 PostgreSQL을 실행한다. 앱은 기본적으로 `localhost:5433`의 `srrrg_local` DB에 접속한다. 기존 볼륨이 다른 DB 이름으로 초기화됐다면 `srrrg_local`을 별도로 생성한다.
2. 32바이트 이상의 임의 JWT 키를 Base64로 만들어 `SRRRG_JWT_KEY_BASE64` 환경 변수에 넣는다. 예: PowerShell에서 `$env:SRRRG_JWT_KEY_BASE64 = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`.
3. `./gradlew bootRun`으로 실행한다. Windows에서는 `.\gradlew.bat bootRun`을 사용한다. 포트나 접속 정보가 다르면 `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`를 설정한다.

OAuth 공급자를 사용하려면 해당 공급자의 `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`, `KAKAO_CLIENT_ID`·`KAKAO_CLIENT_SECRET`, `GITHUB_CLIENT_ID`·`GITHUB_CLIENT_SECRET`을 설정한다. 일부만 설정한 공급자는 시작을 거부한다. `SRRRG_BASE_URL`은 외부에서 접근하는 Spring 앱의 HTTPS 주소로 설정하고 공급자 콘솔에는 `{baseUrl}/login/oauth2/code/{provider}`를 콜백으로 등록한다. React 주소는 `SRRRG_FRONTEND_URL`로 별도 설정한다. 로컬 HTTP는 `localhost`와 `127.0.0.1`에서만 허용한다.

인증 경로와 Bearer 헤더·OAuth 교환권 계약은 [인증 문서](docs/auth.md)를 따른다. PostgreSQL 스키마는 Flyway가 관리하고 인증 저장은 Spring Data JPA를 사용한다. Redis는 현재 인증에 쓰지 않는다.

## 검증

`./gradlew test`는 외부 공급자와 컨테이너 없이 앱 시작·보안 경로를 확인한다. 실제 PostgreSQL 쿼리는 Compose DB를 대상으로 다음처럼 별도 확인한다.

```powershell
$env:SRRRG_TEST_DATABASE_URL = 'jdbc:postgresql://localhost:5433/srrrg_local'
.\gradlew.bat test --tests link.srrrg.auth.PostgresAuthIntegrationTests --tests link.srrrg.auth.PostgresOAuthFlowTests
```

포트는 `docker compose port postgres 5432`로 확인해 맞춘다. 테스트 데이터는 롤백하거나 개별 삭제하며 Flyway의 인증 테이블은 남는다. 콜백 테스트는 로컬 가짜 공급자로 토큰 교환과 자체 JWT 발급까지 확인한다. 실제 공급자 로그인은 각 공급자의 자격 증명으로 확인해야 한다.
