import http from 'k6/http';
import { check, fail } from 'k6';
import { SharedArray } from 'k6/data';
import { scenario } from 'k6/execution';
import { getCsrf, login, logUnexpectedResponse, withCsrfHeaders } from '../lib/auth.js';
import { env } from '../lib/env.js';

const BASE_URL = env('BASE_URL');
const SELLER_PASSWORD = env('PASSWORD');

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomSteppedInt(min, max, step) {
  const steps = Math.floor((max - min) / step);
  return min + randomInt(0, steps) * step;
}

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let inQuotes = false;

  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];

    if (inQuotes) {
      if (char === '"' && text[i + 1] === '"') {
        field += '"';
        i += 1;
      } else if (char === '"') {
        inQuotes = false;
      } else {
        field += char;
      }
      continue;
    }

    if (char === '"') {
      inQuotes = true;
    } else if (char === ',') {
      row.push(field);
      field = '';
    } else if (char === '\n') {
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else if (char !== '\r') {
      field += char;
    }
  }

  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }

  return rows;
}

function requiredText(value, fallback) {
  const text = String(value || '').trim();
  return text.length > 0 ? text : fallback;
}

function limitLength(value, maxLength) {
  return value.length <= maxLength ? value : value.slice(0, maxLength);
}

const products = new SharedArray('register csv products', function () {
  const rows = parseCsv(open('./data/product_name_description_sample.csv'));
  const header = rows[0] || [];
  const nameIndex = header.indexOf('name');
  const descriptionIndex = header.indexOf('description');

  if (nameIndex < 0 || descriptionIndex < 0) {
    throw new Error('product_name_description_sample.csv must include name and description columns');
  }

  return rows
    .slice(1)
    .map((row, index) => {
      const name = limitLength(requiredText(row[nameIndex], `Product ${index + 1}`), 50);
      const description = limitLength(requiredText(row[descriptionIndex], name), 1000);

      return {
        name,
        description,
        categoryId: randomInt(10, 1009),
        unitPrice: randomSteppedInt(1000, 300000, 100),
        initialInventory: randomSteppedInt(0, 3000, 10),
      };
    });
});

const sellers = new SharedArray('seller login users', function () {
  const rows = JSON.parse(open('./data/mock_nickname_email.json'));

  if (rows.length < 71) {
    throw new Error('mock_nickname_email.json must contain at least 71 rows');
  }

  return rows.slice(0, 71).map((row) => ({
    email: row.email,
    password: SELLER_PASSWORD,
  }));
});

const vus = Number(__ENV.VUS || 10);
const iterations = Number(__ENV.PRODUCT_ITERATIONS || products.length);
const maxDuration = __ENV.MAX_DURATION || '10m';

export const options = {
  scenarios: {
    register_products_csv: {
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

export default function () {
  const iteration = scenario.iterationInTest;
  const product = products[iteration % products.length];
  const seller = sellers[iteration % sellers.length];

  login(BASE_URL, seller.email, seller.password);

  const csrf = getCsrf(BASE_URL);
  const res = http.post(`${BASE_URL}/products`, JSON.stringify(product), {
    headers: withCsrfHeaders(csrf, {
      'Content-Type': 'application/json',
    }),
    tags: { name: 'POST /products' },
  });

  const registered = check(res, {
    'register product status is 200': (r) => r.status === 200,
  });

  if (!registered) {
    logUnexpectedResponse('register product failed', res, {
      sellerEmail: seller.email,
      categoryId: product.categoryId,
      name: product.name,
    });
    fail('register product failed');
  }
}
