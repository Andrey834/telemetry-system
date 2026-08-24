// Нагрузочный тест ingestion-service + query-service через публичный домен.
// Запуск: k6 run load-test/telemetry-load.js
// Параметры (env-переменные, необязательные):
//   INGEST_URL  — по умолчанию https://ingest.telemetry.srvmls.ru/telemetry
//   QUERY_URL   — по умолчанию https://query.telemetry.srvmls.ru/devices
//   DEVICES     — сколько разных deviceId симулируем (по умолчанию 50)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const INGEST_URL = __ENV.INGEST_URL || 'https://ingest.telemetry.srvmls.ru/telemetry';
const QUERY_URL = __ENV.QUERY_URL || 'https://query.telemetry.srvmls.ru/devices';
const DEVICE_COUNT = parseInt(__ENV.DEVICES || '50', 10);

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

function randomDeviceId() {
  return `load-test-${Math.floor(Math.random() * DEVICE_COUNT)}`;
}

function jitter(base, spread) {
  return base + (Math.random() - 0.5) * spread;
}

export function ingest() {
  const payload = JSON.stringify({
    deviceId: randomDeviceId(),
    routeId: 1,
    lat: jitter(CENTER_LAT, 0.5),
    lon: jitter(CENTER_LON, 0.5),
    speedKmh: Math.round(Math.random() * 120 * 10) / 10,
    timestamp: new Date().toISOString(),
  });

  const res = http.post(INGEST_URL, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  ingestDuration.add(res.timings.duration);
  const ok = check(res, { 'ingest status 202': (r) => r.status === 202 });
  ingestErrors.add(!ok);

  sleep(0.2);
}

export function query() {
  const res = http.get(QUERY_URL);

  queryDuration.add(res.timings.duration);
  const ok = check(res, { 'query status 200': (r) => r.status === 200 });
  queryErrors.add(!ok);

  sleep(1);
}
