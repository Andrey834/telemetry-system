import { Component, computed, signal } from '@angular/core';

type ToastType = 'ok' | 'bad';

interface ToastMessage {
  id: number;
  text: string;
  type: ToastType;
}

const AUTO_HIDE_MS = 5000;

@Component({
  selector: 'app-toast',
  host: { class: 'pointer-events-none fixed bottom-4 right-4 z-[1200] flex flex-col gap-2' },
  templateUrl: './toast.html',
})
export class Toast {
  private readonly messages = signal<ToastMessage[]>([]);
  protected readonly visible = computed(() => this.messages());

  private nextId = 0;

  show(text: string, type: ToastType = 'bad'): void {
    const id = this.nextId++;
    this.messages.update((list) => [...list, { id, text, type }]);
    setTimeout(() => this.dismiss(id), AUTO_HIDE_MS);
  }

  protected dismiss(id: number): void {
    this.messages.update((list) => list.filter((m) => m.id !== id));
  }
}
