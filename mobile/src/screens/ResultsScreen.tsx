import React, { useCallback, useState } from 'react';
import { Image, Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { thumb } from '../api/config';
import { useShoppingList } from '../api/hooks';
import type { CompareLine, CompareOption, Comparison, StrategyName, StrategyResult } from '../api/types';
import { FreshnessBadge } from '../components/FreshnessBadge';
import { ErrorView, Loading } from '../components/StateViews';
import { StoreBadge } from '../components/StoreBadge';
import { colors, radius, shadow, spacing } from '../components/theme';
import { useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';
import { getPreferences, type StoredPreferences } from '../storage/local';
import { formatPrice } from '../utils/format';

const STRATEGIES: StrategyName[] = ['CHEAPEST', 'MID_RANGE', 'PREMIUM'];
const ICON: Record<StrategyName, string> = { CHEAPEST: '💰', MID_RANGE: '⚖️', PREMIUM: '⭐' };

function Line({ line }: { line: CompareLine }) {
  const { t } = useI18n();
  const unit = t.cmp.unit[line.perUnit];
  const reasonFn = t.cmp.reason[line.reasonCode];
  return (
    <Pressable style={styles.line} onPress={() => line.externalUrl && Linking.openURL(line.externalUrl)} accessibilityHint={t.cmp.open}>
      <View style={styles.lineImg}>
        {line.imageUrl ? <Image source={{ uri: thumb(line.imageUrl, 128) }} style={{ width: 44, height: 44 }} resizeMode="contain" /> : <Text>🛒</Text>}
      </View>
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={styles.lineName} numberOfLines={2}>{line.productName}</Text>
        <Text style={styles.lineMeta}>
          {line.quantity} {line.quantityUnit === 'kg' ? t.cmp.unit.kg : '×'} {formatPrice(line.price)}
          {line.perUnit !== 'pack' && line.perUnit !== 'pcs' ? `  ·  ${formatPrice(line.pricePerUnit)}/${unit}` : ''}
        </Text>
        <Text style={styles.reason}>{reasonFn(line.pricePercentile, line.comparableProducts)}</Text>
        <FreshnessBadge capturedAt={line.capturedAt} freshness={line.freshness} />
      </View>
      <Text style={styles.lineTotal}>{formatPrice(line.lineTotal)}</Text>
    </Pressable>
  );
}

function OptionView({ option }: { option: CompareOption }) {
  const { t } = useI18n();
  if (option.stores.length === 0) return <Text style={styles.muted}>{t.cmp.noOption}</Text>;
  return (
    <View style={{ gap: spacing.md }}>
      {option.stores.map((s) => (
        <View key={s.storeId} style={styles.storeCard}>
          <View style={styles.storeHeader}>
            <StoreBadge slug={s.storeSlug} size={36} />
            <Text style={styles.storeName}>{s.storeName}</Text>
            <Text style={styles.storeTotal}>{formatPrice(s.total)}</Text>
          </View>
          {s.lines.map((l) => <Line key={l.itemId} line={l} />)}
          <View style={styles.storeFooter}>
            <Text style={styles.muted}>{t.cmp.subtotal}: {formatPrice(s.subtotal)}</Text>
            <Text style={styles.muted}>
              {s.deliveryFee != null ? `${t.cmp.delivery}: ${formatPrice(s.deliveryFee)}` : t.cmp.deliveryUnknown}
            </Text>
          </View>
        </View>
      ))}
    </View>
  );
}

function StrategyView({ s, comparison }: { s: StrategyResult; comparison: Comparison }) {
  const { t } = useI18n();
  const [view, setView] = useState<'REC' | 'SINGLE' | 'MULTI'>('REC');
  const option = view === 'SINGLE' ? s.singleStore : view === 'MULTI' ? s.multiStore
    : s.recommended === 'SINGLE_STORE' ? s.singleStore : s.multiStore;
  const missing = comparison.items.filter((i) => i.status !== 'OK' || s.missingItemIds.includes(i.itemId));
  return (
    <View style={{ gap: spacing.md }}>
      <View style={styles.totalCard}>
        <Text style={styles.totalLabel}>{ICON[s.strategy]} {t.cmp[s.strategy]} · {t.cmp.total}</Text>
        <Text style={styles.totalValue}>{s.total != null ? formatPrice(s.total) : '—'}</Text>
        <Text style={styles.totalSub}>
          {t.cmp.recommended}: {s.recommended === 'SINGLE_STORE' ? t.cmp.oneStore : `${t.cmp.multiStore} (${t.cmp.stores(s.multiStore.storeCount)})`}
        </Text>
        <View style={styles.diffRow}>
          {Object.entries(s.differenceVsOthers).map(([k, v]) => (
            <Text key={k} style={styles.diffChip}>
              {v! <= 0 ? `−${formatPrice(-v!)} ${t.cmp.cheaperBy}` : `+${formatPrice(v!)} ${t.cmp.moreExpensiveBy}`} {t.cmp.vs(t.cmp[k as StrategyName])}
            </Text>
          ))}
        </View>
      </View>

      <View style={styles.segment}>
        {(['REC', 'SINGLE', 'MULTI'] as const).map((v) => (
          <Pressable key={v} style={[styles.segBtn, view === v && styles.segBtnActive]} onPress={() => setView(v)}>
            <Text style={[styles.segText, view === v && styles.segTextActive]}>
              {v === 'REC' ? t.cmp.recommended : v === 'SINGLE' ? t.cmp.oneStore : t.cmp.multiStore}
            </Text>
          </Pressable>
        ))}
      </View>
      {option ? <OptionView option={option} /> : <Text style={styles.muted}>{t.noSingleStore}</Text>}

      {missing.length > 0 && (
        <View style={styles.missingCard}>
          <Text style={styles.sectionTitle}>{t.cmp.notIncluded}</Text>
          {missing.map((m) => (
            <Text key={m.itemId} style={styles.missingText}>• {m.label} — {t.cmp.status[m.status] || t.cmp.status.NO_CURRENT_PRICE}</Text>
          ))}
        </View>
      )}
      {(s.notes.length > 0 || comparison.warnings.length > 0) && (
        <View style={styles.notesCard}>
          <Text style={styles.sectionTitle}>{t.cmp.notes}</Text>
          {[...s.notes, ...comparison.warnings].map((n, i) => <Text key={i} style={styles.muted}>• {n}</Text>)}
        </View>
      )}
    </View>
  );
}

export function ResultsScreen() {
  const { t } = useI18n();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const list = useShoppingList(t.myShoppingList);
  const [prefs, setPrefs] = useState<StoredPreferences | null>(null);
  const [tab, setTab] = useState<StrategyName>('CHEAPEST');
  useFocusEffect(useCallback(() => { getPreferences().then(setPrefs); }, []));

  const result = useQuery({
    queryKey: ['compare', list.data?.id, list.data?.updatedAt, prefs],
    enabled: !!list.data && !!prefs,
    queryFn: () => api.compare(list.data!.id, {
      maxStores: prefs!.maxStores,
      includeDelivery: prefs!.includeDelivery,
      allowStalePrices: prefs!.allowStalePrices,
      preferredBrands: prefs!.preferredBrands.split(',').map((b) => b.trim()).filter(Boolean),
    }),
  });

  if (list.isLoading || result.isLoading || !prefs) return <Loading />;
  if (result.isError || !result.data) return <ErrorView error={result.error} onRetry={() => result.refetch()} />;
  const c = result.data;
  const current = c.strategies.find((s) => s.strategy === tab)!;

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <View style={styles.tabs}>
        {STRATEGIES.map((name) => {
          const s = c.strategies.find((x) => x.strategy === name)!;
          const active = tab === name;
          return (
            <Pressable key={name} style={[styles.tab, active && styles.tabActive]} onPress={() => setTab(name)}>
              <Text style={styles.tabIcon}>{ICON[name]}</Text>
              <Text style={[styles.tabTitle, active && styles.tabTitleActive]}>{t.cmp[name]}</Text>
              <Text style={[styles.tabTotal, active && styles.tabTitleActive]}>{s.total != null ? formatPrice(s.total) : '—'}</Text>
            </Pressable>
          );
        })}
      </View>
      <Pressable onPress={() => navigation.navigate('Preferences')}>
        <Text style={styles.prefsLink}>⚙️ {t.cmp.preferences}</Text>
      </Pressable>
      <StrategyView key={tab} s={current} comparison={c} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, paddingBottom: spacing.xl * 3, gap: spacing.md },
  tabs: { flexDirection: 'row', gap: spacing.sm },
  tab: { flex: 1, backgroundColor: colors.card, borderRadius: radius.md, paddingVertical: spacing.md, alignItems: 'center', gap: 2, ...shadow },
  tabActive: { backgroundColor: colors.primary },
  tabIcon: { fontSize: 20 },
  tabTitle: { fontSize: 13, fontWeight: '800', color: colors.text },
  tabTitleActive: { color: '#fff' },
  tabTotal: { fontSize: 13, fontWeight: '700', color: colors.muted },
  prefsLink: { color: colors.primary, fontWeight: '700', textAlign: 'right' },
  totalCard: { backgroundColor: colors.primary, borderRadius: radius.lg, padding: spacing.lg, gap: 4 },
  totalLabel: { color: '#CFEFE0', fontWeight: '800', fontSize: 14 },
  totalValue: { color: '#fff', fontSize: 34, fontWeight: '900' },
  totalSub: { color: '#E8F7F0', fontWeight: '600' },
  diffRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 6 },
  diffChip: { backgroundColor: 'rgba(255,255,255,0.18)', color: '#fff', borderRadius: 10, paddingHorizontal: 8, paddingVertical: 3, fontSize: 12,
    fontWeight: '700', overflow: 'hidden' },
  segment: { flexDirection: 'row', backgroundColor: colors.card, borderRadius: radius.sm, padding: 4, ...shadow },
  segBtn: { flex: 1, paddingVertical: 8, borderRadius: 8, alignItems: 'center' },
  segBtnActive: { backgroundColor: colors.primarySoft },
  segText: { color: colors.muted, fontWeight: '700', fontSize: 13 },
  segTextActive: { color: colors.primary },
  storeCard: { backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.md, gap: spacing.sm, ...shadow },
  storeHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  storeName: { flex: 1, fontSize: 17, fontWeight: '900', color: colors.text },
  storeTotal: { fontSize: 19, fontWeight: '900', color: colors.text },
  storeFooter: { flexDirection: 'row', justifyContent: 'space-between', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.border, paddingTop: 6 },
  line: { flexDirection: 'row', gap: spacing.sm, paddingVertical: 6, alignItems: 'center' },
  lineImg: { width: 48, height: 48, borderRadius: 8, backgroundColor: '#F7F8FA', alignItems: 'center', justifyContent: 'center' },
  lineName: { fontSize: 14, fontWeight: '700', color: colors.text },
  lineMeta: { fontSize: 12, color: colors.muted },
  reason: { fontSize: 11, color: colors.primary, fontWeight: '600' },
  lineTotal: { fontSize: 15, fontWeight: '900', color: colors.text },
  missingCard: { backgroundColor: '#FFF8EB', borderRadius: radius.md, padding: spacing.md, gap: 4 },
  missingText: { color: '#6B4400' },
  notesCard: { backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.md, gap: 4 },
  sectionTitle: { fontWeight: '900', color: colors.text, marginBottom: 2 },
  muted: { color: colors.muted, fontSize: 12 },
});
