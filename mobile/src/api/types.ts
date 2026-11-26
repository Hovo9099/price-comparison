// Mirrors the backend DTOs (am.pricecompare.web.dto).

export type Freshness = 'FRESH' | 'RECENT' | 'STALE' | 'OUTDATED';

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

/** What is known about a store's catalog: NOT_FOUND is only meaningful for COMPLETE catalogs. */
export type CatalogState = 'COMPLETE' | 'PARTIAL' | 'NOT_IMPORTED' | 'UNAVAILABLE' | 'NOT_IMPLEMENTED';

export interface Store {
  id: number;
  name: string;
  slug: string;
  websiteUrl: string | null;
  logoUrl: string | null;
  active: boolean;
  integrated?: boolean;
  catalogState?: CatalogState;
  catalogMessage?: string | null;
  listings?: number;
  pricedListings?: number;
  lastCompleteCrawlAt?: string | null;
  lastPriceUpdateAt?: string | null;
  delivery?: DeliveryTerms | null;
}

/** Delivery terms as published by the store; null amounts are unknown. */
export interface DeliveryTerms {
  fee: number | null;
  freeFrom: number | null;
  minOrder: number | null;
  smallOrderBelow: number | null;
  smallOrderFee: number | null;
  source: string | null;
  checkedAt: string | null;
}

/** One store's own listing with its own price (never merged with other stores). */
export interface Offer {
  storeId: number;
  storeName: string;
  storeSlug: string;
  storeProductId: number;
  externalProductId: string | null;
  externalName: string;
  brand: string | null;
  size: string | null;
  imageUrl: string | null;
  externalUrl: string | null;
  price: number;
  originalPrice: number | null;
  discountPercent: number | null;
  unitPrice: number | null;
  unitPriceUnit: string | null;
  currency: string;
  available: boolean;
  usable: boolean;
  capturedAt: string;
  freshness: Freshness;
}

export interface StoreMatch {
  storeId: number;
  storeName: string;
  storeSlug: string;
  matches: number;
  catalogState: CatalogState;
  message: string | null;
}

export interface SearchResult extends Page<ProductSummary> {
  totalMatches: number;
  stores: StoreMatch[];
}

export type StoreRowStatus =
  | 'AVAILABLE' | 'OUT_OF_STOCK' | 'OUTDATED' | 'NOT_FOUND' | 'NOT_YET_IMPORTED' | 'CATALOG_UNAVAILABLE' | 'NOT_INTEGRATED';

export interface StoreRow {
  storeId: number;
  storeName: string;
  storeSlug: string;
  status: StoreRowStatus;
  message: string | null;
  offers: Offer[];
}

export interface Category {
  id: number;
  name: string;
  nameEn: string | null;
  slug: string;
  icon: string | null;
  productCount: number;
}

export interface Cheapest {
  price: number;
  currency: string;
  storeId: number;
  storeName: string;
  storeSlug: string;
  capturedAt: string;
  freshness: Freshness;
}

export interface ProductSummary {
  id: number;
  name: string;
  nameEn: string | null;
  brand: string | null;
  size: string | null;
  soldByWeight: boolean;
  imageUrl: string | null;
  categoryId: number | null;
  cheapest: Cheapest | null;
  maxPrice: number | null;
  storeCount: number;
  offers?: Offer[];
}

export interface ProductDetail {
  id: number;
  name: string;
  nameEn: string | null;
  brand: string | null;
  barcode: string | null;
  sizeValue: number | null;
  sizeUnit: string | null;
  size: string | null;
  variant: string | null;
  soldByWeight: boolean;
  imageUrl: string | null;
  category: Category | null;
  aliases: string[];
}

export interface StorePrice {
  storeId: number;
  storeName: string;
  storeSlug: string;
  storeProductId: number;
  externalName: string;
  externalUrl: string | null;
  price: number;
  originalPrice: number | null;
  discountPercent: number | null;
  unitPrice: number | null;
  unitPriceUnit: string | null;
  currency: string;
  available: boolean;
  soldByWeight: boolean;
  capturedAt: string;
  validFrom: string;
  ageHours: number;
  freshness: Freshness;
  cheapest: boolean;
}

export interface PriceComparison {
  product: ProductDetail;
  cheapest: Cheapest | null;
  mostExpensive: number | null;
  potentialSaving: number | null;
  prices: StorePrice[];
}

export interface ProductOffers {
  product: ProductDetail;
  cheapest: Cheapest | null;
  storesWithOffer: number;
  stores: StoreRow[];
}

export interface PricePoint {
  capturedAt: string;
  price: number;
  originalPrice: number | null;
  available: boolean;
}

export interface PriceHistory {
  productId: number;
  days: number;
  series: { storeId: number; storeName: string; storeSlug: string; points: PricePoint[] }[];
}

export interface PriceDrop {
  product: ProductSummary;
  storeId: number;
  storeName: string;
  previousPrice: number;
  currentPrice: number;
  dropPercent: number;
  changedAt: string;
}

export interface ShoppingListItem {
  id: number;
  productId: number | null;
  name: string;
  nameEn: string | null;
  size: string | null;
  imageUrl: string | null;
  freeText: string | null;
  quantity: number;
  cheapest: Cheapest | null;
  conceptCode: string | null;
  sizeValue: number | null;
  sizeUnit: string | null;
  percent: number | null;
  preferredBrand: string | null;
  rawText: string | null;
  requiresChoice: boolean;
  soldByWeight: boolean;
}

export interface ShoppingList {
  id: number;
  name: string;
  createdAt: string;
  updatedAt: string;
  items: ShoppingListItem[];
}

export interface BasketLine {
  itemId: number;
  productId: number;
  name: string;
  quantity: number;
  unitPrice: number;
  total: number;
  externalName: string | null;
  capturedAt: string | null;
  freshness: Freshness | null;
}

export interface StoreBasket {
  storeId: number;
  storeName: string;
  storeSlug: string;
  total: number;
  itemCount: number;
  complete: boolean;
  items: BasketLine[];
  missingItemIds: number[];
}

export interface BasketOptimization {
  shoppingListId: number;
  currency: string;
  cheapestSingleStore: StoreBasket | null;
  singleStoreOptions: StoreBasket[];
  cheapestMultiStore: {
    total: number;
    storeCount: number;
    complete: boolean;
    stores: StoreBasket[];
    missingItemIds: number[];
  };
  savings: number | null;
  unavailableItems: { itemId: number; productId: number | null; name: string; reason: string }[];
  containsStalePrices: boolean;
  calculatedAt: string;
}

// ---- Smart shopping list -------------------------------------------------------------------------

export interface ConceptRef {
  code: string;
  nameHy: string;
  nameEn: string;
}

export interface Concept extends ConceptRef {
  nameRu: string | null;
  category: string | null;
  defaultSizeValue: number | null;
  defaultSizeUnit: string | null;
  sizes: number[];
  soldByWeight: boolean;
  requiresChoice: boolean;
  choices: ConceptRef[];
  alternatives: ConceptRef[];
}

export type ParseStatus = 'RECOGNIZED' | 'AMBIGUOUS' | 'NEEDS_CHOICE' | 'UNRECOGNIZED';

export interface ParsedItem {
  raw: string;
  phrase: string;
  status: ParseStatus;
  confidence: number;
  concept: Concept | null;
  alternatives: Concept[];
  quantity: number;
  quantityUnit: 'pcs' | 'kg';
  sizeValue: number | null;
  sizeUnit: string | null;
  percent: number | null;
  hint: string | null;
  matchingProducts: number;
  productSuggestions: ProductSummary[];
}

export interface ParseResult {
  items: ParsedItem[];
  list: ShoppingList | null;
}

export interface ComparePreferences {
  maxStores?: number | null;
  preferOneStore?: boolean;
  includeDelivery?: boolean;
  allowStalePrices?: boolean;
  preferredBrands?: string[];
}

export type ItemStatus = 'OK' | 'NEEDS_CHOICE' | 'NO_MATCHING_PRODUCT' | 'NO_CURRENT_PRICE' | 'UNRECOGNIZED';

export interface ItemStatusInfo {
  itemId: number;
  label: string;
  conceptCode: string | null;
  productId: number | null;
  quantity: number;
  quantityUnit: string;
  sizeValue: number | null;
  sizeUnit: string | null;
  status: ItemStatus;
  comparableProducts: number;
  note: string | null;
  choices: ConceptRef[];
}

export interface CompareLine {
  itemId: number;
  itemLabel: string;
  productId: number;
  productName: string;
  brand: string | null;
  storeProductId: number;
  externalName: string;
  externalUrl: string | null;
  imageUrl: string | null;
  price: number;
  pricePerUnit: number;
  perUnit: 'l' | 'kg' | 'pcs' | 'pack';
  quantity: number;
  quantityUnit: string;
  lineTotal: number;
  capturedAt: string;
  freshness: Freshness;
  pricePercentile: number | null;
  comparableProducts: number;
  medianPricePerUnit: number | null;
  reasonCode: 'CHEAPEST' | 'MID_RANGE' | 'PREMIUM' | 'ONLY_OPTION' | 'FIXED_PRODUCT';
  reason: string;
}

export interface StoreOption {
  storeId: number;
  storeName: string;
  storeSlug: string;
  subtotal: number;
  deliveryFee: number | null;
  deliveryFeeKnown: boolean;
  total: number;
  lines: CompareLine[];
}

export interface CompareOption {
  total: number;
  storeCount: number;
  complete: boolean;
  stores: StoreOption[];
  missingItemIds: number[];
}

export type StrategyName = 'CHEAPEST' | 'MID_RANGE' | 'PREMIUM';

export interface StrategyResult {
  strategy: StrategyName;
  recommended: 'SINGLE_STORE' | 'MULTI_STORE';
  total: number | null;
  singleStore: CompareOption | null;
  multiStore: CompareOption;
  missingItemIds: number[];
  differenceVsOthers: Partial<Record<StrategyName, number>>;
  notes: string[];
}

export interface Comparison {
  comparisonId: number;
  shoppingListId: number;
  items: ItemStatusInfo[];
  strategies: StrategyResult[];
  warnings: string[];
  calculatedAt: string;
}

export interface PriceWatch {
  id: number;
  productId: number;
  name: string;
  nameEn: string | null;
  size: string | null;
  imageUrl: string | null;
  targetPrice: number | null;
  startPrice: number | null;
  cheapest: Cheapest | null;
  dropped: boolean;
  lastNotifiedAt: string | null;
  createdAt: string;
}

export type DealVerdict = 'GENUINE' | 'INFLATED' | 'UNVERIFIED';

export interface Deal {
  product: ProductSummary;
  storeId: number;
  storeName: string;
  storeSlug: string;
  price: number;
  originalPrice: number;
  discountPercent: number;
  verdict: DealVerdict;
  maxPaid90Days: number | null;
  historyDays: number;
  since: string;
  cheapestAnywhere: boolean;
}
