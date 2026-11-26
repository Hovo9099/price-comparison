import React, { useCallback, useEffect, useState } from 'react';
import { FlatList, Linking, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import * as Location from 'expo-location';
import { api } from '../api/client';
import type { NearbyStore } from '../api/types';
import { StoreBadge } from '../components/StoreBadge';
import { Empty, ErrorView, Loading } from '../components/StateViews';
import { colors, radius, spacing } from '../components/theme';
import { useI18n } from '../i18n';

type State =
  | { kind: 'checking' }
  | { kind: 'needPermission' }
  | { kind: 'locating' }
  | { kind: 'ready'; stores: NearbyStore[] }
  | { kind: 'error'; error: unknown };

export function mapUrl(s: NearbyStore): string {
  const label = encodeURIComponent(s.name);
  return Platform.OS === 'ios'
    ? `http://maps.apple.com/?ll=${s.latitude},${s.longitude}&q=${label}`
    : `geo:${s.latitude},${s.longitude}?q=${s.latitude},${s.longitude}(${label})`;
}

/** Nearest known branch of each store. The position is sent only for this calculation and never stored. */
export function NearbyScreen() {
  const { t } = useI18n();
  const [state, setState] = useState<State>({ kind: 'checking' });

  const load = useCallback(async (ask: boolean) => {
    try {
      let { status } = await Location.getForegroundPermissionsAsync();
      if (status !== 'granted' && ask) status = (await Location.requestForegroundPermissionsAsync()).status;
      if (status !== 'granted') {
        setState({ kind: 'needPermission' });
        return;
      }
      setState({ kind: 'locating' });
      const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      // ~100 m precision is plenty for "which branch is closest"
      const lat = Math.round(pos.coords.latitude * 1000) / 1000;
      const lon = Math.round(pos.coords.longitude * 1000) / 1000;
      setState({ kind: 'ready', stores: await api.nearbyStores(lat, lon) });
    } catch (e) {
      setState({ kind: 'error', error: e });
    }
  }, []);

  useEffect(() => {
    load(false);
  }, [load]);

  if (state.kind === 'checking') return <Loading />;
  if (state.kind === 'error') return <ErrorView error={state.error} onRetry={() => load(true)} />;
  if (state.kind === 'needPermission') {
    return (
      <View style={styles.center}>
        <Text style={styles.message}>{t.nearby.needPermission}</Text>
        <Pressable style={styles.button} onPress={() => load(true)} accessibilityRole="button">
          <Text style={styles.buttonText}>{t.nearby.grant}</Text>
        </Pressable>
      </View>
    );
  }
  if (state.kind === 'locating') {
    return <View style={styles.center}><Text style={styles.message}>{t.nearby.locating}</Text></View>;
  }

  return (
    <FlatList
      style={styles.screen}
      contentContainerStyle={styles.content}
      data={state.stores}
      keyExtractor={(s) => String(s.storeId)}
      ListHeaderComponent={<Text style={styles.note}>{t.nearby.note}</Text>}
      ListEmptyComponent={<Empty text={t.nearby.empty} />}
      renderItem={({ item }) => (
        <View style={styles.card}>
          <StoreBadge slug={item.storeSlug} size={36} />
          <View style={styles.body}>
            <Text style={styles.store}>{item.storeName}</Text>
            <Text style={styles.branch}>{item.name}</Text>
            {item.address ? <Text style={styles.address}>{item.address}</Text> : null}
          </View>
          <View style={styles.right}>
            <Text style={styles.distance}>{t.nearby.km(item.distanceKm)}</Text>
            <Pressable onPress={() => Linking.openURL(mapUrl(item))} accessibilityRole="link" hitSlop={8}>
              <Text style={styles.link}>{t.nearby.openMap}</Text>
            </Pressable>
          </View>
        </View>
      )}
    />
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },
  note: { color: colors.muted, marginBottom: spacing.xs },
  card: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, backgroundColor: colors.card, borderRadius: radius.md,
    padding: spacing.md },
  body: { flex: 1 },
  store: { fontWeight: '800', fontSize: 15, color: colors.text },
  branch: { color: colors.text, marginTop: 2 },
  address: { color: colors.muted, fontSize: 12, marginTop: 2 },
  right: { alignItems: 'flex-end', gap: spacing.xs },
  distance: { fontWeight: '900', color: colors.primary, fontSize: 16 },
  link: { color: colors.primary, fontWeight: '600', fontSize: 13 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, backgroundColor: colors.background },
  message: { fontSize: 15, color: colors.text, textAlign: 'center', marginBottom: spacing.lg },
  button: { backgroundColor: colors.primary, paddingHorizontal: spacing.xl, paddingVertical: spacing.md, borderRadius: radius.sm },
  buttonText: { color: '#fff', fontWeight: '700', fontSize: 15 },
});
