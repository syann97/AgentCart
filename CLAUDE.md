# Claude Code 작업 지침

이 파일은 Claude Code의 프로젝트 진입점입니다. 공통 규칙은 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)에 있습니다.

1. 공통 개발 규칙의 `범위와 변경`, `환경과 데이터`를 읽고, 검증·문서·PR 절은 해당 작업에 맞춰 읽습니다. 프로젝트가 낯설거나 범위를 판단할 때 [프로젝트 개요](docs/PROJECT_CONTEXT.md)를 읽습니다.
2. [작업별 읽기 경로](docs/README.md#작업별-읽기-경로)에서 관련 context·rules 절과 필요한 가이드만 선택합니다. 모든 링크를 연쇄적으로 읽지 않습니다.
3. 추천 작업은 변경할 계약의 [현재 동작](docs/RECOMMENDATION_PIPELINE.md)과 [승인된 목표](docs/AGENTIC_RAG_PLAN.md)를 함께 확인합니다. 목표를 구현된 기능으로 가정하지 않습니다.

같은 작업에서 읽은 지침은 재사용하되 파일·영역 변경이나 압축 후 기억이 불확실하면 다시 확인합니다. 승인 범위와 사용자 변경을 보존하고, 문서 작업을 런타임 변경으로 확대하지 않습니다. 비밀값을 노출하지 않고 실제 실행한 검증만 완료로 보고합니다.

`docs/skills`는 도구 중립적인 참조 가이드입니다. Claude Code의 도구나 세션 기능을 사용하는 지시는 이 진입점에서 관리하고, 공통 문서에 특정 코딩 도구 사용을 강제하지 않습니다.

애플리케이션이 사용하는 채팅 모델은 [Backend Context](docs/BACKEND_CONTEXT.md)의 설정 기준을 따릅니다. Claude Code로 개발한다는 사실이 애플리케이션의 LLM을 결정하지 않습니다.
