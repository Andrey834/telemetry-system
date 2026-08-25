import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DeviceStatus, DeviceView } from '../models/device-view';
import { RegisteredDevice } from '../models/registered-device';
import { DeviceService } from '../services/device.service';

const STATUS_BADGE_CLASSES: Record<DeviceStatus, string> = {
  ONLINE: 'bg-ok/10 text-ok',
  STALE: 'bg-warn/10 text-warn',
  OFFLINE: 'bg-off/15 text-off',
};

@Component({
  imports: [FormsModule],
  selector: 'app-device-admin',
  host: { class: 'block h-full overflow-y-auto p-4 md:p-6' },
  templateUrl: './device-admin.html',
})
export class DeviceAdmin {
  readonly devices = input<DeviceView[]>([]);

  protected deviceId = '';
  protected name = '';
  protected groupName = '';
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly registered = signal<RegisteredDevice | null>(null);
  protected readonly copied = signal(false);

  private readonly deviceService = inject(DeviceService);

  protected submit(): void {
    const deviceId = this.deviceId.trim();
    const name = this.name.trim();
    const groupName = this.groupName.trim();
    if (!deviceId || !name || !groupName) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);
    this.deviceService.register(deviceId, name, groupName).subscribe({
      next: (result) => {
        this.registered.set(result);
        this.submitting.set(false);
        this.deviceId = '';
        this.name = '';
        this.groupName = '';
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(
          err?.status === 409
            ? 'Устройство с таким deviceId уже существует'
            : 'Не удалось зарегистрировать устройство',
        );
      },
    });
  }

  protected readonly editingDeviceId = signal<string | null>(null);
  protected editName = '';
  protected editGroupName = '';
  protected readonly updatingId = signal<string | null>(null);
  protected readonly updateError = signal<string | null>(null);

  protected startEdit(device: DeviceView): void {
    this.editingDeviceId.set(device.deviceId);
    this.editName = device.name;
    this.editGroupName = device.groupName;
  }

  protected cancelEdit(): void {
    this.editingDeviceId.set(null);
  }

  protected saveEdit(device: DeviceView): void {
    const name = this.editName.trim();
    const groupName = this.editGroupName.trim();
    if (!name || !groupName) {
      return;
    }
    this.updatingId.set(device.deviceId);
    this.updateError.set(null);
    this.deviceService.update(device.deviceId, name, groupName, device.active).subscribe({
      next: () => {
        this.updatingId.set(null);
        this.editingDeviceId.set(null);
      },
      error: () => {
        this.updatingId.set(null);
        this.updateError.set('Не удалось сохранить изменения');
      },
    });
  }

  protected toggleActive(device: DeviceView): void {
    this.updatingId.set(device.deviceId);
    this.updateError.set(null);
    this.deviceService.update(device.deviceId, device.name, device.groupName, !device.active).subscribe({
      next: () => this.updatingId.set(null),
      error: () => {
        this.updatingId.set(null);
        this.updateError.set('Не удалось изменить статус устройства');
      },
    });
  }

  protected statusBadgeClass(status: DeviceStatus): string {
    return STATUS_BADGE_CLASSES[status];
  }

  protected closeKeyModal(): void {
    this.registered.set(null);
    this.copied.set(false);
  }

  protected copyKey(): void {
    const key = this.registered()?.apiKey;
    if (!key) {
      return;
    }
    navigator.clipboard.writeText(key).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }
}
