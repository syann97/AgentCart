# 로컬 실행과 데이터 준비

이 문서는 현재 저장소를 로컬에서 실행하기 위한 설정과 데이터 경로를 설명합니다. 비밀 값과 개인별 설정 파일은 Git에 포함하지 않습니다.

## 준비 사항

- JDK 25. Gradle 9.4.1 wrapper는 [wrapper 설정](../backend/AgentCart/gradle/wrapper/gradle-wrapper.properties)에 포함되어 있습니다.
- Docker Compose. MySQL·Redis·PostgreSQL/pgvector·Kafka·Ollama를 [Compose](../docker-compose.yml)로 실행합니다.
- Node.js와 npm. 프로젝트가 Node 버전을 고정하지 않으므로 사용하는 버전에서 `npm test`와 `npm run build`로 호환성을 확인합니다.
- 합성 상품을 API로 적재할 때만 Python과 `requests`가 필요합니다.

## Backend 로컬 설정

공통 설정은 [application.yaml](../backend/AgentCart/src/main/resources/application.yaml)에 있고 기본 활성 profile은 `local`입니다. 아래 개인 파일은 `.gitignore` 대상입니다.

- `backend/AgentCart/.env`: `bootRun` 작업이 읽는 환경 변수. `JWT_SECRET`, `OPENAI_API_KEY`를 관리하며 현재 로컬 profile의 Anthropic 항목을 유지하면 `ANTHROPIC_API_KEY`도 필요합니다. 이 자동 로드는 `bootRun`에만 정의되어 있어 IDE 실행이나 다른 Gradle task에 그대로 적용된다고 가정하지 않습니다.
- `backend/AgentCart/src/main/resources/application-local.yaml`: CORS, cookie, MySQL, pgvector, Redis, OpenAI chat, Ollama embedding 연결 정보.

로컬 설정의 구조는 다음과 같습니다. 비밀 값은 환경 변수로 둡니다.

```yaml
cors:
  allowed-origins: http://localhost:3000

cookie:
  secure: false
  same-site: Lax

spring:
  autoconfigure:
    exclude:
      - org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration
  datasource:
    url: jdbc:mysql://localhost:3307/agentcart?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    pgvector:
      url: jdbc:postgresql://localhost:5432/agentcart_vector
      username: user
      password: password
      driver-class-name: org.postgresql.Driver
  data:
    redis:
      host: localhost
      port: 6379
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        enabled: false
    ollama:
      base-url: http://localhost:11434
      embedding:
        options:
          model: bge-m3
```

OpenAI·Anthropic·Ollama starter가 모두 빌드에 선언되어 있지만, 현재 추천 서비스는 `openAiChatModel`과 `ollamaEmbeddingModel`을 명시적으로 사용합니다. starter 목록을 실제 모델 경로로 해석하지 않습니다.

## 실행 순서

저장소 루트에서 인프라를 시작하고 Ollama 모델을 준비합니다.

```powershell
docker compose up -d
docker compose exec ollama ollama pull bge-m3
```

Backend는 `backend/AgentCart`에서 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Frontend는 `frontend`에서 실행합니다.

```powershell
npm install
npm run dev
```

Frontend 예시 환경 파일은 [.env.local.example](../frontend/.env.local.example)입니다. `NEXT_PUBLIC_API_BASE_URL`의 코드 기본값도 `http://localhost:8080`입니다. 예시의 `NEXT_PUBLIC_APP_ENV`는 현재 애플리케이션 코드에서 읽지 않습니다.

## 합성 상품과 임베딩

[scripts/data](../scripts/data/)의 JSON 8개에는 합계 500개 항목이 있습니다. 이 수는 저장소 파일 수이며 DB에 자동 적재되거나 모두 성공했다는 뜻은 아닙니다.

[seed_products_from_json.py](../scripts/seed_products_from_json.py)는 JSON 파일 하나를 읽어 관리자 권한의 `POST /api/products`를 호출합니다. 스크립트에 정의된 계정이 먼저 존재하고 `ADMIN` 역할을 가져야 하며, 중복 상품은 실패할 수 있습니다. 상품 생성·수정 때 [ProductEmbeddingService](../backend/AgentCart/src/main/java/com/agentcart/product/service/ProductEmbeddingService.java)가 Ollama `bge-m3` 임베딩을 생성합니다. Ollama나 pgvector가 없거나 생성이 실패해도 상품 저장은 남고 임베딩은 누락될 수 있으므로 두 저장소의 개수를 별도로 확인합니다.

기존 상품의 현재 임베딩을 다시 만들 때는 관리자 인증으로 `POST /api/products/reembed`를 사용합니다. 응답 count는 처리 대상으로 순회한 상품 수이며 성공한 embedding 행 수를 보장하지 않으므로 pgvector를 별도로 확인합니다. PostgreSQL migration V4는 벡터 열을 1024차원으로 바꾸면서 기존 임베딩을 비웁니다.

`scripts/generate_embeddings.py`는 이전 OpenAI `text-embedding-3-small` 1536차원 배치 경로입니다. 현재 1024차원 `bge-m3` 스키마·모델과 맞지 않으므로 현재 재생성 절차로 사용하지 않습니다. 이 차이는 후속 스크립트 정비 전까지 유지되는 알려진 한계입니다.

## 검증 명령

Backend의 Mockito 추천 단위 테스트:

```powershell
cd backend/AgentCart
.\gradlew.bat test --tests "com.agentcart.recommendation.unit.*"
```

전체 Backend 테스트에는 Docker가 필요한 Testcontainers 테스트가 포함됩니다. Frontend 검증은 다음 명령을 사용합니다.

```powershell
cd frontend
npm test
npm run lint
npm run build
```

실제 LLM 호출, 데이터 적재, 임베딩 재생성은 비용과 외부 상태가 생기는 별도 작업입니다. 문서 또는 mock 기반 검증을 수행했다고 이 작업들까지 실행된 것으로 보고하지 않습니다.
