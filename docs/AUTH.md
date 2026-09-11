# 현재 인증 계약

이 문서는 현재 Backend와 Frontend에 구현된 JWT 인증 동작을 설명합니다. 변경 원칙은 [Backend 인증 가이드](skills/backend/auth.md)와 [Frontend 인증 가이드](skills/frontend/auth.md)를 참조합니다.

## 인증 아키텍처 개요

```
Client (Browser)
  │
  ├─ AccessToken  →  sessionStorage (JS 접근 가능)
  └─ RefreshToken →  HttpOnly Cookie (path=/api/auth/refresh)

Server (Spring Boot)
  ├─ AccessToken 검증: JwtVerificationFilter (매 요청)
  └─ RefreshToken 저장: Redis (TTL 7일)
```

현재 [SecurityConfig](../backend/AgentCart/src/main/java/com/agentcart/config/SecurityConfig.java)는 HTTP session을 만들지 않는 stateless 방식입니다. Access token은 서명과 만료를 요청마다 확인하고, Refresh token은 Redis 상태와 함께 확인합니다. Access token은 발급 후 30분 동안 별도 server-side 폐기 목록이 없습니다.

| 항목 | 현재 저장 위치 | 현재 수명 / 범위 | 출처 |
|---|---|---|---|
| Access token | 브라우저 `sessionStorage`의 `access_token` | 30분 | [application.yaml](../backend/AgentCart/src/main/resources/application.yaml), [token.utils.ts](../frontend/src/utils/token.utils.ts) |
| Refresh token | `refresh_token` HttpOnly cookie | 7일, path `/api/auth/refresh` | [LoginSuccessHandler](../backend/AgentCart/src/main/java/com/agentcart/auth/handler/LoginSuccessHandler.java), [AuthController](../backend/AgentCart/src/main/java/com/agentcart/auth/controller/AuthController.java) |
| Refresh token 상태 | Redis의 member/token 양방향 key | 7일 | [RefreshTokenRedisRepository](../backend/AgentCart/src/main/java/com/agentcart/auth/redis/RefreshTokenRedisRepository.java) |

cookie의 `Secure`와 `SameSite`는 profile 설정을 사용합니다. 공통 기본값은 `Secure=true`, `SameSite=None`이고 현재 로컬 개인 설정은 `Secure=false`, `SameSite=Lax`를 사용합니다. HttpOnly는 JavaScript의 cookie 직접 접근을 막지만 XSS 전체를 방지한다는 뜻은 아닙니다.

## 인증 흐름 다이어그램

### 1. 로그인

```
Client                          Server                        Redis
  │                               │                             │
  │── POST /api/auth/login ───────▶│                             │
  │   { email, password }          │                             │
  │                                │ 비밀번호 검증                │
  │                                │── save RefreshToken ───────▶│
  │                                │   rt:member:{id} → token    │
  │                                │   rt:token:{token} → id     │
  │◀── 200 OK ─────────────────────│                             │
  │   Set-Cookie: refresh_token    │                             │
  │   { data: { accessToken, ... }}│                             │
  │                                │                             │
  │ sessionStorage.set(accessToken)│                             │
```

### 2. API 요청 (인증 필요)

```
Client                          Server
  │                               │
  │── GET /api/products ──────────▶│
  │   Authorization: Bearer <AT>   │
  │                                │ JwtVerificationFilter
  │                                │ 토큰 서명·만료 검증
  │◀── 200 OK ─────────────────────│
```

### 3. 토큰 재발급 (AccessToken 만료 시)

```
Client                          Server                        Redis
  │                               │                             │
  │── POST /api/auth/refresh ─────▶│  (HttpOnly Cookie 자동 첨부) │
  │                                │── findByToken ─────────────▶│
  │                                │◀── memberId ────────────────│
  │                                │── deleteByMemberId ─────────▶│  (기존 토큰 삭제)
  │                                │── save(newToken) ───────────▶│  (신규 토큰 저장)
  │◀── 200 OK ─────────────────────│                             │
  │   Set-Cookie: refresh_token(신규)                            │
  │   { data: { accessToken(신규) }}                             │
```

**RefreshToken rotation**: 재발급 시 기존 Redis key를 삭제하고 새 Access/Refresh token을 발급합니다. 이미 교체된 token은 Redis에서 찾을 수 없어 401 대상이 됩니다.

### 4. 로그아웃

```
Client                          Server                        Redis
  │                               │                             │
  │── POST /api/auth/logout ──────▶│                             │
  │   Authorization: Bearer <AT>   │                             │
  │                                │── deleteByMemberId ─────────▶│
  │                                │   rt:member:{id} 삭제       │
  │                                │   rt:token:{token} 삭제     │
  │◀── 200 OK ─────────────────────│                             │
  │                                │                             │
  │ tokenUtils.clear()             │                             │
```

현재 Backend logout은 Redis 상태를 삭제하지만 `refresh_token`을 만료시키는 `Set-Cookie`를 반환하지 않습니다. Frontend Header는 logout API 성공 여부와 관계없이 access token과 Zustand 상태를 지웁니다. 브라우저에 남은 cookie는 수명이 끝날 때까지 보일 수 있지만 Redis 상태가 없으므로 refresh에 사용할 수 없습니다.

---

## Redis 키 구조

| 키 패턴 | 값 | 용도 |
|---------|-----|------|
| `rt:member:{memberId}` | refreshToken 문자열 | 사용자별 토큰 조회·교체 |
| `rt:token:{token}` | memberId 문자열 | 토큰으로 사용자 역조회 |

TTL은 두 key 모두 `jwt.refresh-token-expiration`을 사용하며 현재 7일입니다. 두 key 쓰기·삭제는 Redis transaction으로 묶여 있지 않습니다.

---

## API 엔드포인트

| 메서드 | 경로 | 인증 필요 | 설명 |
|--------|------|-----------|------|
| POST | `/api/auth/register` | X | 회원가입 |
| POST | `/api/auth/login` | X | 로그인 |
| POST | `/api/auth/refresh` | X (쿠키) | 토큰 재발급 |
| POST | `/api/auth/logout` | O | 로그아웃 |
| GET | `/api/auth/me` | O | 내 정보 조회 |

### 응답 구조

```json
// 성공
{
  "success": true,
  "data": { ... },
  "timestamp": "2024-01-01T00:00:00"
}

// 애플리케이션 예외 응답 예시
{
  "success": false,
  "errorCode": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "timestamp": "2024-01-01T00:00:00"
}
```

인증 filter가 시작한 401 등 모든 실패가 항상 같은 JSON body를 갖는다고 가정하지 않습니다. HTTP 계약을 변경할 때 filter·handler·controller 통합 테스트를 기준으로 맞춥니다.

---

## 프론트엔드 토큰 저장 전략

| 토큰 | 저장소 | 이유 |
|------|--------|------|
| AccessToken | `sessionStorage` | JavaScript가 읽어 Bearer header와 SSE URL에 사용. 탭 session 범위 |
| RefreshToken | `HttpOnly Cookie` (`path=/api/auth/refresh`) | JavaScript 직접 접근 불가. refresh endpoint에만 전송 |

### Axios 인터셉터 동작

```
요청 전: sessionStorage에서 AccessToken 꺼내 Authorization 헤더에 주입
응답 후: 401 수신 시 /api/auth/refresh 호출 → 새 AccessToken으로 원 요청 재시도
```

[Axios client](../frontend/src/lib/axios.ts)는 refresh가 진행되는 동안 다른 401 요청을 queue에 둡니다. 현재는 refresh 실패 시 `sessionStorage` token만 지우고 Zustand 상태를 직접 초기화하거나 로그인 화면으로 이동하지 않습니다. 요청별 재시도 완료 표시도 없어 refresh 후 다시 401인 요청에 대해 엄격히 한 번만 재시도한다고 보장하지 않습니다.

### 클라이언트 사이드 라우트 보호

Next.js middleware는 Refresh token cookie를 읽지 않으며 실제 route 보호는 client component인 `ProtectedLayout`과 `useAuthGuard`가 담당합니다. Zustand 인증 상태는 persist되지 않고 시작할 때 `/api/auth/me`로 복원하지도 않습니다. 새 page load에서는 `sessionStorage` token이 남아 있어도 `isAuthenticated=false`에서 시작해 `/login`으로 이동하며 원래 목적지를 보존하지 않습니다.

## SSE 인증

현재 [useSse](../frontend/src/hooks/use-sse.ts)는 Access token을 `token` query parameter로 붙입니다. [JwtVerificationFilter](../backend/AgentCart/src/main/java/com/agentcart/auth/filter/JwtVerificationFilter.java)는 Bearer header가 없으면 이 parameter를 읽습니다.

```text
GET /api/recommendations/stream?query=...&token=<access-token>
```

`EventSource`의 `withCredentials=true`도 설정되어 있지만 Refresh token cookie의 path가 `/api/auth/refresh`로 제한되어 있어 추천 endpoint 인증에는 쓰이지 않습니다. SSE는 Axios의 401 refresh·queue 경로도 거치지 않습니다.

query token은 server·proxy access log, 브라우저 기록, 복사된 URL에 노출될 수 있으므로 로그와 오류 메시지에 전체 URL을 남기지 않습니다. 인증 방식을 바꾸면 Backend filter, CORS, Frontend 구독 코드와 테스트를 함께 갱신합니다.

---

## 검증과 실행

- Backend: [AuthIntegrationTest](../backend/AgentCart/src/test/java/com/agentcart/auth/integration/AuthIntegrationTest.java), auth 단위·repository 테스트
- Frontend: [Axios interceptor 테스트](../frontend/src/lib/__tests__/axios.interceptor.test.ts), [auth guard 테스트](../frontend/src/hooks/__tests__/use-auth-guard.test.tsx), [추천 stream hook 테스트](../frontend/src/features/recommendation/hooks/__tests__/use-recommendation-stream.test.ts)
- 로컬 설정과 실행 순서: [LOCAL_SETUP](LOCAL_SETUP.md)
