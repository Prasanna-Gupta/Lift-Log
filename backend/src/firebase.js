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

export async function sendPushNotification(fcmToken, title, body) {
  try {
    await messaging.send({
      token: fcmToken,
      notification: { title, body },
    });
    return true;
  } catch (err) {
    console.error(`Failed to send push to token ${fcmToken.slice(0, 12)}...:`, err.message);
    return false;
  }
}