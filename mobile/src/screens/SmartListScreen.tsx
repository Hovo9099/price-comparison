import React, { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { keys, useShoppingList } from '../api/hooks';
import type { Concept, ParsedItem, ShoppingList, ShoppingListItem } from '../api/types';
import { StoreBadge } from '../components/StoreBadge';
import { Empty, ErrorView, Loading } from '../components/StateViews';
import { colors, radius, shadow, spacing } from '../components/theme';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';
import { getSavedListIds, rememberList, setShoppingListId } from '../storage/local';
import { formatPrice } from '../utils/format';

function useDebounced<T>(value: T, ms: number): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setV(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return v;
}

/** "1000 ml" -> "1 լ", "500 g" -> "500 գ", "10 pcs" -> "10 հատ" */
export function sizeText(value: number | null | undefined, unit: string | null | undefined): string | null {
  if (value == null || !unit) return null;
  if (unit === 'ml') return value >= 1000 ? `${value / 1000} լ` : `${value} մլ`;
  if (unit === 'g') return value >= 1000 ? `${value / 1000} կգ` : `${value} գ`;
  return `${value} հատ`;
}

function ItemCard({ item, concept, list }: { item: ShoppingListItem; concept: Concept | undefined; list: ShoppingList }) {
  const { t, language } = useI18n();
  const qc = useQueryClient();
  const onSuccess = (l: ShoppingList) => qc.setQueryData(keys.list, l);
  const patch = useMutation({ mutationFn: (b: Parameters<typeof api.patchItem>[2]) => api.patchItem(list.id, item.id, b), onSuccess });
  const remove = useMutation({ mutationFn: () => api.removeItem(list.id, item.id), onSuccess });
  const step = item.soldByWeight ? 0.5 : 1;
  const changeQty = (d: number) => {
    const next = Math.round((item.quantity + d) * 1000) / 1000;
    if (next <= 0) remove.mutate();
    else patch.mutate({ quantity: next });
  };
  const title = item.productId ? productName(item, language) : item.conceptCode ? (language === 'en' && item.nameEn ? item.nameEn : item.name) : item.freeText;
  const unrecognised = !item.productId && !item.conceptCode;
  const extras = [item.preferredBrand ? `«${item.preferredBrand}»` : null, item.percent != null ? `${item.percent}%` : null, item.size]
    .filter(Boolean)
    .join(' · ');

  return (
    <View style={[styles.card, item.requiresChoice && styles.cardWarn, unrecognised && styles.cardMuted]}>
      <View style={styles.cardTop}>
        <View style={{ flex: 1 }}>
          <Text style={styles.itemTitle} numberOfLines={2}>{title}</Text>
          {extras ? <Text style={styles.extras}>{extras}</Text> : null}
          {item.rawText && item.rawText !== title ? <Text style={styles.raw}>“{item.rawText}”</Text> : null}
        </View>
        <Pressable onPress={() => remove.mutate()} hitSlop={10} accessibilityLabel={t.smart.remove}>
          <Text style={styles.removeX}>✕</Text>
        </Pressable>
      </View>

      {item.requiresChoice && concept ? (
        <View style={styles.choiceBox}>
          <Text style={styles.choiceTitle}>⚠️ {t.smart.needsChoice}</Text>
          <View style={styles.chips}>
            {concept.choices.map((c) => (
              <Pressable key={c.code} style={styles.choiceChip} onPress={() => patch.mutate({ conceptCode: c.code })}>
                <Text style={styles.choiceChipText}>{language === 'en' ? c.nameEn : c.nameHy}</Text>
              </Pressable>
            ))}
          </View>
        </View>
      ) : unrecognised ? (
        <Text style={styles.warnText}>{t.smart.unrecognised}</Text>
      ) : (
        <>
          {concept && !concept.soldByWeight && concept.sizes.length > 0 && (
            <View style={[styles.chips, { marginTop: spacing.sm }]}>
              {concept.sizes.map((sz) => {
                const active = item.sizeValue === sz;
                return (
                  <Pressable key={sz} style={[styles.sizeChip, active && styles.sizeChipActive]}
                    onPress={() => patch.mutate({ sizeValue: sz, sizeUnit: concept.defaultSizeUnit ?? undefined })}>
                    <Text style={[styles.sizeChipText, active && styles.sizeChipTextActive]}>{sizeText(sz, concept.defaultSizeUnit)}</Text>
                  </Pressable>
                );
              })}
            </View>
          )}
          <View style={styles.cardBottom}>
            {item.cheapest ? (
              <View style={styles.priceBox}>
                <StoreBadge slug={item.cheapest.storeSlug} size={20} />
                <Text style={styles.fromPrice}>{t.smart.from} {formatPrice(item.cheapest.price)}</Text>
              </View>
            ) : (
              <Text style={styles.noPrice}>{t.smart.noPrice}</Text>
            )}
            <View style={styles.qty}>
              <Pressable style={styles.qtyBtn} onPress={() => changeQty(-step)}><Text style={styles.qtyBtnText}>−</Text></Pressable>
              <Text style={styles.qtyText}>{item.quantity}{item.soldByWeight ? ` ${t.smart.perKg}` : ''}</Text>
              <Pressable style={styles.qtyBtn} onPress={() => changeQty(step)}><Text style={styles.qtyBtnText}>+</Text></Pressable>
            </View>
          </View>
        </>
      )}
    </View>
  );
}

export function SmartListScreen() {
  const { t, language } = useI18n();
  const qc = useQueryClient();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const list = useShoppingList(t.myShoppingList);
  const concepts = useQuery({ queryKey: ['concepts'], queryFn: api.concepts, staleTime: Infinity });
  const conceptByCode = useMemo(() => new Map((concepts.data ?? []).map((c) => [c.code, c])), [concepts.data]);
  const [text, setText] = useState('');
  const [feedback, setFeedback] = useState<ParsedItem[] | null>(null);
  const [listsOpen, setListsOpen] = useState(false);

  const lastToken = useDebounced(text.split(/[,;\n]/).pop()?.trim() ?? '', 300);
  const suggestions = useQuery({
    queryKey: ['suggest', lastToken],
    queryFn: () => api.suggestConcepts(lastToken),
    enabled: lastToken.length >= 2,
  });

  useEffect(() => {
    if (list.data) rememberList(list.data.id);
  }, [list.data?.id]);

  const parse = useMutation({
    mutationFn: (input: string) => api.parseInto(list.data!.id, input),
    onSuccess: (r) => {
      if (r.list) qc.setQueryData(keys.list, r.list);
      setFeedback(r.items);
      setText('');
    },
  });
  const addConcept = useMutation({
    mutationFn: (c: Concept) => api.addItem(list.data!.id, { conceptCode: c.code, quantity: 1 }),
    onSuccess: (l) => {
      qc.setQueryData(keys.list, l);
      setText((cur) => cur.replace(/[^,;\n]*$/, '').replace(/[,;\s]*$/, cur.match(/[,;\n]/) ? ', ' : ''));
    },
  });

  if (list.isLoading) return <Loading />;
  if (list.isError || !list.data) return <ErrorView error={list.error} onRetry={() => list.refetch()} />;
  const items = list.data.items;
  const unrecognisedFeedback = (feedback ?? []).filter((f) => f.status === 'UNRECOGNIZED');

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <FlatList
        contentContainerStyle={styles.content}
        data={items}
        keyExtractor={(i) => String(i.id)}
        keyboardShouldPersistTaps="handled"
        ListHeaderComponent={
          <View>
            <View style={styles.headerRow}>
              <Text style={styles.title} numberOfLines={1}>{list.data.name}</Text>
              <Pressable style={styles.headerBtn} onPress={() => setListsOpen(true)}><Text style={styles.headerBtnText}>📋</Text></Pressable>
              <Pressable style={styles.headerBtn} onPress={() => navigation.navigate('Preferences')}><Text style={styles.headerBtnText}>⚙️</Text></Pressable>
            </View>
            <View style={styles.inputCard}>
              <TextInput
                style={styles.input}
                multiline
                value={text}
                onChangeText={setText}
                placeholder={t.smart.inputPlaceholder}
                placeholderTextColor={colors.muted}
                autoCorrect={false}
                autoCapitalize="none"
              />
              <Text style={styles.hint}>{t.smart.inputHint}</Text>
              {(suggestions.data ?? []).length > 0 && lastToken.length >= 2 && (
                <View style={[styles.chips, { marginTop: spacing.sm }]}>
                  {suggestions.data!.map((c) => (
                    <Pressable key={c.code} style={styles.suggestChip} onPress={() => addConcept.mutate(c)}>
                      <Text style={styles.suggestText}>+ {language === 'en' ? c.nameEn : c.nameHy}</Text>
                    </Pressable>
                  ))}
                </View>
              )}
              <Pressable style={[styles.addBtn, (!text.trim() || parse.isPending) && { opacity: 0.5 }]}
                disabled={!text.trim() || parse.isPending} onPress={() => parse.mutate(text)}>
                {parse.isPending ? <ActivityIndicator color="#fff" /> : <Text style={styles.addBtnText}>{t.smart.add}</Text>}
              </Pressable>
            </View>
            {feedback && (
              <View style={styles.feedback}>
                <Text style={styles.feedbackText}>✓ {t.smart.lastAdded(feedback.length)}</Text>
                {unrecognisedFeedback.length > 0 && (
                  <Text style={styles.warnText}>{t.smart.unrecognised}: {unrecognisedFeedback.map((f) => f.raw).join(', ')}</Text>
                )}
              </View>
            )}
          </View>
        }
        ListEmptyComponent={<Empty text={t.smart.empty} />}
        renderItem={({ item }) => <ItemCard item={item} concept={item.conceptCode ? conceptByCode.get(item.conceptCode) : undefined} list={list.data!} />}
        ListFooterComponent={
          items.length > 0 ? (
            <Pressable style={styles.compareBtn} onPress={() => navigation.navigate('Results')}>
              <Text style={styles.compareText}>{t.smart.compare} →</Text>
            </Pressable>
          ) : null
        }
      />
      <ListsModal visible={listsOpen} onClose={() => setListsOpen(false)} currentId={list.data.id} />
    </SafeAreaView>
  );
}

function ListsModal({ visible, onClose, currentId }: { visible: boolean; onClose: () => void; currentId: number }) {
  const { t } = useI18n();
  const qc = useQueryClient();
  const lists = useQuery({
    queryKey: ['savedLists', visible],
    queryFn: async () => {
      const ids = await getSavedListIds();
      return ids.length ? api.lists(ids) : [];
    },
    enabled: visible,
  });
  const open = async (id: number) => {
    await setShoppingListId(id);
    await qc.invalidateQueries({ queryKey: keys.list });
    onClose();
  };
  const create = useMutation({
    mutationFn: async () => api.createList(t.smart.listName((lists.data?.length ?? 0) + 1)),
    onSuccess: async (l) => {
      await rememberList(l.id);
      await open(l.id);
    },
  });
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.modalBackdrop} onPress={onClose}>
        <Pressable style={styles.modal} onPress={() => undefined}>
          <Text style={styles.modalTitle}>{t.smart.myLists}</Text>
          {(lists.data ?? []).map((l) => (
            <Pressable key={l.id} style={[styles.listRow, l.id === currentId && styles.listRowActive]} onPress={() => open(l.id)}>
              <Text style={styles.listName}>{l.name}</Text>
              <Text style={styles.listCount}>{l.items.length}</Text>
            </Pressable>
          ))}
          <Pressable style={styles.addBtn} onPress={() => create.mutate()}>
            <Text style={styles.addBtnText}>+ {t.smart.newList}</Text>
          </Pressable>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, paddingBottom: spacing.xl * 3 },
  headerRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.md },
  title: { flex: 1, fontSize: 24, fontWeight: '900', color: colors.text },
  headerBtn: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.card, alignItems: 'center', justifyContent: 'center', ...shadow },
  headerBtnText: { fontSize: 18 },
  inputCard: { backgroundColor: colors.card, borderRadius: radius.lg, padding: spacing.md, ...shadow },
  input: { minHeight: 96, fontSize: 17, color: colors.text, textAlignVertical: 'top' },
  hint: { fontSize: 12, color: colors.muted, marginTop: 4 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  suggestChip: { backgroundColor: colors.primarySoft, borderRadius: 14, paddingHorizontal: 10, paddingVertical: 6 },
  suggestText: { color: colors.primary, fontWeight: '700' },
  addBtn: { marginTop: spacing.md, backgroundColor: colors.primary, borderRadius: radius.sm, paddingVertical: 13, alignItems: 'center' },
  addBtnText: { color: '#fff', fontSize: 16, fontWeight: '800' },
  feedback: { marginTop: spacing.sm, padding: spacing.sm, gap: 2 },
  feedbackText: { color: colors.success, fontWeight: '700' },
  card: { backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.md, marginTop: spacing.md, borderWidth: 2,
    borderColor: 'transparent', ...shadow },
  cardWarn: { borderColor: colors.accent },
  cardMuted: { opacity: 0.75 },
  cardTop: { flexDirection: 'row', gap: spacing.sm },
  itemTitle: { fontSize: 17, fontWeight: '800', color: colors.text },
  extras: { fontSize: 13, color: colors.primary, fontWeight: '700', marginTop: 2 },
  raw: { fontSize: 12, color: colors.muted, marginTop: 2 },
  removeX: { fontSize: 18, color: colors.muted, paddingHorizontal: 4 },
  choiceBox: { marginTop: spacing.sm, gap: 6 },
  choiceTitle: { fontWeight: '800', color: '#9A6100' },
  choiceChip: { borderWidth: 1.5, borderColor: colors.accent, borderRadius: 14, paddingHorizontal: 10, paddingVertical: 6, backgroundColor: '#FFF8EB' },
  choiceChipText: { color: '#6B4400', fontWeight: '700' },
  sizeChip: { borderWidth: 1, borderColor: colors.border, borderRadius: 12, paddingHorizontal: 10, paddingVertical: 5 },
  sizeChipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  sizeChipText: { color: colors.text, fontWeight: '600' },
  sizeChipTextActive: { color: '#fff' },
  cardBottom: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: spacing.md },
  priceBox: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  fromPrice: { fontSize: 15, fontWeight: '800', color: colors.primary },
  noPrice: { color: colors.danger, fontWeight: '600' },
  warnText: { color: '#9A6100', marginTop: spacing.sm, fontWeight: '600' },
  qty: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  qtyBtn: { width: 34, height: 34, borderRadius: 17, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center' },
  qtyBtnText: { fontSize: 20, fontWeight: '800', color: colors.primary },
  qtyText: { minWidth: 34, textAlign: 'center', fontWeight: '800', fontSize: 15 },
  compareBtn: { marginTop: spacing.xl, backgroundColor: colors.accent, borderRadius: radius.md, paddingVertical: 18, alignItems: 'center', ...shadow },
  compareText: { color: '#1C1300', fontSize: 18, fontWeight: '900' },
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'flex-end' },
  modal: { backgroundColor: colors.card, borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: spacing.lg, gap: spacing.sm, paddingBottom: spacing.xl * 2 },
  modalTitle: { fontSize: 20, fontWeight: '900', marginBottom: spacing.sm },
  listRow: { flexDirection: 'row', justifyContent: 'space-between', padding: spacing.md, borderRadius: radius.sm, backgroundColor: colors.background },
  listRowActive: { backgroundColor: colors.primarySoft },
  listName: { fontWeight: '700', color: colors.text },
  listCount: { color: colors.muted },
});
