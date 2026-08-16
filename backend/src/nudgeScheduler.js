import cron from 'node-cron';
import { supabase } from './supabase.js';
import { sendPushNotification } from './firebase.js';

function nowTimeString() {
  const now = new Date();
  return now.toLocaleTimeString('en-GB', { timeZone: 'Asia/Kolkata', hour12: false });
}

function todayDateString() {
  const now = new Date();
  if (now.getHours() < 4) {
    now.setDate(now.getDate() - 1);
  }
  return now.toISOString().slice(0, 10);
}

const { data: allUsers, error: allUsersError } = await supabase.from('users').select('id, name');
console.log(`[nudge check] DEBUG: raw unfiltered user count = ${allUsers?.length ?? 'ERROR'}, error = ${allUsersError?.message}`);
async function checkAndSendNudges() {
  const currentTime = nowTimeString();
  console.log(`[nudge check] currentTime string = "${currentTime}"`);
  const today = todayDateString();
  console.log(`[nudge check] Running at IST ${currentTime}, date=${today}`);

  const { data: users, error: usersError } = await supabase
    .from('users')
    .select('id, name, nudge_cutoff_time, nudge_enabled')
    .eq('nudge_enabled', true)
    .lte('nudge_cutoff_time', currentTime);

  if (usersError) {
    console.error('[nudge check] Failed to fetch users:', usersError.message);
    return;
  }

  console.log(`[nudge check] ${users?.length ?? 0} user(s) past cutoff:`, users?.map(u => `${u.name} (${u.nudge_cutoff_time})`));

  for (const user of users || []) {
    const { data: workoutToday, error: workoutError } = await supabase
      .from('workouts')
      .select('id')
      .eq('user_id', user.id)
      .eq('date', today)
      .limit(1);

    if (workoutError) console.error(`[nudge check] workout query error for ${user.name}:`, workoutError.message);
    if (workoutToday && workoutToday.length > 0) {
      console.log(`[nudge check] ${user.name} already logged today — skipping`);
      continue;
    }

    const { data: alreadyNudged, error: nudgedError } = await supabase
      .from('nudges')
      .select('id')
      .eq('to_user_id', user.id)
      .gte('created_at', `${today}T00:00:00Z`)
      .limit(1);

    if (nudgedError) console.error(`[nudge check] nudges query error for ${user.name}:`, nudgedError.message);
    if (alreadyNudged && alreadyNudged.length > 0) {
      console.log(`[nudge check] ${user.name} already nudged today — skipping`);
      continue;
    }

    const { data: tokens, error: tokensError } = await supabase
      .from('device_tokens')
      .select('fcm_token')
      .eq('user_id', user.id);

    if (tokensError) console.error(`[nudge check] tokens query error for ${user.name}:`, tokensError.message);
    if (!tokens || tokens.length === 0) {
      console.log(`[nudge check] ${user.name} has no registered device — skipping`);
      continue;
    }

    const message = "Haven't seen you log a workout today — everything okay?";
    let anySent = false;
    for (const { fcm_token } of tokens) {
      console.log(`[nudge check] Sending push to ${user.name}, token ${fcm_token.slice(0, 12)}...`);
      const sent = await sendPushNotification(fcm_token, 'Gym App', message);
      console.log(`[nudge check] Send result: ${sent}`);
      if (sent) anySent = true;
    }

    if (anySent) {
      await supabase.from('nudges').insert({ to_user_id: user.id, message });
      console.log(`[nudge check] Recorded nudge for ${user.name}`);
    }
  }
}

export function startNudgeScheduler() {
  cron.schedule('*/15 * * * *', checkAndSendNudges);
  console.log('Nudge scheduler started (checks every 15 minutes)');
}