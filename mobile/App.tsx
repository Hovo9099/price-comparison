import React, { useEffect, useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer, createNavigationContainerRef } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { loadApiBaseUrl } from './src/api/config';
import { Loading } from './src/components/StateViews';
import { I18nProvider, useI18n } from './src/i18n';
import { RootNavigator } from './src/navigation/RootNavigator';
import type { RootStackParamList } from './src/navigation/types';
import { onNotificationTap, registerForPush } from './src/notifications';
import { hasDeviceId } from './src/storage/local';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 60_000 } },
});

const navigationRef = createNavigationContainerRef<RootStackParamList>();

/** Opens the product when a price-drop notification is tapped; refreshes the push token of installs that watch prices. */
function PushBootstrap({ navReady }: { navReady: boolean }) {
  const { t, language } = useI18n();
  useEffect(() => {
    hasDeviceId().then((has) => (has ? registerForPush(language, false, t.watch.channel) : false)).catch(() => undefined);
  }, [language, t.watch.channel]);
  useEffect(() => {
    if (!navReady) return undefined;
    return onNotificationTap((productId) => navigationRef.navigate('ProductDetails', { productId }));
  }, [navReady]);
  return null;
}

export default function App() {
  const [ready, setReady] = useState(false);
  const [navReady, setNavReady] = useState(false);
  useEffect(() => {
    loadApiBaseUrl().finally(() => setReady(true));
  }, []);

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <I18nProvider>
          {ready ? (
              <NavigationContainer ref={navigationRef} onReady={() => setNavReady(true)}>
                <RootNavigator />
                <PushBootstrap navReady={navReady} />
              </NavigationContainer>
            ) : (
              <Loading />
            )}
          <StatusBar style="dark" />
        </I18nProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
