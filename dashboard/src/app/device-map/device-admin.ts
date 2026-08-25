import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DeviceView } from '../models/device-view';
import { RegisteredDevice } from '../models/registered-device';
import { DeviceService } from '../services/device.service';

@Component({
  imports: [FormsModule],
  selector: 'app-device-admin',
  host: { class: 'block h-full overflow-y-auto bg-gray-900 p-4 md:p-6' },
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
