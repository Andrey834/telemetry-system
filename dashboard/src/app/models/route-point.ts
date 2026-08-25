// Контракт ответа query-service (GET /devices/{id}/history).
export interface RoutePoint {
  lat: number;
  lon: number;
  speedKmh: number | null;
  recordedAt: string;
}
