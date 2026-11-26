import { formatPrice, freshnessOf, timeAgo } from '../utils/format';
import { hy } from '../i18n/hy';
import { en } from '../i18n/en';

describe('formatPrice', () => {
  it('groups thousands and uses the dram sign', () => {
    expect(formatPrice(1090)).toBe('1 090 ֏');
    expect(formatPrice(570)).toBe('570 ֏');
    expect(formatPrice(12450)).toBe('12 450 ֏');
    expect(formatPrice(1234567)).toBe('1 234 567 ֏');
  });
  it('keeps decimals only when present', () => {
    expect(formatPrice(99.5)).toBe('99.50 ֏');
  });
  it('handles missing values and other currencies', () => {
    expect(formatPrice(null)).toBe('—');
    expect(formatPrice(5, 'USD')).toBe('5 USD');
  });
});

describe('timeAgo', () => {
  const now = new Date('2026-10-08T12:00:00Z');
  it('formats in Armenian', () => {
    expect(timeAgo('2026-10-08T10:00:00Z', hy, now)).toBe('2 ժամ առաջ');
    expect(timeAgo('2026-10-07T11:00:00Z', hy, now)).toBe('1 օր առաջ');
    expect(timeAgo('2026-10-08T11:59:30Z', hy, now)).toBe('հենց նոր');
  });
  it('formats in English', () => {
    expect(timeAgo('2026-10-08T11:30:00Z', en, now)).toBe('30 min ago');
  });
});

describe('freshnessOf', () => {
  const now = new Date('2026-10-08T12:00:00Z');
  it('matches backend thresholds', () => {
    expect(freshnessOf('2026-10-08T00:00:00Z', now)).toBe('FRESH');
    expect(freshnessOf('2026-10-06T12:00:01Z', now)).toBe('RECENT');
    expect(freshnessOf('2026-10-04T12:00:00Z', now)).toBe('STALE');
    expect(freshnessOf('2026-09-30T12:00:00Z', now)).toBe('OUTDATED');
  });
});
