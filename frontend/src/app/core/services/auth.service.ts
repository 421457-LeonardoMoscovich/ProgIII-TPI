import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthRequest, AuthResponse } from '../models/user.model';

const TOKEN_KEY = 'ptcg_token';
const USERNAME_KEY = 'ptcg_username';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private readonly base = `${environment.apiBaseUrl}/auth`;

  readonly currentUser = signal<string | null>(this.storedUsername());

  login(req: AuthRequest) {
    return this.http.post<AuthResponse>(`${this.base}/login`, req).pipe(
      tap(res => this.storeSession(res))
    );
  }

  register(req: AuthRequest) {
    return this.http.post<AuthResponse>(`${this.base}/register`, req).pipe(
      tap(res => this.storeSession(res))
    );
  }

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return this.getToken() !== null;
  }

  private storeSession(res: AuthResponse) {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USERNAME_KEY, res.username);
    this.currentUser.set(res.username);
  }

  private storedUsername(): string | null {
    return localStorage.getItem(USERNAME_KEY);
  }
}
