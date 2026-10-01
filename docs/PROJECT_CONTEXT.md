# Project Context

AgentCart는 상품 추천을 통해 Agentic RAG를 학습하는 개인 프로젝트입니다. 커머스 도메인은 추천을 위한 상품 정보·회원 문맥·주문 이력을 제공합니다.

## 현재 상태

- Spring Boot Backend와 Next.js Frontend를 사용합니다.
- 회원·상품·장바구니·주문·Mock 결제·추천 이력 기능이 있습니다.
- 추천은 OpenAI 또는 Anthropic Claude 중 설정으로 선택한 채팅 모델, 제한된 단일 에이전트와 `searchCatalog`를 사용하는 Agentic RAG 파이프라인입니다. [현행 동작](RECOMMENDATION_PIPELINE.md)
- 데이터의 1차 범위는 합성 상품 JSON 500개와 [10개 추천 시나리오](RECOMMENDATION_SCENARIOS.md)입니다.

## 구현 상태와 근거

Agent와 SSE 계약은 구현됐습니다. 변경할 동작의 현재 값과 남은 목표는 [현재 파이프라인](RECOMMENDATION_PIPELINE.md)과 [구현 계획](AGENTIC_RAG_PLAN.md)에서 확인합니다. 실제 Claude 실행·오프라인 판정의 수치와 한계는 [Agent 평가](../evaluation/recommendation/AGENTIC_RAG_EVALUATION_2026-09-16.md), [근거 개선 평가](../evaluation/recommendation/GROUNDING_EVALUATION_2026-09-18.md)에 보존합니다.

## 작업 원칙

기존 컴포넌트를 재사용하고 변경은 작은 단위로 검증합니다. [공통 개발 규칙](DEVELOPMENT.md), [문서 인덱스](README.md)를 따릅니다. 코딩 도구용 지침과 애플리케이션의 추천 에이전트 설계는 역할이 다릅니다.
