# 인증 흐름

## 범위와 식별

Google, Kakao, GitHub 로그인만 제공한다. 필수 계정 정보는 내부 사용자 ID와 `(provider, provider_user_id)`뿐이다. 이메일·이름을 요구하거나 저장하지 않는다. 다른 공급자의 같은 이메일을 자동으로 합치지 않으므로 계정 연결은 별도 기능으로 설계해야 한다.

## OAuth2 로그인

1. 브라우저가 `GET /oauth2/authorization/{provider}`를 연다. Spring Security가 `state`와 PKCE S256 값을 만든다. 콜백 주소는 요청의 Host가 아니라 `SRRRG_BASE_URL`로 고정한다.
2. 인가 요청은 PostgreSQL의 `oauth_authorization_requests`에 10분 동안 보관한다. 브라우저에는 조회용 난수의 `HttpOnly; SameSite=Lax` 쿠키만 주고 DB에는 그 해시를 저장한다. 이 쿠키는 OAuth `state` 검증에만 쓰며 API 인증에는 쓰지 않는다.
3. 공급자가 `GET /login/oauth2/code/{provider}`로 돌려보내면 쿠키와 `state`가 모두 맞는 행을 한 번만 소비한다. PKCE 검증 값은 공급자 토큰 교환에 사용한다.
4. 공급자 사용자 ID로 계정을 찾거나 만든다. GitHub는 추가 scope를 요청하지 않고, Google은 사용자 식별을 위한 `profile` scope만 요청한다. 공급자 토큰은 이후 사용하지 않으므로 보관하지 않는다.
5. Spring은 1분짜리 일회용 교환권을 DB에 저장하고 팝업을 `SRRRG_FRONTEND_URL/auth/popup#ticket=...`으로 이동시킨다. URL에는 액세스·리프레시 토큰을 넣지 않는다. 팝업의 React 페이지는 fragment를 주소에서 즉시 지우고 교환권을 부모 React 창에 `postMessage`로 전달한 뒤 닫힌다. 부모 창은 출처와 팝업 창을 확인하고 `POST /api/auth/exchange`에 교환권을 제출한다. 성공 응답에서 자체 토큰을 받으며 같은 교환권은 재사용할 수 없다.

사용자가 공급자 로그인을 취소하거나 인증이 실패하면 Spring은 상세 오류를 노출하지 않고 팝업을 `SRRRG_FRONTEND_URL/auth/popup#error=oauth_failed`로 이동시킨다. 팝업은 같은 메시지 채널로 실패를 알리고 닫힌다.

인가 요청·교환권·리프레시 토큰은 공용 DB에 있어 요청이 다른 파드에 도착해도 처리할 수 있다. 만료된 임시 행은 새 로그인 시도 때 정리한다. 이동 경로는 설정값으로 고정되어 있어 요청 파라미터로 외부 URL을 받지 않는다.

### React 팝업 계약

- 로그인 팝업은 브라우저의 차단을 피하도록 사용자 클릭 안에서 `/oauth2/authorization/{provider}`로 연다.
- 팝업은 부모 창에 `{ type: "srrrg.oauth.complete", status: "success", ticket }` 또는 `{ type: "srrrg.oauth.complete", status: "error", error: "oauth_failed" }`를 전달한다.
- `postMessage`의 대상은 `*` 대신 설정된 프런트 출처로 제한한다. 부모 창은 `event.origin`이 프런트 출처이고 `event.source`가 자신이 연 팝업인지 모두 확인한다.
- 액세스·리프레시 토큰은 팝업에서 교환하거나 창 사이에 전달하지 않는다. 부모 창이 교환 API를 호출하고 토큰을 관리한다.
- 프런트의 `Cross-Origin-Opener-Policy`가 팝업의 `window.opener`를 끊지 않도록 배포 헤더를 확인한다.

## JWT와 리프레시 토큰

- `accessToken`: 자체 서명한 HS256 JWT. 사용자 ID만 담고 5분 뒤 만료한다. 일반 API에는 `Authorization: Bearer <accessToken>`으로 보낸다. 모든 파드는 같은 서명 키를 사용해야 한다.
- `refreshToken`: 32바이트 난수에 접두사를 붙인 값. DB에는 SHA-256 해시만 저장한다. 30일 수명이며 refresh·logout에는 `Authorization: Bearer <refreshToken>`으로 보낸다.
- 로그인 교환·갱신 응답은 `tokenType`, `accessToken`, `refreshToken`, `expiresIn`(초)을 JSON으로 반환하고 `Cache-Control: no-store`를 설정한다. 외부 주소는 HTTPS를 요구한다.

`POST /api/auth/refresh`는 리프레시 토큰을 행 잠금으로 소비하고 같은 계열의 새 토큰과 JWT를 발급한다. 사용된 토큰이 다시 오면 계열 전체를 폐기한다. `POST /api/auth/logout`은 헤더로 받은 리프레시 토큰의 계열을 폐기한다. 이미 발급된 JWT는 즉시 취소되지 않으며 최대 5분 뒤 만료한다.

## 브라우저 API

| 요청 | 결과 |
|---|---|
| `POST /api/auth/exchange` | `{ "ticket": "..." }`를 받아 토큰 JSON 반환, 무효·재사용 시 401 |
| `GET /api/auth/me` | 액세스 Bearer JWT의 `userId`를 반환, 없으면 401 |
| `POST /api/auth/refresh` | 리프레시 Bearer 토큰을 회전하고 새 토큰 JSON 반환, 무효면 401 |
| `POST /api/auth/logout` | 리프레시 Bearer 토큰 계열을 폐기하고 204 반환 |

API 인증 정보는 모두 명시적인 헤더로만 받으므로 CSRF 토큰 엔드포인트와 검사를 사용하지 않는다. OAuth `state`용 임시 쿠키는 공급자 콜백 검증에 계속 사용한다. refresh·logout에는 액세스 JWT가 아닌 리프레시 토큰을 헤더로 보내야 한다.

`SRRRG_FRONTEND_URL`로 지정한 정확한 출처만 API CORS를 허용하며 쿠키 자격 증명은 허용하지 않는다. React는 액세스·리프레시 토큰을 메모리에 보관하는 것을 기본으로 삼는다. 이 경우 새로고침하면 다시 로그인해야 한다. 장기 로그인을 위해 브라우저 저장소에 리프레시 토큰을 남기면 스크립트가 그 값을 읽을 수 있어 별도 위험 판단이 필요하다. 계정 연결과 공급자 API 호출은 이 범위에 포함되지 않는다.
