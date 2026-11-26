import React, { useState } from 'react';
import { Image, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useHistory, useListMutations, useOffers } from '../api/hooks';
import { FreshnessBadge } from '../components/FreshnessBadge';
import { PriceHistoryChart } from '../components/PriceHistoryChart';
import { ErrorView, Loading } from '../components/StateViews';
import { StoreComparisonTable } from '../components/StoreComparisonTable';
import { WatchButton } from '../components/WatchButton';
import { colors, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';
import { formatPrice } from '../utils/format';
import { thumb } from '../api/config';

export function ProductDetailsScreen() {
  const { t, language } = useI18n();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { productId } = useRoute<RouteProp<RootStackParamList, 'ProductDetails'>>().params;
  const prices = useOffers(productId);
  const history = useHistory(productId, 90);
  const { add } = useListMutations(t.myShoppingList);
  const [added, setAdded] = useState(false);

  if (prices.isLoading) return <Loading />;
  if (prices.isError || !prices.data) return <ErrorView error={prices.error} onRetry={() => prices.refetch()} />;
  const { product, cheapest, stores } = prices.data;
  const available = stores.filter((r) => r.status === 'AVAILABLE' && r.offers[0]).map((r) => r.offers[0].price);
  const potentialSaving = cheapest && available.length > 1 ? Math.max(...available) - cheapest.price : null;

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={prices.isRefetching} onRefresh={() => { prices.refetch(); history.refetch(); }} />}
    >
      <View style={styles.header}>
        {product.imageUrl ? <Image source={{ uri: thumb(product.imageUrl, 480) }} style={styles.image} resizeMode="contain" /> : null}
        <Text style={styles.name}>{productName(product, language)}</Text>
        {language === 'hy' && product.nameEn ? <Text style={styles.subtitle}>{product.nameEn}</Text> : null}
        <Text style={styles.meta}>
          {[product.brand, product.size, product.barcode ? `EAN ${product.barcode}` : null].filter(Boolean).join(' · ')}
        </Text>
      </View>

      <View style={styles.cheapestBox}>
        <Text style={styles.cheapestLabel}>{t.cheapest}</Text>
        {cheapest ? (
          <>
            <Text style={styles.cheapestPrice}>
              {formatPrice(cheapest.price, cheapest.currency)}
              {product.soldByWeight ? <Text style={styles.perKg}> / {t.perKg}</Text> : null}
            </Text>
            <Text style={styles.cheapestStore}>{cheapest.storeName}</Text>
            <View style={styles.freshPill}>
              <FreshnessBadge capturedAt={cheapest.capturedAt} freshness={cheapest.freshness} />
            </View>
            {potentialSaving ? (
              <Text style={styles.saving}>{t.potentialSaving}: {formatPrice(potentialSaving)}</Text>
            ) : null}
          </>
        ) : (
          <Text style={styles.unavailable}>{t.unavailable}</Text>
        )}
      </View>

      <Pressable
        style={[styles.addButton, added && styles.addButtonDone]}
        disabled={add.isPending}
        onPress={() => add.mutate({ productId: product.id, quantity: 1 }, { onSuccess: () => setAdded(true) })}
      >
        <Text style={styles.addText}>{added ? `✓ ${t.addedToList}` : `+ ${t.addToList}`}</Text>
      </Pressable>
      {add.isError ? <Text style={styles.unavailable}>{String((add.error as Error).message)}</Text> : null}
      <WatchButton productId={product.id} />

      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={[styles.sectionTitle, styles.shrink]}>{t.storeComparison}</Text>
          <Pressable onPress={() => navigation.navigate('PriceComparison', { productId, title: productName(product, language) })}>
            <Text style={styles.link}>{t.seeAllPrices}</Text>
          </Pressable>
        </View>
        <StoreComparisonTable rows={stores} cheapestPrice={cheapest?.price} />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>{t.priceHistory}</Text>
        {history.data ? <PriceHistoryChart history={history.data} /> : history.isLoading ? <Loading /> : null}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, paddingBottom: spacing.xl * 2 },
  header: { alignItems: 'center', gap: 4 },
  image: { width: 180, height: 180, borderRadius: 20, backgroundColor: '#fff' },
  name: { fontSize: 20, fontWeight: '700', textAlign: 'center', color: colors.text, marginTop: spacing.sm },
  subtitle: { fontSize: 14, color: colors.muted, textAlign: 'center' },
  meta: { fontSize: 13, color: colors.muted, textAlign: 'center' },
  cheapestBox: {
    marginTop: spacing.lg, backgroundColor: colors.primary, borderRadius: 20, padding: spacing.lg, alignItems: 'center', gap: 4,
  },
  cheapestLabel: { fontSize: 13, color: '#CFEFE0', textTransform: 'uppercase', letterSpacing: 1 },
  cheapestPrice: { fontSize: 36, fontWeight: '900', color: '#fff' },
  perKg: { fontSize: 16, color: '#CFEFE0', fontWeight: '400' },
  cheapestStore: { fontSize: 17, fontWeight: '700', color: '#fff' },
  saving: { marginTop: 4, color: '#FFE3A3', fontWeight: '700' },
  unavailable: { color: colors.danger, fontWeight: '600', textAlign: 'center' },
  addButton: { marginTop: spacing.lg, backgroundColor: colors.accent, borderRadius: 14, paddingVertical: 15, alignItems: 'center' },
  addButtonDone: { backgroundColor: colors.success },
  addText: { color: '#1C1300', fontSize: 16, fontWeight: '800' },
  freshPill: { backgroundColor: '#fff', borderRadius: 10, paddingHorizontal: 8, paddingVertical: 3, marginTop: 4 },
  section: { marginTop: spacing.xl },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm },
  sectionTitle: { fontSize: 17, fontWeight: '700', color: colors.text },
  link: { color: colors.primary, fontWeight: '600', marginLeft: spacing.sm },
  shrink: { flexShrink: 1 },
});
