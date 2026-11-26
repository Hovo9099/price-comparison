import React from 'react';
import { Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import type { StoreRow } from '../api/types';
import { useI18n } from '../i18n';
import { formatPrice } from '../utils/format';
import { storeShortName } from '../utils/stores';
import { FreshnessBadge } from './FreshnessBadge';
import { StoreBadge } from './StoreBadge';
import { colors, radius, shadow, spacing } from './theme';

const STATUS_COLOR: Record<string, string> = {
  AVAILABLE: colors.success,
  OUT_OF_STOCK: colors.danger,
  OUTDATED: colors.muted,
  NOT_FOUND: colors.muted,
  NOT_YET_IMPORTED: '#B7791F',
  CATALOG_UNAVAILABLE: '#B7791F',
  NOT_INTEGRATED: colors.muted,
};

/**
 * One row per supermarket: the store's own listing, price and status. Stores without the product say why
 * (not found after a full crawl / catalog not imported / not integrated) instead of being hidden.
 */
export function StoreComparisonTable({ rows, cheapestPrice }: { rows: StoreRow[]; cheapestPrice?: number }) {
  const { t } = useI18n();
  return (
    <View style={styles.table}>
      {rows.map((r, i) => {
        const o = r.offers[0];
        const isCheapest = r.status === 'AVAILABLE' && o && cheapestPrice !== undefined && o.price === cheapestPrice;
        return (
          <Pressable
            key={r.storeId}
            style={({ pressed }) => [styles.row, i > 0 && styles.divider, pressed && o?.externalUrl ? { opacity: 0.85 } : null]}
            onPress={() => o?.externalUrl && Linking.openURL(o.externalUrl)}
            disabled={!o?.externalUrl}
            accessibilityHint={o?.externalUrl ? t.openInStore : undefined}
          >
            <StoreBadge slug={r.storeSlug} size={34} />
            <View style={styles.middle}>
              <Text style={styles.store}>{storeShortName(r.storeName)}</Text>
              {o ? (
                <>
                  <Text style={styles.listing} numberOfLines={2}>
                    {o.externalName}
                    {o.size ? <Text style={styles.size}> · {o.size}</Text> : null}
                  </Text>
                  <FreshnessBadge capturedAt={o.capturedAt} freshness={o.freshness} />
                </>
              ) : r.message ? (
                <Text style={styles.message} numberOfLines={2}>{r.message}</Text>
              ) : null}
            </View>
            <View style={styles.right}>
              {o ? (
                <Text style={[styles.price, isCheapest && styles.cheapest, !o.usable && styles.unusable]}>
                  {formatPrice(o.price, o.currency)}
                </Text>
              ) : null}
              {o?.discountPercent ? <Text style={styles.discount}>−{o.discountPercent}%</Text> : null}
              <Text style={[styles.status, { color: STATUS_COLOR[r.status] ?? colors.muted }]}>
                {isCheapest ? t.cheapest : t.rowStatus[r.status] ?? r.status}
              </Text>
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  table: { backgroundColor: colors.card, borderRadius: radius.md, ...shadow },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, padding: spacing.md },
  divider: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#E3E6EA' },
  middle: { flex: 1, gap: 2 },
  store: { fontSize: 14, fontWeight: '700', color: colors.text },
  listing: { fontSize: 12, color: colors.text },
  size: { color: colors.muted },
  message: { fontSize: 12, color: colors.muted },
  right: { alignItems: 'flex-end', gap: 2, maxWidth: '38%' },
  price: { fontSize: 16, fontWeight: '800', color: colors.text },
  cheapest: { color: colors.primary },
  unusable: { color: colors.muted, textDecorationLine: 'line-through' },
  discount: { fontSize: 11, color: colors.danger, fontWeight: '700' },
  status: { fontSize: 11, fontWeight: '700', textAlign: 'right' },
});
