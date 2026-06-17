# Frontend Skill Usage Guide

Future Codex frontend prompts should explicitly mention the relevant skill or skills. This keeps implementation chats
aligned with the installed guidance.

## Installed Skills For Frontend Work

Installed frontend-related skills:

- `frontend-design`
- `building-native-ui`
- `native-data-fetching`
- `vercel-react-native-skills`
- `web-design-guidelines`
- `webapp-testing`
- `accessibility`

The installed skill files live under `.agents/skills/`. They are local reference guidance for Codex/agents, not
frontend app source code, not package dependencies, and not runtime files. Future implementation prompts should
explicitly mention the relevant skill names at the top of the prompt. Codex may read the relevant
`.agents/skills/<skill>/SKILL.md` and reference files, but normal frontend implementation must not modify `.agents/`
or `skills-lock.json`.

Historical/reference install commands only, for rebuilding a local agent environment if needed. Do not run these during
normal frontend implementation tasks:

```powershell
npx.cmd skills add https://github.com/anthropics/skills --skill frontend-design
npx.cmd skills add https://github.com/expo/skills --skill building-native-ui
npx.cmd skills add https://github.com/expo/skills --skill native-data-fetching
npx.cmd skills add https://github.com/vercel-labs/agent-skills --skill vercel-react-native-skills
npx.cmd skills add https://github.com/vercel-labs/agent-skills --skill web-design-guidelines
npx.cmd skills add https://github.com/anthropics/skills --skill webapp-testing
npx.cmd skills add addyosmani/web-quality-skills@accessibility
```

`.agents/` and `skills-lock.json` are local agent tooling files. Do not include them in the normal StockMentor docs
commit unless intentionally versioning skills separately.

### `frontend-design`

Use for:

- theme direction
- visual design
- screen UX
- design system
- copywriting
- empty state wording
- error state wording
- educational AI wording

Best prompts:

- "Use `frontend-design` to refine the StockMentor mobile dashboard design."
- "Use `frontend-design` to review empty and error state wording."

### `building-native-ui`

Use for:

- Expo Router route groups
- mobile/native UI structure
- tabs and stack layouts
- route file structure
- responsive native layout
- admin web/tablet layout decisions inside Expo
- safe area and scroll behavior

Relevant references:

- `.agents/skills/building-native-ui/references/route-structure.md`
- `.agents/skills/building-native-ui/references/storage.md`

Best prompts:

- "Use `building-native-ui` to implement the Expo Router route groups."
- "Use `building-native-ui` to build the beginner tab layout."

### `native-data-fetching`

Use for:

- API client design
- fetch wrappers
- React Query setup
- Basic Auth request headers
- admin token handling
- environment variables
- loading and error state
- retries
- cancellation
- network debugging

Relevant reference:

- `.agents/skills/native-data-fetching/references/expo-router-loaders.md`

Best prompts:

- "Use `native-data-fetching` to implement the StockMentor API client."
- "Use `native-data-fetching` to add React Query hooks for stock browsing."
- "Use `native-data-fetching` to debug Expo Web admin network failures."

### `vercel-react-native-skills`

Use for:

- React Native and Expo implementation patterns
- list and scroll performance
- animation and rendering performance
- reusable mobile component implementation
- performance review for stock lists, suggestion lists, portfolio positions, and admin tables

Best prompts:

- "Use `vercel-react-native-skills` to review the stock list implementation for React Native performance."
- "Use `vercel-react-native-skills` while implementing the paper-trading portfolio list."

### `web-design-guidelines`

Use for:

- admin web/tablet UI review
- browser-friendly table/filter/detail layouts
- web UI consistency checks
- final admin console design audit

Best prompts:

- "Use `web-design-guidelines` to review the admin users table and detail page."
- "Use `web-design-guidelines` during the final admin web polish pass."

### `webapp-testing`

Use for:

- Expo Web browser testing
- admin console Playwright checks
- screenshot-based UI verification
- browser console and network debugging

Best prompts:

- "Use `webapp-testing` to verify the Expo Web admin login and users table."
- "Use `webapp-testing` to run the final frontend demo flow in the browser."

### `accessibility`

Use for:

- WCAG/HCI checks
- keyboard navigation
- focus order and visible focus
- touch target and contrast review
- form labels, error messaging, and modal escape behavior

Best prompts:

- "Use `accessibility` to audit the login, onboarding, and admin token forms."
- "Use `accessibility` during the final frontend polish phase."

## Reference Usage Rules

- Use route/storage reference docs when changing route structure or auth storage.
- Use Expo Router loaders reference only when considering web route loaders. For this MVP, prefer shared client-side
  fetching because the native app needs the same data flow.
- Use Context7 for current library documentation when a prompt asks about Expo, React Native, React Query, or other
  library/API details.

## Prompt Rule For Future Chats

Future frontend implementation prompts should say which skill to use. Example:

```text
Use `building-native-ui` and `native-data-fetching`.
Implement the beginner stock list screen from docs/frontend/backend-api-screen-map.md.
```

If a prompt forgets to name a relevant installed skill, Codex should still use the skill when the task clearly matches
it.
