import { environment } from '../../environments/environment';

declare global {
  interface Window {
    __env?: { queryServiceUrl?: string };
  }
}

// window.__env заполняется entrypoint-скриптом контейнера при старте (см. public/env.template.js) —
// так один и тот же образ конфигурируется под разные окружения без пересборки. В ng serve
// window.__env пустой (public/env.js — заглушка), поэтому используется environment.ts.
export function apiBaseUrl(): string {
  return window.__env?.queryServiceUrl || environment.queryServiceUrl;
}
