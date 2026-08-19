import http from 'k6/http';
import exec from 'k6/execution';
import {check, sleep} from 'k6';
import {Counter, Trend} from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const totalOrders = Number(__ENV.TOTAL_ORDERS || 10000);
const vus = Number(__ENV.VUS || 1000);
const completed = new Counter('async_checkout_completed');
const queueTime = new Trend('async_queue_duration', true);

export const options = {
  scenarios: {asyncCheckout: {executor: 'shared-iterations', vus, iterations: totalOrders, maxDuration: '10m'}},
  thresholds: {
    checks: ['rate>0.99'], http_req_failed: ['rate<0.01'],
    'http_req_duration{operation:submit}': ['p(95)<2000'],
    async_queue_duration: ['p(95)<30000'],
    async_checkout_completed: [`count>=${Math.ceil(totalOrders * 0.999)}`],
  },
};

export function setup() {
  const response = http.post(`${baseUrl}/api/products`, JSON.stringify({name: `Async Load ${Date.now()}`, stock: totalOrders}), {
    headers: {'Content-Type': 'application/json', 'X-Client-Id': 'async-setup'},
    tags: {name: 'POST /api/products', operation: 'setup'},
  });
  if (response.status !== 201) throw new Error(`setup failed: ${response.status} ${response.body}`);
  return {productId: response.json('productId')};
}

export default function (data) {
  const headers = {'Content-Type': 'application/json', 'X-Client-Id': `async-user-${exec.vu.idInTest}`, 'Idempotency-Key': `async-${__VU}-${__ITER}-${Date.now()}`};
  const started = Date.now();
  const submit = http.post(`${baseUrl}/api/orders/requests`, JSON.stringify({productId: data.productId, quantity: 1}), {
    headers,
    tags: {name: 'POST /api/orders/requests', operation: 'submit'},
  });
  if (!check(submit, {'queue accepts order': response => response.status === 202})) return;
  const requestId = submit.json('requestId');
  if (!requestId) {
    check(null, {'queue returns request id': () => false});
    return;
  }
  let pollDelay = 0.5;
  for (let attempt = 0; attempt < 30; attempt += 1) {
    sleep(pollDelay);
    pollDelay = Math.min(pollDelay * 1.5, 2);
    const status = http.get(`${baseUrl}/api/orders/requests/${requestId}`, {
      headers,
      tags: {name: 'GET /api/orders/requests/:requestId', operation: 'status'},
    });
    if (!status.body || status.status === 0) continue;
    const state = status.json('status');
    if (state === 'RESERVED') {
      queueTime.add(Date.now() - started);
      let payment;
      for (let paymentAttempt = 0; paymentAttempt < 10; paymentAttempt += 1) {
        payment = http.post(`${baseUrl}/api/orders/${status.json('orderId')}/payments`, JSON.stringify({result: 'SUCCESS'}), {
          headers,
          tags: {name: 'POST /api/orders/:orderId/payments', operation: 'payment'},
        });
        if (payment.status === 200 && payment.body && payment.json('status') === 'COMPLETED') break;
        sleep(Math.min(0.1 * (paymentAttempt + 1), 0.5));
      }
      if (check(payment, {'order completes': response => response.status === 200 && response.body && response.json('status') === 'COMPLETED'})) completed.add(1);
      return;
    }
    if (state === 'FAILED' || state === 'QUEUE_FAILED' || state === 'NOT_FOUND') {
      check(status, {'async order not failed': () => false}); return;
    }
  }
  check(null, {'async order completes before timeout': () => false});
}
