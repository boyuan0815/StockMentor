# Frontend Skill Usage Guide

Future Codex frontend prompts should explicitly mention the relevant skill or skills. This keeps implementation chats aligned with the installed guidance.

## Installed Skills For Frontend Documentation And Planning

Approved installed skills:

- `frontend-design`
- `building-native-ui`
- `native-data-fetching`

Future reference install commands only:

```powershell
npx.cmd skills add https://github.com/anthropics/skills --skill frontend-design
npx.cmd skills add https://github.com/expo/skills --skill building-native-ui
npx.cmd skills add https://github.com/expo/skills --skill native-data-fetching
```

`.agents/` and `skills-lock.json` are local agent tooling files. Do not include them in the normal StockMentor docs commit unless intentionally versioning skills separately.

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

## Deferred Skills

Do not use these until the relevant phase:

- `vercel-react-native-skills`: frontend implementation and performance patterns.
- `web-design-guidelines`: admin web/tablet UI review.
- `webapp-testing`: Expo Web/admin browser testing.
- `accessibility`: accessibility review phase.

## Reference Usage Rules

- Use route/storage reference docs when changing route structure or auth storage.
- Use Expo Router loaders reference only when considering web route loaders. For this MVP, prefer shared client-side fetching because the native app needs the same data flow.
- Use Context7 for current library documentation when a prompt asks about Expo, React Native, React Query, or other library/API details.

## Prompt Rule For Future Chats

Future frontend implementation prompts should say which skill to use. Example:

```text
Use `building-native-ui` and `native-data-fetching`.
Implement the beginner stock list screen from docs/frontend/backend-api-screen-map.md.
```

If a prompt forgets to name a relevant installed skill, Codex should still use the skill when the task clearly matches it.
