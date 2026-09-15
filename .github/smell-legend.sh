#!/usr/bin/env bash
# Prints a short explainer for each structural smell as GitHub-flavoured markdown.
# The habits jobs pipe this into $GITHUB_STEP_SUMMARY on failure, so a red smell
# check comes with a legend instead of just a wall of findings.
set -eu
cat <<'MD'
## 📖 Structural smells — what each one means

Each smell is a machine-checkable shadow of a function or file doing **more than
one thing**. Fix toward the missing abstraction (a value object, a strategy, a
named step) — never by splitting to a line count.

| Smell | What it's telling you | Fix toward |
|---|---|---|
| `oversized-function` | too long to hold one idea | extract a named step / collaborator |
| `oversized-file` | the file carries too many responsibilities | split by concern into cohesive units |
| `high-complexity` | too many branches = too many decisions in one place | replace conditionals with polymorphism/strategy; lift guard clauses |
| `too-many-parameters` | the function juggles too many collaborators | introduce a parameter object, or split the responsibility |
| `deep-nesting` | a nested block wants to be its own named function | extract it; use early returns |
| `duplication` | the same logic lives in two places | extract one shared function |
| `dead-code` / unused export | nothing references it | delete it |

The specific findings (file + line) are in the **Smells** step log above. Full
rationale: foundry `presets/code-standards.md`. Clearing a smell is necessary, not
sufficient — "is this *one* thing?" is still your call.
MD
