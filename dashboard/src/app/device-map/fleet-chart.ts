import { DatePipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { Chart, ChartConfiguration, ChartData, Plugin, ScriptableContext } from 'chart.js';
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

/** Полупрозрачная версия токен-цвета для заливки баров/зон — сам токен уже может быть rgb()
 * с своей альфой (тема), поэтому не трогаем её напрямую, просто накладываем color-mix. */
function fade(color: string, percent: number): string {
  return `color-mix(in oklab, ${color} ${percent}%, transparent)`;
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
      fg: cssVar('--fg'),
      text: cssVar('--fg-muted'),
      faint: cssVar('--fg-faint'),
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
    cutout: '68%',
    plugins: { legend: { display: false } },
  }));

  /** Число устройств в кольце доната — доступного встроенного способа у Chart.js нет,
   * рисуется поверх canvas отдельным плагином. */
  protected readonly statusChartPlugins = computed<Plugin<'doughnut'>[]>(() => {
    const total = this.devices().length;
    const fg = this.chartTheme().fg;
    const faint = this.chartTheme().faint;
    return [
      {
        id: 'centerCount',
        afterDraw: (chart: Chart) => {
          const { ctx, chartArea } = chart;
          if (!chartArea) {
            return;
          }
          const cx = (chartArea.left + chartArea.right) / 2;
          const cy = (chartArea.top + chartArea.bottom) / 2;
          ctx.save();
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.font = '600 20px Inter, system-ui, sans-serif';
          ctx.fillStyle = fg;
          ctx.fillText(String(total), cx, cy - 7);
          ctx.font = '400 10px Inter, system-ui, sans-serif';
          ctx.fillStyle = faint;
          ctx.fillText('всего', cx, cy + 11);
          ctx.restore();
        },
      },
    ];
  });

  protected readonly speedChartOptions = computed<ChartConfiguration<'line'>['options']>(() => ({
    animation: false,
    interaction: { mode: 'index', intersect: false },
    scales: {
      x: { grid: { display: false }, border: { color: this.chartTheme().grid }, ticks: { color: this.chartTheme().faint, maxRotation: 0 } },
      y: {
        grid: { color: this.chartTheme().grid },
        border: { display: false },
        ticks: { color: this.chartTheme().faint },
        title: { display: true, text: 'км/ч', color: this.chartTheme().faint },
      },
    },
    plugins: {
      legend: { display: true, position: 'bottom', labels: { color: this.chartTheme().text, boxWidth: 14, boxHeight: 2 } },
      tooltip: {
        backgroundColor: cssVar('--surface-2'),
        titleColor: this.chartTheme().fg,
        bodyColor: this.chartTheme().text,
        borderColor: cssVar('--line-strong'),
        borderWidth: 1,
        padding: 8,
      },
    },
  }));

  protected readonly barChartOptions = computed<ChartConfiguration<'bar'>['options']>(() => ({
    animation: false,
    indexAxis: 'y',
    scales: {
      x: { grid: { color: this.chartTheme().grid }, border: { display: false }, ticks: { color: this.chartTheme().faint }, stacked: true },
      y: { grid: { display: false }, border: { color: this.chartTheme().grid }, ticks: { color: this.chartTheme().text }, stacked: true },
    },
    plugins: { legend: { position: 'bottom', labels: { color: this.chartTheme().text, boxWidth: 10, boxHeight: 10 } } },
  }));

  protected readonly histogramOptions = computed<ChartConfiguration<'bar'>['options']>(() => ({
    animation: false,
    scales: {
      x: { grid: { display: false }, border: { color: this.chartTheme().grid }, ticks: { color: this.chartTheme().faint } },
      y: { grid: { color: this.chartTheme().grid }, border: { display: false }, ticks: { color: this.chartTheme().faint } },
    },
    plugins: { legend: { display: false } },
  }));

  protected readonly activityChartOptions = computed<ChartConfiguration<'line'>['options']>(() => ({
    animation: false,
    interaction: { mode: 'index', intersect: false },
    scales: {
      x: { grid: { display: false }, border: { color: this.chartTheme().grid }, ticks: { color: this.chartTheme().faint, maxRotation: 0 } },
      y: { grid: { color: this.chartTheme().grid }, border: { display: false }, ticks: { color: this.chartTheme().faint } },
    },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: cssVar('--surface-2'),
        titleColor: this.chartTheme().fg,
        bodyColor: this.chartTheme().text,
        borderColor: cssVar('--line-strong'),
        borderWidth: 1,
        padding: 8,
      },
    },
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
      datasets: [{
        data: statuses.map((status) => counts[status]),
        backgroundColor: statuses.map((status) => colors[status]),
        borderWidth: 0,
      }],
    };
  });

  /** Легенда доната рисуется своей вёрсткой в шаблоне (не встроенной Chart.js) — так проще
   * показать жирные числа рядом с подписью, как в макете. */
  protected readonly statusLegend = computed(() => {
    const counts: Record<string, number> = { ONLINE: 0, STALE: 0, OFFLINE: 0 };
    for (const device of this.devices()) {
      counts[device.status] = (counts[device.status] ?? 0) + 1;
    }
    const colors = this.statusColors() as Record<string, string>;
    return (['ONLINE', 'STALE', 'OFFLINE'] as const)
      .filter((status) => counts[status] > 0)
      .map((status) => ({ label: STATUS_LABELS[status], count: counts[status], color: colors[status] }));
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
        pointRadius: 0,
        pointHoverRadius: 4,
        borderWidth: 2.5,
        fill: false,
        tension: 0.35,
      });
    }

    let colorIndex = 1;
    for (const [deviceId, points] of this.compareSeries()) {
      datasets.push({
        data: points.map((point) => point.speedKmh ?? 0),
        label: labelOf(deviceId),
        borderColor: colors[colorIndex % colors.length],
        backgroundColor: colors[colorIndex % colors.length],
        pointRadius: 0,
        pointHoverRadius: 4,
        borderWidth: 2,
        borderDash: [4, 3],
        fill: false,
        tension: 0.35,
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
    const accent = this.chartTheme().accent;
    return {
      labels: SPEED_BUCKETS.map(([label]) => label + ' км/ч'),
      datasets: [{
        data: counts,
        label: 'Устройств',
        backgroundColor: fade(accent, 35),
        borderColor: accent,
        borderWidth: 2,
        borderRadius: 4,
        borderSkipped: false,
        maxBarThickness: 40,
      }],
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
      datasets: statuses.map((status, i) => ({
        data: groupNames.map((name) => groups.get(name)![status]),
        label: STATUS_LABELS[status],
        backgroundColor: colors[status],
        borderRadius: 3,
        borderSkipped: false,
        barThickness: 14,
        // borderSkipped:false + borderRadius на каждом сегменте стека рисует скруглённые углы
        // с обеих сторон сегмента, а не только на крайнем — приемлемо для тонких статус-полосок.
        order: i,
      })),
    };
  });

  protected readonly activityChartData = computed<ChartData<'line'>>(() => {
    const accent = this.chartTheme().accent;
    return {
      labels: this.activityPoints().map((p) => new Date(p.bucket).toLocaleTimeString()),
      datasets: [
        {
          data: this.activityPoints().map((p) => p.count),
          label: 'Сообщений/мин',
          borderColor: accent,
          borderWidth: 2.5,
          pointRadius: 0,
          pointHoverRadius: 4,
          backgroundColor: (context: ScriptableContext<'line'>) => {
            const { ctx, chartArea } = context.chart;
            if (!chartArea) {
              return fade(accent, 15);
            }
            const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
            gradient.addColorStop(0, fade(accent, 32));
            gradient.addColorStop(1, fade(accent, 0));
            return gradient;
          },
          fill: true,
          tension: 0.35,
        },
      ],
    };
  });

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
