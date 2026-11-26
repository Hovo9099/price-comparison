import { SafeAreaView } from 'react-native-safe-area-context';
import React, { useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useListMutations, useShoppingList } from '../api/hooks';
import type { ShoppingListItem } from '../api/types';
import { Empty, ErrorView, Loading } from '../components/StateViews';
import { colors, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';
import { formatPrice } from '../utils/format';

export function ShoppingListScreen() {
  const { t, language } = useI18n();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const list = useShoppingList(t.myShoppingList);
  const { add, update, remove } = useListMutations(t.myShoppingList);
  const [text, setText] = useState('');

  if (list.isLoading) return <Loading />;
  if (list.isError || !list.data) return <ErrorView error={list.error} onRetry={() => list.refetch()} />;
  const items = list.data.items;

  const changeQty = (item: ShoppingListItem, delta: number) => {
    const next = Math.round((item.quantity + delta) * 1000) / 1000;
    if (next <= 0) remove.mutate(item.id);
    else update.mutate({ itemId: item.id, quantity: next });
  };

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <FlatList
        contentContainerStyle={styles.content}
        data={items}
        keyExtractor={(i) => String(i.id)}
        ListHeaderComponent={<Text style={styles.title}>{t.myShoppingList}</Text>}
        ListEmptyComponent={<Empty text={t.emptyList} />}
        renderItem={({ item }) => (
          <Pressable
            style={styles.item}
            onPress={() => item.productId && navigation.navigate('ProductDetails', { productId: item.productId, title: item.name })}
          >
            <View style={{ flex: 1 }}>
              <Text style={styles.itemName} numberOfLines={2}>
                {item.productId ? productName(item, language) : `📝 ${item.freeText}`}
              </Text>
              {item.cheapest ? (
                <Text style={styles.itemMeta}>
                  {formatPrice(item.cheapest.price)} · {item.cheapest.storeName}
                </Text>
              ) : item.productId ? (
                <Text style={styles.unavailable}>{t.unavailable}</Text>
              ) : null}
            </View>
            <View style={styles.qty}>
              <Pressable style={styles.qtyBtn} onPress={() => changeQty(item, -1)} accessibilityLabel={t.remove}>
                <Text style={styles.qtyBtnText}>−</Text>
              </Pressable>
              <Text style={styles.qtyText}>{item.quantity}</Text>
              <Pressable style={styles.qtyBtn} onPress={() => changeQty(item, 1)} accessibilityLabel={t.add}>
                <Text style={styles.qtyBtnText}>+</Text>
              </Pressable>
            </View>
          </Pressable>
        )}
        ListFooterComponent={
          <View style={styles.footer}>
            <View style={styles.addRow}>
              <TextInput
                style={styles.input}
                value={text}
                onChangeText={setText}
                placeholder={t.addFreeText}
                placeholderTextColor={colors.muted}
                onSubmitEditing={() => text.trim() && add.mutate({ freeText: text.trim() }, { onSuccess: () => setText('') })}
              />
              <Pressable
                style={styles.addBtn}
                onPress={() => text.trim() && add.mutate({ freeText: text.trim() }, { onSuccess: () => setText('') })}
              >
                <Text style={styles.addBtnText}>{t.add}</Text>
              </Pressable>
            </View>
            {items.length > 0 && (
              <Pressable style={styles.optimize} onPress={() => navigation.navigate('Basket')}>
                <Text style={styles.optimizeText}>{t.findCheapestBasket}</Text>
              </Pressable>
            )}
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, paddingBottom: spacing.xl * 2 },
  title: { fontSize: 22, fontWeight: '800', color: colors.text, marginBottom: spacing.md },
  item: {
    flexDirection: 'row', alignItems: 'center', backgroundColor: colors.card, borderRadius: 14, padding: spacing.md,
    marginBottom: spacing.sm, gap: spacing.sm, shadowColor: '#0B1F17', shadowOpacity: 0.07, shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 }, elevation: 2,
  },
  itemName: { fontSize: 15, fontWeight: '600', color: colors.text },
  itemMeta: { fontSize: 13, color: colors.primary, marginTop: 2, fontWeight: '700' },
  unavailable: { fontSize: 13, color: colors.danger },
  qty: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  qtyBtn: { width: 32, height: 32, borderRadius: 16, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center' },
  qtyBtnText: { fontSize: 18, fontWeight: '700', color: colors.primary },
  qtyText: { minWidth: 24, textAlign: 'center', fontWeight: '600' },
  footer: { marginTop: spacing.md, gap: spacing.md },
  addRow: { flexDirection: 'row', gap: spacing.sm },
  input: {
    flex: 1, backgroundColor: colors.card, borderRadius: 10, paddingHorizontal: spacing.md, height: 44,
    borderWidth: 1, borderColor: colors.border, color: colors.text,
  },
  addBtn: { backgroundColor: colors.card, borderRadius: 10, paddingHorizontal: spacing.lg, justifyContent: 'center', borderWidth: 1, borderColor: colors.primary },
  addBtnText: { color: colors.primary, fontWeight: '700' },
  optimize: { backgroundColor: colors.accent, borderRadius: 14, paddingVertical: 17, alignItems: 'center' },
  optimizeText: { color: '#1C1300', fontSize: 16, fontWeight: '800' },
});
