import React from 'react';
import { Text } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { I18nProvider } from '../i18n';
import { ScanScreen } from '../screens/ScanScreen';
import { api } from '../api/client';

// Fictional, test-only data.
jest.mock('../api/client', () => {
  class ApiError extends Error {
    status: number;
    constructor(code: number, message: string) {
      super(message);
      this.status = code;
    }
  }
  return { ApiError, api: { productByBarcode: jest.fn() } };
});

// The camera is replaced by a button that "scans" a fixed code.
let mockScannedCode = '';
jest.mock('expo-camera', () => {
  const R = require('react');
  const RN = require('react-native');
  return {
    useCameraPermissions: () => [{ granted: true }, jest.fn()],
    CameraView: ({ onBarcodeScanned }: { onBarcodeScanned?: (r: { data: string }) => void }) =>
      R.createElement(RN.Pressable, { testID: 'fake-camera', onPress: () => onBarcodeScanned?.({ data: mockScannedCode }) },
        R.createElement(RN.Text, null, 'camera')),
  };
});

const mocked = api as jest.Mocked<typeof api>;

function Details({ route }: { route: { params: { productId: number; title?: string } } }) {
  return <Text>details {route.params.productId} {route.params.title}</Text>;
}

function renderScan() {
  const Stack = createNativeStackNavigator();
  return render(
    <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
      <I18nProvider>
        <NavigationContainer>
          <Stack.Navigator>
            <Stack.Screen name="Scan" component={ScanScreen} />
            <Stack.Screen name="ProductDetails" component={Details as never} />
          </Stack.Navigator>
        </NavigationContainer>
      </I18nProvider>
    </SafeAreaProvider>,
  );
}

beforeEach(() => jest.clearAllMocks());

test('a scanned barcode opens the product', async () => {
  mockScannedCode = '0999999000017';
  mocked.productByBarcode.mockResolvedValue({
    id: 42, name: 'TEST Մածուն 500գ', nameEn: 'TEST Matsun 500g', brand: null, barcode: mockScannedCode, sizeValue: 500,
    sizeUnit: 'g', size: '500 գ', variant: null, soldByWeight: false, imageUrl: null, category: null, aliases: [],
  });
  renderScan();
  fireEvent.press(await screen.findByTestId('fake-camera'));
  expect(await screen.findByText(/details 42/)).toBeTruthy();
  expect(mocked.productByBarcode).toHaveBeenCalledWith('0999999000017');
});

test('an unknown barcode shows a message and can be rescanned', async () => {
  mockScannedCode = '4000000000000';
  const { ApiError } = jest.requireMock('../api/client');
  mocked.productByBarcode.mockRejectedValue(new ApiError(404, 'not found'));
  renderScan();
  fireEvent.press(await screen.findByTestId('fake-camera'));
  await waitFor(() => expect(screen.getByText(/4000000000000/)).toBeTruthy());
  fireEvent.press(screen.getByRole('button', { name: /Կրկին սկանավորել|Scan again/ }));
  expect(await screen.findByTestId('fake-camera')).toBeTruthy();
});

test('non-numeric codes (QR, URLs) are ignored', async () => {
  mockScannedCode = 'https://example.invalid';
  renderScan();
  fireEvent.press(await screen.findByTestId('fake-camera'));
  expect(mocked.productByBarcode).not.toHaveBeenCalled();
});

