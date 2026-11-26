import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { I18nProvider } from '../i18n';
import { DealsScreen } from '../screens/DealsScreen';
import { api } from '../api/client';
import type { Deal } from '../api/types';

// Fictional, test-only data.
jest.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  api: { deals: jest.fn(), stores: jest.fn(async () => []), categories: jest.fn(async () => []) },
}));
const mocked = api as jest.Mocked<typeof api>;

const deal = (name: string, verdict: Deal['verdict'], pct: number): Deal => ({
  product: { id: pct, name, nameEn: null, brand: null, size: null, soldByWeight: false, imageUrl: null, categoryId: null,
    cheapest: null, maxPrice: null, storeCount: 2, offers: [] } as unknown as Deal['product'],
  storeId: 1, storeName: 'SAS Supermarket', storeSlug: 'sas', price: 500, originalPrice: 600, discountPercent: pct,
  verdict, maxPaid90Days: verdict === 'GENUINE' ? 600 : 520, historyDays: 40, since: new Date().toISOString(), cheapestAnywhere: true,
});

function renderDeals() {
  const Stack = createNativeStackNavigator();
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
  return render(
    <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
      <QueryClientProvider client={qc}>
        <I18nProvider>
          <NavigationContainer>
            <Stack.Navigator>
              <Stack.Screen name="Deals" component={DealsScreen} />
            </Stack.Navigator>
          </NavigationContainer>
        </I18nProvider>
      </QueryClientProvider>
    </SafeAreaProvider>,
  );
}

test('shows verdicts and asks for questionable deals only when toggled', async () => {
  mocked.deals.mockImplementation(async (p) => ({
    items: p.includeInflated
      ? [deal('TEST Պանիր', 'GENUINE', 17), deal('TEST Թեյ', 'INFLATED', 29)]
      : [deal('TEST Պանիր', 'GENUINE', 17)],
    page: 0, size: 20, total: p.includeInflated ? 2 : 1,
  }));
  renderDeals();
  expect(await screen.findByText('TEST Պանիր')).toBeTruthy();
  expect(screen.getByText('✓ Իրական զեղչ')).toBeTruthy();
  expect(screen.getByText(/Վերջին 90 օրում գինը եղել է 600/)).toBeTruthy();
  expect(screen.queryByText('TEST Թեյ')).toBeNull();
  expect(mocked.deals).toHaveBeenLastCalledWith(expect.objectContaining({ includeInflated: false }));

  fireEvent(screen.getByLabelText(/կասկածելի զեղչերը/), 'valueChange', true);
  expect(await screen.findByText('TEST Թեյ')).toBeTruthy();
  expect(screen.getByText('⚠ Կասկածելի զեղչ')).toBeTruthy();
  await waitFor(() => expect(mocked.deals).toHaveBeenLastCalledWith(expect.objectContaining({ includeInflated: true })));
});
