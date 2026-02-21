import http from "k6/http";
import { check } from "k6";

const CONFIG = {
  tenantBaseUrl: __ENV.TENANT_BASE_URL || "http://tenant-service:8081",
  authToken: __ENV.PERF_AUTH_TOKEN || "dev-admin-token"
};

const TARGET_P95_MS = Number(__ENV.TARGET_P95_MS || 300);
const TARGET_ERR_RATE = Number(__ENV.TARGET_ERROR_RATE || 0.01);
const VUS = Number(__ENV.PERF_VUS || 10);
const DURATION = __ENV.PERF_DURATION || "1m";

export const options = {
  scenarios: {
    tenant_create: {
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

function authHeaders() {
  return {
    "Authorization": `Bearer ${CONFIG.authToken}`,
    "Content-Type": "application/json"
  };
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

export default function () {
  const res = http.post(
    `${CONFIG.tenantBaseUrl}/api/v1/tenants`,
    JSON.stringify({ displayName: `Perf Tenant ${uniqueSuffix()}` }),
    { headers: authHeaders() }
  );
  const body = parseJson(res);
  check(res, {
    "tenant create status is 201": (r) => r.status === 201
  });
  check(body, {
    "tenant code exists": (b) => b && typeof b.tenantCode === "string" && b.tenantCode.length > 0
  });
}
