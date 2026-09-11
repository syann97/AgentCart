# Frontend 테스트 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. Frontend는 Vitest, Testing Library, MSW, jsdom을 사용하며 공통 검증 기준은 [DEVELOPMENT](../../DEVELOPMENT.md)를 따릅니다.

## Test Rules

- Test user-visible behavior
- Avoid implementation-detail assertions

## Authentication Tests

- Verify redirect behavior
- Verify token refresh flow

## Streaming Tests

- Verify incremental rendering
- Verify cleanup on disconnect
- Distinguish normal completion, zero results, server failure, and a new search
- Test the current `complete` item contract separately from the planned `status | result | done | error` contract

## 실행

```powershell
npm test
npm run lint
npm run build
```

변경과 직접 관련된 Vitest를 먼저 실행합니다. type·routing·bundling 경계가 관련될 때 lint와 build를 추가하고 실제로 실행하지 않은 검사를 통과했다고 기록하지 않습니다.
