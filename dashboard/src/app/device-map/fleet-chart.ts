import { DatePipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { DeviceEvent } from '../models/device-event';
import { DeviceView } from '../models/device-view';
import { FleetActivityPoint } from '../models/fleet-activity-point';
import { RoutePoint } from '../models/route-point';
import { ThemeService } from '../services/theme.service';

const STATUS_LABELS: Record<string, string> = {
  ONLINE: 'Online',
  STALE: 'Задерживаются',
  OFFLINE: 'Offline',
};

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

function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
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

  private readonly theme = inject(ThemeService);

  // Цвета читаются из тех же CSS-переменных, что и Tailwind-токены в шаблонах — при смене темы
  // graphs пересчитываются вместе с остальным UI, а не остаются на зашитых hex.
  protected readonly chartTheme = computed(() => {
    this.theme.theme();
    return {
      text: cssVar('--fg-muted'),
      grid: cssVar('--line'),
      accent: cssVar('--accent'),
      ok: cssVar('--ok'),
      warn: cssVar('--warn'),
      off: cssVar('--off'),
      bad: cssVar('--bad'),
    };
  });

  private readonly statusColors = computed(() => ({
    ONLINE: this.chartTheme().ok,
    STALE: this.chartTheme().warn,
    OFFLINE: this.chartTheme().off,
  }));

  private readonly compareColors = computed(() => {
    const t = this.chartTheme();
    return [t.accent, t.ok, t.warn, t.bad];
  });

  // Данные обновляются каждые 5с (общий poll в device-map.ts) — анимация на каждое обновление
  // выглядит как моргание графика, а не полезная информация, поэтому везде выключена.
  protected readonly statusChartOptions = computed<ChartConfiguration<'doughnut'>['options']>(() => ({
    animation: false,
    plugins: { legend: { position: 'bottom', labels: { color: this.chartTheme().text } } },
  }));

  protected readonly speedChartOptions = computed<ChartConfiguration<'line'>['options']>(() => ({
    animation: false,
    scales: {
      x: { ticks: { color: this.chartTheme().text }, grid: { color: this.chartTheme().grid } },
      y: {
        ticks: { color: this.chartTheme().text },
        grid: { color: this.chartTheme().grid },
        title: { display: true, text: 'км/ч', color: this.chartTheme().text },
      },
    },
    plugins: { legend: { display: true, labels: { color: this.chartTheme().text } } },
  }));

  protected readonly barChartOptions = computed<ChartConfiguration<'bar'>['options']>(() => ({
    animation: false,
    scales: {
      x: { ticks: { color: this.chartTheme().text }, grid: { color: this.chartTheme().grid }, stacked: true },
      y: { ticks: { color: this.chartTheme().text }, grid: { color: this.chartTheme().grid }, stacked: true },
    },
    plugins: { legend: { position: 'bottom', labels: { color: this.chartTheme().text } } },
  }));

  protected readonly histogramOptions = computed<ChartConfiguration<'bar'>['options']>(() => ({
    animation: false,
    scales: {
      x: { ticks: { color: this.chartTheme().text }, grid: { color: this.chartTheme().grid } },
      y: { ticks: { color: this.chartTheme().text }, grid: { color: this.chartTheme().grid } },
    },
    plugins: { legend: { display: false } },
  }));

  protected readonly activityChartOptions = computed<ChartConfiguration<'line'>['options']>(() => ({
    animation: false,
    scales: {
      x: { ticks: { color: this.chartTheme().text }, grid: { color: this.chartTheme().grid } },
      y: { ticks: { color: this.chartTheme().text }, grid: { color: this.chartTheme().grid } },
    },
    plugins: { legend: { display: false } },
  }));

  protected readonly statusChartData = computed<ChartData<'doughnut'>>(() => {
    const counts: Record<string, number> = { ONLINE: 0, STALE: 0, OFFLINE: 0 };
    for (const device of this.devices()) {
      counts[device.status] = (counts[device.status] ?? 0) + 1;
    }
    const statuses = Object.keys(counts).filter((status) => counts[status] > 0);
    const colors = this.statusColors() as Record<string, string>;
    return {
      labels: statuses.map((status) => STATUS_LABELS[status]),
      datasets: [{ data: statuses.map((status) => counts[status]), backgroundColor: statuses.map((status) => colors[status]) }],
    };
  });

  protected readonly speedChartData = computed<ChartData<'line'>>(() => {
    const datasets: ChartData<'line'>['datasets'] = [];
    const labelOf = (id: string) => this.devices().find((d) => d.deviceId === id)?.name ?? id;
    const colors = this.compareColors();

    if (this.routePoints().length > 0) {
      datasets.push({
        data: this.routePoints().map((point) => point.speedKmh ?? 0),
        label: this.selectedDeviceName() ?? 'Выбранное устройство',
        borderColor: colors[0],
        backgroundColor: colors[0],
        fill: false,
        tension: 0.3,
      });
    }

    let colorIndex = 1;
    for (const [deviceId, points] of this.compareSeries()) {
      datasets.push({
        data: points.map((point) => point.speedKmh ?? 0),
        label: labelOf(deviceId),
        borderColor: colors[colorIndex % colors.length],
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
      datasets: [{ data: counts, label: 'Устройств', backgroundColor: this.chartTheme().accent }],
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
    const colors = this.statusColors() as Record<string, string>;
    return {
      labels: groupNames,
      datasets: statuses.map((status) => ({
        data: groupNames.map((name) => groups.get(name)![status]),
        label: STATUS_LABELS[status],
        backgroundColor: colors[status],
      })),
    };
  });

  protected readonly activityChartData = computed<ChartData<'line'>>(() => ({
    labels: this.activityPoints().map((p) => new Date(p.bucket).toLocaleTimeString()),
    datasets: [
      {
        data: this.activityPoints().map((p) => p.count),
        label: 'Сообщений/мин',
        borderColor: this.chartTheme().accent,
        backgroundColor: `color-mix(in oklab, ${this.chartTheme().accent} 18%, transparent)`,
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
