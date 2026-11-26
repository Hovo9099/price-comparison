import type { NavigatorScreenParams } from '@react-navigation/native';

export type TabParamList = {
  HomeTab: undefined;
  SearchTab: { query?: string; categoryId?: number } | undefined;
  ListTab: undefined;
  ProfileTab: undefined;
};

export type RootStackParamList = {
  Tabs: NavigatorScreenParams<TabParamList>;
  ProductDetails: { productId: number; title?: string };
  PriceComparison: { productId: number; title?: string };
  Category: { categoryId: number; title: string };
  Basket: undefined;
  Results: undefined;
  Preferences: undefined;
};
