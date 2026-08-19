import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const totalOrders = Number(__ENV.TOTAL_ORDERS || 10000);
const vus = Number(__ENV.VUS || 1000);
const completed = new Counter('checkout_completed');

export const options = {
  scenarios: { checkout: { executor: 'shared-iterations', vus, iterations: totalOrders, maxDuration: '10m' } },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<5000'],
    checkout_completed: [`count==${totalOrders}`],
  },
};

export function setup() {
  const response = http.post(`${baseUrl}/api/products`, JSON.stringify({
    name: `Checkout Load ${Date.now()}`, stock: totalOrders,
  }), {headers: {'Content-Type': 'application/json', 'X-Client-Id': 'checkout-setup'}});
  if (response.status !== 201) throw new Error(`setup failed: ${response.status} ${response.body}`);
  return {productId: response.json('productId')};
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'X-Client-Id': `checkout-user-${exec.vu.idInTest}`,
    'Idempotency-Key': `checkout-${__VU}-${__ITER}-${Date.now()}`,
  };
  const reserve = http.post(`${baseUrl}/api/orders/reservations`,
    JSON.stringify({productId: data.productId, quantity: 1}), {headers});
  const reserved = check(reserve, {
    'reservation returns 201': r => r.status === 201,
    'order is reserved': r => r.status === 201 && r.json('status') === 'RESERVED',
  });
  if (!reserved) return;

  const payment = http.post(`${baseUrl}/api/orders/${reserve.json('orderId')}/payments`,
    JSON.stringify({result: 'SUCCESS'}), {headers});
  if (check(payment, {
    'payment returns 200': r => r.status === 200,
    'order is completed': r => r.status === 200 && r.json('status') === 'COMPLETED',
  })) completed.add(1);
  sleep(0.05);
}
