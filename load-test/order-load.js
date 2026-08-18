import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const totalOrders = Number(__ENV.TOTAL_ORDERS || 10000);
const virtualUsers = Number(__ENV.VUS || 1000);
const initialStock = totalOrders;
const created = new Counter('orders_created');

export const options = {
  scenarios: {
    orders: {
      executor: 'shared-iterations',
      vus: virtualUsers,
      iterations: totalOrders,
      maxDuration: '5m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_duration: ['p(95)<3000'],
    orders_created: [`count==${totalOrders}`],
  },
};

export function setup() {
  const response = http.post(
    `${baseUrl}/api/products`,
    JSON.stringify({ name: `LoadTest-${Date.now()}`, stock: initialStock }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (response.status !== 201) {
    fail(`Product setup failed: HTTP ${response.status} ${response.body}`);
  }
  return { productId: response.json('productId') };
}

export default function (data) {
  const response = http.post(
    `${baseUrl}/api/orders`,
    JSON.stringify({ productId: data.productId, quantity: 1 }),
    { headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `load-${data.productId}-${exec.scenario.iterationInTest}`,
      'X-Client-Id': `load-user-${exec.vu.idInTest}`,
    } },
  );
  const successful = check(response, {
    'order returns 201': (result) => result.status === 201,
    'order is completed': (result) => result.json('status') === 'COMPLETED',
    'order id is present': (result) => Boolean(result.json('orderId')),
  });
  if (successful) {
    created.add(1);
  }
  // Keep the test active long enough for Prometheus to capture multiple samples.
  sleep(1);
}

export function teardown(data) {
  // Validate against MySQL source of truth; Redis is eventually consistent via outbox.
  const response = http.get(`${baseUrl}/api/products/${data.productId}`);
  check(response, {
    'final stock request succeeds': (result) => result.status === 200,
    'successful orders exactly consume stock': (result) => result.json('stock') === 0,
  });
}
