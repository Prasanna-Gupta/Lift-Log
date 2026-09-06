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

async function sendAndCleanup(fcmToken, title, body) {
  const result = await sendPushNotification(fcmToken, title, body);
  if (result.staleToken) {
    await supabase.from('device_tokens').delete().eq('fcm_token', result.staleToken);
    console.log(`[cleanup] Removed stale token ${result.staleToken.slice(0, 12)}...`);
  }
  return result.success;
}

async function createNotification(userId, type, title, body, referenceType = null, referenceId = null) {
  const { error } = await supabase.from('notifications').insert({
    user_id: userId, type, title, body,
    reference_type: referenceType, reference_id: referenceId,
  });
  if (error) console.error(`[notification] Failed to save for ${userId}:`, error.message);
}

async function notifyUser(userId, type, title, body, referenceType = null, referenceId = null) {
  await createNotification(userId, type, title, body, referenceType, referenceId);
  const { data: tokens } = await supabase.from('device_tokens').select('fcm_token').eq('user_id', userId);
  if (!tokens || tokens.length === 0) return;
  for (const { fcm_token } of tokens) {
    await sendAndCleanup(fcm_token, title, body);
  }
}

// Returns every user who shares at least one group with userId, excluding userId itself.
async function getSharedGroupUserIds(userId) {
  const { data: myGroups } = await supabase.from('group_members').select('group_id').eq('user_id', userId);
  const groupIds = (myGroups || []).map(g => g.group_id);
  if (groupIds.length === 0) return [];
  const { data: members } = await supabase.from('group_members').select('user_id').in('group_id', groupIds);
  return [...new Set((members || []).map(m => m.user_id))].filter(id => id !== userId);
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
      .from('workouts').select('id').eq('user_id', user.id).eq('date', today).limit(1);
    if (workoutToday && workoutToday.length > 0) continue;

    const { data: alreadyNudged } = await supabase
      .from('nudges').select('id').eq('to_user_id', user.id).eq('type', 'missed_workout')
      .gte('created_at', `${today}T00:00:00Z`).limit(1);
    if (alreadyNudged && alreadyNudged.length > 0) continue;

    const message = "Haven't seen you log a workout today — everything okay?";
    await notifyUser(user.id, 'missed_workout', 'RepUp', message);
    await supabase.from('nudges').insert({ to_user_id: user.id, message, type: 'missed_workout' });
    console.log(`[nudge check] Recorded nudge for ${user.name}`);
  }
}

async function checkMissedDietLog() {
  const currentTime = nowTimeString();
  const today = todayDateString();

  const { data: users, error } = await supabase
    .from('users')
    .select('id, name, nudge_cutoff_time, nudge_enabled')
    .eq('nudge_enabled', true)
    .lte('nudge_cutoff_time', currentTime);

  if (error) {
    console.error('[diet nudge] Failed to fetch users:', error.message);
    return;
  }

  for (const user of users || []) {
    const { data: loggedToday } = await supabase
      .from('diet_logs').select('id').eq('user_id', user.id).eq('date', today).limit(1);
    if (loggedToday && loggedToday.length > 0) continue;

    const { data: alreadyNudged } = await supabase
      .from('nudges').select('id').eq('to_user_id', user.id).eq('type', 'missed_diet')
      .gte('created_at', `${today}T00:00:00Z`).limit(1);
    if (alreadyNudged && alreadyNudged.length > 0) continue;

    const message = "Haven't logged any meals today";
    await notifyUser(user.id, 'missed_diet_log', 'RepUp', message);
    await supabase.from('nudges').insert({ to_user_id: user.id, message, type: 'missed_diet' });
    console.log(`[diet nudge] Recorded nudge for ${user.name}`);
  }
}

async function checkStreakEvents() {
  const { data: streaks, error } = await supabase
    .from('streaks').select('user_id, current_streak, last_notified_streak');

  if (error) {
    console.error('[streak check] Failed to fetch streaks:', error.message);
    return;
  }

  const MILESTONES = new Set([7, 14, 30, 50, 100, 200, 365]);

  for (const s of streaks || []) {
    if (s.current_streak === s.last_notified_streak) continue;

    const { data: userRows } = await supabase.from('users').select('id, name, feed_visible').eq('id', s.user_id).limit(1);
    const user = userRows?.[0];
    if (!user) continue;

    let message = null;
    let type = null;
    if (s.current_streak === 1 && s.last_notified_streak > 1) {
      message = "Welcome back! Your streak restarted — let's build it back up 💪";
      type = 'streak_restarted';
    } else if (MILESTONES.has(s.current_streak)) {
      message = `🎉 ${s.current_streak}-day streak! Keep it going.`;
      type = 'streak_milestone';
    }

    if (message) {
      console.log(`[streak check] Notifying ${user.name}: ${message}`);
      await notifyUser(user.id, type, 'RepUp', message, 'streak', user.id);

      // Only milestones get broadcast to friends — restarts stay private.
      if (type === 'streak_milestone' && user.feed_visible !== false) {
        const sharedIds = await getSharedGroupUserIds(user.id);
        for (const friendId of sharedIds) {
          await notifyUser(
            friendId, 'friend_streak_milestone', 'RepUp',
            `${user.name} just hit a ${s.current_streak}-day streak 🎉`, 'streak', user.id
          );
        }
      }
    }

    await supabase.from('streaks').update({ last_notified_streak: s.current_streak }).eq('user_id', s.user_id);
  }
}

// Scans unprocessed sets for a new personal best. Notifies the lifter, and —
// if their feed is visible — everyone they share a group with.
async function checkNewPRs() {
  const { data: pendingSets, error } = await supabase
    .from('workout_sets')
    .select('id, workout_id, exercise_id, weight, reps')
    .eq('pr_notified', false)
    .limit(100);

  if (error) {
    console.error('[pr check] Failed to fetch pending sets:', error.message);
    return;
  }
  if (!pendingSets || pendingSets.length === 0) return;

  for (const set of pendingSets) {
    const { data: workoutRows } = await supabase
      .from('workouts').select('user_id').eq('id', set.workout_id).limit(1);
    const userId = workoutRows?.[0]?.user_id;

    if (!userId || !set.weight || set.weight <= 0) {
      await supabase.from('workout_sets').update({ pr_notified: true }).eq('id', set.id);
      continue;
    }

    const { data: priorMax } = await supabase.rpc('get_user_max_weight', {
      p_user_id: userId, p_exercise_id: set.exercise_id, p_exclude_set_id: set.id,
    });

    if (set.weight > (priorMax ?? 0)) {
      const { data: exerciseRows } = await supabase.from('exercises').select('name').eq('id', set.exercise_id).limit(1);
      const exerciseName = exerciseRows?.[0]?.name ?? 'an exercise';

      const { data: userRows } = await supabase.from('users').select('name, feed_visible').eq('id', userId).limit(1);
      const actor = userRows?.[0];

      await notifyUser(userId, 'new_pr', 'RepUp', `New PR on ${exerciseName}: ${set.weight}kg 🔥`, 'workout_sets', set.id);
      console.log(`[pr check] New PR for ${actor?.name ?? userId}: ${exerciseName} ${set.weight}kg`);

      if (actor?.feed_visible !== false) {
        const sharedIds = await getSharedGroupUserIds(userId);
        for (const friendId of sharedIds) {
          await notifyUser(
            friendId, 'friend_pr', 'RepUp',
            `${actor?.name ?? 'Someone'} just hit a new PR: ${exerciseName} ${set.weight}kg`, 'workout_sets', set.id
          );
        }
      }
    }

    await supabase.from('workout_sets').update({ pr_notified: true }).eq('id', set.id);
  }
}

// Group-scoped: only notifies users who actually share a group with the
// person who logged. Previously notified every user in the database.
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

  for (const item of unnotified) {
    const { data: actorRows } = await supabase.from('users').select('id, name, feed_visible_workouts, feed_visible_diet').eq('id', item.user_id).limit(1);
    const actor = actorRows?.[0];

    const visibleForThisType = item.activity_type === 'workout'
      ? actor?.feed_visible_workouts !== false
      : item.activity_type === 'diet'
        ? actor?.feed_visible_diet !== false
        : true;

    if (!actor || !visibleForThisType) {
      await supabase.from('activity_feed').update({ friends_notified: true }).eq('id', item.id);
      continue;
    }

    const sharedUserIds = await getSharedGroupUserIds(actor.id);
    if (sharedUserIds.length === 0) {
      await supabase.from('activity_feed').update({ friends_notified: true }).eq('id', item.id);
      continue;
    }

    const { data: recipients } = await supabase.from('users').select('id, name').in('id', sharedUserIds);

    let detail = 'logged activity';
    if (item.activity_type === 'workout') {
      const { data: w } = await supabase.from('workouts').select('title').eq('id', item.reference_id).limit(1);
      detail = `logged a workout: ${w?.[0]?.title ?? 'Workout'}`;
    } else if (item.activity_type === 'diet') {
      const { data: d } = await supabase.from('diet_logs').select('meal_label').eq('id', item.reference_id).limit(1);
      detail = `logged food: ${d?.[0]?.meal_label ?? 'Entry'}`;
    }

    const message = `${actor.name} ${detail}`;
    for (const friend of recipients || []) {
      console.log(`[friend activity] Notifying ${friend.name}: ${message}`);
      await notifyUser(friend.id, 'friend_activity', 'RepUp', message, item.activity_type, item.reference_id);
    }

    await supabase.from('activity_feed').update({ friends_notified: true }).eq('id', item.id);
  }
}

// Notifies a group's creator when someone joins via invite code, and confirms
// the join to the person who joined. Skips the creator's own auto-add at
// group creation — nobody needs to be told they joined their own group.
async function checkGroupJoins() {
  const { data: pending, error } = await supabase
    .from('group_members')
    .select('group_id, user_id')
    .eq('join_notified', false)
    .limit(100);

  if (error) {
    console.error('[group joins] Failed to fetch pending joins:', error.message);
    return;
  }
  if (!pending || pending.length === 0) return;

  for (const membership of pending) {
    const { data: groupRows } = await supabase.from('groups').select('id, name, created_by').eq('id', membership.group_id).limit(1);
    const group = groupRows?.[0];

    if (!group || membership.user_id === group.created_by) {
      await supabase.from('group_members').update({ join_notified: true })
        .eq('group_id', membership.group_id).eq('user_id', membership.user_id);
      continue;
    }

    const { data: joinerRows } = await supabase.from('users').select('name').eq('id', membership.user_id).limit(1);
    const joinerName = joinerRows?.[0]?.name ?? 'Someone';

    await notifyUser(membership.user_id, 'added_to_group', 'RepUp', `You joined ${group.name}`, 'group', group.id);
    await notifyUser(group.created_by, 'group_member_joined', 'RepUp', `${joinerName} joined ${group.name}`, 'group', group.id);
    console.log(`[group joins] ${joinerName} joined ${group.name}`);

    await supabase.from('group_members').update({ join_notified: true })
      .eq('group_id', membership.group_id).eq('user_id', membership.user_id);
  }
}

// Detects rank changes on each group's weekly leaderboard since the last check.
async function checkLeaderboardChanges() {
  const { data: groups, error } = await supabase.from('groups').select('id, name');
  if (error) {
    console.error('[leaderboard check] Failed to fetch groups:', error.message);
    return;
  }

  for (const group of groups || []) {
    const { data: entries, error: lbError } = await supabase.rpc('get_group_leaderboard_internal', { p_group_id: group.id });
    if (lbError) {
      console.error(`[leaderboard check] Failed for group ${group.name}:`, lbError.message);
      continue;
    }
    if (!entries) continue;

    for (let i = 0; i < entries.length; i++) {
      const entry = entries[i];
      const currentRank = i + 1;

      const { data: stateRows } = await supabase
        .from('leaderboard_rank_state').select('last_rank')
        .eq('group_id', group.id).eq('user_id', entry.user_id).limit(1);

      const previousRank = stateRows?.[0]?.last_rank;

      if (previousRank === undefined) {
        // First time seeing this member — record a baseline, don't notify.
        await supabase.from('leaderboard_rank_state').insert({
          group_id: group.id, user_id: entry.user_id, last_rank: currentRank,
        });
        continue;
      }

      if (currentRank < previousRank) {
        if (currentRank === 1) {
          await notifyUser(entry.user_id, 'leaderboard_number_one', 'RepUp', `You're #1 on ${group.name} this week 🏆`, 'group', group.id);
        } else {
          await notifyUser(entry.user_id, 'leaderboard_rank_up', 'RepUp', `You moved up to #${currentRank} on ${group.name}`, 'group', group.id);
        }
      } else if (currentRank > previousRank) {
        await notifyUser(entry.user_id, 'leaderboard_overtaken', 'RepUp', `You dropped to #${currentRank} on ${group.name}`, 'group', group.id);
      }

      if (currentRank !== previousRank) {
        await supabase.from('leaderboard_rank_state')
          .update({ last_rank: currentRank, updated_at: new Date().toISOString() })
          .eq('group_id', group.id).eq('user_id', entry.user_id);
      }
    }
  }
}

async function checkWeeklySummary() {
  const ist = nowInIST();
  const isSunday = ist.getDay() === 0;
  const isEveningWindow = ist.getHours() === 18;
  if (!isSunday || !isEveningWindow) return;

  const todayStr = todayDateString();
  const weekAgo = new Date(ist);
  weekAgo.setDate(weekAgo.getDate() - 7);
  const weekAgoStr = weekAgo.toISOString().slice(0, 10);

  const { data: users, error } = await supabase.from('users').select('id, name, last_weekly_summary_date');
  if (error) {
    console.error('[weekly summary] Failed to fetch users:', error.message);
    return;
  }

  for (const user of users || []) {
    if (user.last_weekly_summary_date === todayStr) continue;

    const { data: workouts } = await supabase
      .from('workouts').select('id').eq('user_id', user.id).gte('date', weekAgoStr);
    const count = workouts?.length ?? 0;

    const message = `You logged ${count} workout${count === 1 ? '' : 's'} this week. ${count > 0 ? 'Nice work 💪' : "Let's get moving next week."}`;
    console.log(`[weekly summary] Notifying ${user.name}: ${message}`);
    await notifyUser(user.id, 'weekly_summary', 'RepUp', message);

    await supabase.from('users').update({ last_weekly_summary_date: todayStr }).eq('id', user.id);
  }
}

export function startNudgeScheduler() {
  cron.schedule('*/15 * * * *', async () => {
    await checkAndSendNudges();
    await checkMissedDietLog();
    await checkStreakEvents();
    await checkNewPRs();
    await checkFriendActivity();
    await checkGroupJoins();
    await checkLeaderboardChanges();
    await checkWeeklySummary();
  });
  console.log('Nudge scheduler started (checks every 15 minutes)');
}