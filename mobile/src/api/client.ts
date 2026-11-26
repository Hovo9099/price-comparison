import { apiBaseUrl } from './config';
import type {
  BasketOptimization,
  Comparison,
  ComparePreferences,
  Concept,
  ParseResult,
  Category,
  Page,
  PriceComparison,
  PriceDrop,
  PriceHistory,
  PriceWatch,
  ProductDetail,
  ProductOffers,
  ProductSummary,
  SearchResult,
  ShoppingList,
  Store,
} from './types';

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(apiBaseUrl() + path, {
    ...init,
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (res.status === 204) {
    return undefined as T;
  }
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = await res.json();
      detail = body.detail ?? detail;
    } catch {
      // not JSON
    }
    throw new ApiError(res.status, detail);
  }
  return (await res.json()) as T;
}

const q = (params: Record<string, string | number | undefined | null>) =>
  Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&');

export const api = {
  searchProducts: (query: string, page = 0, categoryId?: number) =>
    request<SearchResult>(`/api/products/search?${q({ q: query, page, size: 20, categoryId })}`),
  browseProducts: (categoryId: number | undefined, page = 0) =>
    request<Page<ProductSummary>>(`/api/products?${q({ categoryId, page, size: 20 })}`),
  product: (id: number) => request<ProductDetail>(`/api/products/${id}`),
  productByBarcode: (code: string) =>
    request<ProductDetail>(`/api/products/by-barcode/${encodeURIComponent(code)}`),
  prices: (id: number) => request<PriceComparison>(`/api/products/${id}/prices`),
  offers: (id: number) => request<ProductOffers>(`/api/products/${id}/offers`),
  history: (id: number, days = 90) => request<PriceHistory>(`/api/products/${id}/history?days=${days}`),
  priceDrops: (days = 7, limit = 10) => request<PriceDrop[]>(`/api/products/price-drops?${q({ days, limit })}`),
  categories: () => request<Category[]>('/api/categories'),
  stores: () => request<Store[]>('/api/stores'),
  createList: (name: string) => request<ShoppingList>('/api/shopping-lists', { method: 'POST', body: JSON.stringify({ name }) }),
  getList: (id: number) => request<ShoppingList>(`/api/shopping-lists/${id}`),
  addItem: (listId: number, body: { productId?: number; conceptCode?: string; freeText?: string; quantity?: number }) =>
    request<ShoppingList>(`/api/shopping-lists/${listId}/items`, { method: 'POST', body: JSON.stringify(body) }),
  updateItem: (listId: number, itemId: number, quantity: number) =>
    request<ShoppingList>(`/api/shopping-lists/${listId}/items/${itemId}`, { method: 'PATCH', body: JSON.stringify({ quantity }) }),
  patchItem: (
    listId: number,
    itemId: number,
    body: { quantity?: number; sizeValue?: number; sizeUnit?: string; clearSize?: boolean; conceptCode?: string; productId?: number },
  ) => request<ShoppingList>(`/api/shopping-lists/${listId}/items/${itemId}`, { method: 'PATCH', body: JSON.stringify(body) }),
  lists: (ids: number[]) => request<ShoppingList[]>(`/api/shopping-lists?ids=${ids.join(',')}`),
  parse: (text: string) => request<ParseResult>('/api/shopping-lists/parse', { method: 'POST', body: JSON.stringify({ text }) }),
  parseInto: (listId: number, text: string) =>
    request<ParseResult>(`/api/shopping-lists/${listId}/parse`, { method: 'POST', body: JSON.stringify({ text, add: true }) }),
  compare: (listId: number, prefs: ComparePreferences) =>
    request<Comparison>(`/api/shopping-lists/${listId}/compare`, { method: 'POST', body: JSON.stringify(prefs) }),
  concepts: () => request<Concept[]>('/api/lexicon/concepts'),
  suggestConcepts: (q: string) => request<Concept[]>(`/api/lexicon/suggest?${q && `q=${encodeURIComponent(q)}`}&limit=6`),
  removeItem: (listId: number, itemId: number) =>
    request<ShoppingList>(`/api/shopping-lists/${listId}/items/${itemId}`, { method: 'DELETE' }),
  registerDevice: (deviceId: string, pushToken: string | null, language: string) =>
    request<void>('/api/devices/me', { method: 'PUT', headers: { 'X-Device-Id': deviceId }, body: JSON.stringify({ pushToken, language }) }),
  watches: (deviceId: string) => request<PriceWatch[]>('/api/watches', { headers: { 'X-Device-Id': deviceId } }),
  watch: (deviceId: string, productId: number, targetPrice?: number | null) =>
    request<PriceWatch>(`/api/watches/${productId}`, {
      method: 'PUT',
      headers: { 'X-Device-Id': deviceId },
      body: JSON.stringify({ targetPrice: targetPrice ?? null }),
    }),
  unwatch: (deviceId: string, productId: number) =>
    request<void>(`/api/watches/${productId}`, { method: 'DELETE', headers: { 'X-Device-Id': deviceId } }),
  optimize: (listId: number, maxStores?: number) =>
    request<BasketOptimization>(`/api/shopping-lists/${listId}/optimize`, {
      method: 'POST',
      body: JSON.stringify(maxStores ? { maxStores } : {}),
    }),
};
