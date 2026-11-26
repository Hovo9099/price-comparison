import React, { useEffect, useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { loadApiBaseUrl } from './src/api/config';
import { Loading } from './src/components/StateViews';
import { I18nProvider } from './src/i18n';
import { RootNavigator } from './src/navigation/RootNavigator';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 60_000 } },
});

export default function App() {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    loadApiBaseUrl().finally(() => setReady(true));
  }, []);

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <I18nProvider>
          {ready ? (
              <NavigationContainer>
                <RootNavigator />
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
