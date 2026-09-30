import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import { env } from "../lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/debug-product-search.js",
  );
}

const PRODUCT_SEARCH_SIZE = Number(__ENV.PRODUCT_SEARCH_SIZE || 50);
const iterations = Number(__ENV.ITERATIONS || 50);
const vus = Number(__ENV.VUS || 1);
const slowThresholdMs = Number(__ENV.SLOW_THRESHOLD_MS || 1000);

const productKeywords = new SharedArray("product search keywords", function () {
  return JSON.parse(open("./data/product_name_keywords.json"))
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
    debug_product_search: {
      executor: "shared-iterations",
      vus,
      iterations,
      maxDuration: __ENV.MAX_DURATION || "10m",
    },
  },
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate==0"],
    "http_req_duration{name:GET /products}": ["p(95)<1000"],
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

  if (!ok || res.timings.duration >= slowThresholdMs) {
    console.error(
      `product search observed: keyword=${keyword}, page=${page}, status=${res.status}, durationMs=${res.timings.duration.toFixed(2)}, totalPages=${body?.totalPages}, itemCount=${body?.items?.length}`,
    );
  }

  if (!ok) {
    fail(
      `product search failed: keyword=${keyword}, page=${page}, status=${res.status}`,
    );
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
