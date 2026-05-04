import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { UserProfile } from '../../core/models/user-profile.model';

const SCIM_USER_SCHEMA = 'urn:ietf:params:scim:schemas:core:2.0:User';
const SCIM_GROUP_SCHEMA = 'urn:ietf:params:scim:schemas:core:2.0:Group';

interface ScimUserListItem {
	id: string;
	userName: string;
}

interface ScimUserListResponse {
	Resources?: ScimUserListItem[];
	totalResults?: number;
}

@Component({
	selector: 'app-dashboard',
	imports: [ReactiveFormsModule],
	templateUrl: './dashboard.component.html',
	styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
	private readonly auth = inject(AuthService);
	private readonly http = inject(HttpClient);
	private readonly fb = inject(FormBuilder);

	readonly profile = signal<UserProfile | null>(null);
	readonly loadError = signal<string | null>(null);
	readonly adminPing = signal<string | null>(null);
	readonly adminError = signal<string | null>(null);
	readonly addUserSubmitting = signal(false);
	readonly addUserSuccess = signal<string | null>(null);
	readonly addUserError = signal<string | null>(null);

	readonly scimUsers = signal<ScimUserListItem[]>([]);
	readonly scimUsersError = signal<string | null>(null);
	readonly scimUsersLoading = signal(false);
	readonly selectedMemberIds = signal<Set<string>>(new Set());

	readonly addGroupSubmitting = signal(false);
	readonly addGroupSuccess = signal<string | null>(null);
	readonly addGroupError = signal<string | null>(null);

	readonly addUserForm = this.fb.nonNullable.group({
		username: ['', Validators.required],
		email: ['', [Validators.required, Validators.email]],
		firstName: [''],
		lastName: [''],
	});

	readonly addGroupForm = this.fb.nonNullable.group({
		displayName: ['', Validators.required],
		externalId: [''],
	});

	ngOnInit(): void {
		this.auth.loadProfile().subscribe({
			next: (p) => {
				this.profile.set(p);
				this.loadError.set(null);
				if (this.auth.hasRole('ADMIN', p)) {
					this.loadScimUserDirectory();
				}
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

	loadScimUserDirectory(): void {
		this.scimUsersError.set(null);
		this.scimUsersLoading.set(true);
		this.http.get<ScimUserListResponse>(`${environment.apiUrl}/scim/v2/Users?count=100`).subscribe({
			next: (res) => {
				this.scimUsersLoading.set(false);
				this.scimUsers.set(res.Resources ?? []);
			},
			error: () => {
				this.scimUsersLoading.set(false);
				this.scimUsersError.set('Could not load users for group members (SCIM).');
			},
		});
	}

	toggleMember(userId: string): void {
		const next = new Set(this.selectedMemberIds());
		if (next.has(userId)) {
			next.delete(userId);
		} else {
			next.add(userId);
		}
		this.selectedMemberIds.set(next);
	}

	isMemberSelected(userId: string): boolean {
		return this.selectedMemberIds().has(userId);
	}

	private mapScimHttpError(err: HttpErrorResponse, kind: 'user' | 'group' = 'user'): string {
		const scimBody = err.error as { detail?: string } | null;
		const detail = typeof scimBody?.detail === 'string' ? scimBody.detail : null;
		if (err.status === 409) {
			if (kind === 'group') {
				return detail ?? 'Conflict: duplicate display name or external id.';
			}
			return detail ?? 'Conflict: username or email already exists.';
		}
		if (err.status === 400) {
			return detail ?? 'Invalid SCIM payload.';
		}
		if (err.status === 403) {
			return kind === 'group'
				? 'Forbidden: admin role required for SCIM group creation.'
				: 'Forbidden: admin role required for SCIM user creation.';
		}
		return detail ?? `Request failed (${err.status}).`;
	}

	submitAddGroup(): void {
		if (this.addGroupForm.invalid) {
			this.addGroupForm.markAllAsTouched();
			return;
		}
		this.addGroupError.set(null);
		this.addGroupSuccess.set(null);
		this.addGroupSubmitting.set(true);
		const { displayName, externalId } = this.addGroupForm.getRawValue();
		const ext = externalId.trim();
		const members = [...this.selectedMemberIds()].map((id) => ({ value: id }));
		const body: {
			schemas: string[];
			displayName: string;
			externalId?: string;
			members?: { value: string }[];
		} = {
			schemas: [SCIM_GROUP_SCHEMA],
			displayName: displayName.trim(),
		};
		if (ext) {
			body.externalId = ext;
		}
		if (members.length > 0) {
			body.members = members;
		}
		this.http
			.post<{ id: string; displayName: string }>(`${environment.apiUrl}/scim/v2/Groups`, body, {
				headers: { 'Content-Type': 'application/scim+json' },
			})
			.subscribe({
				next: (created) => {
					this.addGroupSubmitting.set(false);
					this.addGroupSuccess.set(
						`Group created (SCIM id: ${created.id}, displayName: ${created.displayName}).`,
					);
					this.addGroupForm.reset();
					this.selectedMemberIds.set(new Set());
					this.loadScimUserDirectory();
				},
				error: (err: HttpErrorResponse) => {
					this.addGroupSubmitting.set(false);
					this.addGroupError.set(this.mapScimHttpError(err, 'group'));
				},
			});
	}

	submitAddUser(): void {
		if (this.addUserForm.invalid) {
			this.addUserForm.markAllAsTouched();
			return;
		}
		this.addUserError.set(null);
		this.addUserSuccess.set(null);
		this.addUserSubmitting.set(true);
		const { username, email, firstName, lastName } = this.addUserForm.getRawValue();
		const given = firstName.trim();
		const family = lastName.trim();
		const body: {
			schemas: string[];
			userName: string;
			active: boolean;
			name?: { givenName?: string; familyName?: string };
			emails: { value: string; primary: boolean; type: string }[];
		} = {
			schemas: [SCIM_USER_SCHEMA],
			userName: username.trim(),
			active: true,
			emails: [{ value: email.trim(), primary: true, type: 'work' }],
		};
		if (given || family) {
			body.name = {
				...(given ? { givenName: given } : {}),
				...(family ? { familyName: family } : {}),
			};
		}
		this.http
			.post<{ id: string; userName: string }>(`${environment.apiUrl}/scim/v2/Users`, body, {
				headers: { 'Content-Type': 'application/scim+json' },
			})
			.subscribe({
				next: (created) => {
					this.addUserSubmitting.set(false);
					this.addUserSuccess.set(`User created (SCIM id: ${created.id}, userName: ${created.userName}).`);
					this.addUserForm.reset();
					this.loadScimUserDirectory();
				},
				error: (err: HttpErrorResponse) => {
					this.addUserSubmitting.set(false);
					this.addUserError.set(this.mapScimHttpError(err));
				},
			});
	}
}
