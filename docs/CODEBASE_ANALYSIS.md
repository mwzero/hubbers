---
title: Codebase Analysis
---

# Codebase Analysis

This page mirrors the current code review summary for the published documentation site.

## Highest-Priority Findings

### 1. Embedded runtime UI assets are inconsistent

The generated `index.html` under framework resources references asset hashes that do not exist in the copied `assets/` directory.

Impact:

- the runtime web UI should be rebuilt and verified before it is described as production-ready
- this does not block publishing the documentation site from `docs/`

### 2. Repo-path defaults are not unified

Different code paths still assume different default repo roots such as `repo` and `hubbers-repo/src/main/resources/repo`.

Impact:

- examples and library-style entry points can behave differently depending on packaging and working directory

### 3. Legacy docs had drifted from the actual artifact formats

The refreshed documentation corrects the biggest mismatches:

- agents are documented as sectioned Markdown with JSON blocks
- tools are documented around `tool.yaml` plus `type` dispatch
- skills are documented as sectioned Markdown rather than YAML frontmatter

## Current Assessment

Hubbers has a solid runtime shape:

- clean module boundaries
- a single execution facade
- executor mediation through `ExecutorRegistry`
- first-class support for skills, forms, and execution history

The current release risk is mostly around packaging consistency, not around the basic runtime architecture.

## Recommendation

Publish the documentation site now from `docs/`. Treat frontend runtime packaging and repo-path normalization as follow-up engineering work.

## Related Docs

- [Project Overview](OVERVIEW.md)
- [Software Architecture](SWA.md)
- [GitHub Pages Publishing](GITHUB_PAGES.md)
