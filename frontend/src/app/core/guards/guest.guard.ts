import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../auth/auth.service';

/** Redirect authenticated users away from the login page. */
export const guestGuard: CanActivateFn = () => {
	const auth = inject(AuthService);
	const router = inject(Router);
	if (auth.isLoggedIn()) {
		return router.parseUrl('/dashboard');
	}
	return auth.tryRestoreSession().pipe(
		map((ok) => (ok ? router.parseUrl('/dashboard') : true)),
	);
};
