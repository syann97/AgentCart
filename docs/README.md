# 문서 인덱스

문서는 **현재 구현**, **승인된 목표**, **개발 지침**을 구분합니다. 목표 설계의 수치를 현재 운영 값으로 소개하거나, 문서 변경만으로 기능이 구현되었다고 기록하지 않습니다.

## 읽는 순서

1. [프로젝트 개요](PROJECT_CONTEXT.md)
2. 추천 작업은 [현재 파이프라인](RECOMMENDATION_PIPELINE.md)과 [목표 설계](AGENTIC_RAG_PLAN.md)
3. [공통 개발 규칙](DEVELOPMENT.md)과 해당 영역의 context·rules·skill

## 문서별 책임

| 문서 | 역할 / 사실 기준 |
|---|---|
| [프로젝트 README](../README.md) | 기능 현황과 문서 진입점 |
| [PROJECT_CONTEXT](PROJECT_CONTEXT.md) | 개인 프로젝트의 목적과 범위 |
| [RECOMMENDATION_PIPELINE](RECOMMENDATION_PIPELINE.md) | 현재 코드의 검색·검증·SSE·실행 값과 알려진 한계 |
| [AGENTIC_RAG_PLAN](AGENTIC_RAG_PLAN.md) | 승인된 다음 구현의 도구·제약·상한·응답·평가 계약 |
| [RECOMMENDATION_SCENARIOS](RECOMMENDATION_SCENARIOS.md) | 현재 JSON 데이터 범위와 후속 평가셋 계획 |
| [BACKEND_CONTEXT](BACKEND_CONTEXT.md) / [BACKEND_RULES](BACKEND_RULES.md) | Backend 구조와 변경 규칙 |
| [FRONTEND_CONTEXT](FRONTEND_CONTEXT.md) / [FRONTEND_RULES](FRONTEND_RULES.md) | Frontend 구조와 변경 규칙 |
| [INFRA_CONTEXT](INFRA_CONTEXT.md) | Compose 서비스, 용도별 TTL, 인프라 설정 출처 |
| [AUTH](AUTH.md) | 실제 인증·쿠키·SSE 인증 동작 |
| [LOCAL_SETUP](LOCAL_SETUP.md) | 실행 전제, 설정 키, 데이터 적재·임베딩 경로 |
| [DEVELOPMENT](DEVELOPMENT.md) | 도구 중립적인 작업·검증·문서 동기화 규칙 |
| [skills](skills/README.md) / [prompts](prompts/README.md) | 필요할 때 읽는 작업 가이드와 공유 템플릿 |

[AGENT_CONTEXT.md](AGENT_CONTEXT.md)는 이전 경로 호환용 안내입니다. 새 설계나 정책을 그 파일에 추가하지 않습니다.

## 정합성 유지

- 실제 동작은 소스·설정·적절한 테스트로 확인합니다. 목표 동작은 승인된 설계와 변경 범위를 기준으로 구현합니다.
- 실행 값은 위 담당 문서에 출처와 함께 기록하고, 다른 문서는 링크로 참조합니다.
- 의존성의 간접 버전은 별도 목록으로 복제하지 않습니다.
- 기능을 구현한 변경에서 현행 문서와 목표 문서의 구현 상태를 함께 갱신합니다.
- 로컬 Markdown 링크는 작성한 파일을 기준으로 상대 경로를 사용합니다.
- 개인 설정·이전 세션 메모는 공유 가이드의 사실 기준으로 사용하지 않습니다.
