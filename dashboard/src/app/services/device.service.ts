import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DeviceView } from '../models/device-view';
import { FleetActivityPoint } from '../models/fleet-activity-point';
import { RegisteredDevice } from '../models/registered-device';
import { RoutePoint } from '../models/route-point';
import { apiBaseUrl } from './api-base-url';

@Injectable({ providedIn: 'root' })
export class DeviceService {
  private readonly baseUrl = `${apiBaseUrl()}/devices`;

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<DeviceView[]> {
    return this.http.get<DeviceView[]>(this.baseUrl);
  }

  getHistory(deviceId: string, limit = 200): Observable<RoutePoint[]> {
    return this.http.get<RoutePoint[]>(`${this.baseUrl}/${deviceId}/history`, {
      params: { limit },
    });
  }

  getActivity(minutes = 60): Observable<FleetActivityPoint[]> {
    return this.http.get<FleetActivityPoint[]>(`${this.baseUrl}/activity`, {
      params: { minutes },
    });
  }

  // ADMIN-only на бэкенде (SecurityConfig) — 403 для OPERATOR, форма скрыта на фронте тем же
  // AuthService.role(), но реальная проверка всегда на сервере.
  register(deviceId: string, name: string, groupName: string): Observable<RegisteredDevice> {
    return this.http.post<RegisteredDevice>(this.baseUrl, { deviceId, name, groupName });
  }
}
