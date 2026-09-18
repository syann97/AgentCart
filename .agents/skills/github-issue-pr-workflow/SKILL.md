---
name: github-issue-pr-workflow
description: Run repository work through GitHub issues and pull requests with consistent scope, validation, troubleshooting history, review-ready descriptions, merge verification, and issue closure. Use when creating or implementing an issue, preparing or updating a PR, or completing a branch-to-merge workflow; do not invoke for local-only coding that has no issue or PR deliverable.
---

# GitHub Issue and PR Workflow

Use the repository's `AGENTS.md`, development rules, templates, and current implementation as the source of truth. This skill supplies workflow discipline; it does not replace repository-specific instructions or broaden authorization.

## Establish the work item

Inspect open issues, dependencies, current branch, dirty files, and recent commits before choosing work. Prefer an existing ready issue. Create an issue when the user asks for one or when a new reusable workstream needs a durable scope and acceptance criteria.

Write an issue so another contributor can act without the conversation:

- concrete problem and evidence;
- priority and dependencies;
- implementation boundaries and excluded work;
- observable completion criteria;
- proportionate verification, including external or manual measurements when required.

Do not describe target behavior as already implemented. Preserve historical artifacts and user changes.

## Implement and verify

Create a focused branch named for the issue. Keep runtime changes, tests, documentation, and evaluation artifacts aligned with the issue's accepted scope.

Run the checks required by repository rules and the changed behavior. Record the exact commands and distinguish executed, cached, skipped, and externally blocked checks. If completion depends on paid services, credentials, publishing, destructive operations, or another external mutation, prepare everything independent of that action before requesting the necessary approval.

## Maintain a troubleshooting ledger

During the work, retain material problems that changed the implementation or verification. For each one, capture:

- **Symptom:** the observable failure and where it occurred;
- **Cause:** the supported root cause, kept distinct from initial hypotheses;
- **Evidence:** the log, test, source behavior, or reproduction that established the cause;
- **Resolution:** the concrete change and why it addresses the cause;
- **Prevention:** the regression test, validation, documentation, or invariant that prevents recurrence.

Include issues that expose environment compatibility, artifact integrity, hidden assumptions, flaky external behavior, or shortcomings in the original test design. Omit routine command mistakes and abandoned exploration unless they explain a reviewer-relevant tradeoff.

When a discovered problem belongs to a different durable scope, create or propose a follow-up issue instead of silently expanding the current one.

## Prepare the PR

Write the PR for a reviewer without conversation history. Lead with the concrete problem and resulting behavior. Include:

- final implementation and important scope boundaries;
- before/after evidence or measurements when behavior or quality changed;
- actual verification commands and results;
- meaningful limitations or unmeasured areas;
- a **Troubleshooting** section using the ledger entries when any material problem occurred;
- the closing keyword for the issue when merge should close it.

Do not leave important troubleshooting only in chat, commit messages, or an unpublished local report. If it is discovered after PR creation, update the PR body before merge. If the PR is already merged, add a concise retrospective comment and link any durable report.

For multiline GitHub bodies, use a body file so Markdown and newlines remain intact. Remove temporary body files after the external operation.

## Complete and audit

When the user has authorized the full GitHub workflow, push the exact reviewed payload, create or update the PR, inspect mergeability and checks, then merge using the repository's normal strategy. Authorization for one payload or destination does not automatically authorize a later issue's payload; honor approval boundaries surfaced by the environment.

After merge, verify the PR is merged, the issue is closed, the target branch points at the merge commit, the remote work branch is deleted when requested, and the local worktree is clean. Report links, commit IDs, checks actually run, and any remaining limitation.

Before closing the work, ask: could someone revisiting this PR understand both what changed and the non-obvious failures that shaped the final solution? If not, improve the PR record first.
