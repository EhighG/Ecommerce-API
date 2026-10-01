import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import { env } from "../lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/product_search/measure-product-search.js",
  );
}

const PRODUCT_SEARCH_SIZE = Number(__ENV.PRODUCT_SEARCH_SIZE || 50);

// 부하 설정은 환경변수로 바꾼다. 기본값은 병목 탐색용이다.
// 성능 측정용 예: SEARCH_RATE=10 DURATION=3m PRE_ALLOCATED_VUS=20 MAX_VUS=100
const searchRate = Number(__ENV.SEARCH_RATE || 30);
const duration = __ENV.DURATION || "5m";
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 50);
const maxVUs = Number(__ENV.MAX_VUS || 200);
const p95Ms = Number(__ENV.SEARCH_P95_MS || 300);
const p99Ms = Number(__ENV.SEARCH_P99_MS || 800);

const productKeywords = new SharedArray("product search keywords", function () {
  return JSON.parse(open("../data/product_name_keywords.json"))
    .map((row) => row.keyword)
    .filter((keyword) => typeof keyword === "string" && keyword.length > 0);
});

if (productKeywords.length === 0) {
  throw new Error(
    "product_name_keywords.json must contain at least one keyword value",
  );
}

export const options = {
  scenarios: {
    measure_product_search: {
      executor: "constant-arrival-rate",
      rate: searchRate,
      timeUnit: "1s",
      duration,
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    checks: ["rate>0.999"],
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:GET /products}": [
      `p(95)<${p95Ms}`,
      `p(99)<${p99Ms}`,
    ],
    dropped_iterations: ["count==0"],
  },
};

function buildProductSearchUrl(keyword, page) {
  const query = [
    `keyword=${encodeURIComponent(keyword)}`,
    `page=${page}`,
    `size=${PRODUCT_SEARCH_SIZE}`,
    "sortBy=ORDER_COUNT",
    "direction=DESC",
  ].join("&");

  return `${BASE_URL}/products?${query}`;
}

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function searchProducts(keyword, page) {
  const res = http.get(buildProductSearchUrl(keyword, page), {
    tags: { name: "GET /products" },
  });
  const body = parseJson(res);
  const ok = check(res, {
    "product search status is 200": (r) => r.status === 200,
    "product search returns items": () =>
      Array.isArray(body?.items) && body.items.length > 0,
  });

  if (!ok) {
    console.error(
      `product search failed: keyword=${keyword}, page=${page}, status=${res.status}, totalPages=${body?.totalPages}, itemCount=${body?.items?.length}`,
    );
    fail(`product search failed: keyword=${keyword}, page=${page}`);
  }

  return body;
}

export default function () {
  const keyword =
    productKeywords[scenario.iterationInTest % productKeywords.length];

  const firstPage = searchProducts(keyword, 0);

  if ((scenario.iterationInTest + 1) % 10 === 0 && firstPage.totalPages >= 2) {
    searchProducts(keyword, 1);
  }
}
