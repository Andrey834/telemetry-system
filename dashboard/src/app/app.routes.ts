import { Routes } from '@angular/router';
import { DeviceMap } from './device-map/device-map';
import { Login } from './login/login';
import { authGuard } from './services/auth.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: '', component: DeviceMap, canActivate: [authGuard] },
];
