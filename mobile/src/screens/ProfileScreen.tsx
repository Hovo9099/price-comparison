import { SafeAreaView } from 'react-native-safe-area-context';
import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/types';
import { api } from '../api/client';
import { apiBaseUrl, setApiBaseUrl } from '../api/config';
import { colors, spacing } from '../components/theme';
import { useI18n, type Language } from '../i18n';
import type { Store } from '../api/types';
import { formatPrice } from '../utils/format';

export function ProfileScreen() {
  const { t, language, setLanguage } = useI18n();
  const qc = useQueryClient();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const [url, setUrl] = useState(apiBaseUrl());
  const stores = useQuery({ queryKey: ['stores'], queryFn: api.stores });

  useEffect(() => setUrl(apiBaseUrl()), []);

  const deliveryText = (d: Store['delivery']) => {
    if (!d) return t.delivery.unknown;
    return [
      d.fee !== null ? t.delivery.fee(formatPrice(d.fee)) : null,
      d.freeFrom !== null ? t.delivery.freeFrom(formatPrice(d.freeFrom)) : null,
      d.smallOrderBelow !== null && d.smallOrderFee !== null
        ? t.delivery.smallOrder(formatPrice(d.smallOrderBelow), formatPrice(d.smallOrderFee)) : null,
      d.minOrder !== null ? t.delivery.minOrder(formatPrice(d.minOrder)) : null,
    ].filter(Boolean).join(' · ');
  };

  const saveUrl = async (value: string | null) => {
    await setApiBaseUrl(value);
    setUrl(apiBaseUrl());
    await qc.resetQueries();
  };

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Text style={styles.title}>{t.profile}</Text>

      <Text style={styles.label}>{t.language}</Text>
      <View style={styles.row}>
        {(['hy', 'en'] as Language[]).map((l) => (
          <Pressable key={l} style={[styles.chip, language === l && styles.chipActive]} onPress={() => setLanguage(l)}>
            <Text style={[styles.chipText, language === l && styles.chipTextActive]}>{l === 'hy' ? 'Հայերեն' : 'English'}</Text>
          </Pressable>
        ))}
      </View>

      <Text style={styles.label}>{t.server}</Text>
      <TextInput style={styles.input} value={url} onChangeText={setUrl} autoCapitalize="none" autoCorrect={false} keyboardType="url" />
      <View style={styles.row}>
        <Pressable style={styles.button} onPress={() => saveUrl(url)}>
          <Text style={styles.buttonText}>{t.save}</Text>
        </Pressable>
        <Pressable style={[styles.button, styles.secondary]} onPress={() => saveUrl(null)}>
          <Text style={[styles.buttonText, { color: colors.primary }]}>{t.reset}</Text>
        </Pressable>
      </View>

      <Text style={styles.label}>{t.dataSources}</Text>
      <Text style={styles.muted}>{t.dataSourcesNote}</Text>
      {(stores.data ?? []).map((s) => (
        <View key={s.id}>
          <Text style={[styles.store, !s.active && styles.mutedStore]}>
            • {s.name} — {s.websiteUrl}
          </Text>
          <Text style={styles.deliveryLine}>{deliveryText(s.delivery)}</Text>
        </View>
      ))}

      <Pressable
        style={[styles.button, { marginTop: spacing.xl, alignSelf: 'flex-start' }]}
        onPress={() => navigation.navigate('Watchlist')}
        accessibilityRole="button"
      >
        <Text style={styles.buttonText}>🔔 {t.watch.title}</Text>
      </Pressable>
    </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  deliveryLine: { fontSize: 12, color: colors.muted, marginLeft: spacing.md, marginBottom: spacing.xs },
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, gap: spacing.sm },
  title: { fontSize: 22, fontWeight: '800', color: colors.text },
  label: { marginTop: spacing.lg, fontWeight: '700', color: colors.text },
  row: { flexDirection: 'row', gap: spacing.sm },
  chip: { paddingHorizontal: spacing.lg, paddingVertical: 8, borderRadius: 16, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.text },
  chipTextActive: { color: '#fff', fontWeight: '700' },
  input: { backgroundColor: colors.card, borderRadius: 10, paddingHorizontal: spacing.md, height: 44, borderWidth: 1, borderColor: colors.border, color: colors.text },
  button: { backgroundColor: colors.primary, borderRadius: 10, paddingHorizontal: spacing.lg, paddingVertical: 10 },
  secondary: { backgroundColor: colors.card, borderWidth: 1, borderColor: colors.primary },
  buttonText: { color: '#fff', fontWeight: '700' },
  muted: { color: colors.muted },
  store: { color: colors.text },
  mutedStore: { color: colors.muted },
});
