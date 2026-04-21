export interface AuthRequest {
  username: string;
  email?: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  username: string;
}
