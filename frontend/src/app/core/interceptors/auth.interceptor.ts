import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';

function isAuthEndpoint(url: string): boolean {
	return (
		url.includes('/auth/login') ||
		url.includes('/auth/refresh') ||
		url.includes('/auth/logout')
	);
}

/**
 * Attaches the access JWT to API calls and, on 401, refreshes tokens once and retries.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
	const auth = inject(AuthService);
	const skipAuthHeader = isAuthEndpoint(req.url);

	let outgoing = req;
	if (!skipAuthHeader) {
		const token = auth.getAccessToken();
		if (token) {
			outgoing = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
		}
	}

	return next(outgoing).pipe(
		catchError((err: HttpErrorResponse) => {
			if (err.status !== 401 || skipAuthHeader) {
				return throwError(() => err);
			}
			return auth.refreshTokens().pipe(
				switchMap((tokens) => {
					const retry = req.clone({
						setHeaders: { Authorization: `Bearer ${tokens.accessToken}` },
					});
					return next(retry);
				}),
				catchError((e) => throwError(() => e)),
			);
		}),
	);
};
