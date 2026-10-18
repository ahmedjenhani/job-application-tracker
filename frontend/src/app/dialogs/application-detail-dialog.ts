import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { errorMessage } from '../core/api';
import { ApplicationStore } from '../core/application-store';
import { AiService } from '../core/data-services';
import { ApplicationStatus, HistoryEntry, InterviewQuestion, STATUS_LABEL, STATUSES } from '../core/models';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { followUpState } from '../shared/follow-up';
import { StatusChip } from '../shared/status-chip';

export type DetailResult = 'edit' | 'deleted' | undefined;

@Component({
  selector: 'app-application-detail-dialog',
  imports: [
    DatePipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatDialogModule,
    MatIconModule,
    MatProgressBarModule,
    StatusChip,
  ],
  templateUrl: './application-detail-dialog.html',
  styleUrl: './application-detail-dialog.scss',
})
export class ApplicationDetailDialog {
  private readonly data = inject<{ id: number }>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<ApplicationDetailDialog, DetailResult>>(MatDialogRef);
  private readonly store = inject(ApplicationStore);
  private readonly ai = inject(AiService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  protected readonly statuses = STATUSES;
  protected readonly labels = STATUS_LABEL;

  protected readonly app = computed(() => this.store.items().find((a) => a.id === this.data.id));
  protected readonly followUp = computed(() => {
    const a = this.app();
    return a ? followUpState(a) : null;
  });

  protected readonly history = signal<HistoryEntry[]>([]);
  protected readonly questions = signal<InterviewQuestion[] | null>(null);
  protected readonly prepBusy = signal(false);
  protected readonly prepError = signal<string | null>(null);

  constructor() {
    effect(() => {
      const a = this.app();
      if (!a) {
        this.ref.close('deleted');
        return;
      }
      a.status; // reload the timeline whenever the status changes
      untracked(() => void this.loadHistory());
    });
  }

  protected async changeStatus(status: ApplicationStatus): Promise<void> {
    try {
      await this.store.changeStatus(this.data.id, status);
    } catch (err) {
      this.snack.open(errorMessage(err), 'OK', { duration: 6000 });
    }
  }

  protected async generatePrep(): Promise<void> {
    this.prepBusy.set(true);
    this.prepError.set(null);
    try {
      this.questions.set(await this.ai.interviewPrep(this.data.id));
    } catch (err) {
      this.prepError.set(errorMessage(err));
    } finally {
      this.prepBusy.set(false);
    }
  }

  protected edit(): void {
    this.ref.close('edit');
  }

  protected delete(): void {
    const a = this.app();
    if (!a) {
      return;
    }
    this.dialog
      .open(ConfirmDialog, {
        data: {
          title: 'Delete application?',
          message: `${a.role} at ${a.company} and its history will be removed permanently.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(async (confirmed) => {
        if (!confirmed) {
          return;
        }
        try {
          await this.store.remove(this.data.id);
        } catch (err) {
          this.snack.open(errorMessage(err), 'OK', { duration: 6000 });
        }
      });
  }

  private async loadHistory(): Promise<void> {
    try {
      this.history.set(await this.store.history(this.data.id));
    } catch {
      this.history.set([]);
    }
  }
}
