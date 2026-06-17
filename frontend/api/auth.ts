import { apiRequest } from '@/api/client';
import type { AuthUserResponse, BasicAuthCredentials } from '@/types/auth';

export const authApi = {
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
