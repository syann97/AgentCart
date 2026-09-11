# AgentCart Frontend

현재 Frontend는 Next.js App Router, React, TypeScript로 회원·상품·장바구니·주문·Mock 결제·추천 화면을 제공합니다. 추천 agent의 단계별 진행 UI는 아직 구현되지 않았습니다.

## 기술 스택

| 분류 | 기술 | 현재 역할 |
|------|------|----------|
| 프레임워크 | Next.js 15.5.18 (App Router) | page와 layout routing |
| UI | React 19.1.0 + Tailwind CSS 4 | component와 styling |
| 서버 상태 | TanStack Query v5 | API 상태와 cache |
| 클라이언트 상태 | Zustand | 현재 인증 member와 상태 |
| HTTP / stream | Axios / browser `EventSource` | REST token 처리 / 추천 SSE |
| 폼 검증 | React Hook Form + Zod | 폼 상태와 client 입력 검증 |
| 테스트 | Vitest, Testing Library, MSW, jsdom | unit·component·hook 검증 |

버전의 기준은 [package.json](package.json)과 [package-lock.json](package-lock.json)입니다. Node.js 버전은 저장소에 고정되어 있지 않습니다.

## 폴더 구조

```
src/
├── app/                  # Next.js App Router 페이지와 레이아웃
│   ├── (protected)/      # client auth guard 아래 상품·추천·장바구니·주문
│   ├── login/
│   └── register/
│
├── features/             # 도메인별 기능 모음 (FSD 방식 참고)
│   ├── auth/             # 로그인/인증
│   │   ├── api/          # API 함수
│   │   ├── components/   # UI (LoginForm 등)
│   │   ├── hooks/        # useMutation 래퍼
│   │   └── types/        # DTO, Zod 스키마
│   ├── product/
│   ├── recommendation/   # SSE 구독 훅과 추천 타입
│   ├── cart/
│   ├── order/
│   └── payment/
│
├── components/
│   ├── ui/               # 재사용 가능한 원자 컴포넌트 (Button, Input 등)
│   └── layout/           # Header, Footer 등 레이아웃 컴포넌트
│
├── lib/                  # Axios와 QueryClient 설정
├── hooks/                # useSse와 auth guard
├── stores/               # Zustand store
├── constants/
├── types/
├── utils/
└── test/                 # 공통 테스트 설정
```

### 구조 선택 이유

- page는 `src/app`, 도메인별 API·hook·type·component는 `src/features`에 둡니다.
- 인증된 page는 `src/app/(protected)`의 `ProtectedLayout`을 거칩니다.
- 공통 component와 hook, 외부 client 설정은 각각 `components`, `hooks`, `lib`에 있습니다.

`features/agent`, `shared`, `entities`, `styles` 디렉터리는 현재 없습니다. 새 구조는 실제 구현과 import를 같은 변경에서 추가합니다.

## 인증 흐름

```
로그인 성공
  → accessToken → sessionStorage (tokenUtils)
  → refresh_token → HttpOnly 쿠키 (백엔드 Set-Cookie)

API 요청
  → Axios Request Interceptor → Authorization: Bearer {accessToken}

401 응답
  → Axios Response Interceptor
  → POST /api/auth/refresh (쿠키 자동 전송)
  → 새 accessToken 저장 → 원 요청 재시도
  → 재발급 실패 시 → tokenUtils.clear()
```

Zustand 인증 상태는 persist되지 않고 시작할 때 `/api/auth/me`로 복원하지도 않습니다. 새 page load에서는 `sessionStorage` token이 남아 있어도 보호 layout이 로그인 화면으로 보낼 수 있습니다. refresh 실패도 token만 지우며 store 초기화와 redirect를 직접 수행하지 않습니다. 실제 cookie·route guard·SSE token 계약은 [AUTH](../docs/AUTH.md)를 참조합니다.

## SSE 스트리밍 구조

```
useRecommendationStream.start(query)
  → useSse(url, { onMessage })
  → EventSource + withCredentials
  → access token을 ?token= query parameter로 전달
  → type=complete 상품을 results 상태에 축적
  → 연결 종료/error 경로에서 검색 상태 종료
```

Backend는 추천 전체를 계산한 뒤 상품별 `complete` 메시지를 전송합니다. `complete`는 상품 하나이며 전체 요청 종료 event가 아닙니다. 현재 type에는 `partial | complete | error`가 선언되어 있지만 Backend가 세 종류를 모두 보내는 것은 아닙니다.

`status | result | done | error`와 재검색 진행 UI는 [Agentic RAG 목표 설계](../docs/AGENTIC_RAG_PLAN.md)의 후속 구현입니다. 현행 계약은 [추천 파이프라인](../docs/RECOMMENDATION_PIPELINE.md)을 따릅니다.

## 시작하기

```bash
# 의존성 설치
npm install

# 환경 변수 설정
cp .env.local.example .env.local
# .env.local 편집: NEXT_PUBLIC_API_BASE_URL=http://localhost:8080

# 개발 서버 실행
npm run dev

# 빌드
npm run build && npm start
```

## 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `NEXT_PUBLIC_API_BASE_URL` | Spring Boot 백엔드 URL | `http://localhost:8080` |
| `NEXT_PUBLIC_APP_ENV` | 예시 파일에는 있으나 현재 소스에서 읽지 않음 | `development` |

## 검증

```powershell
npm test
npm run lint
npm run build
```

변경 범위에 맞는 Vitest를 먼저 실행하고 type·bundle 경계가 관련되면 build까지 확인합니다. SSE 변경은 메시지 누적, 0개 결과, 오류, 재검색 또는 연결 정리처럼 사용자에게 보이는 상태를 검증합니다. 공통 원칙은 [Frontend 변경 규칙](../docs/FRONTEND_RULES.md)을 따릅니다.
