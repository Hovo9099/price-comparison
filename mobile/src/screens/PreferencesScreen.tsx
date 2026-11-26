import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { colors, radius, shadow, spacing } from '../components/theme';
import { useI18n } from '../i18n';
import { DEFAULT_PREFERENCES, getPreferences, setPreferences, type StoredPreferences } from '../storage/local';

export function PreferencesScreen() {
  const { t } = useI18n();
  const navigation = useNavigation();
  const [p, setP] = useState<StoredPreferences>(DEFAULT_PREFERENCES);
  useEffect(() => {
    getPreferences().then(setP);
  }, []);
  const save = async () => {
    await setPreferences(p);
    navigation.goBack();
  };
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <View style={styles.card}>
        <Text style={styles.label}>{t.prefs.storeCount}</Text>
        <View style={styles.row}>
          {[null, 1, 2, 3].map((n) => (
            <Pressable key={String(n)} style={[styles.chip, p.maxStores === n && styles.chipActive]} onPress={() => setP({ ...p, maxStores: n })}>
              <Text style={[styles.chipText, p.maxStores === n && styles.chipTextActive]}>{n ?? t.prefs.any}</Text>
            </Pressable>
          ))}
        </View>
      </View>
      <View style={styles.card}>
        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>{t.prefs.includeDelivery}</Text>
          <Switch value={p.includeDelivery} onValueChange={(v) => setP({ ...p, includeDelivery: v })} trackColor={{ true: colors.primary }} />
        </View>
        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>{t.prefs.allowStale}</Text>
          <Switch value={p.allowStalePrices} onValueChange={(v) => setP({ ...p, allowStalePrices: v })} trackColor={{ true: colors.primary }} />
        </View>
        <Text style={styles.note}>{t.prefs.travelNote}</Text>
      </View>
      <View style={styles.card}>
        <Text style={styles.label}>{t.prefs.brands}</Text>
        <TextInput style={styles.input} value={p.preferredBrands} onChangeText={(v) => setP({ ...p, preferredBrands: v })}
          placeholder={t.prefs.brandsPlaceholder} placeholderTextColor={colors.muted} autoCorrect={false} />
      </View>
      <Pressable style={styles.save} onPress={save}>
        <Text style={styles.saveText}>{t.prefs.save}</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, gap: spacing.md },
  card: { backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.lg, gap: spacing.sm, ...shadow },
  label: { fontWeight: '800', color: colors.text, fontSize: 15 },
  row: { flexDirection: 'row', gap: spacing.sm },
  chip: { paddingHorizontal: spacing.lg, paddingVertical: 8, borderRadius: 16, backgroundColor: colors.background },
  chipActive: { backgroundColor: colors.primary },
  chipText: { color: colors.text, fontWeight: '700' },
  chipTextActive: { color: '#fff' },
  switchRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.md },
  switchLabel: { flex: 1, color: colors.text, fontWeight: '600' },
  note: { color: colors.muted, fontSize: 12 },
  input: { backgroundColor: colors.background, borderRadius: radius.sm, paddingHorizontal: spacing.md, height: 46, color: colors.text },
  save: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 16, alignItems: 'center' },
  saveText: { color: '#fff', fontWeight: '900', fontSize: 16 },
});
