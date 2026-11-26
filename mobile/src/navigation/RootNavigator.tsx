import React from 'react';
import { Text } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { colors } from '../components/theme';
import { useI18n } from '../i18n';
import { BasketScreen } from '../screens/BasketScreen';
import { CategoryScreen } from '../screens/CategoryScreen';
import { DealsScreen } from '../screens/DealsScreen';
import { HomeScreen } from '../screens/HomeScreen';
import { PriceComparisonScreen } from '../screens/PriceComparisonScreen';
import { ProductDetailsScreen } from '../screens/ProductDetailsScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { SearchScreen } from '../screens/SearchScreen';
import { SmartListScreen } from '../screens/SmartListScreen';
import { ResultsScreen } from '../screens/ResultsScreen';
import { PreferencesScreen } from '../screens/PreferencesScreen';
import { ScanScreen } from '../screens/ScanScreen';
import { WatchlistScreen } from '../screens/WatchlistScreen';
import { NearbyScreen } from '../screens/NearbyScreen';
import type { RootStackParamList, TabParamList } from './types';

const Tab = createBottomTabNavigator<TabParamList>();
const Stack = createNativeStackNavigator<RootStackParamList>();

const icon = (emoji: string) => ({ focused }: { focused: boolean }) => (
  <Text style={{ fontSize: 20, opacity: focused ? 1 : 0.5 }}>{emoji}</Text>
);

function Tabs() {
  const { t } = useI18n();
  return (
    <Tab.Navigator screenOptions={{ tabBarActiveTintColor: colors.primary, headerShown: false }}>
      <Tab.Screen name="HomeTab" component={HomeScreen} options={{ title: t.tabs.home, tabBarIcon: icon('🏠') }} />
      <Tab.Screen name="SearchTab" component={SearchScreen} options={{ title: t.tabs.search, tabBarIcon: icon('🔍') }} />
      <Tab.Screen name="DealsTab" component={DealsScreen} options={{ title: t.deals.tab, tabBarIcon: icon('🏷️') }} />
      <Tab.Screen name="ListTab" component={SmartListScreen} options={{ title: t.tabs.list, tabBarIcon: icon('🛒') }} />
      <Tab.Screen name="ProfileTab" component={ProfileScreen} options={{ title: t.tabs.profile, tabBarIcon: icon('👤') }} />
    </Tab.Navigator>
  );
}

export function RootNavigator() {
  const { t } = useI18n();
  return (
    <Stack.Navigator screenOptions={{ headerTintColor: colors.primary }}>
      <Stack.Screen name="Tabs" component={Tabs} options={{ headerShown: false }} />
      <Stack.Screen name="ProductDetails" component={ProductDetailsScreen} options={({ route }) => ({ title: route.params.title ?? '' })} />
      <Stack.Screen name="PriceComparison" component={PriceComparisonScreen} options={{ title: t.priceComparison }} />
      <Stack.Screen name="Category" component={CategoryScreen} options={({ route }) => ({ title: route.params.title })} />
      <Stack.Screen name="Basket" component={BasketScreen} options={{ title: t.basketTitle }} />
      <Stack.Screen name="Results" component={ResultsScreen} options={{ title: t.cmp.title }} />
      <Stack.Screen name="Preferences" component={PreferencesScreen} options={{ title: t.prefs.title }} />
      <Stack.Screen name="Scan" component={ScanScreen} options={{ title: t.scan.title }} />
      <Stack.Screen name="Watchlist" component={WatchlistScreen} options={{ title: t.watch.title }} />
      <Stack.Screen name="Nearby" component={NearbyScreen} options={{ title: t.nearby.title }} />
    </Stack.Navigator>
  );
}
