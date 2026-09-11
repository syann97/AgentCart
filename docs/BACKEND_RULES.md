# Backend 변경 규칙

공통 작업 규칙은 [DEVELOPMENT](DEVELOPMENT.md), 현재 구조는 [BACKEND_CONTEXT](BACKEND_CONTEXT.md)를 따릅니다. 이 문서는 개발 원칙이며 각 원칙이 기존 모든 경로에 구현되어 있다는 뜻은 아닙니다.

## 계층과 데이터

- Controller는 HTTP 입력·인증 문맥·응답을 처리하고 비즈니스 규칙은 Service에서 관리합니다.
- MySQL은 도메인 데이터, PostgreSQL은 임베딩을 담당합니다. 저장소 연결과 트랜잭션 경계를 명시합니다.
- 상품 사실과 가격은 MySQL을 기준으로 반환합니다. 모델이나 벡터 metadata만으로 현재 재고·가격을 확정하지 않습니다.
- HTTP 요청과 외부 LLM 출력은 경계에서 검증합니다. [검증 가이드](skills/backend/validation.md)

## 추천

- 실제 처리 순서·검색 공식·임계값·fallback은 [현재 파이프라인](RECOMMENDATION_PIPELINE.md)이 기준입니다.
- 현재는 후보 검색 이후 검증합니다. SQL 사전 필터가 이미 구현되었다고 가정하지 않습니다.
- 순위 점수와 확신도를 구분합니다. 카테고리·가격·검색 충분성 정책을 점수 하나로 대체하지 않습니다.
- 새 동작은 [승인된 계획](AGENTIC_RAG_PLAN.md)의 명시 조건 보존·제한형 검색·결과 없음 계약에 맞춥니다. 고정된 클래스 실행 순서를 불변 규칙으로 강제하지 않습니다.
- 추천 DTO, 실제 SSE 메시지, Frontend 타입과 훅을 같은 변경에서 맞춥니다. 현재 메시지와 목표 메시지를 혼용하지 않습니다.

## Timeout과 fallback

상품 임베딩, 추천 질의 임베딩, 질의 확장, 후보별 이유 생성, SSE와 요청 전체 deadline은 서로 다른 경계입니다. [현재 값](RECOMMENDATION_PIPELINE.md)을 확인하고 목표 상한은 [구현 계획](AGENTIC_RAG_PLAN.md)에 따라 별도로 적용합니다.

LLM 장애 처리에서 사용자 조건을 없애지 않습니다. 다만 현행 fallback은 이 조건을 만족하지 못하므로 개선 전까지 그 한계를 문서에 유지합니다. Future timeout과 실제 외부 호출 취소를 구분합니다.

## Redis와 Kafka

- TTL은 용도마다 다릅니다. [INFRA_CONTEXT](INFRA_CONTEXT.md)의 키·설정 출처를 따릅니다.
- 재고 락의 대기 시간과 획득 후 유지 시간을 구분합니다. 락 실패 시 기존 예외 계약을 따르고 무조건 요청을 재시도하지 않습니다.
- 기존 추천 이벤트는 Producer와 Consumer, eventId, 중복 처리 정책을 함께 검토합니다.
- Redis 중복 키와 DB 저장이 원자적으로 커밋되지 않는 현재 구조를 exactly-once라고 설명하지 않습니다.
- 개인 프로젝트의 도구 호출 실습을 위해 새로운 메시지 브로커나 추상 계층을 추가하지 않습니다.

## 검증

정책 변경에는 경계·실패 사례를 검증하는 테스트를 적용합니다. 기존 기대값이 승인된 정책과 충돌하면 이유와 새 기대값을 명시해 갱신합니다. [테스트 가이드](skills/backend/testing.md), [추천 평가 계획](RECOMMENDATION_SCENARIOS.md)
