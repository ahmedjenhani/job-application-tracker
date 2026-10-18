import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { errorMessage } from '../../core/api';
import { ApplicationStore } from '../../core/application-store';
import { ApplicationSearch } from '../../core/data-services';
import { ApplicationStatus, JobApplication, STATUS_LABEL, STATUSES } from '../../core/models';
import { AppDialogs } from '../../dialogs/app-dialogs';
import { followUpState } from '../../shared/follow-up';
import { StatusChip } from '../../shared/status-chip';

@Component({
  selector: 'app-list-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    StatusChip,
  ],
  templateUrl: './list-page.html',
  styleUrl: './list-page.scss',
})
export class ListPage {
  private readonly store = inject(ApplicationStore);
  private readonly search = inject(ApplicationSearch);
  private readonly dialogs = inject(AppDialogs);

  protected readonly statuses = STATUSES;
  protected readonly labels = STATUS_LABEL;
  protected readonly columns = ['company', 'role', 'location', 'status', 'applied', 'followUp'];

  protected readonly status = signal<ApplicationStatus | ''>('');
  protected readonly queryInput = signal('');
  private readonly query = toSignal(
    toObservable(this.queryInput).pipe(debounceTime(300), distinctUntilChanged()),
    { initialValue: '' },
  );

  protected readonly rows = signal<JobApplication[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Re-query the server when a filter changes, and when the shared store changes (add/edit/delete/move).
    effect(() => {
      const status = this.status();
      const query = this.query();
      this.store.items();
      untracked(() => void this.refresh(status, query));
    });
  }

  protected view(app: JobApplication): void {
    this.dialogs.openDetail(app.id);
  }

  protected add(): void {
    this.dialogs.openForm();
  }

  protected followUp(app: JobApplication) {
    return followUpState(app);
  }

  private async refresh(status: ApplicationStatus | '', query: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.rows.set(await this.search.search(status, query));
    } catch (err) {
      this.error.set(errorMessage(err));
    } finally {
      this.loading.set(false);
    }
  }
}
