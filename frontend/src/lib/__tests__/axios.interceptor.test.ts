import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';

// tokenUtils를 먼저 모킹한 후 apiClient를 임포트해야 한다
vi.mock('@/utils/token.utils', () => ({
  tokenUtils: {
    get: vi.fn(() => 'old-access-token'),
    set: vi.fn(),
    clear: vi.fn(),
  },
}));

const { apiClient } = await import('@/lib/axios');
const { tokenUtils } = await import('@/utils/token.utils');

describe('Axios 인터셉터 - 401 자동 갱신', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(apiClient);
    vi.clearAllMocks();
  });

  afterEach(() => {
    mock.restore();
  });

  it('401 응답 시 /api/auth/refresh를 호출하고 원래 요청을 재시도한다', async () => {
    mock.onGet('/api/products').replyOnce(401).onGet('/api/products').reply(200, { data: [] });
    mock.onPost('/api/auth/refresh').reply(200, {
      data: { accessToken: 'new-access-token', tokenType: 'Bearer', expiresIn: 1800000 },
    });

    const response = await apiClient.get('/api/products');

    expect(tokenUtils.set).toHaveBeenCalledWith('new-access-token');
    expect(response.status).toBe(200);
  });

  it('refresh 실패 시 토큰을 삭제하고 에러를 반환한다', async () => {
    mock.onGet('/api/products').reply(401);
    mock.onPost('/api/auth/refresh').reply(401);

    await expect(apiClient.get('/api/products')).rejects.toThrow();
    expect(tokenUtils.clear).toHaveBeenCalled();
  });

  it('refresh 중 동시에 들어온 401 요청들은 갱신 완료 후 일괄 재시도한다', async () => {
    let refreshCallCount = 0;

    mock.onPost('/api/auth/refresh').reply(() => {
      refreshCallCount++;
      return [200, { data: { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 1800000 } }];
    });
    mock.onGet('/api/products').replyOnce(401).onGet('/api/products').reply(200, {});
    mock.onGet('/api/cart').replyOnce(401).onGet('/api/cart').reply(200, {});

    await Promise.all([apiClient.get('/api/products'), apiClient.get('/api/cart')]);

    // refresh는 한 번만 호출되어야 한다 (큐 기반 처리)
    expect(refreshCallCount).toBe(1);
  });
});