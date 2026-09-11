# Project Context

AgentCart는 상품 추천을 통해 Agentic RAG를 학습하는 개인 프로젝트입니다. 커머스 도메인은 추천을 위한 상품 정보·회원 문맥·주문 이력을 제공합니다.

## 현재 상태

- Spring Boot Backend와 Next.js Frontend를 사용합니다.
- 회원·상품·장바구니·주문·Mock 결제·추천 이력 기능이 있습니다.
- 추천은 LLM을 포함한 고정형 RAG 파이프라인입니다. [현행 동작](RECOMMENDATION_PIPELINE.md)
- 데이터의 1차 범위는 합성 상품 JSON 500개와 [10개 추천 시나리오](RECOMMENDATION_SCENARIOS.md)입니다.

## 다음 구현

단일 추천 에이전트가 기존 검색을 감싼 `searchCatalog`를 호출하고, 결과가 부족할 때 한 번 재검색하는 [승인된 설계](AGENTIC_RAG_PLAN.md)를 구현합니다. 가격·상품 상태·회원 문맥은 애플리케이션이 관리합니다.

현재 [#173](https://github.com/syann97/AgentCart/issues/173)의 범위는 문서 정합성 정비입니다. 런타임 코드·의존성·SSE 프로토콜 변경은 후속 작업입니다.

## 작업 원칙

기존 컴포넌트를 재사용하고 변경은 작은 단위로 검증합니다. [공통 개발 규칙](DEVELOPMENT.md), [문서 인덱스](README.md)를 따릅니다. 코딩 도구용 지침과 애플리케이션의 추천 에이전트 설계는 역할이 다릅니다.
