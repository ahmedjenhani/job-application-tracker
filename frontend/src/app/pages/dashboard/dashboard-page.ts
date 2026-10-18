import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ChartConfiguration } from 'chart.js';
import { errorMessage } from '../../core/api';
import { ApplicationStore } from '../../core/application-store';
import { AnalyticsService } from '../../core/data-services';
import { Analytics, STATUS_LABEL, STATUSES } from '../../core/models';
import { ThemeService } from '../../core/theme';
import { AppDialogs } from '../../dialogs/app-dialogs';
import { ChartView, resolveCssColor } from '../../shared/chart';
import { followUpState } from '../../shared/follow-up';
import { StatusChip } from '../../shared/status-chip';

/** Same hues as the status chips in styles.scss. */
const STATUS_COLOR: Record<string, string> = {
  WISHLIST: '#64748b',
  APPLIED: '#2563eb',
  INTERVIEW: '#d97706',
  OFFER: '#16a34a',
  REJECTED: '#dc2626',
};

@Component({
  selector: 'app-dashboard-page',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule, ChartView, StatusChip],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage {
  private readonly analytics = inject(AnalyticsService);
  private readonly store = inject(ApplicationStore);
  private readonly theme = inject(ThemeService);
  private readonly dialogs = inject(AppDialogs);

  protected readonly data = signal<Analytics | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Applications still waiting for a reply whose follow-up date has arrived. */
  protected readonly needFollowUp = computed(() =>
    this.store
      .items()
      .filter((a) => {
        const state = followUpState(a);
        return state === 'overdue' || state === 'today';
      })
      .sort((a, b) => (a.followUpDate ?? '').localeCompare(b.followUpDate ?? '')),
  );

  constructor() {
    // Reload the numbers whenever the applications change (an edit on another page, a drag on the board...).
    effect(() => {
      this.store.items();
      untracked(() => void this.load());
    });
  }

  protected readonly hasData = computed(() => (this.data()?.total ?? 0) > 0);

  protected readonly weeklyChart = computed<ChartConfiguration | null>(() => {
    const d = this.data();
    if (!d) {
      return null;
    }
    const colors = this.chartColors();
    return {
      type: 'bar',
      data: {
        labels: d.weekly.map((w) => this.shortDate(w.weekStart)),
        datasets: [
          {
            label: 'Applications',
            data: d.weekly.map((w) => w.count),
            backgroundColor: STATUS_COLOR['APPLIED'],
            borderRadius: 6,
            maxBarThickness: 36,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { color: colors.text }, grid: { display: false } },
          y: {
            beginAtZero: true,
            ticks: { color: colors.text, precision: 0 },
            grid: { color: colors.grid },
          },
        },
      },
    };
  });

  protected readonly statusChart = computed<ChartConfiguration | null>(() => {
    const d = this.data();
    if (!d) {
      return null;
    }
    const colors = this.chartColors();
    return {
      type: 'doughnut',
      data: {
        labels: STATUSES.map((s) => STATUS_LABEL[s]),
        datasets: [
          {
            data: STATUSES.map((s) => d.byStatus[s] ?? 0),
            backgroundColor: STATUSES.map((s) => STATUS_COLOR[s]),
            borderColor: colors.surface,
            borderWidth: 3,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '62%',
        plugins: { legend: { position: 'bottom', labels: { color: colors.text, boxWidth: 12, padding: 14 } } },
      },
    };
  });

  protected readonly funnelChart = computed<ChartConfiguration | null>(() => {
    const d = this.data();
    if (!d) {
      return null;
    }
    const colors = this.chartColors();
    return {
      type: 'bar',
      data: {
        labels: d.funnel.map((f) => f.label),
        datasets: [
          {
            label: 'Applications',
            data: d.funnel.map((f) => f.count),
            backgroundColor: [STATUS_COLOR['APPLIED'], STATUS_COLOR['INTERVIEW'], STATUS_COLOR['OFFER']],
            borderRadius: 6,
            maxBarThickness: 42,
          },
        ],
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { beginAtZero: true, ticks: { color: colors.text, precision: 0 }, grid: { color: colors.grid } },
          y: { ticks: { color: colors.text }, grid: { display: false } },
        },
      },
    };
  });

  protected open(id: number): void {
    this.dialogs.openDetail(id);
  }

  private chartColors() {
    this.theme.mode(); // re-resolve colours when the theme is switched
    return {
      text: resolveCssColor('--mat-sys-on-surface-variant', '#666'),
      grid: resolveCssColor('--mat-sys-outline-variant', '#ddd'),
      surface: resolveCssColor('--mat-sys-surface-container-low', '#fff'),
    };
  }

  private shortDate(iso: string): string {
    const [, m, d] = iso.split('-').map(Number);
    const month = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][m - 1];
    return `${month} ${d}`;
  }

  private async load(): Promise<void> {
    this.error.set(null);
    try {
      this.data.set(await this.analytics.get());
    } catch (err) {
      this.error.set(errorMessage(err));
    } finally {
      this.loading.set(false);
    }
  }
}
