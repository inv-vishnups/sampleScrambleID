import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { TokenResponse } from '../models/token-response.model';
import { UserProfile } from '../models/user-profile.model';
import { TokenStorageService } from './token-storage.service';

/**
 * Login / refresh / logout use a plain {@link HttpClient} built from {@link HttpBackend}
 * so requests skip the JWT interceptor (avoids circular deps and refresh loops).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
	private readonly apiUrl = environment.apiUrl;
	private readonly plainHttp: HttpClient;
	private accessToken: string | null = null;
	private refreshInFlight$: Observable<TokenResponse> | null = null;

	private readonly http = inject(HttpClient);
	private readonly httpBackend = inject(HttpBackend);
	private readonly tokenStorage = inject(TokenStorageService);
	private readonly router = inject(Router);

	constructor() {
		this.plainHttp = new HttpClient(this.httpBackend);
	}

	isLoggedIn(): boolean {
		return this.accessToken != null && this.accessToken.length > 0;
	}

	/**
	 * After a full page reload the access token is gone; restore it using the refresh token.
	 */
	tryRestoreSession(): Observable<boolean> {
		if (this.isLoggedIn()) {
			return of(true);
		}
		const rt = this.tokenStorage.getRefreshToken();
		if (!rt) {
			return of(false);
		}
		return this.refreshTokens().pipe(
			map(() => true),
			catchError(() => of(false)),
		);
	}

	getAccessToken(): string | null {
		return this.accessToken;
	}

	login(username: string, password: string): Observable<TokenResponse> {
		return this.plainHttp
			.post<TokenResponse>(`${this.apiUrl}/auth/login`, { username, password })
			.pipe(tap((t) => this.persistTokens(t)));
	}

	/**
	 * Called from the HTTP interceptor when an API call returns 401.
	 * Uses single in-flight Observable so parallel requests share one refresh.
	 */
	refreshTokens(): Observable<TokenResponse> {
		if (this.refreshInFlight$) {
			return this.refreshInFlight$;
		}
		const rt = this.tokenStorage.getRefreshToken();
		if (!rt) {
			return throwError(() => new Error('No refresh token'));
		}
		this.refreshInFlight$ = this.plainHttp
			.post<TokenResponse>(`${this.apiUrl}/auth/refresh`, { refreshToken: rt })
			.pipe(
				tap((t) => this.persistTokens(t)),
				catchError((err) => {
					this.clearSession();
					return throwError(() => err);
				}),
				finalize(() => {
					this.refreshInFlight$ = null;
				}),
				shareReplay({ bufferSize: 1, refCount: false }),
			);
		return this.refreshInFlight$;
	}

	logout(): Observable<unknown> {
		const rt = this.tokenStorage.getRefreshToken();
		if (!rt) {
			this.clearSession();
			void this.router.navigateByUrl('/login');
			return of(null);
		}
		return this.plainHttp.post(`${this.apiUrl}/auth/logout`, { refreshToken: rt }).pipe(
			catchError(() => of(null)),
			finalize(() => {
				this.clearSession();
				void this.router.navigateByUrl('/login');
			}),
		);
	}

	loadProfile(): Observable<UserProfile> {
		return this.http.get<UserProfile>(`${this.apiUrl}/api/me`);
	}

	clearSession(): void {
		this.accessToken = null;
		this.tokenStorage.clearRefreshToken();
	}

	private persistTokens(t: TokenResponse): void {
		this.accessToken = t.accessToken;
		this.tokenStorage.setRefreshToken(t.refreshToken);
	}

	hasRole(role: string, profile: UserProfile | null): boolean {
		return profile?.roles?.includes(role) ?? false;
	}
}
