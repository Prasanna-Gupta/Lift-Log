# Gym Consistency App — Sprint 0

## What's in this repo

```
backend/          Fastify API skeleton — built, installed, smoke-tested
supabase/schema.sql   Full Postgres schema, ready to paste into Supabase's SQL editor
.github/workflows/ci.yml   Basic CI: install + sanity check on every push to backend/
```

## What's done

- Backend boots (`npm run dev` inside `backend/`), `/health` returns `{"status":"ok"}`
- Route stubs for every endpoint in the PRD's API contract (exercises, workouts, diet-logs, feed, streaks, nudges)
- Streak decay logic (`src/routes/streaks.js` → `computeStreakUpdate`) implemented and tested against all 5 cases from the locked rule: normal continuation, single-miss grace, 2-day reset, already-used-grace reset, same-day re-log
- Day-boundary logic (`resolveLogDate` in `workouts.js`) — post-midnight logs attributed to the previous day
- Supabase schema matches Phase 5 exactly, including a Postgres trigger that auto-populates `activity_feed` whenever a workout or diet log is inserted (so the feed route doesn't need manual dual-writes)

## What you need to do manually (can't be done in this sandbox)

### 1. Create the Supabase project
- Go to supabase.com, create a new project
- Open the SQL editor, paste in `supabase/schema.sql`, run it
- Copy your project URL and service role key into `backend/.env` (copy `.env.example` first)

### 2. Run the backend
```
cd backend
npm install
npm run dev
```

### 3. Scaffold the KMP mobile project (needs Android Studio)
This can't be built in this sandbox — no Android SDK or Gradle access to Google's Maven repo here. Fastest path:

1. Install Android Studio (latest stable) if you don't have it
2. Use the official KMP wizard: https://kmp.jetbrains.com/ — generate a project with Android + iOS targets, Compose Multiplatform UI enabled
3. Download the generated project, open it in Android Studio
4. Confirm the Android target builds and runs on an emulator/your phone before touching iOS (per the Android-first sequencing in Phase 9)
5. Point the shared module's network layer at your locally running backend (`http://10.0.2.2:3000` from an Android emulator, or your machine's LAN IP from a real device)

### 4. Push this repo to GitHub
Once pushed, the CI workflow in `.github/workflows/ci.yml` runs automatically on backend changes.

## Next (Sprint 1)
Auth integration (Supabase Auth) + basic Compose navigation shell, per the Phase 9 roadmap.
