import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ApplicationStore } from '../core/application-store';
import { ApplicationStatus, JobApplication } from '../core/models';
import { ApplicationDetailDialog, DetailResult } from './application-detail-dialog';
import { ApplicationFormDialog } from './application-form-dialog';

/** One place that knows how the add/edit and detail dialogs are opened and chained together. */
@Injectable({ providedIn: 'root' })
export class AppDialogs {
  private readonly dialog = inject(MatDialog);
  private readonly store = inject(ApplicationStore);

  openForm(application?: JobApplication, status?: ApplicationStatus) {
    return this.dialog
      .open(ApplicationFormDialog, {
        data: { application, status },
        width: '760px',
        maxWidth: '96vw',
        autoFocus: 'first-tabbable',
      })
      .afterClosed();
  }

  openDetail(id: number): void {
    this.dialog
      .open<ApplicationDetailDialog, { id: number }, DetailResult>(ApplicationDetailDialog, {
        data: { id },
        width: '720px',
        maxWidth: '96vw',
        autoFocus: false,
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === 'edit') {
          const app = this.store.items().find((a) => a.id === id);
          if (app) {
            this.openForm(app);
          }
        }
      });
  }
}
