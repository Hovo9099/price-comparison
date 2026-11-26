// Admin API client. Base URL: VITE_API_URL (production) or same origin (dev proxy).
const BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/+$/, '') ?? '';
const TOKEN_KEY = 'adminToken';

export const getToken = () => localStorage.getItem(TOKEN_KEY) ?? '';
export const setToken = (t: string) => (t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY));

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...(init.headers as Record<string, string>) };
  if (init.body && !(init.body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const token = getToken();
  if (token) headers['X-Admin-Token'] = token;
  const res = await fetch(BASE + path, { ...init, headers });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      detail = (await res.json()).detail ?? detail;
    } catch {
      /* not json */
    }
    throw new ApiError(res.status, detail);
  }
  return res.json() as Promise<T>;
}

export interface Page<T> { items: T[]; page: number; size: number; total: number }
export type CrawlStatus = 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'UNAVAILABLE';

export interface CrawlRun {
  id: number; storeId: number; storeName: string; storeSlug: string; runType: string; status: CrawlStatus;
  startedAt: string; finishedAt: string | null; maxProducts: number | null; pagesVisited: number;
  productsDiscovered: number; productsUpdated: number; pricesUpdated: number; errors: number; errorMessage: string | null;
}
export type CatalogState = 'COMPLETE' | 'PARTIAL' | 'NOT_IMPORTED' | 'UNAVAILABLE' | 'NOT_IMPLEMENTED';

export interface StoreStatus {
  storeId: number; name: string; slug: string; websiteUrl: string; storeActive: boolean; providerAvailable: boolean;
  unavailableReason: string | null; running: boolean; products: number; activeProducts: number;
  lastRun: CrawlRun | null; lastSuccessfulRun: CrawlRun | null;
  /** false = no catalog parser exists: never shown as integrated */
  implemented: boolean;
  /** COMPLETE only after an unlimited crawl walked the whole catalog */
  catalogState: CatalogState; catalogMessage: string | null;
  pricedListings: number; lastCompleteRun: CrawlRun | null; lastPriceUpdate: string | null;
}
export interface Dashboard {
  stores: StoreStatus[]; canonicalProducts: number; storeProducts: number; currentPrices: number;
  pendingMatches: number; unmatchedStoreProducts: number;
}
export interface CrawlError { id: number; url: string | null; externalId: string | null; message: string; createdAt: string }
export interface CanonicalProduct {
  id: number; name: string; nameEn: string | null; brand: string | null; barcode: string | null; sizeValue: number | null;
  sizeUnit: string | null; variant: string | null; soldByWeight: boolean; categoryId: number | null; imageUrl: string | null;
  active: boolean; storeProductCount: number;
}
export interface StoreProduct {
  id: number; storeId: number; storeName: string; externalProductId: string; externalSku: string | null; externalName: string;
  externalNameEn: string | null; externalUrl: string | null; barcode: string | null; brand: string | null;
  packageSize: number | null; packageUnit: string | null; active: boolean; canonicalProductId: number | null;
  canonicalName: string | null; currentPrice: number | null; capturedAt: string | null; lastSeenAt: string | null;
}
export interface MatchCandidate {
  id: number; confidence: number; status: string; reason: string; createdAt: string;
  storeProduct: StoreProduct; candidate: CanonicalProduct;
}
export interface Category { id: number; name: string; nameEn: string | null; slug: string; productCount: number }
export interface CsvResult { rows: number; imported: number; pricesChanged: number; runIds: number[]; errors: { line: number; message: string }[] }

const qs = (p: Record<string, unknown>) =>
  Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== '').map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join('&');

export type AnomalyStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'SUPERSEDED';
export interface PriceAnomaly {
  id: number; kind: 'SUDDEN_DROP' | 'SUDDEN_RISE' | 'BELOW_MARKET' | 'ABOVE_MARKET'; status: AnomalyStatus;
  newPrice: number; referencePrice: number; storeProductId: number; storeName: string; productName: string;
  productUrl: string | null; canonicalProductId: number | null; detectedAt: string; reviewedAt: string | null;
}

export const adminApi = {
  anomalies: (status: AnomalyStatus, page: number) =>
    request<Page<PriceAnomaly>>(`/api/admin/anomalies?${qs({ status, page, size: 50 })}`),
  anomalyCounts: () => request<{ pending: number }>('/api/admin/anomalies/counts'),
  confirmAnomaly: (id: number) => request<PriceAnomaly>(`/api/admin/anomalies/${id}/confirm`, { method: 'POST' }),
  rejectAnomaly: (id: number) => request<PriceAnomaly>(`/api/admin/anomalies/${id}/reject`, { method: 'POST' }),
  dashboard: () => request<Dashboard>('/api/admin/dashboard'),
  runCrawler: (slug: string, maxProducts?: number) =>
    request<{ runId: number }>(`/api/admin/crawlers/${slug}/run?${qs({ maxProducts })}`, { method: 'POST' }),
  cancelRun: (id: number) => request<{ cancelled: boolean }>(`/api/admin/crawl-runs/${id}/cancel`, { method: 'POST' }),
  runs: (storeId?: number, page = 0) => request<Page<CrawlRun>>(`/api/admin/crawl-runs?${qs({ storeId, page, size: 30 })}`),
  runErrors: (id: number, page = 0) => request<Page<CrawlError>>(`/api/admin/crawl-runs/${id}/errors?${qs({ page, size: 50 })}`),
  canonical: (q: string, page = 0) => request<Page<CanonicalProduct>>(`/api/admin/canonical-products?${qs({ q, page, size: 30 })}`),
  canonicalDetail: (id: number) =>
    request<{ product: CanonicalProduct; storeProducts: StoreProduct[] }>(`/api/admin/canonical-products/${id}`),
  updateCanonical: (id: number, body: Partial<CanonicalProduct>) =>
    request<CanonicalProduct>(`/api/admin/canonical-products/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  storeProducts: (p: { storeId?: number; unmatched?: boolean; q?: string; page?: number }) =>
    request<Page<StoreProduct>>(`/api/admin/store-products?${qs({ ...p, size: 30 })}`),
  mapStoreProduct: (id: number, canonicalProductId: number) =>
    request<StoreProduct>(`/api/admin/store-products/${id}/map`, { method: 'POST', body: JSON.stringify({ canonicalProductId }) }),
  candidates: (status = 'PENDING', page = 0) => request<Page<MatchCandidate>>(`/api/admin/match-candidates?${qs({ status, page, size: 20 })}`),
  approve: (id: number) => request(`/api/admin/match-candidates/${id}/approve`, { method: 'POST' }),
  reject: (id: number) => request(`/api/admin/match-candidates/${id}/reject`, { method: 'POST' }),
  importCsv: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return request<CsvResult>('/api/admin/import/csv', { method: 'POST', body: fd });
  },
  categories: () => request<Category[]>('/api/categories'),
};

export const fmtDate = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleString('hy-AM') : '—');
export const fmtNum = (n: number | null | undefined) => (n === null || n === undefined ? '—' : n.toLocaleString('en-US').replace(/,/g, ' '));
export const fmtPrice = (n: number | null | undefined) => (n === null || n === undefined ? '—' : `${fmtNum(Math.round(n))} ֏`);
