import { supabase } from '../supabase.js';

export default async function feedRoutes(fastify) {
  // GET /feed — group activity feed, newest first
  fastify.get('/feed', async (request, reply) => {
    const { data, error } = await supabase
      .from('activity_feed')
      .select('*, users(name)')
      .order('created_at', { ascending: false })
      .limit(50);
    if (error) return reply.code(500).send({ error: error.message });
    return data;
  });
}
