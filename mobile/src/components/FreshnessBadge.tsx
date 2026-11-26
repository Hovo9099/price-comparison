import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { Freshness } from '../api/types';
import { useI18n } from '../i18n';
import { freshnessColor, timeAgo } from '../utils/format';

/** "Updated 2h ago" with a colour that turns amber/red when the price is stale/outdated. */
export function FreshnessBadge({ capturedAt, freshness }: { capturedAt: string; freshness: Freshness }) {
  const { t } = useI18n();
  const color = freshnessColor[freshness];
  const showLabel = freshness === 'STALE' || freshness === 'OUTDATED';
  return (
    <View style={styles.row}>
      <View style={[styles.dot, { backgroundColor: color }]} />
      <Text style={[styles.text, { color: showLabel ? color : '#6b7280' }]}>
        {showLabel ? `${t.freshness[freshness]} · ` : ''}
        {timeAgo(capturedAt, t)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  dot: { width: 7, height: 7, borderRadius: 4 },
  text: { fontSize: 12 },
});
