import { supabase } from '../supabase.js';

// Day-boundary rule (PRD §13): a workout logged after midnight counts toward
// the previous day. We compute "log_date" server-side from a day-start offset
// rather than trusting the raw client timestamp's calendar date.
function resolveLogDate(rawTimestamp, dayStartHour = 4) {
  const d = new Date(rawTimestamp);
  if (d.getHours() < dayStartHour) {
    d.setDate(d.getDate() - 1);
  }
  return d.toISOString().slice(0, 10); // YYYY-MM-DD
}

export default async function workoutsRoutes(fastify) {
  // POST /workouts — create a workout with nested exercises/sets
  fastify.post('/workouts', async (request, reply) => {
    const { userId, timestamp, notes, exercises } = request.body;
    if (!userId || !exercises?.length) {
      return reply.code(400).send({ error: 'userId and at least one exercise are required' });
    }

    const logDate = resolveLogDate(timestamp || new Date().toISOString());

    const { data: workout, error: workoutError } = await supabase
      .from('workouts')
      .insert({ user_id: userId, date: logDate, notes: notes || null })
      .select()
      .single();
    if (workoutError) return reply.code(500).send({ error: workoutError.message });

    const setRows = exercises.flatMap((ex) =>
      ex.sets.map((set, i) => ({
        workout_id: workout.id,
        exercise_id: ex.exerciseId,
        set_number: i + 1,
        weight: set.weight,
        reps: set.reps,
      }))
    );

    const { error: setsError } = await supabase.from('workout_sets').insert(setRows);
    if (setsError) return reply.code(500).send({ error: setsError.message });

    // TODO (Sprint 4): trigger streak recalculation for userId here.
    return reply.code(201).send({ ...workout, sets_created: setRows.length });
  });

  // GET /workouts/:userId — a user's workout history
  fastify.get('/workouts/:userId', async (request, reply) => {
    const { userId } = request.params;
    const { data, error } = await supabase
      .from('workouts')
      .select('*, workout_sets(*)')
      .eq('user_id', userId)
      .order('date', { ascending: false });
    if (error) return reply.code(500).send({ error: error.message });
    return data;
  });

  // POST /workouts/:id/photo — attach a photo URL after upload to storage
  fastify.post('/workouts/:id/photo', async (request, reply) => {
    const { id } = request.params;
    const { photoUrl } = request.body;
    const { data, error } = await supabase
      .from('workouts')
      .update({ photo_url: photoUrl })
      .eq('id', id)
      .select()
      .single();
    if (error) return reply.code(500).send({ error: error.message });
    return data;
  });
}
