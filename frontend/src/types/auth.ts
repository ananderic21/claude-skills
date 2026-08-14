export interface AuthResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
  username: string
}

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
}
