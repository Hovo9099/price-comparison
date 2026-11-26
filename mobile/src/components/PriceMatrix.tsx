import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { StoreBasket } from '../api/types';
import { useI18n } from '../i18n';
import { formatPrice } from '../utils/format';
import { StoreBadge } from './StoreBadge';
import { colors, radius, shadow, spacing } from './theme';

interface Row {
  itemId: number;
  name: string;
  quantity: number;
}

/**
 * Every supermarket's price for the whole list, one card per store (fits a phone screen): the basket total and the
 * price of each item; the cheapest store for each item and the cheapest complete basket are highlighted in green.
 */
export function PriceMatrix({ rows, stores }: { rows: Row[]; stores: StoreBasket[] }) {
  const { t } = useI18n();
  const priceOf = (s: StoreBasket, itemId: number) => s.items.find((l) => l.itemId === itemId)?.total;
  const cheapestFor = (itemId: number) => {
    const prices = stores.map((s) => priceOf(s, itemId)).filter((p): p is number => p !== undefined);
    return prices.length ? Math.min(...prices) : undefined;
  };
  const completeTotals = stores.filter((s) => s.complete).map((s) => s.total);
  const bestTotal = completeTotals.length ? Math.min(...completeTotals) : undefined;
  const worstTotal = completeTotals.length ? Math.max(...completeTotals) : undefined;

  return (
    <View style={{ gap: spacing.sm }}>
      {stores.map((s) => {
        const isBest = s.complete && s.total === bestTotal;
        const extra = s.complete && bestTotal !== undefined ? s.total - bestTotal : 0;
        return (
          <View key={s.storeId} style={[styles.card, isBest && styles.bestCard]}>
            <View style={styles.header}>
              <StoreBadge slug={s.storeSlug} size={38} />
              <View style={{ flex: 1 }}>
                <Text style={styles.storeName}>{s.storeName}</Text>
                {!s.complete ? (
                  <Text style={styles.incomplete}>{t.incomplete} · {s.itemCount}/{rows.length}</Text>
                ) : isBest ? (
                  <Text style={styles.bestTag}>{t.cheapest}</Text>
                ) : extra > 0 ? (
                  <Text style={styles.extra}>+{formatPrice(extra)}</Text>
                ) : null}
              </View>
              <Text style={[styles.total, isBest && styles.bestText, !s.complete && styles.muted]}>{formatPrice(s.total)}</Text>
            </View>
            <View style={styles.items}>
              {rows.map((r) => {
                const p = priceOf(s, r.itemId);
                const cheapest = p !== undefined && p === cheapestFor(r.itemId);
                return (
                  <View key={r.itemId} style={styles.itemRow}>
                    <Text style={styles.itemName} numberOfLines={1}>
                      {r.quantity !== 1 ? `${r.quantity} × ` : ''}
                      {r.name}
                    </Text>
                    {p === undefined ? (
                      <Text style={styles.missing}>{t.unavailable}</Text>
                    ) : (
                      <Text style={[styles.itemPrice, cheapest && styles.bestText]}>{formatPrice(p)}</Text>
                    )}
                  </View>
                );
              })}
            </View>
          </View>
        );
      })}
      {worstTotal !== undefined && bestTotal !== undefined && worstTotal > bestTotal && (
        <Text style={styles.note}>
          {t.potentialSaving}: {formatPrice(worstTotal - bestTotal)}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.md, borderWidth: 2, borderColor: 'transparent', ...shadow },
  bestCard: { borderColor: colors.success },
  header: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  storeName: { fontSize: 16, fontWeight: '800', color: colors.text },
  bestTag: { alignSelf: 'flex-start', marginTop: 2, fontSize: 10, fontWeight: '800', color: '#fff', backgroundColor: colors.success,
    paddingHorizontal: 6, paddingVertical: 2, borderRadius: 6, overflow: 'hidden', textTransform: 'uppercase' },
  extra: { fontSize: 12, fontWeight: '700', color: colors.danger, marginTop: 2 },
  incomplete: { fontSize: 12, color: colors.muted, marginTop: 2 },
  total: { fontSize: 20, fontWeight: '900', color: colors.text },
  bestText: { color: colors.success },
  muted: { color: colors.muted },
  items: { marginTop: spacing.sm, paddingLeft: 50, gap: 4 },
  itemRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm },
  itemName: { flex: 1, fontSize: 13, color: colors.muted },
  itemPrice: { fontSize: 13, fontWeight: '700', color: colors.text },
  missing: { fontSize: 12, color: colors.danger },
  note: { textAlign: 'center', color: colors.primary, fontWeight: '700', marginTop: 4 },
});
