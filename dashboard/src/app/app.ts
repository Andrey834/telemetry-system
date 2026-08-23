import { Component } from '@angular/core';
import { DeviceMap } from './device-map/device-map';

@Component({
  imports: [DeviceMap],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {}
