import { InjectionToken } from '@angular/core';

export interface AppConfig {
  /** Base URL of the backend, without trailing slash. Empty string means "same origin". */
  apiUrl: string;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

const FALLBACK: AppConfig = { apiUrl: 'http://localhost:8080' };

export async function loadConfig(): Promise<AppConfig> {
  try {
    const response = await fetch('config.json', { cache: 'no-store' });
    if (response.ok) {
      const config = (await response.json()) as AppConfig;
      return { apiUrl: (config.apiUrl ?? FALLBACK.apiUrl).replace(/\/+$/, '') };
    }
  } catch {
    // fall through to the default
  }
  return FALLBACK;
}
