import React, { useState } from 'react';
import { FlatList, Image, Pressable, RefreshControl, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { SafeAreaView } from 'react-native-safe-area-context';
import { thumb } from '../api/config';
import { useCategories, useDeals, useStores } from '../api/hooks';
import type { Deal } from '../api/types';
import { StoreBadge } from '../components/StoreBadge';
import { Empty, ErrorView, Loading } from '../components/StateViews';
import { colors, radius, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';
import { formatPrice } from '../utils/format';

export function DealsScreen() {
  const { t, language } = useI18n();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const [storeId, setStoreId] = useState<number | undefined>();
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [includeInflated, setIncludeInflated] = useState(false);
  const deals = useDeals({ storeId, categoryId, includeInflated });
  const stores = useStores();
  const categories = useCategories();

  const items = deals.data?.pages.flatMap((p) => p.items) ?? [];

  const verdict = (d: Deal) => {
    if (d.verdict === 'GENUINE') {
      return { label: t.deals.genuine, hint: t.deals.genuineHint(formatPrice(d.maxPaid90Days)), style: styles.genuine };
    }
    if (d.verdict === 'INFLATED') {
      return { label: t.deals.inflated, hint: t.deals.inflatedHint(d.maxPaid90Days ? formatPrice(d.maxPaid90Days) : null), style: styles.inflated };
    }
    return { label: t.deals.unverified, hint: t.deals.unverifiedHint, style: styles.unverified };
  };

  const renderItem = ({ item }: { item: Deal }) => {
    const name = productName(item.product, language);
    const v = verdict(item);
    return (
      <Pressable
        style={styles.card}
        onPress={() => navigation.navigate('ProductDetails', { productId: item.product.id, title: name })}
        accessibilityRole="button"
      >
        {item.product.imageUrl ? (
          <Image source={{ uri: thumb(item.product.imageUrl, 160) }} style={styles.image} resizeMode="contain" />
        ) : (
          <View style={styles.image} />
        )}
        <View style={styles.body}>
          <Text style={styles.name} numberOfLines={2}>{name}</Text>
          <View style={styles.priceRow}>
            <Text style={styles.price}>{formatPrice(item.price)}</Text>
            <Text style={styles.original}>{formatPrice(item.originalPrice)}</Text>
            <Text style={[styles.pct, item.verdict === 'INFLATED' && styles.pctMuted]}>−{item.discountPercent}%</Text>
          </View>
          <View style={styles.storeRow}>
            <StoreBadge slug={item.storeSlug} size={18} />
            <Text style={styles.store}>{item.storeName}</Text>
          </View>
          <Text style={[styles.verdict, v.style]}>{v.label}</Text>
          <Text style={styles.hint}>{v.hint}</Text>
          {item.cheapestAnywhere && item.product.storeCount > 1 ? <Text style={styles.best}>★ {t.deals.cheapestAnywhere}</Text> : null}
        </View>
      </Pressable>
    );
  };

  const chip = (key: string, label: string, active: boolean, onPress: () => void) => (
    <Pressable key={key} style={[styles.chip, active && styles.chipActive]} onPress={onPress} accessibilityRole="button">
      <Text style={[styles.chipText, active && styles.chipTextActive]}>{label}</Text>
    </Pressable>
  );

  const header = (
    <View>
      <Text style={styles.title}>{t.deals.title}</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
        {chip('all-stores', t.deals.allStores, storeId === undefined, () => setStoreId(undefined))}
        {(stores.data ?? []).filter((s) => s.integrated !== false && (s.pricedListings ?? 1) > 0)
          .map((s) => chip(`s${s.id}`, s.name, storeId === s.id, () => setStoreId(s.id)))}
      </ScrollView>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
        {chip('all-cats', t.deals.allCategories, categoryId === undefined, () => setCategoryId(undefined))}
        {(categories.data ?? []).filter((c) => c.productCount > 0)
          .map((c) => chip(`c${c.id}`, language === 'en' && c.nameEn ? c.nameEn : c.name, categoryId === c.id, () => setCategoryId(c.id)))}
      </ScrollView>
      <View style={styles.toggle}>
        <Text style={styles.toggleText}>{t.deals.showInflated}</Text>
        <Switch value={includeInflated} onValueChange={setIncludeInflated} accessibilityLabel={t.deals.showInflated} />
      </View>
    </View>
  );

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <FlatList
        contentContainerStyle={styles.content}
        data={items}
        keyExtractor={(d) => `${d.product.id}-${d.storeId}`}
        renderItem={renderItem}
        ListHeaderComponent={header}
        ListEmptyComponent={
          deals.isLoading ? <Loading /> : deals.isError ? <ErrorView error={deals.error} onRetry={() => deals.refetch()} /> : <Empty text={t.deals.empty} />
        }
        onEndReached={() => deals.hasNextPage && !deals.isFetchingNextPage && deals.fetchNextPage()}
        onEndReachedThreshold={0.5}
        refreshControl={<RefreshControl refreshing={deals.isRefetching} onRefresh={() => { deals.refetch(); }} />}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },
  title: { fontSize: 24, fontWeight: '900', color: colors.text, marginBottom: spacing.sm },
  chips: { gap: spacing.sm, paddingVertical: spacing.xs },
  chip: { paddingHorizontal: spacing.md, paddingVertical: 7, borderRadius: 999, backgroundColor: colors.card,
    borderWidth: 1, borderColor: colors.border },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.text, fontWeight: '600', fontSize: 13 },
  chipTextActive: { color: '#fff' },
  toggle: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: spacing.sm },
  toggleText: { color: colors.muted, flex: 1, marginRight: spacing.md },
  card: { flexDirection: 'row', backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.md, gap: spacing.md },
  image: { width: 72, height: 72, borderRadius: radius.sm, backgroundColor: colors.background },
  body: { flex: 1 },
  name: { fontSize: 15, fontWeight: '700', color: colors.text },
  priceRow: { flexDirection: 'row', alignItems: 'baseline', gap: spacing.sm, marginTop: spacing.xs, flexWrap: 'wrap' },
  price: { fontSize: 18, fontWeight: '900', color: colors.primary },
  original: { fontSize: 13, color: colors.muted, textDecorationLine: 'line-through' },
  pct: { fontSize: 13, fontWeight: '800', color: '#fff', backgroundColor: colors.danger, paddingHorizontal: 6,
    borderRadius: 6, overflow: 'hidden' },
  pctMuted: { backgroundColor: colors.muted },
  storeRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, marginTop: spacing.xs },
  store: { color: colors.muted, fontSize: 13 },
  verdict: { marginTop: spacing.sm, fontWeight: '800', fontSize: 13 },
  genuine: { color: colors.success },
  inflated: { color: '#B45309' },
  unverified: { color: colors.muted },
  hint: { color: colors.muted, fontSize: 12, marginTop: 2 },
  best: { color: colors.primaryDark, fontSize: 12, fontWeight: '700', marginTop: spacing.xs },
});
