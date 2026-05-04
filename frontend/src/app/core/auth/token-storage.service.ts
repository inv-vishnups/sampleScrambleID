import { Injectable } from '@angular/core';

const REFRESH_KEY = 'scimapp_refresh_token';

/**
 * Refresh token in sessionStorage (survives reload within the tab).
 * Access token stays in memory only ({@link AuthService}).
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
	getRefreshToken(): string | null {
		return sessionStorage.getItem(REFRESH_KEY);
	}

	setRefreshToken(token: string): void {
		sessionStorage.setItem(REFRESH_KEY, token);
	}

	clearRefreshToken(): void {
		sessionStorage.removeItem(REFRESH_KEY);
	}
}
