import { supabase } from '../supabase.js';

export default async function exercisesRoutes(fastify) {
  // GET /exercises — list the shared exercise library
  fastify.get('/exercises', async (request, reply) => {
    const { data, error } = await supabase.from('exercises').select('*').order('name');
    if (error) return reply.code(500).send({ error: error.message });
    return data;
  });
}
