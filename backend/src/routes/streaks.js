import { supabase } from '../supabase.js';

// Streak decay rule (PRD §13, locked):
// - Miss 1 day  -> streak survives, that day marked "locked/warning", grace is consumed
// - Miss 2-3 consecutive days -> streak resets to zero
// This function is the source of truth for that logic — called after every workout log
// and by the daily nudge cron job (Sprint 4).
export function computeStreakUpdate({ lastLoggedDate, currentStreak, warningUsed, today }) {
  const msPerDay = 1000 * 60 * 60 * 24;
  const daysSinceLastLog = Math.round((new Date(today) - new Date(lastLoggedDate)) / msPerDay);

  if (daysSinceLastLog <= 0) {
    // Already logged today — no change.
    return { currentStreak, warningUsed, lastLoggedDate };
  }
  if (daysSinceLastLog === 1) {
    // Logged yesterday, logging today — normal continuation.
    return { currentStreak: currentStreak + 1, warningUsed: false, lastLoggedDate: today };
  }
  if (daysSinceLastLog === 2 && !warningUsed) {
    // Missed exactly one day, grace not yet used this cycle — streak survives, marked locked.
    return { currentStreak: currentStreak + 1, warningUsed: true, lastLoggedDate: today };
  }
  // Missed 2-3+ consecutive days, or grace already used — hard reset.
  return { currentStreak: 1, warningUsed: false, lastLoggedDate: today };
}

export default async function streaksRoutes(fastify) {
  // GET /streaks/:userId
  fastify.get('/streaks/:userId', async (request, reply) => {
    const { userId } = request.params;
    const { data, error } = await supabase
      .from('streaks')
      .select('*')
      .eq('user_id', userId)
      .single();
    if (error) return reply.code(500).send({ error: error.message });
    return data;
  });
}
