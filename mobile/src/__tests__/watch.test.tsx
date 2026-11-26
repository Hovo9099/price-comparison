import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { I18nProvider } from '../i18n';
import { WatchButton } from '../components/WatchButton';
import { WatchlistScreen } from '../screens/WatchlistScreen';
import { api } from '../api/client';
import type { PriceWatch } from '../api/types';

// Fictional, test-only data.
jest.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  api: { watches: jest.fn(), watch: jest.fn(), unwatch: jest.fn(), registerDevice: jest.fn() },
}));
const mocked = api as jest.Mocked<typeof api>;
const DEVICE = 'test-device-0000-0000-000000000001';

const watch = (over: Partial<PriceWatch> = {}): PriceWatch => ({
  id: 1, productId: 7, name: 'TEST Կոլա 1.5լ', nameEn: 'TEST Cola 1.5L', size: '1.5 լ', imageUrl: null, targetPrice: null,
  startPrice: 570, dropped: true, lastNotifiedAt: null, createdAt: new Date().toISOString(),
  cheapest: { price: 540, currency: 'AMD', storeId: 2, storeName: 'Yerevan City', storeSlug: 'yerevan-city',
    capturedAt: new Date().toISOString(), freshness: 'FRESH' },
  ...over,
});

function wrap(ui: React.ReactElement) {
  const Stack = createNativeStackNavigator();
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { gcTime: Infinity } } });
  return render(
    <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
      <QueryClientProvider client={qc}>
        <I18nProvider>
          <NavigationContainer>
            <Stack.Navigator>
              <Stack.Screen name="Main">{() => ui}</Stack.Screen>
              <Stack.Screen name="ProductDetails">{() => null}</Stack.Screen>
            </Stack.Navigator>
          </NavigationContainer>
        </I18nProvider>
      </QueryClientProvider>
    </SafeAreaProvider>,
  );
}

beforeEach(() => jest.clearAllMocks());

test('follow with a target price saves the watch and registers the device', async () => {
  mocked.watches.mockResolvedValue([]);
  mocked.watch.mockResolvedValue(watch({ targetPrice: 500 }));
  mocked.registerDevice.mockResolvedValue(undefined);
  wrap(<WatchButton productId={7} />);

  fireEvent.press(await screen.findByText(/Հետևել գնին/));
  fireEvent.changeText(screen.getByLabelText(/Նպատակային գին/), '-3');
  fireEvent.press(screen.getByText('Պահպանել'));
  expect(screen.getByText('Մուտքագրեք դրական թիվ')).toBeTruthy();
  expect(mocked.watch).not.toHaveBeenCalled();

  fireEvent.changeText(screen.getByLabelText(/Նպատակային գին/), '500');
  fireEvent.press(screen.getByText('Պահպանել'));
  await waitFor(() => expect(mocked.watch).toHaveBeenCalledWith(DEVICE, 7, 500));
  // no push permission in tests -> registered without a token, user is told drops show in the app
  await waitFor(() => expect(mocked.registerDevice).toHaveBeenCalledWith(DEVICE, null, 'hy'));
  expect(await screen.findByText(/Ծանուցումները հասանելի չեն/)).toBeTruthy();
});

test('watchlist shows current price, start price and drop badge, and can remove', async () => {
  mocked.watches.mockResolvedValue([watch()]);
  mocked.unwatch.mockResolvedValue(undefined);
  wrap(<WatchlistScreen />);

  expect(await screen.findByText('TEST Կոլա 1.5լ')).toBeTruthy();
  expect(screen.getByText(/540.*Yerevan City/)).toBeTruthy();
  expect(screen.getByText(/Սկզբում՝ 570/)).toBeTruthy();
  expect(screen.getByText(/Գինն իջել է/)).toBeTruthy();

  fireEvent.press(screen.getByLabelText(/Դադարեցնել: TEST Կոլա/));
  await waitFor(() => expect(mocked.unwatch).toHaveBeenCalledWith(DEVICE, 7));
});

test('empty watchlist explains how to start', async () => {
  mocked.watches.mockResolvedValue([]);
  wrap(<WatchlistScreen />);
  expect(await screen.findByText(/Դեռ ոչ մի ապրանքի/)).toBeTruthy();
});
