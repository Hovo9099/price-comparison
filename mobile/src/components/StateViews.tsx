import React from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { useI18n } from '../i18n';
import type { Strings } from '../i18n/hy';
import { ApiError } from '../api/client';
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

/** User-facing text for an error, always in the app language (server error details are not shown). */
export function errorText(t: Strings, error: unknown): string {
  if (error instanceof TypeError) return t.serverUnreachable;
  if (error instanceof ApiError) {
    if (error.status === 404) return t.httpError.notFound;
    if (error.status === 409) return t.httpError.conflict;
    if (error.status === 429) return t.httpError.tooMany;
    if (error.status >= 500) return t.httpError.server;
    return t.httpError.badRequest;
  }
  return t.httpError.server;
}

export function ErrorView({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n();
  const message = errorText(t, error);
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
