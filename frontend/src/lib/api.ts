// Axios instance for the Spring Boot API. Every request carries the current
// Supabase access token as a Bearer header; the backend validates it against
// the Supabase JWKS and looks up the matching Redis session.
import axios, { AxiosError } from 'axios';
import { supabase } from './supabase';
import { toast } from './toast';

// Set `{ skipErrorToast: true }` on a request config to suppress the automatic
// error toast (for best-effort calls the caller handles itself).
declare module 'axios' {
  export interface AxiosRequestConfig {
    skipErrorToast?: boolean;
  }
}

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use(async (config) => {
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ message?: string; error?: string }>) => {
    const status = error.response?.status;
    const serverMsg = error.response?.data?.message || error.response?.data?.error;

    if (error.config?.skipErrorToast) {
      return Promise.reject(error);
    }

    if (status === 401) {
      toast('Session expired or revoked — please sign in again.', 'error');
    } else if (status === 403) {
      toast(serverMsg || 'You do not have permission to perform this action.', 'error');
    } else if (status === 429) {
      toast('Too many requests — slow down and try again shortly.', 'error');
    } else if (status && status >= 500) {
      toast(serverMsg || 'Server error. Please try again.', 'error');
    } else if (!error.response) {
      toast('Cannot reach the server. Is the backend running?', 'error');
    }

    return Promise.reject(error);
  },
);

export default api;
