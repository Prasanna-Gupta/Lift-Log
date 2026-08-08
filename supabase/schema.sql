-- Gym Consistency App — schema per PRD Phase 5
-- Run this in the Supabase SQL editor on a fresh project.

create table users (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  email text unique not null,
  created_at timestamptz not null default now()
);

create table exercises (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  muscle_group text,
  equipment_type text
);

create table workouts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  date date not null,
  notes text,
  photo_url text,
  created_at timestamptz not null default now()
);

create table workout_sets (
  id uuid primary key default gen_random_uuid(),
  workout_id uuid not null references workouts(id) on delete cascade,
  exercise_id uuid not null references exercises(id),
  set_number int not null,
  weight numeric not null,
  reps int not null
);

create table diet_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  date date not null,
  meal_label text,
  calories int not null,
  protein_g numeric not null,
  created_at timestamptz not null default now()
);

-- warning_used tracks whether the single-missed-day grace has been consumed
-- for the current cycle (PRD §13 streak decay rule).
create table streaks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references users(id) on delete cascade,
  current_streak int not null default 0,
  longest_streak int not null default 0,
  last_logged_date date,
  warning_used boolean not null default false
);

-- Extensibility layer (Phase 5): new activity types plug in here without
-- altering this table's shape — just add a new activity_type + referenced table.
create table activity_feed (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  activity_type text not null check (activity_type in ('workout', 'diet')),
  reference_id uuid not null,
  created_at timestamptz not null default now()
);

create table nudges (
  id uuid primary key default gen_random_uuid(),
  from_user_id uuid references users(id),
  to_user_id uuid not null references users(id) on delete cascade,
  message text not null,
  created_at timestamptz not null default now(),
  seen boolean not null default false
);

-- Schema-ready for v1.1 reactions (not built into backend routes yet).
create table reactions (
  id uuid primary key default gen_random_uuid(),
  activity_feed_id uuid not null references activity_feed(id) on delete cascade,
  user_id uuid not null references users(id) on delete cascade,
  reaction_type text not null
);

-- Keep the feed in sync automatically whenever a workout or diet log is created.
create or replace function log_to_feed() returns trigger as $$
begin
  insert into activity_feed (user_id, activity_type, reference_id)
  values (new.user_id, tg_argv[0], new.id);
  return new;
end;
$$ language plpgsql;

create trigger workout_feed_trigger
  after insert on workouts
  for each row execute function log_to_feed('workout');

create trigger diet_feed_trigger
  after insert on diet_logs
  for each row execute function log_to_feed('diet');
