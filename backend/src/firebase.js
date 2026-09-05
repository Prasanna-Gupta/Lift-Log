import { initializeApp, cert } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
import { readFileSync } from 'fs';
import 'dotenv/config';

function loadServiceAccount() {
  // Deployed: service account JSON is base64-encoded into an env var,
  // since the raw credential file can't be committed to git.
  if (process.env.FIREBASE_SERVICE_ACCOUNT_BASE64) {
    const json = Buffer.from(process.env.FIREBASE_SERVICE_ACCOUNT_BASE64, 'base64').toString('utf-8');
    return JSON.parse(json);
  }
  // Local dev: fall back to the gitignored file directly, unchanged from before.
  const path = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || './firebase-service-account.json';
  return JSON.parse(readFileSync(path, 'utf-8'));
}

const serviceAccount = loadServiceAccount();
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
    return { success: true };
  } catch (err) {
    console.error(`Failed to send push to token ${fcmToken.slice(0, 12)}...:`, err.message);
    if (err.code === 'messaging/registration-token-not-registered') {
      return { success: false, staleToken: fcmToken };
    }
    return { success: false };
  }
}