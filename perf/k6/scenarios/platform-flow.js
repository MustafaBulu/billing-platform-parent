import http from "k6/http";
import { check, fail, group, sleep } from "k6";

const CONFIG = {
  tenantBaseUrl: __ENV.TENANT_BASE_URL || "http://localhost:8081",
  usageBaseUrl: __ENV.USAGE_BASE_URL || "http://localhost:8082",
  billingBaseUrl: __ENV.BILLING_BASE_URL || "http://localhost:8083",
  invoiceBaseUrl: __ENV.INVOICE_BASE_URL || "http://localhost:8084",
  authToken: __ENV.PERF_AUTH_TOKEN || "dev-admin-token"
};

const TARGET_P95_MS = Number(__ENV.TARGET_P95_MS || 300);
const TARGET_ERR_RATE = Number(__ENV.TARGET_ERROR_RATE || 0.01);
const VUS = Number(__ENV.PERF_VUS || 10);
const DURATION = __ENV.PERF_DURATION || "3m";
const TENANT_POOL_SIZE = Number(__ENV.PERF_TENANT_POOL_SIZE || 200);
const MIN_TENANT_POOL_SIZE = Number(__ENV.PERF_MIN_TENANT_POOL_SIZE || Math.max(10, VUS));
const CREATE_TENANT_MAX_RETRIES = Number(__ENV.PERF_TENANT_CREATE_RETRIES || 3);

export const options = {
  scenarios: {
    platform_flow: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION
    }
  },
  thresholds: {
    http_req_duration: [`p(95)<${TARGET_P95_MS}`],
    http_req_failed: [`rate<${TARGET_ERR_RATE}`]
  }
};

function authHeaders(tenantCode) {
  const headers = {
    "Authorization": `Bearer ${CONFIG.authToken}`,
    "Content-Type": "application/json"
  };
  if (tenantCode) {
    headers["X-Tenant-Id"] = tenantCode;
  }
  return headers;
}

function parseJson(response) {
  try {
    return response.json();
  } catch (e) {
    return null;
  }
}

function uniqueSuffix() {
  return `${Date.now()}-${__VU}-${__ITER}-${Math.floor(Math.random() * 10000)}`;
}

function createTenant(displayName) {
  const res = http.post(
    `${CONFIG.tenantBaseUrl}/api/v1/tenants`,
    JSON.stringify({ displayName: displayName }),
    { headers: authHeaders() }
  );
  const body = parseJson(res);
  if (res.status !== 201 || !body || typeof body.tenantCode !== "string" || body.tenantCode.length === 0) {
    return null;
  }
  return body.tenantCode;
}

function createTenantWithRetry(displayName) {
  for (let attempt = 1; attempt <= CREATE_TENANT_MAX_RETRIES; attempt += 1) {
    const tenantCode = createTenant(displayName);
    if (tenantCode) {
      return tenantCode;
    }
    sleep(0.1 * attempt);
  }
  return null;
}

export function setup() {
  const tenantCodes = [];
  const seed = Date.now();

  for (let i = 0; i < TENANT_POOL_SIZE; i += 1) {
    const displayName = `Perf Tenant Pool ${seed}-${i}`;
    const tenantCode = createTenantWithRetry(displayName);
    if (tenantCode) {
      tenantCodes.push(tenantCode);
    }
  }

  if (tenantCodes.length < MIN_TENANT_POOL_SIZE) {
    fail(`Tenant pool initialization failed: created=${tenantCodes.length}, required=${MIN_TENANT_POOL_SIZE}`);
  }

  return { tenantCodes: tenantCodes };
}

export default function (data) {
  const suffix = uniqueSuffix();
  const customerId = `cust-${suffix}`;
  const metricCode = "api_call";
  const usageQuantity = 120;
  const unitPrice = 0.05;
  const currency = "USD";

  let tenantCode;
  let invoiceId;
  let paymentStatus;

  group("1. Pick tenant from pool", function () {
    const pool = data && Array.isArray(data.tenantCodes) ? data.tenantCodes : [];
    if (pool.length > 0) {
      const index = (__ITER + __VU) % pool.length;
      tenantCode = pool[index];
    } else {
      tenantCode = null;
    }
    check(tenantCode, {
      "tenant selected from pool": (v) => typeof v === "string" && v.length > 0
    });
  });

  if (!tenantCode) {
    return;
  }

  group("2. Ingest usage and read total", function () {
    const idem = `usage-${suffix}`;
    const ingestRes = http.post(
      `${CONFIG.usageBaseUrl}/api/v1/usage/events`,
      JSON.stringify({
        tenantId: tenantCode,
        customerId: customerId,
        idempotencyKey: idem,
        metricCode: metricCode,
        quantity: usageQuantity,
        occurredAt: new Date().toISOString()
      }),
      { headers: authHeaders(tenantCode) }
    );
    check(ingestRes, {
      "usage ingest status is 202": (r) => r.status === 202
    });

    const totalRes = http.get(
      `${CONFIG.usageBaseUrl}/api/v1/usage/totals/${tenantCode}/${customerId}/${metricCode}`,
      { headers: authHeaders(tenantCode) }
    );
    check(totalRes, {
      "usage total status is 200": (r) => r.status === 200
    });
  });

  group("3. Rate usage", function () {
    const rateRes = http.post(
      `${CONFIG.billingBaseUrl}/api/v1/billing/rate`,
      JSON.stringify({
        tenantId: tenantCode,
        customerId: customerId,
        metricCode: metricCode,
        quantity: usageQuantity,
        unitPrice: unitPrice,
        currency: currency
      }),
      { headers: authHeaders(tenantCode) }
    );
    const rateBody = parseJson(rateRes);
    check(rateRes, {
      "billing rate status is 200": (r) => r.status === 200
    });
    check(rateBody, {
      "billing total amount exists": (b) => b && b.totalAmount !== undefined && b.totalAmount !== null
    });
  });

  group("4. Generate invoice", function () {
    const idem = `invoice-${suffix}`;
    const invoiceRes = http.post(
      `${CONFIG.invoiceBaseUrl}/api/v1/invoices/generate`,
      JSON.stringify({
        tenantId: tenantCode,
        customerId: customerId,
        billingPeriod: "2026-02",
        currency: currency,
        lineAmounts: [15.0, 20.0, 25.0],
        idempotencyKey: idem
      }),
      { headers: authHeaders(tenantCode) }
    );
    const invoiceBody = parseJson(invoiceRes);
    check(invoiceRes, {
      "invoice generate status is 202": (r) => r.status === 202
    });
    check(invoiceBody, {
      "invoice id exists": (b) => b && typeof b.invoiceId === "string" && b.invoiceId.length > 0
    });
    invoiceId = invoiceBody ? invoiceBody.invoiceId : null;
  });

  group("5. Generate and settle", function () {
    const idem = `orchestration-${suffix}`;
    const orchestrationRes = http.post(
      `${CONFIG.invoiceBaseUrl}/api/v1/invoices/generate-and-settle`,
      JSON.stringify({
        tenantId: tenantCode,
        customerId: customerId,
        billingPeriod: "2026-02",
        currency: currency,
        lineAmounts: [15.0, 20.0, 25.0],
        idempotencyKey: idem
      }),
      { headers: authHeaders(tenantCode) }
    );
    const body = parseJson(orchestrationRes);
    check(orchestrationRes, {
      "orchestration status is 202": (r) => r.status === 202
    });
    check(body, {
      "orchestration has invoice": (b) => b && b.invoice && typeof b.invoice.invoiceId === "string",
      "orchestration has payment": (b) => b && b.payment && typeof b.payment.transactionId === "string",
      "orchestration has settlement": (b) => b && b.settlement && typeof b.settlement.sagaId === "string"
    });
    paymentStatus = body && body.payment ? body.payment.status : null;
  });

  group("6. Read invoice", function () {
    const targetInvoice = invoiceId;
    if (!targetInvoice) {
      return;
    }
    const getRes = http.get(
      `${CONFIG.invoiceBaseUrl}/api/v1/invoices/${targetInvoice}`,
      { headers: authHeaders(tenantCode) }
    );
    check(getRes, {
      "get invoice status is 200": (r) => r.status === 200
    });
  });

  group("7. Pause", function () {
    check(paymentStatus, {
      "payment status resolved": (v) => typeof v === "string" && v.length > 0
    });
    sleep(0.1);
  });
}
