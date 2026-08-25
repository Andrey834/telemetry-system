// Контракт ответа query-service (GET /devices) — должен оставаться совместим с
// ru.telemetry.query.model.DeviceView на бэкенде.
export type DeviceStatus = 'ONLINE' | 'STALE' | 'OFFLINE';

export interface DeviceView {
  deviceId: string;
  name: string;
  groupName: string;
  active: boolean;
  status: DeviceStatus;
  lat: number | null;
  lon: number | null;
  speedKmh: number | null;
  recordedAt: string | null;
}
