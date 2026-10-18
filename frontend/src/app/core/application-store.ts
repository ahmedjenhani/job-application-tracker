import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Api } from './api';
import { ApplicationRequest, ApplicationStatus, HistoryEntry, JobApplication } from './models';

/**
 * Single source of truth for the user's applications. The board, the dashboard and the dialogs all read
 * the same signal, so a change made anywhere shows up everywhere without extra requests.
 */
@Injectable({ providedIn: 'root' })
export class ApplicationStore {
  private readonly http = inject(HttpClient);
  private readonly api = inject(Api);

  readonly items = signal<JobApplication[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.items.set(await firstValueFrom(this.http.get<JobApplication[]>(this.api.url('/applications'))));
      this.loaded.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  clear(): void {
    this.items.set([]);
    this.loaded.set(false);
  }

  async create(request: ApplicationRequest): Promise<JobApplication> {
    const created = await firstValueFrom(this.http.post<JobApplication>(this.api.url('/applications'), request));
    this.items.update((list) => [created, ...list]);
    return created;
  }

  async update(id: number, request: ApplicationRequest): Promise<JobApplication> {
    const updated = await firstValueFrom(this.http.put<JobApplication>(this.api.url(`/applications/${id}`), request));
    this.replace(updated);
    return updated;
  }

  /** Optimistic: the card moves immediately and jumps back if the server refuses. */
  async changeStatus(id: number, status: ApplicationStatus): Promise<void> {
    const before = this.items();
    const current = before.find((a) => a.id === id);
    if (!current || current.status === status) {
      return;
    }
    this.replace({ ...current, status });
    try {
      const updated = await firstValueFrom(
        this.http.patch<JobApplication>(this.api.url(`/applications/${id}/status`), { status }),
      );
      this.replace(updated);
    } catch (err) {
      this.items.set(before);
      throw err;
    }
  }

  async remove(id: number): Promise<void> {
    await firstValueFrom(this.http.delete<void>(this.api.url(`/applications/${id}`)));
    this.items.update((list) => list.filter((a) => a.id !== id));
  }

  history(id: number): Promise<HistoryEntry[]> {
    return firstValueFrom(this.http.get<HistoryEntry[]>(this.api.url(`/applications/${id}/history`)));
  }

  private replace(app: JobApplication): void {
    this.items.update((list) => list.map((a) => (a.id === app.id ? app : a)));
  }
}
