import Fastify from 'fastify';
import 'dotenv/config';

import exercisesRoutes from './routes/exercises.js';
import workoutsRoutes from './routes/workouts.js';
import dietLogsRoutes from './routes/dietLogs.js';
import feedRoutes from './routes/feed.js';
import streaksRoutes from './routes/streaks.js';
import nudgesRoutes from './routes/nudges.js';
import foodRoutes from './routes/food.js';
import devicesRoutes from './routes/devices.js';
import { startNudgeScheduler } from './nudgeScheduler.js';

const fastify = Fastify({ logger: true });

fastify.get('/health', async () => ({ status: 'ok' }));

fastify.register(exercisesRoutes);
fastify.register(workoutsRoutes);
fastify.register(dietLogsRoutes);
fastify.register(feedRoutes);
fastify.register(streaksRoutes);
fastify.register(nudgesRoutes);
fastify.register(foodRoutes);
fastify.register(devicesRoutes);

const port = process.env.PORT || 3000;

fastify.listen({ port, host: '0.0.0.0' }, (err) => {
  if (err) {
    fastify.log.error(err);
    process.exit(1);
  }
});

startNudgeScheduler();
