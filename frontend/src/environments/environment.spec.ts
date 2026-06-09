import { environment } from './environment';

describe('development environment', () => {
  it('uses the documented local backend URLs', () => {
    expect(environment.apiBaseUrl).toBe('http://localhost:8081/api');
    expect(environment.wsUrl).toBe('http://localhost:8081/ws');
  });
});
