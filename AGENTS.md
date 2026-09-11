# Codex 작업 지침

이 저장소는 Agentic RAG 상품 추천을 학습하는 개인 프로젝트입니다.

- 먼저 [공통 개발 규칙](docs/DEVELOPMENT.md)과 [프로젝트 개요](docs/PROJECT_CONTEXT.md)를 읽습니다.
- 추천 작업은 [현재 파이프라인](docs/RECOMMENDATION_PIPELINE.md)과 [승인된 목표 설계](docs/AGENTIC_RAG_PLAN.md)를 구분합니다. 목표 기능을 이미 구현된 것으로 가정하지 않습니다.
- 작업 영역에 해당하는 context·rules와 [작업 가이드](docs/skills/README.md)만 추가로 읽습니다.
- `docs/skills/**/*.md` 아래의 가이드는 참조 문서입니다. Codex가 자동 등록한 네이티브 스킬이라고 가정하지 않습니다.
- 사용자에게 승인된 범위 안에서 필요한 변경과 검증을 완료합니다. 현재 작업 범위가 문서이면 런타임 코드 변경으로 확장하지 않습니다.
- 동작 변경 시 담당 문서와 관련 테스트를 함께 갱신합니다. 문서만 변경했다면 링크·경로·현재/목표 구분과 `git diff --check`를 검증합니다.
- 실행하지 않은 테스트나 아직 구현하지 않은 기능을 완료로 보고하지 않습니다.

Codex의 지침 발견 방식은 [OpenAI 공식 AGENTS.md 문서](https://learn.chatgpt.com/docs/agent-configuration/agents-md)를 참조합니다. 도메인 규칙은 이 파일에 복제하지 않습니다.
