# Project Context

AgentCart는 상품 추천을 통해 Agentic RAG를 학습하는 개인 프로젝트입니다. 커머스 도메인은 추천을 위한 상품 정보·회원 문맥·주문 이력을 제공합니다.

## 현재 상태

- Spring Boot Backend와 Next.js Frontend를 사용합니다.
- 회원·상품·장바구니·주문·Mock 결제·추천 이력 기능이 있습니다.
- 추천은 OpenAI 또는 Anthropic Claude 중 설정으로 선택한 채팅 모델, 제한된 단일 에이전트와 `searchCatalog`를 사용하는 Agentic RAG 파이프라인입니다. [현행 동작](RECOMMENDATION_PIPELINE.md)
- 데이터의 1차 범위는 합성 상품 JSON 500개와 [10개 추천 시나리오](RECOMMENDATION_SCENARIOS.md)입니다.

## 진행 중인 구현

단일 추천 에이전트가 기존 검색을 감싼 `searchCatalog`를 최대 두 번 호출하며, HTTP/SSE 요청은 진행·결과·종료·오류 이벤트로 이 실행을 전달합니다. 가격·상품 상태·회원 문맥과 호출·시간 상한은 애플리케이션이 관리합니다. 후속 범위는 실제 평가에서 확인된 카테고리 경계 정비입니다.

문서 정합성 정비와 에이전트 기반 코드는 단계별로 진행합니다. Agent와 SSE 계약은 구현됐고 고정 snapshot의 Claude 실제 평가는 완료됐습니다. v2 오프라인 재평가는 명시 정책과 의미 관련성을 분리해 Agent 실행의 명시 정책 위반 0건, 허용 Hit@5 1.0을 기록했습니다. 기대 카테고리 불일치 2건은 명시 조건 위반이 아니며, assistant 판정과 미판정 2건이라는 근거 한계는 유지합니다. 현재/목표 구분은 [현재 파이프라인](RECOMMENDATION_PIPELINE.md)과 [구현 계획](AGENTIC_RAG_PLAN.md)을 기준으로 확인합니다.

## 작업 원칙

기존 컴포넌트를 재사용하고 변경은 작은 단위로 검증합니다. [공통 개발 규칙](DEVELOPMENT.md), [문서 인덱스](README.md)를 따릅니다. 코딩 도구용 지침과 애플리케이션의 추천 에이전트 설계는 역할이 다릅니다.
