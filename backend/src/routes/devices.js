import { supabase } from '../supabase.js';

export default async function devicesRoutes(fastify) {
  // POST /devices/register — { userId, fcmToken }
  fastify.post('/devices/register', async (request, reply) => {
    const { userId, fcmToken } = request.body;
    if (!userId || !fcmToken) {
      return reply.code(400).send({ error: 'userId and fcmToken are required' });
    }

    const { error } = await supabase
      .from('device_tokens')
      .upsert({ user_id: userId, fcm_token: fcmToken }, { onConflict: 'fcm_token' });

    if (error) return reply.code(500).send({ error: error.message });
    return reply.code(200).send({ status: 'registered' });
  });
}