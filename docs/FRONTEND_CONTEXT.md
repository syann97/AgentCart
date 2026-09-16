# Frontend Context

## 현재 구성

루트는 [frontend](../frontend/)이며 의존성 선언과 실행 명령은 [package.json](../frontend/package.json), 설치된 의존성 기준은 [package-lock.json](../frontend/package-lock.json)입니다. Node.js 버전은 프로젝트의 고정 선언으로 확인되지 않으므로 기존 로컬 버전을 프로젝트 보장으로 기록하지 않습니다.

- Next.js App Router, React, TypeScript
- Tailwind CSS
- TanStack Query: 서버 상태 / Zustand: 인증 등 클라이언트 상태
- Axios: 일반 REST 요청 / EventSource: 현재 SSE 구독
- React Hook Form + Zod: 폼과 입력 검증
- Vitest, Testing Library, MSW, jsdom: 테스트 도구

## 구조

페이지는 `src/app`, 보호된 페이지는 `src/app/(protected)`에 있습니다. 도메인 코드는 `src/features/{auth,product,cart,order,payment,recommendation}`에 있고 공통 코드는 `components`, `hooks`, `lib`, `stores`, `types`, `constants`, `utils`로 나뉩니다.

현재 없는 `features/agent`, `shared`, `entities`를 구현된 디렉터리로 안내하지 않습니다. 실제 구성은 [Frontend README](../frontend/README.md)에서 확인합니다.

## 인증과 추천

[AUTH](AUTH.md)가 일반 API와 SSE의 인증 차이, sessionStorage와 인증 스토어의 실제 동작을 설명합니다.

현재 추천 훅은 `status | result | done | error` 판별 유니온을 사용합니다. `result`만 누적하고 `done`의 outcome 또는 `error`에서 연결과 loading 상태를 종료하며, transport 오류는 서버가 보낸 오류와 구분합니다. [FRONTEND_RULES](FRONTEND_RULES.md)
