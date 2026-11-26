import type { Freshness } from '../api/types';
import type { Strings } from '../i18n/hy';

/** 1090 -> "1 090 ֏" (Armenian dram, thin grouping). */
export function formatPrice(value: number | null | undefined, currency = 'AMD'): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  const rounded = Math.round(value * 100) / 100;
  const [int, frac] = rounded.toFixed(rounded % 1 === 0 ? 0 : 2).split('.');
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  const symbol = currency === 'AMD' ? '֏' : currency;
  return `${grouped}${frac ? '.' + frac : ''} ${symbol}`;
}

/** Human readable age of a timestamp, e.g. "2 ժամ առաջ". */
export function timeAgo(iso: string | null | undefined, t: Strings, now: Date = new Date()): string {
  if (!iso) return '—';
  const diffMs = now.getTime() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return t.time.justNow;
  if (minutes < 60) return t.time.minutesAgo(minutes);
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t.time.hoursAgo(hours);
  return t.time.daysAgo(Math.floor(hours / 24));
}

export const freshnessColor: Record<Freshness, string> = {
  FRESH: '#1b8a3a',
  RECENT: '#4a7d1e',
  STALE: '#b26a00',
  OUTDATED: '#b00020',
};

/** Same thresholds as the backend: 0-24h FRESH, 24-72h RECENT, 3-7d STALE, older OUTDATED. */
export function freshnessOf(iso: string, now: Date = new Date()): Freshness {
  const hours = (now.getTime() - new Date(iso).getTime()) / 3600000;
  if (hours < 24) return 'FRESH';
  if (hours < 72) return 'RECENT';
  if (hours < 24 * 7) return 'STALE';
  return 'OUTDATED';
}

export { storeMeta } from './stores';
import { storeMeta } from './stores';

export function storeColor(slug: string): string {
  return storeMeta(slug).color;
}
