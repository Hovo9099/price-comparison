import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useWatchMutations, useWatches } from '../api/hooks';
import { useI18n } from '../i18n';
import { errorText } from './StateViews';
import { registerForPush } from '../notifications';
import { formatPrice } from '../utils/format';
import { colors, radius, spacing } from './theme';

/** "Follow price" on the product page: optional target price, push registration on first use. */
export function WatchButton({ productId }: { productId: number }) {
  const { t, language } = useI18n();
  const watches = useWatches();
  const { save, remove } = useWatchMutations();
  const current = watches.data?.find((w) => w.productId === productId);
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState('');
  const [invalid, setInvalid] = useState(false);
  const [pushOn, setPushOn] = useState<boolean | null>(null);

  const start = () => {
    setTarget(current?.targetPrice ? String(current.targetPrice) : '');
    setInvalid(false);
    setOpen(true);
  };

  const submit = () => {
    const raw = target.trim().replace(',', '.');
    const value = raw ? Number(raw) : null;
    if (value !== null && !(value > 0)) {
      setInvalid(true);
      return;
    }
    save.mutate(
      { productId, targetPrice: value },
      {
        onSuccess: async () => {
          setOpen(false);
          setPushOn(await registerForPush(language, true, t.watch.channel));
        },
      },
    );
  };

  if (open) {
    return (
      <View style={styles.panel}>
        <Text style={styles.label}>{t.watch.targetLabel}</Text>
        <TextInput
          style={styles.input}
          value={target}
          onChangeText={(v) => { setTarget(v); setInvalid(false); }}
          keyboardType="decimal-pad"
          placeholder={current?.cheapest ? String(Math.round(current.cheapest.price)) : ''}
          accessibilityLabel={t.watch.targetLabel}
        />
        {invalid ? <Text style={styles.error}>{t.watch.invalidTarget}</Text> : null}
        <Text style={styles.hint}>{t.watch.targetHint}</Text>
        <View style={styles.row}>
          <Pressable style={[styles.button, styles.primary]} onPress={submit} disabled={save.isPending} accessibilityRole="button">
            <Text style={styles.primaryText}>{t.watch.save}</Text>
          </Pressable>
          <Pressable style={styles.button} onPress={() => setOpen(false)} accessibilityRole="button">
            <Text style={styles.secondaryText}>{t.watch.cancel}</Text>
          </Pressable>
        </View>
        {save.isError ? <Text style={styles.error}>{errorText(t, save.error)}</Text> : null}
      </View>
    );
  }

  return (
    <View>
      {current ? (
        <View style={[styles.panel, styles.following]}>
          <Text style={styles.followingText}>
            🔔 {t.watch.following}
            {current.targetPrice ? ` · ${t.watch.target(formatPrice(current.targetPrice))}` : ''}
          </Text>
          <View style={styles.row}>
            <Pressable style={styles.button} onPress={start} accessibilityRole="button">
              <Text style={styles.secondaryText}>{t.watch.change}</Text>
            </Pressable>
            <Pressable style={styles.button} onPress={() => remove.mutate(productId)} disabled={remove.isPending} accessibilityRole="button">
              <Text style={[styles.secondaryText, { color: colors.danger }]}>{t.watch.stop}</Text>
            </Pressable>
          </View>
        </View>
      ) : (
        <Pressable style={styles.followButton} onPress={start} accessibilityRole="button">
          <Text style={styles.followText}>🔔 {t.watch.follow}</Text>
        </Pressable>
      )}
      {pushOn !== null ? <Text style={styles.hint}>{pushOn ? t.watch.pushOn : t.watch.pushOff}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  followButton: { marginTop: spacing.md, borderWidth: 2, borderColor: colors.primary, borderRadius: 14, paddingVertical: 13,
    alignItems: 'center', backgroundColor: colors.card },
  followText: { color: colors.primary, fontSize: 16, fontWeight: '800' },
  panel: { marginTop: spacing.md, backgroundColor: colors.card, borderRadius: radius.md, padding: spacing.lg,
    borderWidth: 1, borderColor: colors.border },
  following: { backgroundColor: colors.primarySoft, borderColor: colors.primarySoft },
  followingText: { color: colors.primaryDark, fontWeight: '700', fontSize: 15 },
  label: { fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  input: { borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md,
    paddingVertical: 10, fontSize: 16, color: colors.text, backgroundColor: '#fff' },
  hint: { color: colors.muted, fontSize: 13, marginTop: spacing.sm },
  error: { color: colors.danger, marginTop: spacing.xs },
  row: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md },
  button: { paddingHorizontal: spacing.lg, paddingVertical: 10, borderRadius: radius.sm },
  primary: { backgroundColor: colors.primary },
  primaryText: { color: '#fff', fontWeight: '700' },
  secondaryText: { color: colors.primary, fontWeight: '700' },
});
