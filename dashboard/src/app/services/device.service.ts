import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DeviceView } from '../models/device-view';
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
}
