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

function nowInIST() {
  return new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Kolkata' }));
}

// Sends a push and, if the token is confirmed dead, removes it from device_tokens
// so we stop wasting sends on it and stop cluttering the table.
async function sendAndCleanup(fcmToken, title, body) {
  const result = await sendPushNotification(fcmToken, title, body);
  if (result.staleToken) {
    await supabase.from('device_tokens').delete().eq('fcm_token', result.staleToken);
    console.log(`[cleanup] Removed stale token ${result.staleToken.slice(0, 12)}...`);
  }
  return result.success;
}

async function checkAndSendNudges() {
  const currentTime = nowTimeString();
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

  for (const user of users || []) {
    const { data: workoutToday } = await supabase
      .from('workouts')
      .select('id')
      .eq('user_id', user.id)
      .eq('date', today)
      .limit(1);

    if (workoutToday && workoutToday.length > 0) continue;

    const { data: alreadyNudged } = await supabase
      .from('nudges')
      .select('id')
      .eq('to_user_id', user.id)
      .gte('created_at', `${today}T00:00:00Z`)
      .limit(1);

    if (alreadyNudged && alreadyNudged.length > 0) continue;

    const { data: tokens } = await supabase
      .from('device_tokens')
      .select('fcm_token')
      .eq('user_id', user.id);

    if (!tokens || tokens.length === 0) continue;

    const message = "Haven't seen you log a workout today — everything okay?";
    let anySent = false;
    for (const { fcm_token } of tokens) {
      const sent = await sendAndCleanup(fcm_token, 'RepUp', message);
      if (sent) anySent = true;
    }

    if (anySent) {
      await supabase.from('nudges').insert({ to_user_id: user.id, message });
      console.log(`[nudge check] Recorded nudge for ${user.name}`);
    }
  }
}

async function checkStreakEvents() {
  const { data: streaks, error } = await supabase
    .from('streaks')
    .select('user_id, current_streak, last_notified_streak');

  if (error) {
    console.error('[streak check] Failed to fetch streaks:', error.message);
    return;
  }

  const MILESTONES = new Set([7, 14, 30, 50, 100, 200, 365]);

  for (const s of streaks || []) {
    if (s.current_streak === s.last_notified_streak) continue;

    const { data: userRows } = await supabase
      .from('users')
      .select('id, name')
      .eq('id', s.user_id)
      .limit(1);
    const user = userRows?.[0];
    if (!user) continue;

    const { data: tokens } = await supabase
      .from('device_tokens')
      .select('fcm_token')
      .eq('user_id', s.user_id);

    if (!tokens || tokens.length === 0) {
      await supabase.from('streaks').update({ last_notified_streak: s.current_streak }).eq('user_id', s.user_id);
      continue;
    }

    let message = null;
    if (s.current_streak === 1 && s.last_notified_streak > 1) {
      message = "Welcome back! Your streak restarted — let's build it back up 💪";
    } else if (MILESTONES.has(s.current_streak)) {
      message = `🎉 ${s.current_streak}-day streak! Keep it going.`;
    }

    if (message) {
      console.log(`[streak check] Notifying ${user.name}: ${message}`);
      for (const { fcm_token } of tokens) {
        await sendAndCleanup(fcm_token, 'RepUp', message);
      }
    }

    await supabase.from('streaks').update({ last_notified_streak: s.current_streak }).eq('user_id', s.user_id);
  }
}

// Notifies everyone EXCEPT the person who logged, about new activity_feed entries
// since the last check. Respects feed_visible — if you've hidden your activity,
// friends don't get notified about it either.
async function checkFriendActivity() {
  const { data: unnotified, error } = await supabase
    .from('activity_feed')
    .select('id, user_id, activity_type, reference_id')
    .eq('friends_notified', false)
    .limit(50);

  if (error) {
    console.error('[friend activity] Failed to fetch activity_feed:', error.message);
    return;
  }
  if (!unnotified || unnotified.length === 0) return;

  const { data: allUsers } = await supabase.from('users').select('id, name, feed_visible');
  const usersById = new Map((allUsers || []).map(u => [u.id, u]));

  for (const item of unnotified) {
    const actor = usersById.get(item.user_id);
    if (!actor || actor.feed_visible === false) {
      await supabase.from('activity_feed').update({ friends_notified: true }).eq('id', item.id);
      continue;
    }

    let detail = 'logged activity';
    if (item.activity_type === 'workout') {
      const { data: w } = await supabase.from('workouts').select('title').eq('id', item.reference_id).limit(1);
      detail = `logged a workout: ${w?.[0]?.title ?? 'Workout'}`;
    } else if (item.activity_type === 'diet') {
      const { data: d } = await supabase.from('diet_logs').select('meal_label').eq('id', item.reference_id).limit(1);
      detail = `logged food: ${d?.[0]?.meal_label ?? 'Entry'}`;
    }

    const others = (allUsers || []).filter(u => u.id !== item.user_id);
    for (const friend of others) {
      const { data: tokens } = await supabase
        .from('device_tokens')
        .select('fcm_token')
        .eq('user_id', friend.id);
      if (!tokens || tokens.length === 0) continue;

      const message = `${actor.name} ${detail}`;
      console.log(`[friend activity] Notifying ${friend.name}: ${message}`);
      for (const { fcm_token } of tokens) {
        await sendAndCleanup(fcm_token, 'RepUp', message);
      }
    }

    await supabase.from('activity_feed').update({ friends_notified: true }).eq('id', item.id);
  }
}

// Sends a weekly workout-count summary on Sunday evening (IST), once per week per user.
async function checkWeeklySummary() {
  const ist = nowInIST();
  const isSunday = ist.getDay() === 0;
  const isEveningWindow = ist.getHours() === 18; // fires once, any tick within the 18:00 hour
  if (!isSunday || !isEveningWindow) return;

  const todayStr = todayDateString();
  const weekAgo = new Date(ist);
  weekAgo.setDate(weekAgo.getDate() - 7);
  const weekAgoStr = weekAgo.toISOString().slice(0, 10);

  const { data: users, error } = await supabase
    .from('users')
    .select('id, name, last_weekly_summary_date');

  if (error) {
    console.error('[weekly summary] Failed to fetch users:', error.message);
    return;
  }

  for (const user of users || []) {
    if (user.last_weekly_summary_date === todayStr) continue; // already sent this week

    const { data: workouts } = await supabase
      .from('workouts')
      .select('id')
      .eq('user_id', user.id)
      .gte('date', weekAgoStr);

    const count = workouts?.length ?? 0;

    const { data: tokens } = await supabase
      .from('device_tokens')
      .select('fcm_token')
      .eq('user_id', user.id);

    if (tokens && tokens.length > 0) {
      const message = `You logged ${count} workout${count === 1 ? '' : 's'} this week. ${count > 0 ? 'Nice work 💪' : "Let's get moving next week."}`;
      console.log(`[weekly summary] Notifying ${user.name}: ${message}`);
      for (const { fcm_token } of tokens) {
        await sendAndCleanup(fcm_token, 'RepUp', message);
      }
    }

    await supabase.from('users').update({ last_weekly_summary_date: todayStr }).eq('id', user.id);
  }
}

export function startNudgeScheduler() {
  cron.schedule('*/15 * * * *', async () => {
    await checkAndSendNudges();
    await checkStreakEvents();
    await checkFriendActivity();
    await checkWeeklySummary();
  });
  console.log('Nudge scheduler started (checks every 15 minutes)');
}