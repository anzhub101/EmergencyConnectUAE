// Tiny dependency-free toast bus. Any module can fire a toast (e.g. the axios
// interceptor on a 403 denial) and the <Toaster> component renders them.

export type ToastType = 'error' | 'success' | 'info';

export interface ToastDetail {
  id: number;
  message: string;
  type: ToastType;
}

const EVENT = 'app:toast';
let seq = 0;

export const toast = (message: string, type: ToastType = 'info') => {
  const detail: ToastDetail = { id: ++seq, message, type };
  window.dispatchEvent(new CustomEvent<ToastDetail>(EVENT, { detail }));
};

export const onToast = (handler: (t: ToastDetail) => void) => {
  const listener = (e: Event) => handler((e as CustomEvent<ToastDetail>).detail);
  window.addEventListener(EVENT, listener);
  return () => window.removeEventListener(EVENT, listener);
};
