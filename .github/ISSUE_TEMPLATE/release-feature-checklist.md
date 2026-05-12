---
name: Release / Feature Checklist
about: Track release readiness or a larger feature milestone for XL Logic.
title: "[Checklist] "
---

## Scope

- [ ] Define the feature, milestone, or release goal in one or two sentences.
- [ ] List the affected subsystems: runtime, networking, screens, builder, bridge, crafting, docs, or assets.

## Implementation

- [ ] Core behavior is implemented.
- [ ] Edge cases or fallback behavior are handled.
- [ ] Multiplayer impact has been reviewed where relevant.
- [ ] Bridge policy and remote write behavior were reviewed if XLAPI or bridged devices were touched.

## Validation

- [ ] `gradlew.bat test`
- [ ] `gradlew.bat runGameTestServer`
- [ ] `gradlew.bat runClient` smoke test if the change affects gameplay, UI, rendering, or assets.
- [ ] New logic is covered by JUnit or GameTests where it makes sense.

## Documentation

- [ ] `README.md` reflects the current feature state.
- [ ] Website pages under `docs/site/` were updated if user-facing behavior changed.
- [ ] Block reference pages under `docs/site/blocks/` were updated if a block API or interaction changed.
- [ ] Builder docs were updated if new no-code blocks, templates, or generated behavior changed.

## Release notes

- [ ] Summarize the visible user impact.
- [ ] Note known limitations or follow-up work.
- [ ] Mention any compatibility or migration details if older saves, routes, or APIs are affected.

## Final check

- [ ] Working tree is clean.
- [ ] Branch is pushed.
- [ ] The issue can be closed or used as the basis for release notes.