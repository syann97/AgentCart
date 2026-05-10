# AgentCart Frontend

AI Agent 기반 이커머스 서비스의 프론트엔드.  
Next.js 15 App Router + React 19 + TypeScript로 구성된다.

## 기술 스택

| 분류 | 기술 | 선택 이유 |
|------|------|----------|
| 프레임워크 | Next.js 15 (App Router) | SSR/SSG 유연성, 파일 기반 라우팅, Server Component 지원 |
| UI | React 19 + Tailwind CSS | 최신 React 동시성 모드, 유틸리티 CSS로 빠른 스타일링 |
| 서버 상태 | TanStack Query v5 | 캐싱/갱신/무효화 자동 관리, 백엔드 동기화 단순화 |
| 클라이언트 상태 | Zustand | 보일러플레이트 없음, 인증 상태처럼 전역이지만 단순한 상태에 적합 |
| HTTP | Axios | Interceptor 기반 토큰 자동 주입·갱신, 에러 핸들링 집중화 |
| 폼 검증 | React Hook Form + Zod | 비제어 컴포넌트로 성능 최적화, Zod 스키마로 타입과 검증 통합 |
| 언어 | TypeScript (strict) | 컴파일 타임 오류 차단, 백엔드 DTO와 타입 동기화 |

## 폴더 구조

```
src/
├── app/                  # Next.js App Router 페이지 및 레이아웃
│   ├── layout.tsx        # 루트 레이아웃 (Providers, Header 포함)
│   ├── providers.tsx     # QueryClientProvider 래퍼
│   ├── error.tsx         # 전역 에러 바운더리
│   ├── not-found.tsx     # 404 페이지
│   ├── login/            # 로그인 페이지
│   ├── products/         # 상품 목록
│   ├── recommendations/  # AI 추천 (SSE 스트리밍)
│   └── cart/             # 장바구니
│
├── features/             # 도메인별 기능 모음 (FSD 방식 참고)
│   ├── auth/             # 로그인/인증
│   │   ├── api/          # API 함수
│   │   ├── components/   # UI (LoginForm 등)
│   │   ├── hooks/        # useMutation 래퍼
│   │   └── types/        # DTO, Zod 스키마
│   ├── product/
│   ├── recommendation/   # SSE 스트리밍 훅 포함
│   ├── cart/
│   ├── order/
│   └── agent/            # Agent 모니터링 대시보드 (추후 확장)
│
├── components/
│   ├── ui/               # 재사용 가능한 원자 컴포넌트 (Button, Input 등)
│   └── layout/           # Header, Footer 등 레이아웃 컴포넌트
│
├── shared/               # features 간 공유 코드
├── entities/             # 백엔드 도메인 모델 타입 (Member, Product, Order)
├── lib/                  # 외부 라이브러리 설정
│   ├── axios.ts          # Axios 인스턴스 + Interceptor
│   └── query-client.ts   # TanStack Query 클라이언트
│
├── hooks/                # 전역 커스텀 훅 (use-sse 등)
├── stores/               # Zustand 스토어
├── types/                # 전역 타입 (ApiResponse 등)
├── constants/            # API 엔드포인트, 쿼리 키
├── utils/                # 순수 유틸 함수 (token 관리 등)
└── styles/               # 전역 CSS
```

### 구조 선택 이유

- **features 기반 분리**: 기능이 추가될 때 해당 폴더만 건드리면 된다. 횡단 관심사는 `shared`로 분리.
- **lib 분리**: Axios·QueryClient 설정을 한 곳에서 관리하여 테스트 시 모킹이 쉽다.
- **entities 분리**: 백엔드 도메인 모델을 별도 관리해 타입 변경 시 영향 범위를 최소화한다.

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
  → 재발급 실패 시 → tokenUtils.clear() → 로그인 페이지로
```

`accessToken`을 `sessionStorage`에 저장하는 이유:  
`localStorage`는 XSS로 탈취 가능하고, `refresh_token`은 `HttpOnly` 쿠키로 JavaScript 접근 불가.  
탭 종료 시 자동 만료되는 `sessionStorage`가 보안·UX 균형에 적합하다.

## SSE 스트리밍 구조

```
useRecommendationStream(enabled)
  → useSse(url, { onMessage })
  → EventSource + withCredentials
  → JSON 청크 파싱 → results 상태 축적
  → UI에서 점진적 렌더링
```

`EventSource`는 `Authorization` 헤더를 지원하지 않으므로 `?token=` 쿼리 파라미터로 전달한다.  
백엔드에서 해당 파라미터를 별도 처리해야 한다.

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
| `NEXT_PUBLIC_APP_ENV` | 환경 구분 | `development` |

## 향후 확장 포인트

- `src/features/agent/` — Agent 파이프라인 모니터링 대시보드
- `src/features/recommendation/components/` — SSE 스트리밍 결과 카드 UI
- `src/components/ui/` — Button, Input, Badge, Skeleton 등 공통 컴포넌트
- Server Component 활용 — 상품 목록 초기 데이터를 서버에서 fetch
