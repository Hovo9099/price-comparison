import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { storeMeta } from '../utils/stores';

/** Round badge in the store's brand colour with its short label (SAS, YC, C, SM). */
export function StoreBadge({ slug, size = 32 }: { slug: string; size?: number }) {
  const m = storeMeta(slug);
  return (
    <View style={[styles.badge, { width: size, height: size, borderRadius: size / 2, backgroundColor: m.color }]}>
      <Text style={[styles.text, { fontSize: size * (m.short.length > 2 ? 0.3 : 0.38) }]}>{m.short}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: { alignItems: 'center', justifyContent: 'center' },
  text: { color: '#fff', fontWeight: '800' },
});
