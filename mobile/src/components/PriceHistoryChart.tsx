import React, { useMemo, useState } from 'react';
import { LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Line, Polyline, Text as SvgText } from 'react-native-svg';
import type { PriceHistory } from '../api/types';
import { useI18n } from '../i18n';
import { formatPrice, storeColor } from '../utils/format';
import { colors } from './theme';

const HEIGHT = 180;
const PAD = { top: 12, right: 12, bottom: 22, left: 56 };

/** Step-free line chart of observed prices per store over time. */
export function PriceHistoryChart({ history }: { history: PriceHistory }) {
  const { t } = useI18n();
  const [width, setWidth] = useState(0);
  const series = history.series.filter((s) => s.points.length > 0);

  const bounds = useMemo(() => {
    const pts = series.flatMap((s) => s.points);
    if (pts.length === 0) return null;
    const times = pts.map((p) => new Date(p.capturedAt).getTime());
    const prices = pts.map((p) => p.price);
    let minP = Math.min(...prices);
    let maxP = Math.max(...prices);
    if (minP === maxP) {
      minP = minP * 0.95;
      maxP = maxP * 1.05;
    }
    return { minT: Math.min(...times), maxT: Math.max(...times), minP, maxP };
  }, [series]);

  const onLayout = (e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width);
  const totalPoints = series.reduce((n, s) => n + s.points.length, 0);
  if (!bounds || totalPoints < 2) {
    return <Text style={styles.muted}>{t.notEnoughHistory}</Text>;
  }

  const plotW = Math.max(1, width - PAD.left - PAD.right);
  const plotH = HEIGHT - PAD.top - PAD.bottom;
  const x = (iso: string) => {
    const span = bounds.maxT - bounds.minT || 1;
    return PAD.left + ((new Date(iso).getTime() - bounds.minT) / span) * plotW;
  };
  const y = (p: number) => PAD.top + (1 - (p - bounds.minP) / (bounds.maxP - bounds.minP)) * plotH;
  const fmtDate = (ms: number) => {
    const d = new Date(ms);
    return `${d.getDate()}.${String(d.getMonth() + 1).padStart(2, '0')}`;
  };

  return (
    <View onLayout={onLayout}>
      {width > 0 && (
        <Svg width={width} height={HEIGHT}>
          {[bounds.minP, (bounds.minP + bounds.maxP) / 2, bounds.maxP].map((p) => (
            <React.Fragment key={p}>
              <Line x1={PAD.left} x2={width - PAD.right} y1={y(p)} y2={y(p)} stroke={colors.border} strokeWidth={1} />
              <SvgText x={PAD.left - 6} y={y(p) + 4} fontSize={10} fill={colors.muted} textAnchor="end">
                {formatPrice(Math.round(p))}
              </SvgText>
            </React.Fragment>
          ))}
          <SvgText x={PAD.left} y={HEIGHT - 6} fontSize={10} fill={colors.muted}>
            {fmtDate(bounds.minT)}
          </SvgText>
          <SvgText x={width - PAD.right} y={HEIGHT - 6} fontSize={10} fill={colors.muted} textAnchor="end">
            {fmtDate(bounds.maxT)}
          </SvgText>
          {series.map((s) => {
            const color = storeColor(s.storeSlug);
            const pts = s.points.map((p) => `${x(p.capturedAt)},${y(p.price)}`).join(' ');
            return (
              <React.Fragment key={s.storeId}>
                {s.points.length > 1 && <Polyline points={pts} fill="none" stroke={color} strokeWidth={2} />}
                {s.points.map((p) => (
                  <Circle key={p.capturedAt} cx={x(p.capturedAt)} cy={y(p.price)} r={3} fill={p.available ? color : '#fff'} stroke={color} />
                ))}
              </React.Fragment>
            );
          })}
        </Svg>
      )}
      <View style={styles.legend}>
        {series.map((s) => (
          <View key={s.storeId} style={styles.legendItem}>
            <View style={[styles.swatch, { backgroundColor: storeColor(s.storeSlug) }]} />
            <Text style={styles.legendText}>{s.storeName}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  muted: { color: colors.muted, paddingVertical: 8 },
  legend: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginTop: 6 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  swatch: { width: 10, height: 10, borderRadius: 2 },
  legendText: { fontSize: 12, color: colors.text },
});
