# Manga Vision AI & Reader

A web-based manga/manhwa/comic reader with AI-assisted reading (OCR, TTS, translation).

> **Status: Phase 2 — Core Manga Reader.**
> The responsive shell, routing, design tokens, component library, tooling, tests, and
> docs are in place, and the core reader (single/vertical modes, zoom, fit, keyboard and
> fullscreen controls) runs at `/reader` with demo content.
> Import, OCR, TTS, library, sources, and all other application features are
> **not implemented yet** — see [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Quick start

```bash
npm install
npm run dev
```

Then open the printed local URL (default `http://localhost:5173`).

## Commands

| Command             | Purpose                       |
| ------------------- | ----------------------------- |
| `npm run dev`       | Dev server with HMR           |
| `npm run build`     | Type-check + production build |
| `npm run preview`   | Serve the production build    |
| `npm run typecheck` | TypeScript check              |
| `npm run lint`      | ESLint                        |
| `npm run test`      | Vitest (watch)                |
| `npm run test:run`  | Vitest (single run)           |
| `npm run format`    | Format with Prettier          |

Requirements: Node.js 20+ (verified on 24.x), npm 10+.

## Project structure

```
src/
├── app/           App composition: App, routes, navigation manifest
├── components/
│   ├── ui/        Primitives (Button, Input, Card, Badge, Tabs, Dropdown,
│   │              Modal, Toast, state views) + inline icons
│   └── layout/    Shell (AppShell, AppHeader, DesktopSidebar,
│                  MobileNavigation, PageContainer)
├── features/
│   └── reader/     Core manga reader (types, utils, hooks, components, demo)
├── pages/         Route-level views (HomePage gallery, ReaderDemoPage)
├── config/        Typed environment/app configuration
├── styles/        global.css imports: tokens, base, shell, components,
│                  overlays, reader
├── types/         Ambient type declarations
├── utils/         Pure helper functions
└── main.tsx       Entry point
tests/             Vitest suites + setup
docs/              Architecture, development, roadmap, security, decisions,
                   design system, reader
public/            Static assets (incl. reader demo fixtures)
```

## Documentation

| Document                                       | Contents                                |
| ---------------------------------------------- | --------------------------------------- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)   | Architecture, layers, future boundaries |
| [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) | Tokens, shell, components, a11y rules   |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)     | Setup, commands, Git workflow           |
| [docs/READER.md](docs/READER.md)               | Core reader reference                   |
| [docs/ROADMAP.md](docs/ROADMAP.md)             | Phase plan (0 → 27)                     |
| [docs/SECURITY.md](docs/SECURITY.md)           | Security principles and binding rules   |
| [docs/DECISIONS.md](docs/DECISIONS.md)         | Architectural decision records          |

## Configuration

Copy `.env.example` to `.env` if you need to override defaults. Only `VITE_`-prefixed
variables are exposed to the browser. **Never put real API keys in the repository** —
see [`docs/SECURITY.md`](docs/SECURITY.md).

## License

TBD.
# android-extension
