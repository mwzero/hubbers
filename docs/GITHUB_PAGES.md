---
title: GitHub Pages Publishing
---

# GitHub Pages Publishing

This repository is now documented to publish a documentation site from `docs/`.

## What GitHub Pages Should Publish

GitHub Pages should publish the documentation set, not the runtime web UI.

Reason:

- the docs in `docs/` are static Markdown and suitable for Pages
- the runtime UI served by `hubbers web` is API-backed and currently uses absolute asset paths that are not yet prepared for Pages deployment

## Files Added For Pages

- `docs/index.md`: landing page
- `docs/_config.yml`: Jekyll configuration

## Recommended Repository Setting

In GitHub:

1. Open repository settings.
2. Go to `Pages`.
3. Set source to `Deploy from a branch`.
4. Choose the main branch and `/docs` folder.

GitHub Pages will then publish the site directly from the documentation folder.

## What The Published Site Includes

- overview and onboarding
- architecture documentation
- artifact format guides
- build notes
- codebase analysis

## What It Does Not Include

- a working static export of the Hubbers runtime UI
- a browser-only version of the API-backed web server

If you want to publish a static frontend later, treat that as a separate deliverable and first fix:

- Vite `base` configuration for repository subpaths
- absolute `/assets/...` references in the generated frontend output
- separation between static UI hosting and runtime API hosting

## Suggested Publish Workflow

For the current repo, the simplest publication workflow is:

1. Update Markdown docs under `docs/`.
2. Keep `README.md` aligned with the docs landing page.
3. Publish `docs/` through GitHub Pages.
4. Treat the runtime web UI as a separate application served by `hubbers web`.

## Related Docs

- [Documentation Home](index.md)
- [Codebase Analysis](CODEBASE_ANALYSIS.md)
