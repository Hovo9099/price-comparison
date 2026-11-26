import React from 'react';
import { FlatList, Image, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { thumb } from '../api/config';
import { useWatchMutations, useWatches } from '../api/hooks';
import type { PriceWatch } from '../api/types';
import { Empty, ErrorView, Loading } from '../components/StateViews';
import { colors, radius, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';
import { formatPrice } from '../utils/format';

export function WatchlistScreen() {
  const { t, language } = useI18n();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const watches = useWatches();
  const { remove } = useWatchMutations();

  if (watches.isLoading) return <Loading />;
  if (watches.isError) return <ErrorView error={watches.error} onRetry={() => watches.refetch()} />;

  const renderItem = ({ item }: { item: PriceWatch }) => {
    const name = productName(item, language);
    return (
      <Pressable
        style={styles.card}
        onPress={() => navigation.navigate('ProductDetails', { productId: item.productId, title: name })}
        accessibilityRole="button"
      >
        {item.imageUrl ? <Image source={{ uri: thumb(item.imageUrl, 120) }} style={styles.image} resizeMode="contain" /> : null}
        <View style={styles.body}>
          <Text style={styles.name} numberOfLines={2}>{name}</Text>
          {item.size ? <Text style={styles.meta}>{item.size}</Text> : null}
          <Text style={styles.price}>
            {item.cheapest ? `${formatPrice(item.cheapest.price, item.cheapest.currency)} · ${item.cheapest.storeName}` : t.unavailable}
          </Text>
          <Text style={styles.meta}>
            {[item.startPrice ? t.watch.since(formatPrice(item.startPrice)) : null,
              item.targetPrice ? t.watch.target(formatPrice(item.targetPrice)) : null].filter(Boolean).join(' · ')}
          </Text>
          {item.dropped ? <Text style={styles.dropped}>↓ {t.watch.dropped}</Text> : null}
        </View>
        <Pressable
          onPress={() => remove.mutate(item.productId)}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityLabel={`${t.watch.stop}: ${name}`}
        >
          <Text style={styles.remove}>✕</Text>
        </Pressable>
      </Pressable>
    );
  };

  return (
    <FlatList
      style={styles.screen}
      contentContainerStyle={styles.content}
      data={watches.data ?? []}
      keyExtractor={(w) => String(w.id)}
      renderItem={renderItem}
      ListEmptyComponent={<Empty text={t.watch.empty} />}
      refreshControl={<RefreshControl refreshing={watches.isRefetching} onRefresh={() => { watches.refetch(); }} />}
    />
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },
  card: { flexDirection: 'row', alignItems: 'center', backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.md,
    gap: spacing.md },
  image: { width: 56, height: 56 },
  body: { flex: 1 },
  name: { fontSize: 15, fontWeight: '700', color: colors.text },
  meta: { fontSize: 12, color: colors.muted, marginTop: 2 },
  price: { fontSize: 15, fontWeight: '800', color: colors.primary, marginTop: spacing.xs },
  dropped: { marginTop: spacing.xs, alignSelf: 'flex-start', backgroundColor: colors.successSoft, color: colors.success,
    fontWeight: '800', fontSize: 12, paddingHorizontal: spacing.sm, paddingVertical: 2, borderRadius: 8, overflow: 'hidden' },
  remove: { fontSize: 18, color: colors.muted, padding: spacing.xs },
});
