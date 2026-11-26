import { Platform } from 'react-native';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { api } from './api/client';
import { getDeviceId } from './storage/local';

Notifications.setNotificationHandler({
  handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }),
});

function projectId(): string | undefined {
  return (Constants.expoConfig?.extra?.eas?.projectId as string | undefined) ?? Constants.easConfig?.projectId;
}

/**
 * Gets an Expo push token (asking for permission only when {@code ask} is true) and registers it with the backend
 * together with the UI language. Returns true when notifications will be delivered. Never throws: without
 * permission, on a simulator, or without an EAS project id the watch still works and drops show in the app.
 */
export async function registerForPush(language: string, ask: boolean, channelName = 'Price drops'): Promise<boolean> {
  let token: string | null = null;
  try {
    if (Device.isDevice) {
      if (Platform.OS === 'android') {
        await Notifications.setNotificationChannelAsync('price-drops', {
          name: channelName,
          importance: Notifications.AndroidImportance.DEFAULT,
        });
      }
      let { status } = await Notifications.getPermissionsAsync();
      if (status !== 'granted' && ask) {
        status = (await Notifications.requestPermissionsAsync()).status;
      }
      const id = projectId();
      if (status === 'granted' && id) {
        token = (await Notifications.getExpoPushTokenAsync({ projectId: id })).data;
      }
    }
  } catch (e) {
    console.warn('Push registration failed', e);
  }
  try {
    await api.registerDevice(await getDeviceId(), token, language);
  } catch (e) {
    console.warn('Could not register device', e);
    return false;
  }
  return token !== null;
}

/** Calls {@code open(productId)} when the user taps a price-drop notification (also for a cold start). */
export function onNotificationTap(open: (productId: number) => void): () => void {
  const handle = (r: Notifications.NotificationResponse | null) => {
    const id = Number(r?.notification.request.content.data?.productId);
    if (Number.isFinite(id) && id > 0) open(id);
  };
  Notifications.getLastNotificationResponseAsync().then(handle).catch(() => undefined);
  const sub = Notifications.addNotificationResponseReceivedListener(handle);
  return () => sub.remove();
}
