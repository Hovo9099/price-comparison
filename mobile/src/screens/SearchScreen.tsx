import { SafeAreaView } from 'react-native-safe-area-context';
import React, { useEffect, useState } from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useSearch } from '../api/hooks';
import { ProductCard } from '../components/ProductCard';
import { SearchBar } from '../components/SearchBar';
import { Empty, ErrorView, Loading } from '../components/StateViews';
import { colors, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList, TabParamList } from '../navigation/types';
import { addRecentSearch } from '../storage/local';
import { StoreBadge } from '../components/StoreBadge';
import { storeShortName } from '../utils/stores';

function useDebounced<T>(value: T, ms: number): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setV(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return v;
}

export function SearchScreen() {
  const { t, language } = useI18n();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const route = useRoute<RouteProp<TabParamList, 'SearchTab'>>();
  const [text, setText] = useState(route.params?.query ?? '');
  const query = useDebounced(text.trim(), 350);
  const search = useSearch(query);

  useEffect(() => {
    if (route.params?.query !== undefined) setText(route.params.query);
  }, [route.params?.query]);

  useEffect(() => {
    if (query.length >= 2 && (search.data?.pages[0]?.total ?? 0) > 0) addRecentSearch(query);
  }, [query, search.data]);

  const items = search.data?.pages.flatMap((p) => p.items) ?? [];
  const total = search.data?.pages[0]?.total ?? 0;
  const first = search.data?.pages[0];
  const totalMatches = first?.totalMatches ?? total;
  const stores = first?.stores ?? [];

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <View style={styles.header}>
        <SearchBar value={text} onChangeText={setText} placeholder={t.searchPlaceholder} autoFocus={!route.params?.query} />
        {query.length >= 2 && search.isSuccess && <Text style={styles.count}>{totalMatches} {t.results}</Text>}
        {query.length >= 2 && search.isSuccess && stores.length > 0 && (
          <View style={styles.coverage} accessibilityLabel={t.resultsByStore}>
            {stores.map((s) => (
              <View key={s.storeId} style={styles.coverageItem}>
                <StoreBadge slug={s.storeSlug} size={18} />
                <Text style={[styles.coverageText, s.catalogState !== 'COMPLETE' && styles.coverageMuted]}>
                  {s.catalogState === 'COMPLETE' || s.matches > 0
                    ? `${storeShortName(s.storeName)} ${s.matches}`
                    : `${storeShortName(s.storeName)}: ${t.catalogState[s.catalogState] ?? s.catalogState}`}
                  {s.catalogState === 'PARTIAL' ? ` (${t.catalogState.PARTIAL})` : ''}
                </Text>
              </View>
            ))}
          </View>
        )}
      </View>
      {query.length < 2 ? (
        <Empty text={t.typeToSearch} />
      ) : search.isLoading ? (
        <Loading />
      ) : search.isError ? (
        <ErrorView error={search.error} onRetry={() => search.refetch()} />
      ) : items.length === 0 ? (
        <Empty text={t.noResults} />
      ) : (
        <FlatList
          contentContainerStyle={styles.list}
          data={items}
          keyExtractor={(p) => String(p.id)}
          renderItem={({ item }) => (
            <ProductCard
              product={item}
              onPress={() => navigation.navigate('ProductDetails', { productId: item.id, title: productName(item, language) })}
            />
          )}
          onEndReached={() => search.hasNextPage && !search.isFetchingNextPage && search.fetchNextPage()}
          onEndReachedThreshold={0.5}
          keyboardShouldPersistTaps="handled"
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  header: { padding: spacing.lg, paddingBottom: spacing.sm, gap: spacing.sm },
  count: { color: colors.muted, fontSize: 13 },
  coverage: { flexDirection: 'row', flexWrap: 'wrap', columnGap: 10, rowGap: 4 },
  coverageItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  coverageText: { fontSize: 12, color: colors.text, fontWeight: '600' },
  coverageMuted: { color: colors.muted, fontWeight: '400' },
  list: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xl },
});
