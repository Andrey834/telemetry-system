import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import * as L from 'leaflet';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { DeviceService } from '../services/device.service';
import { TelemetryState } from '../models/telemetry-state';

const POLL_INTERVAL_MS = 5000;
const DEFAULT_CENTER: L.LatLngExpression = [55.751244, 37.618423]; // Москва — стартовый вид карты

// Классическая проблема Leaflet + бандлеры: относительные url() в CSS не резолвятся так,
// как ожидает Leaflet, из-за чего маркеры остаются без иконки. Иконки скопированы в
// assets/leaflet отдельным assets-глобом (angular.json), путь указываем явно.
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'assets/leaflet/marker-icon-2x.png',
  iconUrl: 'assets/leaflet/marker-icon.png',
  shadowUrl: 'assets/leaflet/marker-shadow.png',
});

@Component({
  imports: [],
  selector: 'app-device-map',
  styleUrl: './device-map.scss',
  templateUrl: './device-map.html',
})
export class DeviceMap implements AfterViewInit, OnDestroy {
  @ViewChild('mapContainer', { static: true }) private mapContainer!: ElementRef<HTMLDivElement>;

  protected readonly deviceCount = signal(0);
  protected readonly lastUpdated = signal<Date | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly devices = signal<TelemetryState[]>([]);
  protected readonly selectedDeviceId = signal<string | null>(null);

  private readonly deviceService = inject(DeviceService);

  private map?: L.Map;
  // Обновляем позиции существующих маркеров, а не пересоздаём слой на каждый poll —
  // так у маркера не "мигает" state между тиками опроса.
  private readonly markers = new Map<string, L.Marker>();
  private pollSubscription?: Subscription;

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

  protected selectDevice(deviceId: string): void {
    const marker = this.markers.get(deviceId);
    if (!marker || !this.map) {
      return;
    }
    this.selectedDeviceId.set(deviceId);
    this.map.flyTo(marker.getLatLng(), Math.max(this.map.getZoom(), 15));
    marker.openPopup();
  }

  private render(states: TelemetryState[]): void {
    this.error.set(null);
    this.deviceCount.set(states.length);
    this.lastUpdated.set(new Date());
    this.devices.set([...states].sort((a, b) => a.deviceId.localeCompare(b.deviceId)));

    const seen = new Set<string>();

    for (const state of states) {
      seen.add(state.deviceId);
      const position: L.LatLngExpression = [state.lat, state.lon];
      const existing = this.markers.get(state.deviceId);

      if (existing) {
        existing.setLatLng(position).setPopupContent(this.popupHtml(state));
      } else {
        const marker = L.marker(position).bindPopup(this.popupHtml(state)).addTo(this.map!);
        this.markers.set(state.deviceId, marker);
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
  }

  private popupHtml(state: TelemetryState): string {
    const speed = state.speedKmh != null ? `${state.speedKmh.toFixed(1)} км/ч` : '—';
    return `<strong>${state.deviceId}</strong><br>Скорость: ${speed}<br>Обновлено: ${new Date(state.recordedAt).toLocaleTimeString()}`;
  }
}
