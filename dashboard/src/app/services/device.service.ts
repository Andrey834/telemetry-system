import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DeviceEvent } from '../models/device-event';
import { DeviceView } from '../models/device-view';
import { FleetActivityPoint } from '../models/fleet-activity-point';
import { RegisteredDevice } from '../models/registered-device';
import { RoutePoint } from '../models/route-point';
import { apiBaseUrl } from './api-base-url';
import { AuthService } from './auth.service';

const RECONNECT_DELAY_MS = 3000;

@Injectable({ providedIn: 'root' })
export class DeviceService {
  private readonly baseUrl = `${apiBaseUrl()}/devices`;
  private readonly auth = inject(AuthService);

  constructor(private readonly http: HttpClient) {}

  /** Push вместо повторных запросов — один долгоживущий поток вместо HTTP-запроса каждые 5с.
   * Не через HttpClient/EventSource: EventSource не умеет кастомный Authorization-заголовок
   * (только query-параметр/cookie), а у нас везде Bearer-JWT — поэтому обычный fetch() с ручным
   * построчным разбором SSE-кадров ("data: ...\n\n") и самостоятельным переподключением. */
  streamDevices(): Observable<DeviceView[]> {
    return new Observable<DeviceView[]>((subscriber) => {
      const controller = new AbortController();
      let stopped = false;

      const readStream = async (): Promise<void> => {
        const response = await fetch(`${this.baseUrl}/stream`, {
          headers: { Authorization: `Bearer ${this.auth.token}` },
          signal: controller.signal,
        });
        if (!response.ok || !response.body) {
          throw new Error(`Поток /devices/stream ответил ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (!stopped) {
          const { value, done } = await reader.read();
          if (done) {
            return;
          }
          buffer += decoder.decode(value, { stream: true });
          const frames = buffer.split('\n\n');
          buffer = frames.pop() ?? '';

          for (const frame of frames) {
            const dataLines = frame.split('\n').filter((line) => line.startsWith('data:'));
            if (dataLines.length === 0) {
              continue;
            }
            const json = dataLines.map((line) => line.slice(5).trimStart()).join('\n');
            subscriber.next(JSON.parse(json) as DeviceView[]);
          }
        }
      };

      const connectLoop = async () => {
        while (!stopped) {
          try {
            await readStream();
          } catch {
            // Обрыв соединения (рестарт пода/сеть) — не критично, переподключаемся ниже, если ещё
            // не отписались.
          }
          if (!stopped) {
            await new Promise((resolve) => setTimeout(resolve, RECONNECT_DELAY_MS));
          }
        }
      };

      connectLoop();

      return () => {
        stopped = true;
        controller.abort();
      };
    });
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

  getEvents(hours = 24, limit = 50): Observable<DeviceEvent[]> {
    return this.http.get<DeviceEvent[]>(`${this.baseUrl}/events`, {
      params: { hours, limit },
    });
  }

  // ADMIN-only на бэкенде (SecurityConfig) — 403 для OPERATOR, форма скрыта на фронте тем же
  // AuthService.role(), но реальная проверка всегда на сервере.
  register(deviceId: string, name: string, groupName: string): Observable<RegisteredDevice> {
    return this.http.post<RegisteredDevice>(this.baseUrl, { deviceId, name, groupName });
  }

  update(deviceId: string, name: string, groupName: string, active: boolean): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${deviceId}`, { name, groupName, active });
  }
}
