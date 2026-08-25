// Нагрузочный тест ingestion-service + query-service через публичный домен.
// Запуск: k6 run load-test/telemetry-load.js
// Параметры (env-переменные):
//   INGEST_URL     — по умолчанию https://ingest.telemetry.srvmls.ru/telemetry
//   QUERY_URL      — по умолчанию https://query.telemetry.srvmls.ru
//   DEVICE_KEYS    — JSON {deviceId: apiKey} реальных устройств из реестра (см. scripts/seed-secrets.md)
//   QUERY_USERNAME — логин dashboard-пользователя (по умолчанию admin)
//   QUERY_PASSWORD — пароль dashboard-пользователя (обязателен)
//
// Пример:
//   DEVICE_KEYS='{"bus-1":"...","bus-2":"...","bus-3":"...","truck-1":"...","truck-2":"..."}' \
//   QUERY_PASSWORD='...' \
//   k6 run load-test/telemetry-load.js
import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const INGEST_URL = __ENV.INGEST_URL || 'https://ingest.telemetry.srvmls.ru/telemetry';
const QUERY_URL = __ENV.QUERY_URL || 'https://query.telemetry.srvmls.ru';
const QUERY_USERNAME = __ENV.QUERY_USERNAME || 'admin';
const QUERY_PASSWORD = __ENV.QUERY_PASSWORD;
const DEVICE_KEYS = JSON.parse(__ENV.DEVICE_KEYS || '{}');
const DEVICE_IDS = Object.keys(DEVICE_KEYS);

const ingestErrors = new Rate('ingest_errors');
const queryErrors = new Rate('query_errors');
const ingestDuration = new Trend('ingest_duration', true);
const queryDuration = new Trend('query_duration', true);

// Точка отсчёта — Москва, разбрасываем устройства по области ~50км вокруг неё.
const CENTER_LAT = 55.751244;
const CENTER_LON = 37.618423;

export const options = {
  scenarios: {
    ingest: {
      executor: 'ramping-vus',
      exec: 'ingest',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '30s', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '30s', target: 0 },
      ],
    },
    query: {
      executor: 'constant-vus',
      exec: 'query',
      vus: 5,
      duration: '5m30s',
    },
  },
  thresholds: {
    ingest_errors: ['rate<0.01'],
    query_errors: ['rate<0.01'],
    ingest_duration: ['p(95)<1000'],
    query_duration: ['p(95)<1000'],
  },
};

export function setup() {
  if (DEVICE_IDS.length === 0) {
    fail('DEVICE_KEYS не задан — нужен JSON {deviceId: apiKey} реальных устройств из реестра');
  }
  if (!QUERY_PASSWORD) {
    fail('QUERY_PASSWORD не задан — нужен пароль dashboard-пользователя для логина в query-service');
  }

  const res = http.post(
    `${QUERY_URL}/auth/login`,
    JSON.stringify({ username: QUERY_USERNAME, password: QUERY_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (res.status !== 200) {
    fail(`логин в query-service не удался: ${res.status} ${res.body}`);
  }
  return { token: res.json('token') };
}

function jitter(base, spread) {
  return base + (Math.random() - 0.5) * spread;
}

function randomDeviceId() {
  return DEVICE_IDS[Math.floor(Math.random() * DEVICE_IDS.length)];
}

export function ingest() {
  const deviceId = randomDeviceId();
  const payload = JSON.stringify({
    deviceId,
    routeId: 1,
    lat: jitter(CENTER_LAT, 0.5),
    lon: jitter(CENTER_LON, 0.5),
    speedKmh: Math.round(Math.random() * 120 * 10) / 10,
    timestamp: new Date().toISOString(),
  });

  const res = http.post(INGEST_URL, payload, {
    headers: { 'Content-Type': 'application/json', 'X-Device-Key': DEVICE_KEYS[deviceId] },
  });

  ingestDuration.add(res.timings.duration);
  const ok = check(res, { 'ingest status 202': (r) => r.status === 202 });
  ingestErrors.add(!ok);

  sleep(0.2);
}

export function query(data) {
  const res = http.get(`${QUERY_URL}/devices`, {
    headers: { Authorization: `Bearer ${data.token}` },
  });

  queryDuration.add(res.timings.duration);
  const ok = check(res, { 'query status 200': (r) => r.status === 200 });
  queryErrors.add(!ok);

  sleep(1);
}
