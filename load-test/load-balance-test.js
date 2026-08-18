import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
http.setResponseCallback(http.expectedStatuses(200, 404));

export const options = { vus: 1, iterations: 30 };

export default function () {
  const response = http.get(`${baseUrl}/actuator/health`, {
    headers: { 'X-Client-Id': 'load-balance-check' },
  });
  // The API route test below is used because /actuator belongs to the gateway itself.
  const api = http.get(`${baseUrl}/api/products/999999999`, {
    headers: { 'X-Client-Id': 'load-balance-check' },
  });
  const instance = api.headers['X-Order-Instance'];
  console.log(`request served by ${instance || 'missing-instance-header'}`);
  check(api, {
    'request reached an order instance': (result) => Boolean(result.headers['X-Order-Instance']),
    'missing demo product returns 404': (result) => result.status === 404,
  });
  check(response, { 'gateway is healthy': (result) => result.status === 200 });
}
