import React, { useCallback, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCategories, usePriceDrops, useShoppingList, useStores } from '../api/hooks';
import { SearchBar } from '../components/SearchBar';
import { StoreBadge } from '../components/StoreBadge';
import { colors, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';
import { clearRecentSearches, getRecentSearches } from '../storage/local';
import { formatPrice } from '../utils/format';

const CATEGORY_EMOJI: Record<string, string> = {
  dairy: '🥛', eggs: '🥚', meat: '🥩', fish: '🐟', bread: '🍞', produce: '🍎', grains: '🍚', pantry: '🫙',
  sweets: '🍫', drinks: '🥤', 'coffee-tea': '☕', alcohol: '🍷', frozen: '🧊', baby: '🍼', household: '🧽',
  'personal-care': '🧴', pets: '🐾', other: '🛍️',
};

type Nav = NativeStackNavigationProp<RootStackParamList>;

export function HomeScreen() {
  const { t, language } = useI18n();
  const navigation = useNavigation<Nav>();
  const categories = useCategories();
  const stores = useStores();
  // stores whose catalog is actually imported (never ones without a crawler)
  const liveStores = stores.data
    ? stores.data.filter((st) => st.integrated !== false && (st.pricedListings ?? 1) > 0).map((st) => st.slug)
    : ['sas', 'yerevan-city', 'carrefour', 'supermarket-am'];
  const drops = usePriceDrops();
  const list = useShoppingList(t.myShoppingList);
  const [recent, setRecent] = useState<string[]>([]);
  const insets = useSafeAreaInsets();

  useFocusEffect(
    useCallback(() => {
      getRecentSearches().then(setRecent);
    }, []),
  );

  const openSearch = (query?: string) => navigation.navigate('Tabs', { screen: 'SearchTab', params: { query } });
  const refreshing = categories.isRefetching || drops.isRefetching;

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { categories.refetch(); drops.refetch(); list.refetch(); }} />}
    >
      <View style={[styles.hero, { paddingTop: insets.top + spacing.lg }]}>
        <Text style={styles.title}>{t.appName}</Text>
        <Text style={styles.subtitle}>{t.greeting}</Text>
        <View style={styles.storeRow}>
          {liveStores.map((slug) => (
            <StoreBadge key={slug} slug={slug} size={28} />
          ))}
          <Text style={styles.storesText}>{t.storesTracked(liveStores.length)}</Text>
        </View>
        <View style={styles.searchWrap}>
          <View style={styles.searchFlex}>
            <SearchBar placeholder={t.searchPlaceholder} onPress={() => openSearch()} />
          </View>
          <Pressable
            style={styles.scanButton}
            onPress={() => navigation.navigate('Scan')}
            accessibilityRole="button"
            accessibilityLabel={t.scan.title}
          >
            <Text style={styles.scanIcon}>▥</Text>
            <Text style={styles.scanText}>{t.scan.button}</Text>
          </Pressable>
        </View>
      </View>

      <View style={styles.quickRow}>
        <Pressable style={styles.quick} onPress={() => navigation.navigate('Nearby')} accessibilityRole="button">
          <Text style={styles.quickText}>📍 {t.nearby.button}</Text>
        </Pressable>
        <Pressable style={styles.quick} onPress={() => navigation.navigate('Watchlist')} accessibilityRole="button">
          <Text style={styles.quickText}>🔔 {t.watch.title}</Text>
        </Pressable>
      </View>

      {recent.length > 0 && (
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>{t.recentSearches}</Text>
            <Pressable onPress={() => clearRecentSearches().then(() => setRecent([]))}>
              <Text style={styles.link}>{t.clear}</Text>
            </Pressable>
          </View>
          <View style={styles.chips}>
            {recent.map((r) => (
              <Pressable key={r} style={styles.chip} onPress={() => openSearch(r)}>
                <Text style={styles.chipText}>{r}</Text>
              </Pressable>
            ))}
          </View>
        </View>
      )}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>{t.categories}</Text>
        <View style={styles.grid}>
          {(categories.data ?? []).filter((c) => c.productCount > 0).map((c) => (
            <Pressable
              key={c.id}
              style={styles.category}
              onPress={() => navigation.navigate('Category', { categoryId: c.id, title: language === 'en' && c.nameEn ? c.nameEn : c.name })}
            >
              <Text style={styles.categoryEmoji}>{CATEGORY_EMOJI[c.slug] ?? '🛍️'}</Text>
              <Text style={styles.categoryName} numberOfLines={2}>
                {language === 'en' && c.nameEn ? c.nameEn : c.name}
              </Text>
              <Text style={styles.categoryCount}>{c.productCount}</Text>
            </Pressable>
          ))}
        </View>
        {categories.isError && <Text style={styles.error}>{t.serverUnreachable}</Text>}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>{t.priceDrops}</Text>
        {(drops.data ?? []).length === 0 && !drops.isLoading ? <Text style={styles.muted}>{t.noPriceDrops}</Text> : null}
        {(drops.data ?? []).map((d) => (
          <Pressable
            key={`${d.product.id}-${d.storeId}`}
            style={styles.drop}
            onPress={() => navigation.navigate('ProductDetails', { productId: d.product.id, title: productName(d.product, language) })}
          >
            <View style={{ flex: 1 }}>
              <Text style={styles.dropName} numberOfLines={1}>{productName(d.product, language)}</Text>
              <Text style={styles.muted}>{d.storeName}</Text>
            </View>
            <View style={{ alignItems: 'flex-end' }}>
              <Text style={styles.dropPrice}>{formatPrice(d.currentPrice)}</Text>
              <Text style={styles.dropOld}>{formatPrice(d.previousPrice)} · -{d.dropPercent}%</Text>
            </View>
          </Pressable>
        ))}
      </View>

      <Pressable style={[styles.section, styles.listCard]} onPress={() => navigation.navigate('Tabs', { screen: 'ListTab' })}>
        <Text style={styles.sectionTitle}>🛒 {t.myShoppingList}</Text>
        <Text style={styles.muted}>
          {list.data ? `${list.data.items.length} ${t.items}` : '…'}
        </Text>
        <Text style={styles.link}>{t.openList} →</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, paddingTop: 0, paddingBottom: spacing.xl * 2 },
  hero: { backgroundColor: colors.primary, marginHorizontal: -spacing.lg, paddingHorizontal: spacing.lg, paddingTop: spacing.xl,
    paddingBottom: spacing.xl, borderBottomLeftRadius: 28, borderBottomRightRadius: 28 },
  title: { fontSize: 26, fontWeight: '900', color: '#fff' },
  subtitle: { fontSize: 14, color: '#CFEFE0', marginTop: 4 },
  storeRow: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: spacing.md },
  storesText: { color: '#E8F7F0', fontSize: 12, marginLeft: 4, fontWeight: '600' },
  searchWrap: { marginTop: spacing.lg, flexDirection: 'row', alignItems: 'stretch', gap: spacing.sm },
  searchFlex: { flex: 1 },
  scanButton: { backgroundColor: '#fff', borderRadius: 14, paddingHorizontal: spacing.md, alignItems: 'center',
    justifyContent: 'center' },
  scanIcon: { fontSize: 18, color: colors.primary, fontWeight: '900' },
  scanText: { fontSize: 11, color: colors.primary, fontWeight: '700' },
  section: { marginTop: spacing.xl },
  quickRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.lg },
  quick: { flex: 1, backgroundColor: colors.card, borderRadius: 14, paddingVertical: spacing.md, alignItems: 'center',
    borderWidth: 1, borderColor: colors.border },
  quickText: { color: colors.primary, fontWeight: '700' },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sectionTitle: { fontSize: 17, fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  link: { color: colors.primary, fontWeight: '600' },
  muted: { color: colors.muted },
  error: { color: colors.danger },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  chip: { backgroundColor: colors.card, borderRadius: 16, paddingHorizontal: spacing.md, paddingVertical: 6, borderWidth: 1, borderColor: colors.border },
  chipText: { color: colors.text },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  category: {
    width: '31%', backgroundColor: colors.card, borderRadius: 16, padding: spacing.sm, alignItems: 'center',
    minHeight: 100, justifyContent: 'center', shadowColor: '#0B1F17', shadowOpacity: 0.06, shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 }, elevation: 2,
  },
  categoryEmoji: { fontSize: 28 },
  categoryName: { fontSize: 12, textAlign: 'center', color: colors.text, marginTop: 4 },
  categoryCount: { fontSize: 11, color: colors.muted },
  drop: {
    flexDirection: 'row', backgroundColor: colors.card, borderRadius: 10, padding: spacing.md, marginBottom: spacing.sm,
    borderWidth: 1, borderColor: colors.border, gap: spacing.sm,
  },
  dropName: { fontWeight: '600', color: colors.text },
  dropPrice: { fontWeight: '700', color: colors.success, fontSize: 16 },
  dropOld: { fontSize: 12, color: colors.muted },
  listCard: { backgroundColor: colors.primarySoft, borderRadius: 18, padding: spacing.lg, gap: 4 },
});
