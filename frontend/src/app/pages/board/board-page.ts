import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { errorMessage } from '../../core/api';
import { ApplicationStore } from '../../core/application-store';
import { ApplicationStatus, JobApplication, STATUS_LABEL, STATUSES } from '../../core/models';
import { AppDialogs } from '../../dialogs/app-dialogs';
import { followUpState } from '../../shared/follow-up';

@Component({
  selector: 'app-board-page',
  imports: [
    CdkDrag,
    CdkDropList,
    CdkDropListGroup,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './board-page.html',
  styleUrl: './board-page.scss',
})
export class BoardPage {
  protected readonly store = inject(ApplicationStore);
  private readonly dialogs = inject(AppDialogs);
  private readonly snack = inject(MatSnackBar);
  private readonly router = inject(Router);

  /** Set by a notification link (/board?open=ID) to show that application straight away. */
  readonly open = input<string>();

  protected readonly labels = STATUS_LABEL;
  protected readonly query = signal('');

  private readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const all = this.store.items();
    if (!q) {
      return all;
    }
    return all.filter((a) =>
      [a.company, a.role, a.location ?? ''].some((field) => field.toLowerCase().includes(q)),
    );
  });

  protected readonly columns = computed(() =>
    STATUSES.map((status) => ({ status, items: this.filtered().filter((a) => a.status === status) })),
  );

  /** True while a card is being dragged, so the drop is not mistaken for a click. */
  private dragging = false;

  constructor() {
    effect(() => {
      const id = Number(this.open());
      if (id && this.store.loaded()) {
        untracked(() => {
          this.dialogs.openDetail(id);
          void this.router.navigate([], { queryParams: { open: null }, replaceUrl: true });
        });
      }
    });
  }

  protected add(status?: ApplicationStatus): void {
    this.dialogs.openForm(undefined, status);
  }

  protected view(app: JobApplication): void {
    if (!this.dragging) {
      this.dialogs.openDetail(app.id);
    }
  }

  protected onDragStarted(): void {
    this.dragging = true;
  }

  protected onDragEnded(): void {
    setTimeout(() => (this.dragging = false));
  }

  protected async drop(event: CdkDragDrop<ApplicationStatus, ApplicationStatus, JobApplication>): Promise<void> {
    if (event.previousContainer === event.container) {
      return;
    }
    try {
      await this.store.changeStatus(event.item.data.id, event.container.data);
    } catch (err) {
      this.snack.open(errorMessage(err), 'OK', { duration: 6000 });
    }
  }

  protected followUp(app: JobApplication) {
    return followUpState(app);
  }
}
