import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      console.error(`[HTTP ${err.status}] ${req.method} ${req.url}`, err.error ?? err.message);
      return throwError(() => err);
    }),
  );
