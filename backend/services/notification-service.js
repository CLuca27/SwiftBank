import firebase from '../config/firebase.js';
import config from '../config/index.js';

/**
 * Înregistrează un dispozitiv pentru notificări push
 */
async function registerDevice(userId, deviceId, deviceName, fcmToken) {
    const { data, error } = await config.supabase
        .from('fcm_tokens')
        .upsert({
            user_id: userId,
            device_id: deviceId,
            device_name: deviceName,
            fcm_token: fcmToken,
            updated_at: new Date().toISOString()
        }, {
            onConflict: 'user_id,device_id'
        })
        .select()
        .single();

    if (error) {
        console.error('[Notifications] Error registering device:', error);
        throw error;
    }

    console.log(`[Notifications] Device registered for user ${userId}: ${deviceName}`);
    return data;
}

/**
 * Șterge un dispozitiv
 */
async function unregisterDevice(userId, deviceId) {
    const { error } = await config.supabase
        .from('fcm_tokens')
        .delete()
        .eq('user_id', userId)
        .eq('device_id', deviceId);

    if (error) {
        console.error('[Notifications] Error unregistering device:', error);
        throw error;
    }

    console.log(`[Notifications] Device unregistered for user ${userId}`);
}

/**
 * Obține toate token-urile FCM pentru un utilizator
 */
async function getUserDeviceTokens(userId) {
    const { data, error } = await config.supabase
        .from('fcm_tokens')
        .select('fcm_token, device_name')
        .eq('user_id', userId);

    if (error) {
        console.error('[Notifications] Error fetching device tokens:', error);
        return [];
    }

    return data || [];
}

/**
 * Obține setările de notificări ale utilizatorului
 */
async function getUserNotificationSettings(userId) {
    const { data, error } = await config.supabase
        .from('user_preferences')
        .select('settings')
        .eq('user_id', userId)
        .single();

    if (error || !data) {
        // Returnează defaults dacă nu există
        return {
            push_enabled: true,
            transaction_alerts: true
        };
    }

    const settings = data.settings || {};
    const notifications = settings.notifications || {};

    return {
        push_enabled: notifications.push_enabled !== false,
        transaction_alerts: notifications.transaction_alerts !== false
    };
}

/**
 * Trimite notificare push către un utilizator
 */
async function sendPushNotification(userId, title, body, data = {}) {
    // Verifică setările utilizatorului
    const settings = await getUserNotificationSettings(userId);

    if (!settings.push_enabled) {
        console.log(`[Notifications] Push disabled for user ${userId}`);
        return { sent: false, reason: 'push_disabled' };
    }

    // Obține token-urile dispozitivelor
    const devices = await getUserDeviceTokens(userId);

    if (devices.length === 0) {
        console.log(`[Notifications] No devices registered for user ${userId}`);
        return { sent: false, reason: 'no_devices' };
    }

    const tokens = devices.map(d => d.fcm_token);

    // Construiește mesajul
    const message = {
        notification: {
            title: title,
            body: body
        },
        android: {
            priority: 'high',
            notification: {
                channelId: 'swiftbank_transactions',
                priority: 'high',
                defaultSound: true
            }
        },
        tokens: tokens
    };

    try {
        const response = await firebase.messaging().sendEachForMulticast(message);

        console.log(`[Notifications] Sent to user ${userId}: ${response.successCount} success, ${response.failureCount} failed`);

        // Șterge token-urile invalide
        if (response.failureCount > 0) {
            const tokensToRemove = [];
            response.responses.forEach((resp, idx) => {
                if (!resp.success) {
                    const errorCode = resp.error?.code;
                    if (errorCode === 'messaging/invalid-registration-token' ||
                        errorCode === 'messaging/registration-token-not-registered') {
                        tokensToRemove.push(tokens[idx]);
                    }
                }
            });

            // Șterge token-urile invalide din DB
            for (const token of tokensToRemove) {
                await config.supabase
                    .from('fcm_tokens')
                    .delete()
                    .eq('fcm_token', token);
            }
        }

        return { sent: true, successCount: response.successCount };
    } catch (error) {
        console.error('[Notifications] Error sending push:', error);
        return { sent: false, reason: 'send_error', error: error.message };
    }
}

/**
 * Notifică utilizatorul că a primit un transfer
 */
async function notifyTransferReceived(recipientUserId, senderName, amount, currency) {
    // Verifică dacă transaction_alerts e activat
    const settings = await getUserNotificationSettings(recipientUserId);

    if (!settings.transaction_alerts) {
        console.log(`[Notifications] Transaction alerts disabled for user ${recipientUserId}`);
        return { sent: false, reason: 'transaction_alerts_disabled' };
    }

    const title = 'Ai primit bani!';
    const body = `${amount} ${currency} de la ${senderName}`;

    return await sendPushNotification(recipientUserId, title, body, {
        type: 'TRANSFER_RECEIVED',
        amount: amount.toString(),
        currency: currency,
        sender_name: senderName
    });
}

/**
 * Notifică utilizatorul că transferul a fost efectuat cu succes
 */
async function notifyTransferSent(senderUserId, recipientName, amount, currency) {
    const settings = await getUserNotificationSettings(senderUserId);

    if (!settings.transaction_alerts) {
        console.log(`[Notifications] Transaction alerts disabled for user ${senderUserId}`);
        return { sent: false, reason: 'transaction_alerts_disabled' };
    }

    const title = 'Transfer efectuat';
    const body = `${amount} ${currency} către ${recipientName}`;

    return await sendPushNotification(senderUserId, title, body, {
        type: 'TRANSFER_SENT',
        amount: amount.toString(),
        currency: currency,
        recipient_name: recipientName
    });
}

export default {
    registerDevice,
    unregisterDevice,
    getUserDeviceTokens,
    sendPushNotification,
    notifyTransferReceived,
    notifyTransferSent
};
