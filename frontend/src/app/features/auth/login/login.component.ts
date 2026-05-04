import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

@Component({
	selector: 'app-login',
	imports: [ReactiveFormsModule],
	templateUrl: './login.component.html',
	styleUrl: './login.component.scss',
})
export class LoginComponent {
	private readonly fb = inject(FormBuilder);
	private readonly auth = inject(AuthService);
	private readonly router = inject(Router);

	readonly error = signal<string | null>(null);
	readonly submitting = signal(false);

	readonly form = this.fb.nonNullable.group({
		username: ['', Validators.required],
		password: ['', Validators.required],
	});

	submit(): void {
		if (this.form.invalid) {
			this.form.markAllAsTouched();
			return;
		}
		this.error.set(null);
		this.submitting.set(true);
		const { username, password } = this.form.getRawValue();
		this.auth.login(username, password).subscribe({
			next: () => {
				this.submitting.set(false);
				void this.router.navigateByUrl('/dashboard');
			},
			error: () => {
				this.submitting.set(false);
				this.error.set('Invalid username or password.');
			},
		});
	}
}
