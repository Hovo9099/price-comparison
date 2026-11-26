import React from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { useI18n } from '../i18n';
import { colors, spacing } from './theme';

export function Loading() {
  const { t } = useI18n();
  return (
    <View style={styles.center}>
      <ActivityIndicator color={colors.primary} />
      <Text style={styles.muted}>{t.loading}</Text>
    </View>
  );
}

export function ErrorView({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n();
  const message = error instanceof TypeError ? t.serverUnreachable : error instanceof Error ? error.message : String(error);
  return (
    <View style={styles.center}>
      <Text style={styles.error}>{t.error}</Text>
      <Text style={styles.muted}>{message}</Text>
      {onRetry && (
        <Pressable style={styles.button} onPress={onRetry} accessibilityRole="button">
          <Text style={styles.buttonText}>{t.retry}</Text>
        </Pressable>
      )}
    </View>
  );
}

export function Empty({ text }: { text: string }) {
  return (
    <View style={styles.center}>
      <Text style={styles.muted}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { padding: spacing.xl, alignItems: 'center', justifyContent: 'center', gap: spacing.sm },
  muted: { color: colors.muted, textAlign: 'center' },
  error: { color: colors.danger, fontWeight: '700', fontSize: 16 },
  button: { backgroundColor: colors.primary, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, borderRadius: 8 },
  buttonText: { color: '#fff', fontWeight: '600' },
});
