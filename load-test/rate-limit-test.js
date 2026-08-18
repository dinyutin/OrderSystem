import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const limited = new Counter('gateway_429');
http.setResponseCallback(http.expectedStatuses(404, 429));

export const options = {
  vus: 20,
  iterations: 200,
  thresholds: { gateway_429: ['count>0'] },
};

export default function () {
  const response = http.get(`${baseUrl}/api/products/999999999`, {
    headers: { 'X-Client-Id': 'same-rate-limit-bucket' },
  });
  if (response.status === 429) limited.add(1);
  check(response, {
    'response is accepted or rate limited': (result) => [404, 429].includes(result.status),
    '429 comes from gateway limiter': (result) => result.status !== 429 || Boolean(result.headers['X-RateLimit-Remaining']),
  });
}
