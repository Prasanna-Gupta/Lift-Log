import { createClient } from '@supabase/supabase-js';
import 'dotenv/config';

// Service-role client for backend use only — never ship this key to the mobile app.
export const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_SERVICE_ROLE_KEY
);
