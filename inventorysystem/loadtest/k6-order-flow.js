import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8081/api';
const API_KEY = __ENV.API_KEY || 'dev-api-key-change-me';
const RATE = Number(__ENV.RATE || 200);
const DURATION = __ENV.DURATION || '60s';
const HOT_STOCK = Number(__ENV.HOT_STOCK || 1000);
const SKUS = Number(__ENV.SKUS || 200);

const HEADERS = { 'Content-Type': 'application/json', 'X-API-Key': API_KEY };

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const accepted = new Counter('hot_orders_accepted');
const soldOut = new Counter('hot_orders_rejected_insufficient_stock');

export const options = {
  scenarios: {
    hot_sku: {
      executor: 'constant-arrival-rate', exec: 'hotSku',
      rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 100, maxVUs: 500,
    },
    many_skus: {
      executor: 'constant-arrival-rate', exec: 'manySkus',
      rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 100, maxVUs: 500,
    },
  },
  thresholds: {
    // Tune to what you actually measure, then quote the measured numbers.
    'http_req_duration{scenario:many_skus}': ['p(95)<300'],
    'http_req_duration{scenario:hot_sku}': ['p(95)<800'],
    http_req_failed: ['rate<0.01'],
  },
};

function createProduct(sku, stock) {
  const res = http.post(`${BASE}/products`, JSON.stringify({
    sku, name: sku, price: 10.0, initialStock: stock,
  }), { headers: HEADERS });
  if (res.status !== 201) {
    throw new Error(`setup: create product failed ${res.status} ${res.body}`);
  }
  return res.json('id');
}

export function setup() {
  const run = Date.now();
  const hotId = createProduct(`LT-HOT-${run}`, HOT_STOCK);
  const ids = [];
  for (let i = 0; i < SKUS; i++) {
    ids.push(createProduct(`LT-${run}-${i}`, 1000000));
  }
  return { hotId, ids };
}

function placeOrder(productId) {
  const key = `k6-${__VU}-${__ITER}-${Date.now()}-${Math.random()}`;
  return http.post(`${BASE}/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }] }),
    { headers: { ...HEADERS, 'Idempotency-Key': key } });
}

export function hotSku(data) {
  const res = placeOrder(data.hotId);
  check(res, { 'hot: 201 or 409': (r) => r.status === 201 || r.status === 409 });
  if (res.status === 201) accepted.add(1);
  if (res.status === 409) soldOut.add(1);
}

export function manySkus(data) {
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const res = placeOrder(id);
  check(res, { 'many: 201': (r) => r.status === 201 });
}

export function teardown(data) {
  const inv = http.get(`${BASE}/inventory/${data.hotId}`, { headers: HEADERS }).json();
  console.log(`HOT SKU final state: onHand=${inv.onHand} reserved=${inv.reserved} available=${inv.available}`);
  console.log('Correctness: reserved must equal hot_orders_accepted, never exceed onHand.');
  check(inv, {
    'no oversell (reserved <= onHand)': (i) => i.reserved <= i.onHand,
    'available never negative': (i) => i.available >= 0,
  });
}
