import { initializeApp, cert } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
import { readFileSync } from 'fs';
import 'dotenv/config';

const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || './firebase-service-account.json';
const serviceAccount = JSON.parse(readFileSync(serviceAccountPath, 'utf-8'));

const app = initializeApp({
  credential: cert(serviceAccount),
});

const messaging = getMessaging(app);

// Always returns a consistent shape: { success, staleToken? }
// staleToken is set when Firebase confirms the token is dead, so callers can clean it up.
export async function sendPushNotification(fcmToken, title, body) {
  try {
    await messaging.send({
      token: fcmToken,
      notification: { title, body },
    });
    return { success: true };
  } catch (err) {
    console.error(`Failed to send push to token ${fcmToken.slice(0, 12)}...:`, err.message);
    if (err.code === 'messaging/registration-token-not-registered') {
      return { success: false, staleToken: fcmToken };
    }
    return { success: false };
  }
}