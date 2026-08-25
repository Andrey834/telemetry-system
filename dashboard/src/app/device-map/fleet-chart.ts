import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { DeviceEvent } from '../models/device-event';
import { DeviceView } from '../models/device-view';
import { FleetActivityPoint } from '../models/fleet-activity-point';
import { RoutePoint } from '../models/route-point';

const STATUS_COLORS: Record<string, string> = {
  ONLINE: '#22c55e',
  STALE: '#eab308',
  OFFLINE: '#6b7280',
};

const STATUS_LABELS: Record<string, string> = {
  ONLINE: 'Online',
  STALE: 'Задерживаются',
  OFFLINE: 'Offline',
};

const COMPARE_COLORS = ['#2563eb', '#f97316', '#a855f7', '#14b8a6', '#ec4899', '#84cc16'];

// Пауза между соседними точками истории дольше этого порога считается простоем при подсчёте
// аптайма — то же значение, что STALE_THRESHOLD на бэке (query-service DeviceQueryService).
const UPTIME_GAP_THRESHOLD_MS = 5 * 60 * 1000;

const SPEED_BUCKETS: Array<[label: string, min: number, max: number]> = [
  ['0–20', 0, 20],
  ['20–40', 20, 40],
  ['40–60', 40, 60],
  ['60+', 60, Infinity],
];

function haversineKm(a: RoutePoint, b: RoutePoint): number {
  const R = 6371;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLon = toRad(b.lon - a.lon);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

@Component({
  imports: [BaseChartDirective, DatePipe],
  selector: 'app-fleet-chart',
  templateUrl: './fleet-chart.html',
})
export class FleetChart {
  readonly devices = input<DeviceView[]>([]);
  readonly selectedDeviceId = input<string | null>(null);
  readonly selectedDeviceName = input<string | null>(null);
  readonly routePoints = input<RoutePoint[]>([]);
  readonly compareSeries = input<Map<string, RoutePoint[]>>(new Map());
  readonly activityPoints = input<FleetActivityPoint[]>([]);
  readonly events = input<DeviceEvent[]>([]);

  // Данные обновляются каждые 5с (общий poll в device-map.ts) — анимация на каждое обновление
  // выглядит как моргание графика, а не полезная информация, поэтому везде выключена.
  protected readonly statusChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    animation: false,
    plugins: { legend: { position: 'bottom', labels: { color: '#e5e7eb' } } },
  };

  protected readonly speedChartOptions: ChartConfiguration<'line'>['options'] = {
    animation: false,
    scales: {
      x: { ticks: { color: '#9ca3af' } },
      y: { ticks: { color: '#9ca3af' }, title: { display: true, text: 'км/ч', color: '#9ca3af' } },
    },
    plugins: { legend: { display: true, labels: { color: '#e5e7eb' } } },
  };

  protected readonly barChartOptions: ChartConfiguration<'bar'>['options'] = {
    animation: false,
    scales: {
      x: { ticks: { color: '#9ca3af' }, stacked: true },
      y: { ticks: { color: '#9ca3af' }, stacked: true },
    },
    plugins: { legend: { position: 'bottom', labels: { color: '#e5e7eb' } } },
  };

  protected readonly histogramOptions: ChartConfiguration<'bar'>['options'] = {
    animation: false,
    scales: { x: { ticks: { color: '#9ca3af' } }, y: { ticks: { color: '#9ca3af' } } },
    plugins: { legend: { display: false } },
  };

  protected readonly activityChartOptions: ChartConfiguration<'line'>['options'] = {
    animation: false,
    scales: { x: { ticks: { color: '#9ca3af' } }, y: { ticks: { color: '#9ca3af' } } },
    plugins: { legend: { display: false } },
  };

  protected readonly statusChartData = computed<ChartData<'doughnut'>>(() => {
    const counts: Record<string, number> = { ONLINE: 0, STALE: 0, OFFLINE: 0 };
    for (const device of this.devices()) {
      counts[device.status] = (counts[device.status] ?? 0) + 1;
    }
    const statuses = Object.keys(counts).filter((status) => counts[status] > 0);
    return {
      labels: statuses.map((status) => STATUS_LABELS[status]),
      datasets: [{ data: statuses.map((status) => counts[status]), backgroundColor: statuses.map((status) => STATUS_COLORS[status]) }],
    };
  });

  protected readonly speedChartData = computed<ChartData<'line'>>(() => {
    const datasets: ChartData<'line'>['datasets'] = [];
    const labelOf = (id: string) => this.devices().find((d) => d.deviceId === id)?.name ?? id;

    if (this.routePoints().length > 0) {
      datasets.push({
        data: this.routePoints().map((point) => point.speedKmh ?? 0),
        label: this.selectedDeviceName() ?? 'Выбранное устройство',
        borderColor: COMPARE_COLORS[0],
        backgroundColor: 'rgba(37, 99, 235, 0.1)',
        fill: false,
        tension: 0.3,
      });
    }

    let colorIndex = 1;
    for (const [deviceId, points] of this.compareSeries()) {
      datasets.push({
        data: points.map((point) => point.speedKmh ?? 0),
        label: labelOf(deviceId),
        borderColor: COMPARE_COLORS[colorIndex % COMPARE_COLORS.length],
        fill: false,
        tension: 0.3,
      });
      colorIndex++;
    }

    // Labels (время) берём с самой длинной загруженной серии — точки сравниваемых устройств
    // приходят с разными временными метками, точное совмещение по времени не делаем (не критично
    // для визуального сравнения формы графиков).
    const longest = [this.routePoints(), ...this.compareSeries().values()].reduce(
      (a, b) => (b.length > a.length ? b : a),
      [] as RoutePoint[],
    );
    return {
      labels: longest.map((point) => new Date(point.recordedAt).toLocaleTimeString()),
      datasets,
    };
  });

  /** Пробег выбранного устройства (сумма расстояний между соседними точками истории). */
  protected readonly distanceKm = computed(() => {
    const points = this.routePoints();
    let total = 0;
    for (let i = 1; i < points.length; i++) {
      total += haversineKm(points[i - 1], points[i]);
    }
    return total;
  });

  protected readonly avgSpeed = computed(() => {
    const speeds = this.routePoints().map((p) => p.speedKmh).filter((s): s is number => s != null);
    return speeds.length === 0 ? null : speeds.reduce((a, b) => a + b, 0) / speeds.length;
  });

  protected readonly maxSpeed = computed(() => {
    const speeds = this.routePoints().map((p) => p.speedKmh).filter((s): s is number => s != null);
    return speeds.length === 0 ? null : Math.max(...speeds);
  });

  /** % времени "в движении" за загруженный период — разрывы между соседними точками дольше
   * UPTIME_GAP_THRESHOLD_MS считаются простоем. */
  protected readonly uptimePercent = computed(() => {
    const points = this.routePoints();
    if (points.length < 2) {
      return null;
    }
    const start = new Date(points[0].recordedAt).getTime();
    const end = new Date(points[points.length - 1].recordedAt).getTime();
    const span = end - start;
    if (span <= 0) {
      return null;
    }
    let downtime = 0;
    for (let i = 1; i < points.length; i++) {
      const gap = new Date(points[i].recordedAt).getTime() - new Date(points[i - 1].recordedAt).getTime();
      if (gap > UPTIME_GAP_THRESHOLD_MS) {
        downtime += gap;
      }
    }
    return Math.max(0, Math.min(100, ((span - downtime) / span) * 100));
  });

  protected readonly speedHistogramData = computed<ChartData<'bar'>>(() => {
    const counts = SPEED_BUCKETS.map(() => 0);
    for (const device of this.devices()) {
      if (device.speedKmh == null) {
        continue;
      }
      const index = SPEED_BUCKETS.findIndex(([, min, max]) => device.speedKmh! >= min && device.speedKmh! < max);
      if (index >= 0) {
        counts[index]++;
      }
    }
    return {
      labels: SPEED_BUCKETS.map(([label]) => label + ' км/ч'),
      datasets: [{ data: counts, label: 'Устройств', backgroundColor: '#2563eb' }],
    };
  });

  protected readonly groupStatusData = computed<ChartData<'bar'>>(() => {
    const groups = new Map<string, Record<string, number>>();
    for (const device of this.devices()) {
      const counts = groups.get(device.groupName) ?? { ONLINE: 0, STALE: 0, OFFLINE: 0 };
      counts[device.status]++;
      groups.set(device.groupName, counts);
    }
    const groupNames = [...groups.keys()].sort((a, b) => a.localeCompare(b));
    const statuses: Array<keyof typeof STATUS_LABELS> = ['ONLINE', 'STALE', 'OFFLINE'];
    return {
      labels: groupNames,
      datasets: statuses.map((status) => ({
        data: groupNames.map((name) => groups.get(name)![status]),
        label: STATUS_LABELS[status],
        backgroundColor: STATUS_COLORS[status],
      })),
    };
  });

  protected readonly activityChartData = computed<ChartData<'line'>>(() => ({
    labels: this.activityPoints().map((p) => new Date(p.bucket).toLocaleTimeString()),
    datasets: [
      {
        data: this.activityPoints().map((p) => p.count),
        label: 'Сообщений/мин',
        borderColor: '#22c55e',
        backgroundColor: 'rgba(34, 197, 94, 0.15)',
        fill: true,
        tension: 0.2,
      },
    ],
  }));

  protected exportCsv(): void {
    const points = this.routePoints();
    if (points.length === 0) {
      return;
    }
    const header = 'recordedAt,lat,lon,speedKmh\n';
    const rows = points.map((p) => `${p.recordedAt},${p.lat},${p.lon},${p.speedKmh ?? ''}`).join('\n');
    const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${this.selectedDeviceId() ?? 'device'}-history.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }
}
