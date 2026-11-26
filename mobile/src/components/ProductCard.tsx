import React from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import type { ProductSummary } from '../api/types';
import { productName, useI18n } from '../i18n';
import { formatPrice } from '../utils/format';
import { thumb } from '../api/config';
import { FreshnessBadge } from './FreshnessBadge';
import { StoreBadge } from './StoreBadge';
import { colors, radius, shadow, spacing } from './theme';

export function ProductCard({ product, onPress }: { product: ProductSummary; onPress: () => void }) {
  const { t, language } = useI18n();
  const c = product.cheapest;
  return (
    <Pressable style={({ pressed }) => [styles.card, pressed && styles.pressed]} onPress={onPress} accessibilityRole="button">
      <View style={styles.imageBox}>
        {product.imageUrl ? (
          <Image source={{ uri: thumb(product.imageUrl, 192) }} style={styles.image} resizeMode="contain" />
        ) : (
          <Text style={styles.placeholder}>🛒</Text>
        )}
      </View>
      <View style={styles.body}>
        <Text style={styles.name} numberOfLines={2}>{productName(product, language)}</Text>
        <View style={styles.metaRow}>
          {product.size ? <Text style={styles.sizeChip}>{product.size}</Text> : null}
          {product.storeCount > 1 ? <Text style={styles.storesChip}>{t.storeCount(product.storeCount)}</Text> : null}
        </View>
        {c ? (
          <View style={styles.priceRow}>
            <View>
              <Text style={styles.price}>{formatPrice(c.price, c.currency)}</Text>
              <FreshnessBadge capturedAt={c.capturedAt} freshness={c.freshness} />
            </View>
            <View style={styles.storeBox}>
              <StoreBadge slug={c.storeSlug} size={22} />
              <Text style={styles.store} numberOfLines={1}>{c.storeName}</Text>
            </View>
          </View>
        ) : (
          <Text style={styles.unavailable}>{t.unavailable}</Text>
        )}
        {product.offers && product.offers.length > 1 ? (
          <View style={styles.offers}>
            {product.offers.map((o) => (
              <View key={o.storeProductId} style={[styles.offer, !o.usable && styles.offerUnusable]}>
                <StoreBadge slug={o.storeSlug} size={16} />
                <Text style={[styles.offerPrice, !o.usable && styles.offerPriceUnusable]}>{formatPrice(o.price, o.currency)}</Text>
              </View>
            ))}
          </View>
        ) : null}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { flexDirection: 'row', backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.md,
    marginBottom: spacing.md, gap: spacing.md, ...shadow },
  pressed: { opacity: 0.85 },
  imageBox: { width: 72, height: 72, borderRadius: radius.sm, backgroundColor: '#F7F8FA', alignItems: 'center', justifyContent: 'center' },
  image: { width: 64, height: 64 },
  placeholder: { fontSize: 28 },
  body: { flex: 1, gap: 6 },
  name: { fontSize: 15, fontWeight: '700', color: colors.text },
  metaRow: { flexDirection: 'row', gap: 6, flexWrap: 'wrap' },
  sizeChip: { fontSize: 11, color: colors.text, backgroundColor: colors.background, paddingHorizontal: 8, paddingVertical: 2, borderRadius: 8, overflow: 'hidden' },
  storesChip: { fontSize: 11, color: colors.primary, backgroundColor: colors.primarySoft, paddingHorizontal: 8, paddingVertical: 2, borderRadius: 8, overflow: 'hidden', fontWeight: '700' },
  priceRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' },
  price: { fontSize: 20, fontWeight: '800', color: colors.primary },
  storeBox: { flexDirection: 'row', alignItems: 'center', gap: 6, maxWidth: '50%' },
  store: { fontSize: 12, color: colors.text, fontWeight: '600', flexShrink: 1 },
  unavailable: { color: colors.danger },
  offers: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  offer: { flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: colors.background, borderRadius: 10,
    paddingLeft: 2, paddingRight: 8, paddingVertical: 2 },
  offerUnusable: { opacity: 0.5 },
  offerPrice: { fontSize: 11, fontWeight: '700', color: colors.text },
  offerPriceUnusable: { textDecorationLine: 'line-through' },
});
