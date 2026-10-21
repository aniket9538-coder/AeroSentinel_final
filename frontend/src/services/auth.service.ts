import apiClient from './api';

export const authService = {
  login: async (credentials: { email: string; password?: string }): Promise<{ token: string; user: any }> => {
    const response = await apiClient.post('/auth/login', credentials);
    if (response.data?.token) {
      localStorage.setItem('aerosentinel_jwt', response.data.token);
    }
    return response.data;
  },
  loginAsAuthority: async (): Promise<{ token: string; user: any }> => {
    return authService.login({ email: 'authority@aerosentinel.gov', password: 'password' });
  },
  loginAsAdmin: async (): Promise<{ token: string; user: any }> => {
    return authService.login({ email: 'admin@aerosentinel.gov', password: 'password' });
  },
  logout: () => {
    localStorage.removeItem('aerosentinel_jwt');
  },
  getToken: (): string | null => {
    return typeof localStorage !== 'undefined' ? localStorage.getItem('aerosentinel_jwt') : null;
  },
  isAuthenticated: (): boolean => {
    return !!authService.getToken();
  },
  getRole: (): string | null => {
    const token = authService.getToken();
    if (!token) return null;
    try {
      const parts = token.split('.');
      if (parts.length >= 2) {
        const payload = JSON.parse(atob(parts[1]));
        return payload.role || (payload.authorities && payload.authorities[0]) || null;
      }
    } catch {
      // ignore decode error
    }
    return null;
  },
  getCurrentUser: () => {
    const token = authService.getToken();
    if (!token) return null;
    const role = authService.getRole() || 'AUTHORITY';
    return { name: 'Municipal Authority Officer', role };
  }
};

export default authService;
