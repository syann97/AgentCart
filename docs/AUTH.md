# AgentCart — 인증 시스템 가이드

AI 기반 쇼핑 추천 서비스 AgentCart의 JWT 인증 흐름을 설명합니다.

---

## 인증 아키텍처 개요

```
Client (Browser)
  │
  ├─ AccessToken  →  sessionStorage (메모리에 가깝게, JS 접근 가능)
  └─ RefreshToken →  HttpOnly Cookie (path=/api/auth/refresh, JS 접근 불가)

Server (Spring Boot)
  ├─ AccessToken 검증: JwtVerificationFilter (매 요청)
  └─ RefreshToken 저장: Redis (TTL 7일)
```

### 왜 Stateless JWT를 선택했는가

- DB 조회 없이 토큰만으로 인증 검증 가능 → 수평 확장(Scale-out)에 유리
- Spring Security의 Stateless 세션 설정과 자연스럽게 결합
- AI Agent 파이프라인 특성상 다수의 내부 서비스 간 인증 전달이 단순해짐
- 단점(토큰 즉시 무효화 불가)은 짧은 AccessToken 만료(30분) + Redis 기반 RefreshToken 관리로 완화

### 왜 Redis에 RefreshToken을 저장하는가

- RDB에 토큰을 저장하면 재발급·로그아웃 시마다 쓰기 트랜잭션이 발생함
- Redis는 TTL을 네이티브로 지원 → 만료된 토큰 정리를 별도 배치 없이 처리
- 사용자당 1개 토큰 정책(1개 기기): `rt:member:{memberId}` 키로 기존 토큰을 덮어씀
- 로그아웃·토큰 탈취 의심 시 해당 키만 삭제하면 즉시 무효화 가능

---

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

**RefreshToken Rotation**: 재발급 시마다 RefreshToken도 교체됨. 탈취된 토큰으로 재발급 시도 시 이미 삭제된 토큰이므로 401 반환.

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
  │ sessionStorage.clear()         │                             │
```

---

## Redis 키 구조

| 키 패턴 | 값 | 용도 |
|---------|-----|------|
| `rt:member:{memberId}` | refreshToken 문자열 | 사용자별 토큰 조회·교체 |
| `rt:token:{token}` | memberId 문자열 | 토큰으로 사용자 역조회 |

TTL: 두 키 모두 7일 (재발급 시 갱신됨)

---

## API 엔드포인트

| 메서드 | 경로 | 인증 필요 | 설명 |
|--------|------|-----------|------|
| POST | `/api/auth/register` | X | 회원가입 |
| POST | `/api/auth/login` | X | 로그인 |
| POST | `/api/auth/refresh` | X (쿠키) | 토큰 재발급 |
| POST | `/api/auth/logout` | O | 로그아웃 |
| GET | `/api/auth/me` | O | 내 정보 조회 |

### 공통 응답 구조

```json
// 성공
{
  "success": true,
  "data": { ... },
  "timestamp": "2024-01-01T00:00:00"
}

// 실패
{
  "success": false,
  "errorCode": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "timestamp": "2024-01-01T00:00:00"
}
```

---

## 프론트엔드 토큰 저장 전략

| 토큰 | 저장소 | 이유 |
|------|--------|------|
| AccessToken | `sessionStorage` | XSS 위험을 최소화하면서 JS 접근 가능. 탭 닫으면 자동 삭제 |
| RefreshToken | `HttpOnly Cookie` (`path=/api/auth/refresh`) | JS 접근 불가 → XSS 차단. 경로 제한으로 불필요한 요청에 첨부 방지 |

### Axios 인터셉터 동작

```
요청 전: sessionStorage에서 AccessToken 꺼내 Authorization 헤더에 주입
응답 후: 401 수신 시 /api/auth/refresh 호출 → 새 AccessToken으로 원 요청 재시도
```

### 클라이언트 사이드 라우트 보호

Next.js middleware에서 RefreshToken 쿠키를 읽을 수 없음 (`path=/api/auth/refresh` 제한으로 브라우저가 다른 경로에는 쿠키를 전송하지 않음). 따라서 인증 보호는 클라이언트 컴포넌트(`ProtectedLayout` + `useAuthGuard`)에서 처리.

---

## 로컬 실행

```bash
# 인프라 (MySQL, Redis)
docker compose up -d

# 백엔드
cd backend/AgentCart
./gradlew bootRun --args='--spring.profiles.active=local'

# 프론트엔드
cd frontend
npm install
npm run dev
```
