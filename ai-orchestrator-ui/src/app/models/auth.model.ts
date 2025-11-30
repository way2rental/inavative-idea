export interface AuthResponse {
  token: string;
  type: string;
  username: string;
  role: string;
}

export interface User {
  username: string;
  role: string;
  token: string;
}
