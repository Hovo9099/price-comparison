import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Crypto from 'expo-crypto';

const LIST_KEY = 'shoppingListId';
const RECENT_KEY = 'recentSearches';
const MAX_RECENT = 8;

export async function getShoppingListId(): Promise<number | null> {
  const v = await AsyncStorage.getItem(LIST_KEY);
  return v ? Number(v) : null;
}

export async function setShoppingListId(id: number | null): Promise<void> {
  if (id === null) await AsyncStorage.removeItem(LIST_KEY);
  else await AsyncStorage.setItem(LIST_KEY, String(id));
}

export async function getRecentSearches(): Promise<string[]> {
  try {
    const v = await AsyncStorage.getItem(RECENT_KEY);
    return v ? (JSON.parse(v) as string[]) : [];
  } catch {
    return [];
  }
}

export async function addRecentSearch(q: string): Promise<string[]> {
  const query = q.trim();
  if (!query) return getRecentSearches();
  const list = [query, ...(await getRecentSearches()).filter((s) => s.toLowerCase() !== query.toLowerCase())].slice(0, MAX_RECENT);
  await AsyncStorage.setItem(RECENT_KEY, JSON.stringify(list));
  return list;
}

export async function clearRecentSearches(): Promise<void> {
  await AsyncStorage.removeItem(RECENT_KEY);
}

// ---- saved lists and comparison preferences ----------------------------------------------------

const LISTS_KEY = 'savedListIds';
const PREFS_KEY = 'comparePreferences';

export async function getSavedListIds(): Promise<number[]> {
  try {
    const v = await AsyncStorage.getItem(LISTS_KEY);
    return v ? (JSON.parse(v) as number[]) : [];
  } catch {
    return [];
  }
}

export async function rememberList(id: number): Promise<void> {
  const ids = [id, ...(await getSavedListIds()).filter((x) => x !== id)].slice(0, 30);
  await AsyncStorage.setItem(LISTS_KEY, JSON.stringify(ids));
}

export interface StoredPreferences {
  maxStores: number | null;
  includeDelivery: boolean;
  allowStalePrices: boolean;
  preferredBrands: string;
}

export const DEFAULT_PREFERENCES: StoredPreferences = { maxStores: null, includeDelivery: false, allowStalePrices: true, preferredBrands: '' };

export async function getPreferences(): Promise<StoredPreferences> {
  try {
    const v = await AsyncStorage.getItem(PREFS_KEY);
    return v ? { ...DEFAULT_PREFERENCES, ...(JSON.parse(v) as StoredPreferences) } : DEFAULT_PREFERENCES;
  } catch {
    return DEFAULT_PREFERENCES;
  }
}

export async function setPreferences(p: StoredPreferences): Promise<void> {
  await AsyncStorage.setItem(PREFS_KEY, JSON.stringify(p));
}

// ---- anonymous install id (credential for price watches) ---------------------------------------

const DEVICE_KEY = 'deviceId';
let deviceIdCache: string | null = null;

/** Random UUID identifying this install for price watches; created on first use. */
export async function getDeviceId(): Promise<string> {
  if (deviceIdCache) return deviceIdCache;
  let id = await AsyncStorage.getItem(DEVICE_KEY);
  if (!id) {
    id = Crypto.randomUUID();
    await AsyncStorage.setItem(DEVICE_KEY, id);
  }
  deviceIdCache = id;
  return id;
}

/** True when this install has already used price watches (so it has a device id). */
export async function hasDeviceId(): Promise<boolean> {
  return deviceIdCache !== null || (await AsyncStorage.getItem(DEVICE_KEY)) !== null;
}
