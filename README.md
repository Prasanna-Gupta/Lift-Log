# RepUp (formerly "Gym Consistency App")

A gym consistency + accountability app for a fixed friend group of 3. Built around social proof and gentle nudges — deliberately not gamified, no XP/points/competition.

## Stack

- **Mobile:** Kotlin Multiplatform (KMP) + Compose Multiplatform, Android-first. iOS target is scaffolded in the project structure but unbuilt/untested.
- **Backend:** Node.js + Fastify, `node-cron` for the nudge scheduler
- **Database/Auth:** Supabase (PostgreSQL), Google OAuth via browser-based flow (not native Credential Manager — that path reliably fails on our test device)
- **Push:** Firebase Cloud Messaging, HTTP v1 API via `firebase-admin`
- **Food data:** USDA FoodData Central API (live), local dataset import parked

## Structure
gym-app/
├── backend/ Fastify API — auth-gated routes, nudge scheduler, USDA proxy
├── supabase/
│ └── schema.sql Full Postgres schema + RLS policies
├── mobile/
│ ├── shared/ KMP shared code — all UI, data layer, business logic
│ │ └── src/
│ │ ├── commonMain/
│ │ │ ├── kotlin/com/asur/gymapp/ Screens, repos, models
│ │ │ └── composeResources/drawable/ Muscle-group SVGs + logo
│ │ └── androidMain/ expect/actual Android implementations
│ └── androidApp/ Android entry point, manifest, launcher icon
└── .github/workflows/ci.yml Backend install + sanity check on push

## Status: core app complete, redesigned, functional

Every primary flow is built, wired to Supabase, and has been through a full visual redesign pass (dark theme, consistent card language, flat surfaces, sentence case).

**Built and working:**
- Auth — Google OAuth, browser flow, session persistence
- Onboarding — weight/height ruler pickers (kg/lb, cm/ft-in toggles), date-of-birth wheel picker, activity level, goal
- Workout logging — persistent editable day-cards, full exercise picker (873-exercise library, muscle/equipment filters), SET/KG/REPS entry, workout templates
- Diet logging — USDA food search, portion picker, calculated daily targets (Mifflin-St Jeor)
- Home feed — grouped by user, 3-day activity window, group "trained today" status row
- Streaks — Postgres-trigger-based, single-miss grace rule, correctly excludes empty day-cards (see below)
- Nudges — FCM push, `node-cron` scheduler on the backend
- Profile — stats, streak heatmap, editable details, feed visibility toggle
- Progress — PRs with estimated 1RM, volume charts (week/month/all-time), calendar-aligned ranges
- Progress photos — private storage, month-grouped grid

### Known limitations
- **iOS is unbuilt.** The KMP structure supports it but it's never been run on a simulator or device.
- **Backend runs locally only.** Nudges only fire while the dev machine is running `npm run dev`. Not yet deployed to Railway/Render.
- **USDA food search hits the live API per request.** Local database import (Foundation Foods + SR Legacy datasets) is parked pending manual download — the sandbox environment can't reach `fdc.nal.usda.gov`.
- **No body measurements feature.** Distinct from progress photos; no schema or screen exists yet.
- **Only one privacy toggle** (`feed_visible`), not granular per-metric controls.
- **Home feed entries aren't tappable** — no detail screen behind a feed card yet.
- **No dedicated nudges settings screen** — cutoff time + on/off toggle live on Profile only.
- **No body-weight trend chart** on Progress, despite `body_weight_logs` having the data.

### Known technical debt
- `saveWorkout`'s update path (delete-then-reinsert sets) isn't wrapped in a transaction — a crash mid-save could leave a day-card with zero sets.
- Several profile/progress queries make 2+ round-trips where a Postgres view could collapse them to one (the "exclude empty day-cards" filter is currently applied client-side in multiple places rather than once, server-side).
- Template count fetching in the "New Day" sheet is N+1 (one query per template).

### Fixed this cycle, worth knowing about
- **Empty day-cards no longer inflate anything.** Creating a `workouts` row used to count as a "workout" for streaks, Profile stats, Progress totals, and the group feed — even with zero sets logged. Streak and feed triggers were moved from firing on `workouts` insert to firing on `workout_sets` insert (first set logged = the real "did a workout" event). Historical bad data was cleaned up; a `streaks.current_streak`/`longest_streak` inconsistency from before the fix was left as-is rather than retroactively recalculated.
- **`kotlinx.datetime.Clock` unresolved under Kotlin 2.4.10.** kotlinx-datetime's `Clock` re-export doesn't resolve `.System` cleanly against newer Kotlin during metadata/iOS compilation (Android compiled fine throughout, which is what made this hard to diagnose). Fix: import `Clock` from `kotlin.time` directly with `@OptIn(ExperimentalTime::class)`, not from `kotlinx.datetime`.
- Various `String.format`/JVM-only calls replaced with KMP-safe equivalents (these silently compile on Android but break iOS).
- Compose Multiplatform's resource loader **cannot decode raw SVG on Android** — the 18 muscle-group illustrations had to be converted to Android Vector Drawable XML, not left as `.svg`, despite living in the same `composeResources/drawable/` folder either way.

## Local setup

### Backend
```bash
cd backend
npm install
cp .env.example .env   # fill in Supabase URL, service role key, USDA API key
npm run dev
```
`/health` should return `{"status":"ok"}`.

### Supabase
Schema lives in `supabase/schema.sql` — paste into the SQL editor on a fresh project. Includes RLS policies, the exercise library seed, and the streak/feed triggers described above.

### Mobile
Open `mobile/` in Android Studio. Point the shared module's Supabase client at your project URL/anon key. For a locally running backend from an Android emulator, use `http://10.0.2.2:3000`; from a physical device on the same network, use your machine's LAN IP (requires `network_security_config.xml` cleartext exception for dev).

## Next up
See the deferred-features list in project notes — body-weight trend chart, photo weight-captions, Home feed tap-through, dedicated nudges screen, and the technical debt items above.
