import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from './client';
import { getShoppingListId, setShoppingListId } from '../storage/local';
import type { ShoppingList } from './types';

export const keys = {
  search: (q: string, categoryId?: number) => ['search', q, categoryId] as const,
  browse: (categoryId?: number) => ['browse', categoryId] as const,
  prices: (id: number) => ['prices', id] as const,
  offers: (id: number) => ['offers', id] as const,
  history: (id: number, days: number) => ['history', id, days] as const,
  categories: ['categories'] as const,
  drops: ['priceDrops'] as const,
  list: ['shoppingList'] as const,
};

export function useSearch(query: string, categoryId?: number) {
  return useInfiniteQuery({
    queryKey: keys.search(query, categoryId),
    queryFn: ({ pageParam }) => api.searchProducts(query, pageParam, categoryId),
    initialPageParam: 0,
    getNextPageParam: (last) => ((last.page + 1) * last.size < last.total ? last.page + 1 : undefined),
    enabled: query.trim().length >= 2,
  });
}

export function useBrowse(categoryId?: number) {
  return useInfiniteQuery({
    queryKey: keys.browse(categoryId),
    queryFn: ({ pageParam }) => api.browseProducts(categoryId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => ((last.page + 1) * last.size < last.total ? last.page + 1 : undefined),
  });
}

export const usePrices = (id: number) => useQuery({ queryKey: keys.prices(id), queryFn: () => api.prices(id) });
export const useOffers = (id: number) => useQuery({ queryKey: keys.offers(id), queryFn: () => api.offers(id) });
export const useHistory = (id: number, days = 90) =>
  useQuery({ queryKey: keys.history(id, days), queryFn: () => api.history(id, days) });
export const useStores = () => useQuery({ queryKey: ['stores'], queryFn: api.stores, staleTime: 10 * 60_000 });
export const useCategories = () => useQuery({ queryKey: keys.categories, queryFn: api.categories, staleTime: 10 * 60_000 });
export const usePriceDrops = () => useQuery({ queryKey: keys.drops, queryFn: () => api.priceDrops(7, 10) });

/** The device's shopping list; created on first use and remembered locally. */
async function loadOrCreateList(name: string): Promise<ShoppingList> {
  const id = await getShoppingListId();
  if (id) {
    try {
      return await api.getList(id);
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 404)) throw e;
    }
  }
  const created = await api.createList(name);
  await setShoppingListId(created.id);
  return created;
}

export function useShoppingList(defaultName: string) {
  return useQuery({ queryKey: keys.list, queryFn: () => loadOrCreateList(defaultName) });
}

export function useListMutations(defaultName: string) {
  const qc = useQueryClient();
  const onSuccess = (list: ShoppingList) => qc.setQueryData(keys.list, list);
  const listId = async () => (await loadOrCreateList(defaultName)).id;
  return {
    add: useMutation({
      mutationFn: async (body: { productId?: number; freeText?: string; quantity?: number }) => api.addItem(await listId(), body),
      onSuccess,
    }),
    update: useMutation({
      mutationFn: async ({ itemId, quantity }: { itemId: number; quantity: number }) =>
        api.updateItem(await listId(), itemId, quantity),
      onSuccess,
    }),
    remove: useMutation({
      mutationFn: async (itemId: number) => api.removeItem(await listId(), itemId),
      onSuccess,
    }),
  };
}
