import { supabase } from '../supabase.js';

export default async function dietLogsRoutes(fastify) {
  // POST /diet-logs — create a diet entry
  fastify.post('/diet-logs', async (request, reply) => {
    const { userId, calories, proteinG, mealLabel } = request.body;
    if (!userId || calories == null || proteinG == null) {
      return reply.code(400).send({ error: 'userId, calories, and proteinG are required' });
    }
    const { data, error } = await supabase
      .from('diet_logs')
      .insert({
        user_id: userId,
        date: new Date().toISOString().slice(0, 10),
        calories,
        protein_g: proteinG,
        meal_label: mealLabel || null,
      })
      .select()
      .single();
    if (error) return reply.code(500).send({ error: error.message });
    return reply.code(201).send(data);
  });

  // GET /diet-logs/:userId — a user's diet history
  fastify.get('/diet-logs/:userId', async (request, reply) => {
    const { userId } = request.params;
    const { data, error } = await supabase
      .from('diet_logs')
      .select('*')
      .eq('user_id', userId)
      .order('date', { ascending: false });
    if (error) return reply.code(500).send({ error: error.message });
    return data;
  });
}
