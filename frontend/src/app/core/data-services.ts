import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Api } from './api';
import {
  Analytics,
  AppNotification,
  ApplicationStatus,
  InterviewQuestion,
  JobApplication,
  JobPostingExtraction,
} from './models';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(Api);

  get(): Promise<Analytics> {
    return firstValueFrom(this.http.get<Analytics>(this.api.url('/analytics')));
  }
}

/** Server-side filtered list used by the "List" page (the board filters locally). */
@Injectable({ providedIn: 'root' })
export class ApplicationSearch {
  private readonly http = inject(HttpClient);
  private readonly api = inject(Api);

  search(status: ApplicationStatus | '', q: string): Promise<JobApplication[]> {
    const params: Record<string, string> = {};
    if (status) {
      params['status'] = status;
    }
    if (q.trim()) {
      params['q'] = q.trim();
    }
    return firstValueFrom(this.http.get<JobApplication[]>(this.api.url('/applications'), { params }));
  }
}

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(Api);

  parseJob(text: string): Promise<JobPostingExtraction> {
    return firstValueFrom(this.http.post<JobPostingExtraction>(this.api.url('/ai/parse-job'), { text }));
  }

  async interviewPrep(applicationId: number): Promise<InterviewQuestion[]> {
    const res = await firstValueFrom(
      this.http.post<{ questions: InterviewQuestion[] }>(this.api.url(`/ai/applications/${applicationId}/interview-prep`), {}),
    );
    return res.questions;
  }
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(Api);

  readonly items = signal<AppNotification[]>([]);
  readonly unread = computed(() => this.items().filter((n) => !n.read).length);

  async refresh(): Promise<void> {
    this.items.set(await firstValueFrom(this.http.get<AppNotification[]>(this.api.url('/notifications'))));
  }

  async markRead(id: number): Promise<void> {
    await firstValueFrom(this.http.post<void>(this.api.url(`/notifications/${id}/read`), {}));
    this.items.update((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
  }

  async markAllRead(): Promise<void> {
    await firstValueFrom(this.http.post<void>(this.api.url('/notifications/read-all'), {}));
    this.items.update((list) => list.map((n) => ({ ...n, read: true })));
  }

  clear(): void {
    this.items.set([]);
  }
}
