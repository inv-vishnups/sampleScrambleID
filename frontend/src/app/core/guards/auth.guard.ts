import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../auth/auth.service';

export const authGuard: CanActivateFn = () => {
	const auth = inject(AuthService);
	const router = inject(Router);
	if (auth.isLoggedIn()) {
		return true;
	}
	return auth.tryRestoreSession().pipe(
		map((ok) => (ok ? true : router.parseUrl('/login'))),
	);
};
