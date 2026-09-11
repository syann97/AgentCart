# Frontend UI 작업 가이드

이 파일은 도구 중립적인 저장소 참고 문서입니다. 현재 구조와 상태 계약은 [Frontend Context](../../FRONTEND_CONTEXT.md), 공통 변경 원칙은 [Frontend Rules](../../FRONTEND_RULES.md)를 따릅니다.

## Component Rules

- Keep components focused and small
- Extract repeated UI into reusable components
- Avoid deeply nested JSX

## State Rules

- Keep local state minimal
- Avoid duplicated derived state
- Reset request state together when starting a new search

## Rendering Rules

- Avoid unnecessary re-renders
- Prefer conditional rendering over hidden DOM
- Do not call state setters from the render body

## Styling Rules

- Follow existing components and Tailwind conventions
- Avoid inline styles unless trivial

추천 UI는 loading, result, zero-result, error를 구분하고 가격과 상품 사실은 server DTO에서 표시합니다. 내부 agent 추론이나 검증되지 않은 신뢰도 표현은 사용자에게 노출하지 않습니다.
