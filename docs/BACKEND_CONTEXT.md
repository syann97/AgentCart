# Backend Context

## 현재 구성

Backend 루트는 [backend/AgentCart](../backend/AgentCart/)입니다. Java toolchain, Spring Boot·Spring AI·명시 의존성은 [build.gradle](../backend/AgentCart/build.gradle), Gradle 배포 버전은 [wrapper 설정](../backend/AgentCart/gradle/wrapper/gradle-wrapper.properties)이 기준입니다.

Spring Framework·Security·Hibernate·Jackson 등 간접 의존성의 세부 버전은 Gradle dependency resolution을 기준으로 확인합니다. 문서에 별도 고정 목록을 유지하지 않습니다.

- Spring MVC Controller → Service → Repository 구조
- MySQL: 회원·상품·주문·추천 이력 등 도메인 데이터
- PostgreSQL + pgvector: 상품 임베딩, 별도 JDBC 연결·Flyway 경로
- Redis: refresh token, 질의 확장 캐시, 이벤트 중복 처리 억제, 재고 분산 락
- Kafka: 추천 이력 이벤트

## LLM과 임베딩

현재 서비스는 `openAiChatModel`과 `ollamaEmbeddingModel`을 명시적으로 주입합니다. 로컬 설정에서 채팅은 `gpt-4o-mini`, 임베딩은 `bge-m3`를 사용합니다. OpenAI·Anthropic·Ollama starter가 함께 선언되어 있다는 사실과 실제 호출 모델을 구분합니다.

`application-local.yaml`은 개인 파일로 Git에 포함되지 않습니다. 실행 전 모델과 데이터 소스 설정은 [LOCAL_SETUP](LOCAL_SETUP.md)을 확인합니다.

[현재 추천 파이프라인](RECOMMENDATION_PIPELINE.md)은 `ChatModel` 직접 호출을 사용합니다. [단일 에이전트 설계](AGENTIC_RAG_PLAN.md)의 도구 호출 루프와 안정 버전 전환은 후속 작업입니다.

## 직렬화와 테스트

주요 ObjectMapper는 Jackson 3의 `tools.jackson.databind.ObjectMapper`를 사용하며 annotation 패키지와 구분합니다. [ApiResponse](../backend/AgentCart/src/main/java/com/agentcart/common/ApiResponse.java)가 REST 응답 기준입니다.

Mockito 단위 테스트, JPA slice, Testcontainers 통합·마이그레이션 테스트를 구분합니다. H2는 일부 JPA slice 용도로 선언되어 있으며 실제 MySQL FULLTEXT·pgvector 동작 검증을 대체하지 않습니다. [Backend 테스트 가이드](skills/backend/testing.md)

개발 규칙은 [BACKEND_RULES](BACKEND_RULES.md), 인증 계약은 [AUTH](AUTH.md)를 참조합니다.
