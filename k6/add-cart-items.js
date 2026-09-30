import http from 'k6/http';
import { check, fail } from 'k6';
import { SharedArray } from 'k6/data';
import { scenario } from 'k6/execution';
import { getCsrf, login, logUnexpectedResponse, withCsrfHeaders } from './lib/auth.js';
import { env } from './lib/env.js';

const BASE_URL = env('BASE_URL');
const BUYER_PASSWORD = env('PASSWORD');
const PRODUCT_SAMPLE_SIZE = 50;
const RANDOM_PRODUCT_COUNT = 20;
const REPEAT_COUNT = 3;

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function sampleWithoutReplacement(items, count) {
  const pool = items.slice();
  const picked = [];
  const limit = Math.min(count, pool.length);

  for (let i = 0; i < limit; i += 1) {
    const index = randomInt(0, pool.length - 1);
    picked.push(pool[index]);
    pool.splice(index, 1);
  }

  return picked;
}

const buyers = new SharedArray('available buyer emails', function () {
  return JSON.parse(open('./data/available_buyer_email.json')).map((row) => ({
    email: row.email,
    password: BUYER_PASSWORD,
  }));
});

const vus = Number(__ENV.VUS || 10);
const iterations = Number(__ENV.CART_ITEM_ITERATIONS || buyers.length);
const maxDuration = __ENV.MAX_DURATION || '10m';

export const options = {
  scenarios: {
    add_cart_items: {
      executor: 'shared-iterations',
      vus,
      iterations,
      maxDuration,
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const res = http.get(`${BASE_URL}/products?size=${PRODUCT_SAMPLE_SIZE}`, {
    tags: { name: 'GET /products' },
  });
  const body = res.json();

  const ok = check(res, {
    'product list status is 200': (r) => r.status === 200,
    'product list has 50 items': () => Array.isArray(body?.items) && body.items.length === PRODUCT_SAMPLE_SIZE,
  });

  if (!ok) {
    logUnexpectedResponse('product list failed', res);
    fail('product list failed');
  }

  return {
    products: body.items.map((item) => ({
      id: item.id,
    })),
  };
}

export default function (data) {
  const buyer = buyers[scenario.iterationInTest % buyers.length];

  login(BASE_URL, buyer.email, buyer.password);

  for (let repeat = 0; repeat < REPEAT_COUNT; repeat += 1) {
    const selectedProducts = sampleWithoutReplacement(data.products, RANDOM_PRODUCT_COUNT);

    for (const product of selectedProducts) {
      const req = {
        productId: product.id,
        quantity: randomInt(1, 20),
      };

      const csrf = getCsrf(BASE_URL);
      const res = http.post(`${BASE_URL}/cart-items`, JSON.stringify(req), {
        headers: withCsrfHeaders(csrf, {
          'Content-Type': 'application/json',
        }),
        tags: { name: 'POST /cart-items' },
      });

      const added = check(res, {
        'add cart item status is 200': (r) => r.status === 200,
      });

      if (!added) {
        logUnexpectedResponse('add cart item failed', res, {
          buyerEmail: buyer.email,
          productId: req.productId,
          quantity: req.quantity,
          repeat,
        });
        fail('add cart item failed');
      }
    }
  }
}
