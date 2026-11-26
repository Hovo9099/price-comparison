import React from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useBrowse } from '../api/hooks';
import { ProductCard } from '../components/ProductCard';
import { Empty, ErrorView, Loading } from '../components/StateViews';
import { colors, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';

export function CategoryScreen() {
  const { t, language } = useI18n();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { categoryId } = useRoute<RouteProp<RootStackParamList, 'Category'>>().params;
  const browse = useBrowse(categoryId);
  const items = browse.data?.pages.flatMap((p) => p.items) ?? [];

  if (browse.isLoading) return <Loading />;
  if (browse.isError) return <ErrorView error={browse.error} onRetry={() => browse.refetch()} />;
  return (
    <View style={styles.screen}>
      {items.length === 0 ? (
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
          onEndReached={() => browse.hasNextPage && !browse.isFetchingNextPage && browse.fetchNextPage()}
          onEndReachedThreshold={0.5}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  list: { padding: spacing.lg },
});
