import { apiRequest } from '@/api/client';
import type { AuthUserResponse, BasicAuthCredentials } from '@/types/auth';

export type RegisterRequest = {
  email: string;
  username: string;
  password: string;
  confirmPassword: string;
};

export const authApi = {
  register(request: RegisterRequest) {
    return apiRequest<AuthUserResponse>('/api/auth/register', {
      method: 'POST',
      body: request,
    });
  },

  login(credentials: BasicAuthCredentials) {
    return apiRequest<AuthUserResponse>('/api/auth/login', {
      method: 'POST',
      credentials,
    });
  },

  me(credentials: BasicAuthCredentials) {
    return apiRequest<AuthUserResponse>('/api/auth/me', {
      credentials,
    });
  },
};
