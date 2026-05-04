import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { UserProfile } from '../../core/models/user-profile.model';

@Component({
	selector: 'app-dashboard',
	imports: [],
	templateUrl: './dashboard.component.html',
	styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
	private readonly auth = inject(AuthService);
	private readonly http = inject(HttpClient);

	readonly profile = signal<UserProfile | null>(null);
	readonly loadError = signal<string | null>(null);
	readonly adminPing = signal<string | null>(null);
	readonly adminError = signal<string | null>(null);

	ngOnInit(): void {
		this.auth.loadProfile().subscribe({
			next: (p) => {
				this.profile.set(p);
				this.loadError.set(null);
			},
			error: () => this.loadError.set('Could not load your profile.'),
		});
	}

	logout(): void {
		this.auth.logout().subscribe();
	}

	tryAdminPing(): void {
		this.adminError.set(null);
		this.adminPing.set(null);
		this.http.get(`${environment.apiUrl}/api/admin/ping`, { responseType: 'text' }).subscribe({
			next: (t) => this.adminPing.set(t),
			error: () => this.adminError.set('Forbidden or not an admin.'),
		});
	}

	isAdmin(): boolean {
		return this.auth.hasRole('ADMIN', this.profile());
	}
}
