// Контракт ответа query-service (GET /devices) — должен оставаться совместим с
// ru.telemetry.query.model.TelemetryState на бэкенде.
export interface TelemetryState {
  deviceId: string;
  routeId: number | null;
  lat: number;
  lon: number;
  speedKmh: number | null;
  recordedAt: string;
}
