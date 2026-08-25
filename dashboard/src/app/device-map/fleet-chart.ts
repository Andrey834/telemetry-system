import { Component, computed, input } from '@angular/core';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { DeviceView } from '../models/device-view';
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

@Component({
  imports: [BaseChartDirective],
  selector: 'app-fleet-chart',
  templateUrl: './fleet-chart.html',
})
export class FleetChart {
  readonly devices = input<DeviceView[]>([]);
  readonly selectedDeviceName = input<string | null>(null);
  readonly routePoints = input<RoutePoint[]>([]);

  protected readonly statusChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    plugins: { legend: { position: 'bottom', labels: { color: '#e5e7eb' } } },
  };

  protected readonly speedChartOptions: ChartConfiguration<'line'>['options'] = {
    scales: {
      x: { ticks: { color: '#9ca3af' } },
      y: { ticks: { color: '#9ca3af' }, title: { display: true, text: 'км/ч', color: '#9ca3af' } },
    },
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

  protected readonly speedChartData = computed<ChartData<'line'>>(() => ({
    labels: this.routePoints().map((point) => new Date(point.recordedAt).toLocaleTimeString()),
    datasets: [
      {
        data: this.routePoints().map((point) => point.speedKmh ?? 0),
        label: 'Скорость, км/ч',
        borderColor: '#2563eb',
        backgroundColor: 'rgba(37, 99, 235, 0.15)',
        fill: true,
        tension: 0.3,
      },
    ],
  }));
}
