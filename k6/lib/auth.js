import http from 'k6/http';
import { check, fail } from 'k6';

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

export function readApiError(res) {
  const body = parseJson(res);

  if (!body || typeof body.code !== 'number') {
    return null;
  }

  return {
    code: body.code,
    message: body.message,
  };
}

export function logUnexpectedResponse(label, res, context = {}) {
  const apiError = readApiError(res);
  const suffix = Object.entries(context)
    .map(([key, value]) => `${key}=${value}`)
    .join(', ');

  if (apiError) {
    console.error(
      `${label}: status=${res.status}, code=${apiError.code}, message=${apiError.message}${suffix ? `, ${suffix}` : ''}`
    );
    return;
  }

  console.error(`${label}: status=${res.status}${suffix ? `, ${suffix}` : ''}`);
}

export function checkApiError(res, expectedStatus, expectedCode) {
  const apiError = readApiError(res);

  return check(res, {
    [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus,
    [`error code is ${expectedCode}`]: () => apiError?.code === expectedCode,
  });
}

export function getCsrf(baseUrl, options = {}) {
  const res = http.get(`${baseUrl}/auth/csrf`, {
    tags: { name: 'GET /auth/csrf' },
  });
  const csrf = parseJson(res);

  const ok = check(res, {
    'csrf status is 200': (r) => r.status === 200,
    'csrf headerName exists': () => typeof csrf?.headerName === 'string' && csrf.headerName.length > 0,
    'csrf token exists': () => typeof csrf?.token === 'string' && csrf.token.length > 0,
  });

  if (!ok && options.failOnError !== false) {
    logUnexpectedResponse('csrf failed', res);
    fail('csrf failed');
  }

  return csrf;
}

export function withCsrfHeaders(csrf, headers = {}) {
  return {
    ...headers,
    [csrf.headerName]: csrf.token,
  };
}

export function requestWithCsrfRetry(method, baseUrl, path, body, csrfRef, options = {}) {
  const hasJsonBody = body !== null && body !== undefined;
  const requestBody = hasJsonBody ? JSON.stringify(body) : null;
  const params = {
    headers: withCsrfHeaders(csrfRef.value, {
      ...(hasJsonBody ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
    }),
    tags: options.tags,
  };

  let res = http.request(method, `${baseUrl}${path}`, requestBody, params);

  if (res.status !== 403) {
    return res;
  }

  csrfRef.value = getCsrf(baseUrl);

  return http.request(method, `${baseUrl}${path}`, requestBody, {
    ...params,
    headers: withCsrfHeaders(csrfRef.value, {
      ...(hasJsonBody ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
    }),
  });
}

export function postJsonWithCsrfRetry(baseUrl, path, body, csrfRef, options = {}) {
  return requestWithCsrfRetry('POST', baseUrl, path, body, csrfRef, options);
}

export function patchWithCsrfRetry(baseUrl, path, csrfRef, options = {}) {
  return requestWithCsrfRetry('PATCH', baseUrl, path, null, csrfRef, options);
}

export function login(baseUrl, email, password, options = {}) {
  const csrf = options.csrf || getCsrf(baseUrl, options);
  const res = http.post(
    `${baseUrl}/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: withCsrfHeaders(csrf, {
        'Content-Type': 'application/json',
      }),
      tags: { name: 'POST /auth/login' },
    }
  );

  const ok = check(res, {
    'login status is 200': (r) => r.status === 200,
  });

  if (!ok && options.failOnError !== false) {
    logUnexpectedResponse('login failed', res, { email });
    fail('login failed');
  }

  return { res, csrf };
}

export function logout(baseUrl, options = {}) {
  const csrfRef = options.csrfRef || { value: options.csrf || getCsrf(baseUrl, options) };
  const res = requestWithCsrfRetry('POST', baseUrl, '/auth/logout', null, csrfRef, {
    tags: { name: 'POST /auth/logout' },
  });

  const ok = check(res, {
    'logout status is 200': (r) => r.status === 200,
  });

  if (!ok && options.failOnError !== false) {
    logUnexpectedResponse('logout failed', res);
    fail('logout failed');
  }

  return { res, csrf: csrfRef.value };
}
