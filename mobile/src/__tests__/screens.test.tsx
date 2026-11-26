import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { I18nProvider } from '../i18n';
import { ProductDetailsScreen } from '../screens/ProductDetailsScreen';
import { BasketScreen } from '../screens/BasketScreen';
import { SearchScreen } from '../screens/SearchScreen';
import { ShoppingListScreen } from '../screens/ShoppingListScreen';
import { api } from '../api/client';
import type { ProductOffers, BasketOptimization, PriceComparison, PriceHistory, ShoppingList } from '../api/types';

// Fictional, test-only data.
jest.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  api: {
    prices: jest.fn(),
    offers: jest.fn(),
    history: jest.fn(),
    searchProducts: jest.fn(),
    getList: jest.fn(),
    createList: jest.fn(),
    addItem: jest.fn(),
    optimize: jest.fn(),
  },
}));
const mocked = api as jest.Mocked<typeof api>;

const now = new Date().toISOString();
const twoDaysAgo = new Date(Date.now() - 50 * 3600_000).toISOString();

const comparison: PriceComparison = {
  product: {
    id: 1, name: 'TEST Կոլա 1.5լ', nameEn: 'TEST Cola 1.5L', brand: 'TestBrand', barcode: '0000000000017',
    sizeValue: 1500, sizeUnit: 'ml', size: '1.5 լ', variant: null, soldByWeight: false, imageUrl: null, category: null, aliases: [],
  },
  cheapest: { price: 570, currency: 'AMD', storeId: 2, storeName: 'Yerevan City', storeSlug: 'yerevan-city', capturedAt: now, freshness: 'FRESH' },
  mostExpensive: 600,
  potentialSaving: 30,
  prices: [
    { storeId: 2, storeName: 'Yerevan City', storeSlug: 'yerevan-city', storeProductId: 11, externalName: 'TEST Cola', externalUrl: null,
      price: 570, originalPrice: 650, discountPercent: 12, unitPrice: null, unitPriceUnit: null, currency: 'AMD', available: true,
      soldByWeight: false, capturedAt: now, validFrom: now, ageHours: 0, freshness: 'FRESH', cheapest: true },
    { storeId: 3, storeName: 'Carrefour Armenia', storeSlug: 'carrefour', storeProductId: 12, externalName: 'TEST Cola', externalUrl: null,
      price: 600, originalPrice: null, discountPercent: null, unitPrice: null, unitPriceUnit: null, currency: 'AMD', available: true,
      soldByWeight: false, capturedAt: twoDaysAgo, validFrom: twoDaysAgo, ageHours: 50, freshness: 'RECENT', cheapest: false },
  ],
};
const offer = (storeId: number, storeName: string, storeSlug: string, price: number, capturedAt: string) => ({
  storeId, storeName, storeSlug, storeProductId: storeId * 10, externalProductId: `X${storeId}`, externalName: `TEST Cola (${storeSlug})`,
  brand: null, size: '1.5 լ', imageUrl: null, externalUrl: null, price, originalPrice: null, discountPercent: null, unitPrice: null,
  unitPriceUnit: null, currency: 'AMD', available: true, usable: true, capturedAt, freshness: 'FRESH' as const,
});
const offers: ProductOffers = {
  product: comparison.product,
  cheapest: comparison.cheapest,
  storesWithOffer: 2,
  stores: [
    { storeId: 2, storeName: 'Yerevan City', storeSlug: 'yerevan-city', status: 'AVAILABLE', message: null, offers: [offer(2, 'Yerevan City', 'yerevan-city', 570, now)] },
    { storeId: 3, storeName: 'Carrefour Armenia', storeSlug: 'carrefour', status: 'AVAILABLE', message: null, offers: [offer(3, 'Carrefour Armenia', 'carrefour', 600, twoDaysAgo)] },
    { storeId: 1, storeName: 'SAS Supermarket', storeSlug: 'sas', status: 'NOT_FOUND', message: 'Not found in the store catalog', offers: [] },
    { storeId: 4, storeName: 'Nor Zovq', storeSlug: 'nor-zovq', status: 'NOT_INTEGRATED', message: 'No public catalog', offers: [] },
  ],
};
const history: PriceHistory = {
  productId: 1, days: 90,
  series: [{ storeId: 2, storeName: 'Yerevan City', storeSlug: 'yerevan-city', points: [
    { capturedAt: twoDaysAgo, price: 600, originalPrice: null, available: true },
    { capturedAt: now, price: 570, originalPrice: 650, available: true },
  ] }],
};
const list: ShoppingList = {
  id: 7, name: 'list', createdAt: now, updatedAt: now,
  items: [
    { id: 70, productId: 1, name: 'TEST Կոլա 1.5լ', nameEn: 'TEST Cola 1.5L', size: '1.5 լ', imageUrl: null, freeText: null, quantity: 2,
      cheapest: comparison.cheapest, conceptCode: null, sizeValue: null, sizeUnit: null, percent: null, preferredBrand: null, rawText: null, requiresChoice: false, soldByWeight: false },
    { id: 71, productId: null, name: 'Հաց', nameEn: null, size: null, imageUrl: null, freeText: 'Հաց', quantity: 1, cheapest: null, conceptCode: null, sizeValue: null, sizeUnit: null, percent: null, preferredBrand: null, rawText: null, requiresChoice: false, soldByWeight: false },
  ],
};
const basket: BasketOptimization = {
  shoppingListId: 7, currency: 'AMD',
  cheapestSingleStore: { storeId: 3, storeName: 'Carrefour Armenia', storeSlug: 'carrefour', total: 1200, itemCount: 1, complete: true, items: [], missingItemIds: [] },
  singleStoreOptions: [],
  cheapestMultiStore: {
    total: 1140, storeCount: 1, complete: true, missingItemIds: [],
    stores: [{ storeId: 2, storeName: 'Yerevan City', storeSlug: 'yerevan-city', total: 1140, itemCount: 1, complete: true, missingItemIds: [],
      items: [{ itemId: 70, productId: 1, name: 'TEST Կոլա 1.5լ', quantity: 2, unitPrice: 570, total: 1140, externalName: 'TEST Cola', capturedAt: now, freshness: 'FRESH' }] }],
  },
  savings: 60,
  unavailableItems: [{ itemId: 71, productId: null, name: 'Հաց', reason: 'Not linked' }],
  containsStalePrices: false,
  calculatedAt: now,
};

const Stack = createNativeStackNavigator();

async function renderScreen(component: React.ComponentType, params?: object) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { gcTime: Infinity } } });
  const metrics = { frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } };
  return await render(
    <SafeAreaProvider initialMetrics={metrics}>
    <QueryClientProvider client={qc}>
      <I18nProvider>
        <NavigationContainer>
          <Stack.Navigator>
            <Stack.Screen name="Under test" component={component} initialParams={params} />
          </Stack.Navigator>
        </NavigationContainer>
      </I18nProvider>
    </QueryClientProvider>
    </SafeAreaProvider>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  mocked.prices.mockResolvedValue(comparison);
  mocked.offers.mockResolvedValue(offers);
  mocked.history.mockResolvedValue(history);
  mocked.getList.mockResolvedValue(list);
  mocked.createList.mockResolvedValue(list);
  mocked.addItem.mockResolvedValue(list);
  mocked.optimize.mockResolvedValue(basket);
  mocked.searchProducts.mockResolvedValue({
    items: [{ id: 1, name: 'TEST Կոլա 1.5լ', nameEn: 'TEST Cola 1.5L', brand: null, size: '1.5 լ', soldByWeight: false, imageUrl: null,
      categoryId: null, cheapest: comparison.cheapest, maxPrice: 600, storeCount: 2,
      offers: [offer(2, 'Yerevan City', 'yerevan-city', 570, now), offer(3, 'Carrefour Armenia', 'carrefour', 600, now)] }],
    page: 0, size: 20, total: 1, totalMatches: 1,
    stores: [
      { storeId: 2, storeName: 'Yerevan City', storeSlug: 'yerevan-city', matches: 1, catalogState: 'COMPLETE', message: null },
      { storeId: 3, storeName: 'Carrefour Armenia', storeSlug: 'carrefour', matches: 1, catalogState: 'COMPLETE', message: null },
      { storeId: 4, storeName: 'Nor Zovq', storeSlug: 'nor-zovq', matches: 0, catalogState: 'NOT_IMPLEMENTED', message: 'No public catalog' },
    ],
  });
});

test('product details shows cheapest price, store comparison and adds to list', async () => {
  await renderScreen(ProductDetailsScreen, { productId: 1 });
  expect((await screen.findAllByText('Ամենաէժան')).length).toBeGreaterThan(0);
  expect(screen.getAllByText('570 ֏').length).toBeGreaterThan(0);
  expect(screen.getByText('Carrefour')).toBeTruthy();
  // stores without the product say why instead of disappearing
  expect(screen.getByText('Չի գտնվել')).toBeTruthy();
  expect(screen.getByText('Միացված չէ')).toBeTruthy();
  expect(screen.getByText('Գների պատմություն')).toBeTruthy();
  await fireEvent.press(screen.getByText('+ Ավելացնել ցուցակին'));
  await waitFor(() => expect(mocked.addItem).toHaveBeenCalledWith(7, { productId: 1, quantity: 1 }));
  expect(await screen.findByText('✓ Ավելացվեց ցուցակին')).toBeTruthy();
});

test('search screen queries the API and lists results', async () => {
  await renderScreen(SearchScreen, { query: 'կոլա' });
  expect(await screen.findByText('TEST Կոլա 1.5լ')).toBeTruthy();
  expect(mocked.searchProducts).toHaveBeenCalledWith('կոլա', 0, undefined);
  expect(screen.getByText('2 խանութում')).toBeTruthy();
  // per-store result counts, and the store without a crawler is reported as not integrated
  expect(screen.getByText('Yerevan City 1')).toBeTruthy();
  expect(screen.getByText('Nor Zovq: միացված չէ')).toBeTruthy();
  expect(screen.getByText('600 ֏')).toBeTruthy();
});

test('shopping list shows items and the basket button', async () => {
  await renderScreen(ShoppingListScreen);
  expect(await screen.findByText('TEST Կոլա 1.5լ')).toBeTruthy();
  expect(screen.getByText('📝 Հաց')).toBeTruthy();
  expect(screen.getByText('Գտնել ամենաէժան զամբյուղը')).toBeTruthy();
});

test('basket screen shows single-store, combination, total and savings', async () => {
  await renderScreen(BasketScreen);
  expect(await screen.findByText('ԱՄԵՆԱԷԺԱՆ ՄԵԿ ԽԱՆՈՒԹՈՒՄ')).toBeTruthy();
  expect(screen.getAllByText('1 200 ֏').length).toBeGreaterThan(0);
  expect(screen.getByText('ԱՄԵՆԱԷԺԱՆ ՀԱՄԱԴՐՈՒԹՅՈՒՆ')).toBeTruthy();
  expect(screen.getAllByText('1 140 ֏').length).toBeGreaterThan(0);
  expect(screen.getAllByText('60 ֏').length).toBeGreaterThan(0);
  expect(screen.getByText('ԳՆԵՐԸ ԲՈԼՈՐ ՍՈՒՊԵՐՄԱՐԿԵՏՆԵՐՈՒՄ')).toBeTruthy();
  expect(screen.getByText('• Հաց')).toBeTruthy();
});
