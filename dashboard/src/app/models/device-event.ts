// Контракт ответа query-service (GET /devices/events) — ru.telemetry.query.model.DeviceEvent.
export interface DeviceEvent {
  deviceId: string;
  deviceName: string;
  type: 'OFFLINE' | 'ONLINE';
  occurredAt: string;
}
