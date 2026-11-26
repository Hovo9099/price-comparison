/** Display metadata for known stores (brand colours and short badge labels). */
export const STORE_META: Record<string, { short: string; color: string }> = {
  sas: { short: 'SAS', color: '#E30613' },
  'yerevan-city': { short: 'YC', color: '#00843D' },
  carrefour: { short: 'C', color: '#004E9F' },
  'supermarket-am': { short: 'SM', color: '#6D2C91' },
  'nor-zovq': { short: 'NZ', color: '#F39200' },
  parma: { short: 'P', color: '#D4145A' },
};

export const storeMeta = (slug: string) => STORE_META[slug] ?? { short: slug.slice(0, 2).toUpperCase(), color: '#555' };

/** Short display names used in compact tables. */
export const storeShortName = (name: string) => name.replace(' Supermarket', '').replace(' Armenia', '');
