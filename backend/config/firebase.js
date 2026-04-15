import admin from 'firebase-admin';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

let firebaseApp = null;

function initializeFirebase() {
    if (firebaseApp) {
        return firebaseApp;
    }

    try {
        const serviceAccountPath = join(__dirname, 'firebase-service-account.json');
        const serviceAccount = JSON.parse(readFileSync(serviceAccountPath, 'utf8'));

        firebaseApp = admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });

        console.log('[Firebase] Initialized successfully');
        return firebaseApp;
    } catch (error) {
        console.error('[Firebase] Failed to initialize:', error.message);
        return null;
    }
}

// Inițializează la import
initializeFirebase();

export default admin;
