import { supabase } from '../supabase.js';

export default async function nudgesRoutes(fastify) {
  // GET /nudges/:userId — unseen nudges for a user
  fastify.get('/nudges/:userId', async (request, reply) => {
    const { userId } = request.params;
    const { data, error } = await supabase
      .from('nudges')
      .select('*')
      .eq('to_user_id', userId)
      .eq('seen', false)
      .order('created_at', { ascending: false });
    if (error) return reply.code(500).send({ error: error.message });
    return data;
  });

  // POST /nudges/trigger — cron-triggered daily check (Sprint 4: wire to per-user cutoff times + FCM/APNs)
  fastify.post('/nudges/trigger', async (request, reply) => {
    // TODO Sprint 4: for each user, check their configured cutoff time against
    // whether they've logged a workout today (using the same day-boundary rule
    // as workouts.js), and insert a nudge row + send push if not.
    return reply.send({ status: 'not implemented yet — Sprint 4' });
  });
}
