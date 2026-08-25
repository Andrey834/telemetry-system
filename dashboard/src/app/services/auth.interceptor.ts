import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token;
  const isLoginRequest = req.url.includes('/auth/login');

  const request = !token || isLoginRequest
    ? req
    : req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

  return next(request).pipe(
    catchError((err) => {
      // Токен истёк/отозван — разлогиниваем и уводим на login, а не показываем "не удалось
      // получить данные" бесконечно на каждый следующий poll.
      if (err?.status === 401 && !isLoginRequest) {
        auth.logout();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    }),
  );
};
