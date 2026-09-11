# Claude Code 작업 지침

이 파일은 Claude Code의 프로젝트 진입점입니다. 공통 규칙은 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)에 있습니다.

1. [프로젝트 개요](docs/PROJECT_CONTEXT.md)와 공통 개발 규칙을 읽습니다.
2. 작업 영역의 context·rules와 [필요한 작업 가이드](docs/skills/README.md)를 선택합니다.
3. 추천 작업은 [현재 동작](docs/RECOMMENDATION_PIPELINE.md)과 [승인된 목표](docs/AGENTIC_RAG_PLAN.md)를 함께 확인합니다.

`docs/skills`는 도구 중립적인 참조 가이드입니다. Claude Code의 도구나 세션 기능을 사용하는 지시는 이 진입점에서 관리하고, 공통 문서에 특정 코딩 도구 사용을 강제하지 않습니다.

애플리케이션이 사용하는 채팅 모델은 [Backend Context](docs/BACKEND_CONTEXT.md)의 설정 기준을 따릅니다. Claude Code로 개발한다는 사실이 애플리케이션의 LLM을 결정하지 않습니다.
