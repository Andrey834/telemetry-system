import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import * as L from 'leaflet';
import 'leaflet.heat';
import { Router } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { DeviceService } from '../services/device.service';
import { AuthService } from '../services/auth.service';
import { DeviceView, DeviceStatus } from '../models/device-view';
import { FleetActivityPoint } from '../models/fleet-activity-point';
import { RoutePoint } from '../models/route-point';
import { FleetChart } from './fleet-chart';
import { DeviceAdmin } from './device-admin';
import { Toast } from '../shared/toast';

const POLL_INTERVAL_MS = 5000;
const DEFAULT_CENTER: L.LatLngExpression = [55.751244, 37.618423]; // Москва — стартовый вид карты

type SortBy = 'name' | 'speed' | 'status';
type Tab = 'map' | 'charts' | 'devices';

// Порядок статусов в сортировке "по статусу" — сначала те, на кого стоит смотреть внимательнее.
const STATUS_ORDER: Record<DeviceStatus, number> = { OFFLINE: 0, STALE: 1, ONLINE: 2 };

// Классическая проблема Leaflet + бандлеры: относительные url() в CSS не резолвятся так,
// как ожидает Leaflet, из-за чего маркеры остаются без иконки. Иконки скопированы в
// assets/leaflet отдельным assets-глобом (angular.json), путь указываем явно.
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'assets/leaflet/marker-icon-2x.png',
  iconUrl: 'assets/leaflet/marker-icon.png',
  shadowUrl: 'assets/leaflet/marker-shadow.png',
});

@Component({
  imports: [FleetChart, DeviceAdmin, Toast],
  host: { class: 'flex flex-col h-full' },
  selector: 'app-device-map',
  templateUrl: './device-map.html',
})
export class DeviceMap implements AfterViewInit, OnDestroy {
  @ViewChild('mapContainer', { static: true }) private mapContainer!: ElementRef<HTMLDivElement>;
  @ViewChild(Toast) private toast!: Toast;

  protected readonly deviceCount = signal(0);
  protected readonly lastUpdated = signal<Date | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly devices = signal<DeviceView[]>([]);
  protected readonly selectedDeviceId = signal<string | null>(null);
  protected readonly routePoints = signal<RoutePoint[]>([]);

  protected readonly compareIds = signal<Set<string>>(new Set());
  protected readonly compareSeries = signal<Map<string, RoutePoint[]>>(new Map());
  protected readonly activityPoints = signal<FleetActivityPoint[]>([]);
  protected readonly heatmapEnabled = signal(false);

  protected readonly sortBy = signal<SortBy>('name');
  protected readonly activeTab = signal<Tab>('map');
  protected readonly mobileSidebarOpen = signal(false);

  protected readonly selectedDevice = computed(() =>
    this.devices().find((d) => d.deviceId === this.selectedDeviceId()) ?? null,
  );

  // Группировка по groupName, внутри группы — сортировка по выбранному полю. Группы идут
  // в алфавитном порядке названия — предсказуемее, чем "как пришло с бэкенда".
  protected readonly groupedDevices = computed(() => {
    const groups = new Map<string, DeviceView[]>();
    for (const device of this.devices()) {
      const list = groups.get(device.groupName) ?? [];
      list.push(device);
      groups.set(device.groupName, list);
    }

    const comparator = this.comparatorFor(this.sortBy());
    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([groupName, list]) => ({ groupName, devices: [...list].sort(comparator) }));
  });

  private readonly deviceService = inject(DeviceService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private map?: L.Map;
  // Обновляем позиции существующих маркеров, а не пересоздаём слой на каждый poll —
  // так у маркера не "мигает" state между тиками опроса.
  private readonly markers = new Map<string, L.Marker>();
  private routeLine?: L.Polyline;
  private heatLayer?: L.HeatLayer;
  private pollSubscription?: Subscription;

  // Для toast "устройство пропало" — статус с прошлого тика поллинга, чтобы поймать именно
  // переход в OFFLINE, а не отрендерить toast на каждый poll, пока оно там остаётся.
  private readonly previousStatuses = new Map<string, DeviceStatus>();
  private hasRenderedOnce = false;

  ngAfterViewInit(): void {
    this.map = L.map(this.mapContainer.nativeElement).setView(DEFAULT_CENTER, 11);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(this.map);

    this.pollSubscription = interval(POLL_INTERVAL_MS)
      .pipe(startWith(0), switchMap(() => this.deviceService.getAll()))
      .subscribe({
        next: (states) => this.render(states),
        error: () => this.error.set('Не удалось получить данные от query-service'),
      });
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
    this.map?.remove();
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }

  protected setSortBy(value: SortBy): void {
    this.sortBy.set(value);
  }

  protected setActiveTab(tab: Tab): void {
    this.activeTab.set(tab);
    if (tab === 'charts') {
      this.deviceService.getActivity().subscribe({
        next: (points) => this.activityPoints.set(points),
        error: () => this.activityPoints.set([]),
      });
    }
  }

  protected toggleMobileSidebar(): void {
    this.mobileSidebarOpen.update((open) => !open);
  }

  protected selectDevice(deviceId: string): void {
    this.selectedDeviceId.set(deviceId);
    this.mobileSidebarOpen.set(false);

    const marker = this.markers.get(deviceId);
    if (marker && this.map) {
      this.map.flyTo(marker.getLatLng(), Math.max(this.map.getZoom(), 15));
      marker.openPopup();
    }

    this.deviceService.getHistory(deviceId).subscribe({
      next: (points) => {
        this.routePoints.set(points);
        this.drawRoute(points);
      },
      error: () => this.routePoints.set([]),
    });
  }

  /** Отдельный от selectDevice чекбокс "сравнить" — не двигает карту, только добавляет линию
   * скорости этого устройства на общий график в charts. */
  protected toggleCompare(deviceId: string, event: Event): void {
    event.stopPropagation();
    const next = new Set(this.compareIds());
    if (next.has(deviceId)) {
      next.delete(deviceId);
      this.compareIds.set(next);
      const series = new Map(this.compareSeries());
      series.delete(deviceId);
      this.compareSeries.set(series);
      return;
    }

    next.add(deviceId);
    this.compareIds.set(next);
    this.deviceService.getHistory(deviceId).subscribe({
      next: (points) => {
        const series = new Map(this.compareSeries());
        series.set(deviceId, points);
        this.compareSeries.set(series);
      },
    });
  }

  protected toggleHeatmap(): void {
    this.heatmapEnabled.update((v) => !v);
    if (!this.heatmapEnabled()) {
      this.heatLayer?.remove();
      this.heatLayer = undefined;
    } else {
      this.renderHeatmap();
    }
  }

  private renderHeatmap(): void {
    if (!this.map || !this.heatmapEnabled()) {
      return;
    }
    const points: L.HeatLatLngTuple[] = this.devices()
      .filter((d) => d.lat != null && d.lon != null)
      .map((d) => [d.lat!, d.lon!, 1]);

    if (this.heatLayer) {
      this.heatLayer.setLatLngs(points);
    } else {
      this.heatLayer = L.heatLayer(points, { radius: 30, blur: 20 }).addTo(this.map);
    }
  }

  private drawRoute(points: RoutePoint[]): void {
    this.routeLine?.remove();
    if (!this.map || points.length < 2) {
      return;
    }
    this.routeLine = L.polyline(
      points.map((p) => [p.lat, p.lon] as L.LatLngExpression),
      { color: '#2563eb', weight: 3 },
    ).addTo(this.map);
  }

  private comparatorFor(sortBy: SortBy): (a: DeviceView, b: DeviceView) => number {
    switch (sortBy) {
      case 'speed':
        return (a, b) => (b.speedKmh ?? -1) - (a.speedKmh ?? -1);
      case 'status':
        return (a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status] || a.name.localeCompare(b.name);
      case 'name':
      default:
        return (a, b) => a.name.localeCompare(b.name);
    }
  }

  private render(allDevices: DeviceView[]): void {
    this.error.set(null);
    this.lastUpdated.set(new Date());
    this.checkForNewlyOffline(allDevices);
    this.devices.set(allDevices);

    // Устройства без текущих координат (ни разу не отчитались, либо запись протухла в Redis)
    // остаются в списке слева, но маркер на карте им ставить нечем.
    const onMap = allDevices.filter((d) => d.lat != null && d.lon != null);
    this.deviceCount.set(onMap.length);

    const seen = new Set<string>();

    for (const device of onMap) {
      seen.add(device.deviceId);
      const position: L.LatLngExpression = [device.lat!, device.lon!];
      const existing = this.markers.get(device.deviceId);

      if (existing) {
        existing.setLatLng(position).setPopupContent(this.popupHtml(device));
      } else {
        const marker = L.marker(position).bindPopup(this.popupHtml(device)).addTo(this.map!);
        this.markers.set(device.deviceId, marker);
      }
    }

    // Устройство пропало из ответа query-service (TTL/рестарт) — убираем маркер с карты.
    for (const [deviceId, marker] of this.markers) {
      if (!seen.has(deviceId)) {
        marker.remove();
        this.markers.delete(deviceId);
        if (this.selectedDeviceId() === deviceId) {
          this.selectedDeviceId.set(null);
        }
      }
    }

    this.renderHeatmap();
  }

  private checkForNewlyOffline(allDevices: DeviceView[]): void {
    if (this.hasRenderedOnce) {
      for (const device of allDevices) {
        const prev = this.previousStatuses.get(device.deviceId);
        if ((prev === 'ONLINE' || prev === 'STALE') && device.status === 'OFFLINE') {
          this.toast?.show(`${device.name} пропало — давно не шлёт данные`);
        }
      }
    }
    for (const device of allDevices) {
      this.previousStatuses.set(device.deviceId, device.status);
    }
    this.hasRenderedOnce = true;
  }

  private popupHtml(device: DeviceView): string {
    const speed = device.speedKmh != null ? `${device.speedKmh.toFixed(1)} км/ч` : '—';
    const updated = device.recordedAt ? new Date(device.recordedAt).toLocaleTimeString() : '—';
    return `<strong>${device.name}</strong><br>Скорость: ${speed}<br>Обновлено: ${updated}`;
  }
}
