import React from 'react';
import { Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import type { StorePrice } from '../api/types';
import { useI18n } from '../i18n';
import { formatPrice } from '../utils/format';
import { FreshnessBadge } from './FreshnessBadge';
import { StoreBadge } from './StoreBadge';
import { colors, radius, shadow, spacing } from './theme';

/** One store's price; {@code cheapestPrice} shows how much more this store costs. */
export function StorePriceRow({ price, cheapestPrice }: { price: StorePrice; cheapestPrice?: number }) {
  const { t } = useI18n();
  const diff = cheapestPrice !== undefined && price.available ? price.price - cheapestPrice : 0;
  return (
    <Pressable
      style={({ pressed }) => [styles.row, price.cheapest && styles.cheapest, pressed && { opacity: 0.85 }]}
      onPress={() => price.externalUrl && Linking.openURL(price.externalUrl)}
      accessibilityHint={t.openInStore}
    >
      <StoreBadge slug={price.storeSlug} size={40} />
      <View style={styles.left}>
        <View style={styles.titleRow}>
          <Text style={styles.store}>{price.storeName}</Text>
          {price.cheapest && <Text style={styles.cheapestTag}>{t.cheapest}</Text>}
        </View>
        <Text style={styles.external} numberOfLines={1}>{price.externalName}</Text>
        <FreshnessBadge capturedAt={price.capturedAt} freshness={price.freshness} />
      </View>
      <View style={styles.right}>
        {price.available ? (
          <>
            <Text style={[styles.price, price.cheapest && styles.cheapestPrice]}>{formatPrice(price.price, price.currency)}</Text>
            {diff > 0 ? <Text style={styles.diff}>+{formatPrice(diff)}</Text> : null}
            {price.originalPrice ? (
              <Text style={styles.old}>{formatPrice(price.originalPrice)} −{price.discountPercent}%</Text>
            ) : null}
            {price.soldByWeight ? <Text style={styles.meta}>/ {t.perKg}</Text> : null}
          </>
        ) : (
          <Text style={styles.unavailable}>{t.unavailable}</Text>
        )}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, backgroundColor: colors.card, borderRadius: radius.md,
    padding: spacing.md, marginBottom: spacing.sm, borderWidth: 2, borderColor: 'transparent', ...shadow },
  cheapest: { borderColor: colors.success },
  left: { flex: 1, gap: 3 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  store: { fontSize: 15, fontWeight: '800', color: colors.text },
  cheapestTag: { fontSize: 10, fontWeight: '800', color: '#fff', backgroundColor: colors.success, paddingHorizontal: 6, paddingVertical: 2,
    borderRadius: 6, overflow: 'hidden', textTransform: 'uppercase' },
  external: { fontSize: 12, color: colors.muted },
  right: { alignItems: 'flex-end' },
  price: { fontSize: 19, fontWeight: '800', color: colors.text },
  cheapestPrice: { color: colors.success },
  diff: { fontSize: 12, color: colors.danger, fontWeight: '700' },
  old: { fontSize: 11, color: colors.muted, textDecorationLine: 'line-through' },
  meta: { fontSize: 11, color: colors.muted },
  unavailable: { color: colors.danger, fontWeight: '700' },
});
