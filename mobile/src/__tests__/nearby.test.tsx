import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import * as Location from 'expo-location';
import { I18nProvider } from '../i18n';
import { NearbyScreen } from '../screens/NearbyScreen';
import { api } from '../api/client';

// Fictional, test-only data.
jest.mock('../api/client', () => ({ ApiError: class ApiError extends Error {}, api: { nearbyStores: jest.fn() } }));
const mocked = api as jest.Mocked<typeof api>;
beforeEach(() => jest.clearAllMocks());

test('lists the nearest branch per store using a rounded position', async () => {
  mocked.nearbyStores.mockResolvedValue([
    { storeId: 2, storeName: 'Yerevan City', storeSlug: 'yerevan-city', locationId: 1, name: 'TEST Branch', address: 'Test st 1',
      latitude: 40.18, longitude: 44.51, distanceKm: 0.35 },
    { storeId: 1, storeName: 'SAS Supermarket', storeSlug: 'sas', locationId: 2, name: 'TEST SAS', address: null,
      latitude: 40.2, longitude: 44.52, distanceKm: 2.46 },
  ]);
  render(<I18nProvider><NearbyScreen /></I18nProvider>);
  expect(await screen.findByText('TEST Branch')).toBeTruthy();
  expect(screen.getByText('350 մ')).toBeTruthy();
  expect(screen.getByText('2.5 կմ')).toBeTruthy();
  expect(mocked.nearbyStores).toHaveBeenCalledWith(40.181, 44.512);
});

test('asks for permission instead of locating when not granted', async () => {
  (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValueOnce({ status: 'denied' });
  render(<I18nProvider><NearbyScreen /></I18nProvider>);
  await waitFor(() => expect(screen.getByText(/տեղորոշման թույլտվություն/)).toBeTruthy());
  expect(mocked.nearbyStores).not.toHaveBeenCalled();
});
