import React from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useRoute, type RouteProp } from '@react-navigation/native';
import { useOffers } from '../api/hooks';
import { ErrorView, Loading } from '../components/StateViews';
import { StoreComparisonTable } from '../components/StoreComparisonTable';
import { StorePriceRow } from '../components/StorePriceRow';
import type { StorePrice } from '../api/types';
import { colors, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';
import { formatPrice } from '../utils/format';

/**
 * One row per supermarket (including stores that do not sell it, with the reason), then every listing of every store
 * when a store has more than one listing linked to this product.
 */
export function PriceComparisonScreen() {
  const { t, language } = useI18n();
  const { productId } = useRoute<RouteProp<RootStackParamList, 'PriceComparison'>>().params;
  const offers = useOffers(productId);
  if (offers.isLoading) return <Loading />;
  if (offers.isError || !offers.data) return <ErrorView error={offers.error} onRetry={() => offers.refetch()} />;
  const d = offers.data;
  const extra: StorePrice[] = d.stores.filter((r) => r.offers.length > 1).flatMap((r) =>
    r.offers.map((o) => ({
      storeId: o.storeId, storeName: o.storeName, storeSlug: o.storeSlug, storeProductId: o.storeProductId,
      externalName: o.externalName, externalUrl: o.externalUrl, price: o.price, originalPrice: o.originalPrice,
      discountPercent: o.discountPercent, unitPrice: o.unitPrice, unitPriceUnit: o.unitPriceUnit, currency: o.currency,
      available: o.usable, soldByWeight: false, capturedAt: o.capturedAt, validFrom: o.capturedAt, ageHours: 0,
      freshness: o.freshness, cheapest: false,
    })),
  );
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <Text style={styles.name}>{productName(d.product, language)}</Text>
        {d.cheapest && (
          <Text style={styles.summary}>
            {t.cheapest}: {formatPrice(d.cheapest.price)} · {d.cheapest.storeName}
          </Text>
        )}
      </View>
      <StoreComparisonTable rows={d.stores} cheapestPrice={d.cheapest?.price} />
      {extra.length > 0 && (
        <View style={styles.extra}>
          {extra.map((p) => <StorePriceRow key={p.storeProductId} price={p} cheapestPrice={d.cheapest?.price} />)}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg },
  header: { marginBottom: spacing.md, gap: 4 },
  name: { fontSize: 18, fontWeight: '700', color: colors.text },
  summary: { color: colors.success, fontWeight: '600' },
  extra: { marginTop: spacing.lg },
});
