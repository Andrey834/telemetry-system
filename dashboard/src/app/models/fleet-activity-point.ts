// Контракт ответа query-service (GET /devices/activity) — ru.telemetry.query.model.FleetActivityPoint.
export interface FleetActivityPoint {
  bucket: string;
  count: number;
}
