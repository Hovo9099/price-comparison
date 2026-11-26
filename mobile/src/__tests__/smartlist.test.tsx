import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { I18nProvider } from '../i18n';
import { SmartListScreen } from '../screens/SmartListScreen';
import { ResultsScreen } from '../screens/ResultsScreen';
import { api } from '../api/client';
import type { Comparison, Concept, ShoppingList } from '../api/types';

// Fictional, test-only data.
jest.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  api: {
    getList: jest.fn(), createList: jest.fn(), parseInto: jest.fn(), addItem: jest.fn(), patchItem: jest.fn(),
    removeItem: jest.fn(), concepts: jest.fn(), suggestConcepts: jest.fn(), compare: jest.fn(), lists: jest.fn(),
    shareList: jest.fn(), unshareList: jest.fn(), joinList: jest.fn(),
  },
}));
const mocked = api as jest.Mocked<typeof api>;
const now = new Date().toISOString();

const cheese: Concept = {
  code: 'cheese', nameHy: 'Պանիր', nameEn: 'Cheese', nameRu: 'Сыр', category: 'dairy', defaultSizeValue: null, defaultSizeUnit: null,
  sizes: [], soldByWeight: false, requiresChoice: true, alternatives: [],
  choices: [{ code: 'cheese-lori', nameHy: 'Պանիր Լոռի', nameEn: 'Lori cheese' }, { code: 'cheese-chanakh', nameHy: 'Պանիր Չանախ', nameEn: 'Chanakh cheese' }],
};
const milk: Concept = {
  code: 'milk', nameHy: 'Կաթ', nameEn: 'Milk', nameRu: 'Молоко', category: 'dairy', defaultSizeValue: 1000, defaultSizeUnit: 'ml',
  sizes: [500, 1000, 2000], soldByWeight: false, requiresChoice: false, alternatives: [], choices: [],
};
const base = { productId: null, nameEn: null, imageUrl: null, freeText: null, percent: null, preferredBrand: null, soldByWeight: false };
const list: ShoppingList = {
  id: 3, name: 'Իմ ցուցակը', createdAt: now, updatedAt: now,
  items: [
    { ...base, id: 1, name: 'Կաթ', size: '1 լ', quantity: 1, conceptCode: 'milk', sizeValue: 1000, sizeUnit: 'ml', rawText: 'kat', requiresChoice: false,
      cheapest: { price: 700, currency: 'AMD', storeId: 2, storeName: 'Yerevan City', storeSlug: 'yerevan-city', capturedAt: now, freshness: 'FRESH' } },
    { ...base, id: 2, name: 'Պանիր', size: null, quantity: 1, conceptCode: 'cheese', sizeValue: null, sizeUnit: null, rawText: 'panir', requiresChoice: true, cheapest: null },
  ],
};
const option = (total: number, slug: string, name: string) => ({
  total, storeCount: 1, complete: true, missingItemIds: [],
  stores: [{ storeId: 1, storeName: name, storeSlug: slug, subtotal: total, deliveryFee: null, deliveryFeeKnown: false, total,
    lines: [{ itemId: 1, itemLabel: 'Կաթ 1 լ', productId: 9, productName: `TEST Կաթ ${name}`, brand: null, storeProductId: 5, externalName: 'TEST',
      externalUrl: null, imageUrl: null, price: total, pricePerUnit: total, perUnit: 'l' as const, quantity: 1, quantityUnit: 'pcs', lineTotal: total,
      capturedAt: now, freshness: 'FRESH' as const, pricePercentile: 50, comparableProducts: 4, medianPricePerUnit: 700, reasonCode: 'MID_RANGE' as const, reason: '' }] }],
});
const comparison: Comparison = {
  comparisonId: 1, shoppingListId: 3, calculatedAt: now, warnings: ['Delivery fees and travel costs are not included.'],
  items: [
    { itemId: 1, label: 'Կաթ 1 լ', conceptCode: 'milk', productId: null, quantity: 1, quantityUnit: 'pcs', sizeValue: 1000, sizeUnit: 'ml', status: 'OK', comparableProducts: 4, note: null, choices: [] },
    { itemId: 2, label: 'Պանիր', conceptCode: 'cheese', productId: null, quantity: 1, quantityUnit: 'pcs', sizeValue: null, sizeUnit: null, status: 'NEEDS_CHOICE', comparableProducts: 0, note: null, choices: [] },
  ],
  strategies: [
    { strategy: 'CHEAPEST', recommended: 'SINGLE_STORE', total: 500, singleStore: option(500, 'sas', 'SAS'), multiStore: option(500, 'sas', 'SAS'), missingItemIds: [2], differenceVsOthers: { MID_RANGE: -150, PREMIUM: -700 }, notes: [] },
    { strategy: 'MID_RANGE', recommended: 'SINGLE_STORE', total: 650, singleStore: option(650, 'carrefour', 'Carrefour'), multiStore: option(650, 'carrefour', 'Carrefour'), missingItemIds: [2], differenceVsOthers: { CHEAPEST: 150, PREMIUM: -550 }, notes: [] },
    { strategy: 'PREMIUM', recommended: 'SINGLE_STORE', total: 1200, singleStore: option(1200, 'carrefour', 'Carrefour'), multiStore: option(1200, 'carrefour', 'Carrefour'), missingItemIds: [2], differenceVsOthers: { CHEAPEST: 700, MID_RANGE: 550 }, notes: [] },
  ],
};

const Stack = createNativeStackNavigator();
function renderScreen(component: React.ComponentType) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { gcTime: Infinity } } });
  const metrics = { frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } };
  return render(
    <SafeAreaProvider initialMetrics={metrics}>
      <QueryClientProvider client={qc}>
        <I18nProvider>
          <NavigationContainer>
            <Stack.Navigator>
              <Stack.Screen name="Under test" component={component} />
            </Stack.Navigator>
          </NavigationContainer>
        </I18nProvider>
      </QueryClientProvider>
    </SafeAreaProvider>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  mocked.getList.mockResolvedValue(list);
  mocked.createList.mockResolvedValue(list);
  mocked.concepts.mockResolvedValue([milk, cheese]);
  mocked.suggestConcepts.mockResolvedValue([milk]);
  mocked.parseInto.mockResolvedValue({ items: [], list });
  mocked.patchItem.mockResolvedValue(list);
  mocked.compare.mockResolvedValue(comparison);
});

test('smart list sends free text, shows recognised items, sizes and type choices', async () => {
  renderScreen(SmartListScreen);
  expect(await screen.findByText('Կաթ')).toBeTruthy();
  expect(screen.getByText('սկսած 700 ֏')).toBeTruthy();
  expect(screen.getByText('2 լ')).toBeTruthy(); // size chips from the concept
  // cheese needs a type: choosing one patches the item
  fireEvent.press(await screen.findByText('Պանիր Լոռի'));
  await waitFor(() => expect(mocked.patchItem).toHaveBeenCalledWith(3, 2, { conceptCode: 'cheese-lori' }));
  // typing several products and adding sends one parse request
  fireEvent.changeText(screen.getByPlaceholderText(/kat, dzu 10/), 'kat, dzu, panir');
  fireEvent.press(screen.getByText('Ավելացնել'));
  await waitFor(() => expect(mocked.parseInto).toHaveBeenCalledWith(3, 'kat, dzu, panir'));
});

test('results show three strategies with totals, reasons and excluded items', async () => {
  renderScreen(ResultsScreen);
  expect(await screen.findByText('Ամենաէժան')).toBeTruthy();
  expect(screen.getByText('Միջին')).toBeTruthy();
  expect(screen.getByText('Պրեմիում')).toBeTruthy();
  expect(screen.getAllByText('500 ֏').length).toBeGreaterThan(0);
  fireEvent.press(screen.getByText('Պրեմիում'));
  expect(await screen.findByText('TEST Կաթ Carrefour')).toBeTruthy();
  expect(screen.getAllByText('1 200 ֏').length).toBeGreaterThan(0);
  expect(screen.getByText(/Պանիր — Ընտրեք տեսակը/)).toBeTruthy();
  expect(screen.getByText(/Delivery fees and travel costs are not included/)).toBeTruthy();
});

test('shares the list with a code and joins another list by code', async () => {
  const { Share } = require('react-native');
  const shareSpy = jest.spyOn(Share, 'share').mockResolvedValue({ action: 'sharedAction' });
  mocked.lists.mockResolvedValue([list]);
  mocked.shareList.mockResolvedValue({ code: 'ABCD2345' });
  const family: ShoppingList = { ...list, id: 9, name: 'TEST Ընտանիք', items: [] };
  mocked.joinList.mockResolvedValue(family);
  renderScreen(SmartListScreen);

  fireEvent.press(await screen.findByText('📋'));
  fireEvent.press(await screen.findByText(/Կիսվել այս ցուցակով/));
  expect(await screen.findByText('Կոդ՝ ABCD2345')).toBeTruthy();
  expect(mocked.shareList).toHaveBeenCalledWith(3);
  expect(shareSpy).toHaveBeenCalledWith({ message: expect.stringContaining('ABCD2345') });

  fireEvent.changeText(screen.getByLabelText('Ցուցակի կոդ'), 'wxyz-6789');
  fireEvent.press(screen.getByText('Միանալ'));
  await waitFor(() => expect(mocked.joinList).toHaveBeenCalledWith('wxyz-6789'));
  shareSpy.mockRestore();
});
