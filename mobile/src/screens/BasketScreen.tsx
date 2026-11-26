import React, { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useShoppingList } from '../api/hooks';
import type { StoreBasket } from '../api/types';
import { PriceMatrix } from '../components/PriceMatrix';
import { ErrorView, Loading } from '../components/StateViews';
import { StoreBadge } from '../components/StoreBadge';
import { colors, radius, shadow, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import { formatPrice } from '../utils/format';

function StoreBlock({ basket, showItems }: { basket: StoreBasket; showItems: boolean }) {
  return (
    <View style={styles.storeBlock}>
      <View style={styles.storeHeader}>
        <StoreBadge slug={basket.storeSlug} size={34} />
        <Text style={styles.storeName}>{basket.storeName}</Text>
        <Text style={styles.storeTotal}>{formatPrice(basket.total)}</Text>
      </View>
      {showItems &&
        basket.items.map((l) => (
          <View key={l.itemId} style={styles.line}>
            <Text style={styles.lineName} numberOfLines={1}>
              {l.quantity !== 1 ? `${l.quantity} × ` : ''}
              {l.name}
            </Text>
            <Text style={styles.linePrice}>{formatPrice(l.total)}</Text>
          </View>
        ))}
    </View>
  );
}

export function BasketScreen() {
  const { t, language } = useI18n();
  const list = useShoppingList(t.myShoppingList);
  const [maxStores, setMaxStores] = useState<number | undefined>(undefined);
  const listId = list.data?.id;
  const result = useQuery({
    queryKey: ['optimize', listId, maxStores, list.data?.updatedAt],
    queryFn: () => api.optimize(listId!, maxStores),
    enabled: !!listId,
  });

  if (list.isLoading || result.isLoading) return <Loading />;
  if (result.isError || !result.data) return <ErrorView error={result.error ?? list.error} onRetry={() => result.refetch()} />;
  const r = result.data;
  const rows = (list.data?.items ?? [])
    .filter((i) => i.productId !== null)
    .map((i) => ({ itemId: i.id, name: productName(i, language), quantity: i.quantity }));
  const storesForMatrix = [...r.singleStoreOptions].sort((a, b) => Number(b.complete) - Number(a.complete) || a.total - b.total);

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      {r.savings !== null && r.savings > 0 && (
        <View style={styles.savingsBanner}>
          <Text style={styles.savingsLabel}>{t.savings}</Text>
          <Text style={styles.savingsValue}>{formatPrice(r.savings)}</Text>
        </View>
      )}

      <Text style={styles.heading}>{t.allStorePrices.toUpperCase()}</Text>
      <PriceMatrix rows={rows} stores={storesForMatrix} />

      <Text style={styles.heading}>{t.cheapestSingleStore}</Text>
      <View style={styles.card}>
        {r.cheapestSingleStore ? (
          <StoreBlock basket={r.cheapestSingleStore} showItems={false} />
        ) : (
          <Text style={styles.muted}>{t.noSingleStore}</Text>
        )}
      </View>

      <View style={styles.headingRow}>
        <Text style={[styles.heading, { marginTop: 0 }]}>{t.cheapestCombination}</Text>
      </View>
      <View style={styles.filter}>
        <Text style={styles.muted}>{t.maxStores}:</Text>
        {[undefined, 1, 2, 3].map((n) => (
          <Pressable key={String(n)} style={[styles.chip, maxStores === n && styles.chipActive]} onPress={() => setMaxStores(n)}>
            <Text style={[styles.chipText, maxStores === n && styles.chipTextActive]}>{n ?? t.any}</Text>
          </Pressable>
        ))}
      </View>
      <View style={styles.card}>
        {r.cheapestMultiStore.stores.map((s) => (
          <StoreBlock key={s.storeId} basket={s} showItems />
        ))}
        <View style={styles.divider} />
        <View style={styles.totalRow}>
          <Text style={styles.totalLabel}>{t.total}</Text>
          <Text style={styles.totalValue}>{formatPrice(r.cheapestMultiStore.total)}</Text>
        </View>
        {r.savings !== null && (
          <View style={styles.totalRow}>
            <Text style={styles.totalLabel}>{t.savings}</Text>
            <Text style={[styles.totalValue, { color: colors.success }]}>{formatPrice(r.savings)}</Text>
          </View>
        )}
        <View style={styles.totalRow}>
          <Text style={styles.totalLabel}>{t.storesTitle}</Text>
          <Text style={styles.totalValue}>{r.cheapestMultiStore.storeCount}</Text>
        </View>
      </View>

      {r.containsStalePrices && <Text style={styles.warning}>⚠️ {t.stalePricesWarning}</Text>}

      {r.unavailableItems.length > 0 && (
        <>
          <Text style={styles.heading}>{t.unavailableItems}</Text>
          <View style={styles.card}>
            {r.unavailableItems.map((u) => (
              <Text key={u.itemId} style={styles.muted}>• {u.name}</Text>
            ))}
          </View>
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, paddingBottom: spacing.xl * 2 },
  savingsBanner: { backgroundColor: colors.primary, borderRadius: radius.lg, padding: spacing.lg, flexDirection: 'row',
    justifyContent: 'space-between', alignItems: 'center' },
  savingsLabel: { color: '#CFEFE0', fontSize: 15, fontWeight: '700' },
  savingsValue: { color: '#fff', fontSize: 26, fontWeight: '900' },
  heading: { marginTop: spacing.xl, marginBottom: spacing.sm, fontSize: 12, fontWeight: '800', color: colors.muted, letterSpacing: 1 },
  headingRow: { marginTop: spacing.xl, marginBottom: spacing.sm },
  filter: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, flexWrap: 'wrap', marginBottom: spacing.sm },
  chip: { paddingHorizontal: spacing.md, paddingVertical: 6, borderRadius: 14, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.text },
  chipTextActive: { color: '#fff', fontWeight: '700' },
  card: { backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.lg, gap: spacing.md, ...shadow },
  storeBlock: { gap: 6 },
  storeHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  storeName: { flex: 1, fontSize: 17, fontWeight: '800', color: colors.text },
  storeTotal: { fontSize: 19, fontWeight: '900', color: colors.text },
  line: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm, paddingLeft: 42 },
  lineName: { flex: 1, color: colors.text },
  linePrice: { color: colors.text, fontWeight: '700' },
  divider: { height: 1, backgroundColor: colors.border },
  totalRow: { flexDirection: 'row', justifyContent: 'space-between' },
  totalLabel: { fontSize: 16, color: colors.text },
  totalValue: { fontSize: 18, fontWeight: '900', color: colors.text },
  muted: { color: colors.muted },
  warning: { marginTop: spacing.md, color: '#b26a00' },
});
