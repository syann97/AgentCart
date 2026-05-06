# CLAUDE.md

You must follow project context strictly.

Load:
@docs/PROJECT_CONTEXT.md
@docs/AGENT_CONTEXT.md
@docs/BACKEND_CONTEXT.md
@docs/FRONTEND_CONTEXT.md

Do not assume missing business rules.
Ask if unclear.

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

## 5. Project-Specific Rules (AgentCart)

### 5.1 Agent Pipeline

- Pipeline order must be:
  Planner → Executor → Evaluator
- Do not change execution order

---

### 5.2 LLM Usage

- LLM is optional and must not be required for system operation
- Always implement fallback logic
- Timeout must be 5 seconds

---

### 5.3 Evaluator

Validation order:

1. DbValidator
2. ConsistencyValidator
3. RuleFilterValidator
4. LlmCrossValidator

Rules:
- Step 1~3 failure → discard result
- Step 4 failure → log only, do not throw exception

---

### 5.4 Hybrid Search

- SQL filtering must run before vector search
- score = 0.4 * BM25 + 0.6 * Vector
- Normalize scores to 0~1

---

### 5.5 Kafka

- Always implement both Producer and Consumer
- Include eventId (UUID v4)
- Ensure idempotency in consumer

---

### 5.6 Redis

- TTL must be 5 minutes
- Use Redisson lock (timeout 5 seconds)
- Lock failure → throw exception and retry

---

### 5.7 Recommendation Result

Must include:
- reason
- conditions
- score

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
