// Tests for api(). fetch is replaced with a fake, so no backend is needed.
import { describe, expect, it, vi } from 'vitest';
import { ApiError, api, tokenStore } from './client';

function fakeFetch(status: number, body: unknown) {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status }));
  vi.stubGlobal('fetch', fetch);
  return fetch;
}

describe('api', () => {
  it('adds the /api prefix and returns the answer', async () => {
    const fetch = fakeFetch(200, { status: 'UP' });

    await expect(api('/health')).resolves.toEqual({ status: 'UP' });
    expect(fetch.mock.calls[0][0]).toBe('/api/health');
  });

  it('sends the sign-in token when there is one', async () => {
    const fetch = fakeFetch(200, {});
    tokenStore.set('abc123');

    await api('/portfolios');

    const headers = fetch.mock.calls[0][1].headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer abc123');
  });

  it("turns the backend's error into an ApiError with the backend's message", async () => {
    fakeFetch(400, { message: 'The file needs a ticker column.' });

    const error = await api('/portfolios/1/holdings/upload').catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.message).toBe('The file needs a ticker column.');
    expect(error.status).toBe(400);
  });

  it('explains when the backend cannot be reached at all', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(api('/health')).rejects.toThrow('Cannot reach the server. Is the backend running?');
  });
});
