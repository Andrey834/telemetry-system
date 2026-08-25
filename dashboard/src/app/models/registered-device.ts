// Контракт ответа query-service (POST /devices) — ru.telemetry.query.service.DeviceAdminService.RegisteredDevice.
// apiKey приходит в открытом виде только здесь, один раз — бэкенд хранит только его хеш.
export interface RegisteredDevice {
  deviceId: string;
  name: string;
  groupName: string;
  apiKey: string;
}
