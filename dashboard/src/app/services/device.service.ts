import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TelemetryState } from '../models/telemetry-state';

declare global {
  interface Window {
    __env?: { queryServiceUrl?: string };
  }
}

@Injectable({ providedIn: 'root' })
export class DeviceService {
  // window.__env заполняется entrypoint-скриптом контейнера при старте (см. public/env.template.js) —
  // так один и тот же образ конфигурируется под разные окружения без пересборки. В ng serve
  // window.__env пустой (public/env.js — заглушка), поэтому используется environment.ts.
  private readonly baseUrl = `${window.__env?.queryServiceUrl || environment.queryServiceUrl}/devices`;

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<TelemetryState[]> {
    return this.http.get<TelemetryState[]>(this.baseUrl);
  }
}
