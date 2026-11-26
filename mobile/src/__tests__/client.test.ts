import { api, ApiError } from '../api/client';

describe('api client', () => {
  const originalFetch = global.fetch;
  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('encodes Armenian search queries', async () => {
    const fetchMock = jest.fn().mockResolvedValue({ ok: true, json: async () => ({ items: [], page: 0, size: 20, total: 0 }) });
    global.fetch = fetchMock as unknown as typeof fetch;
    await api.searchProducts('կաթ');
    expect(fetchMock.mock.calls[0][0]).toContain('/api/products/search?q=%D5%AF%D5%A1%D5%A9&page=0&size=20');
  });

  it('turns problem details into ApiError', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: async () => ({ detail: 'Product 5 not found' }),
    }) as unknown as typeof fetch;
    await expect(api.prices(5)).rejects.toEqual(new ApiError(404, 'Product 5 not found'));
  });
});
