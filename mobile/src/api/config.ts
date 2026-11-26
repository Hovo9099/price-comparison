import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

const OVERRIDE_KEY = 'apiBaseUrlOverride';

/**
 * Backend base URL. Priority: value saved in Profile screen > EXPO_PUBLIC_API_URL > emulator defaults
 * (Android emulator reaches the host machine via 10.0.2.2).
 */
export const defaultApiBaseUrl: string =
  process.env.EXPO_PUBLIC_API_URL ??
  (Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080');

let current = defaultApiBaseUrl;

export function apiBaseUrl(): string {
  return current;
}

export async function loadApiBaseUrl(): Promise<string> {
  try {
    const saved = await AsyncStorage.getItem(OVERRIDE_KEY);
    if (saved) {
      current = saved;
    }
  } catch {
    // keep default
  }
  return current;
}

export async function setApiBaseUrl(url: string | null): Promise<void> {
  if (url && url.trim()) {
    current = url.trim().replace(/\/+$/, '');
    await AsyncStorage.setItem(OVERRIDE_KEY, current);
  } else {
    current = defaultApiBaseUrl;
    await AsyncStorage.removeItem(OVERRIDE_KEY);
  }
}

/** Small JPEG thumbnail of a store image via the backend's image proxy (store images are often 1-2 MB). */
export function thumb(url: string | null | undefined, width = 256): string | undefined {
  return url ? `${current}/api/images?w=${width}&url=${encodeURIComponent(url)}` : undefined;
}
