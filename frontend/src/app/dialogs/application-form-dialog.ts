import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { errorMessage, parseIsoDate, toIsoDate } from '../core/api';
import { ApplicationStore } from '../core/application-store';
import { AiService } from '../core/data-services';
import { ApplicationRequest, ApplicationStatus, JobApplication, STATUS_LABEL, STATUSES } from '../core/models';

export interface FormDialogData {
  application?: JobApplication;
  /** Pre-selected status for a new application (e.g. the column the user clicked "add" in). */
  status?: ApplicationStatus;
}

@Component({
  selector: 'app-application-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  templateUrl: './application-form-dialog.html',
  styleUrl: './application-form-dialog.scss',
})
export class ApplicationFormDialog {
  private readonly data = inject<FormDialogData | null>(MAT_DIALOG_DATA, { optional: true }) ?? {};
  private readonly ref = inject<MatDialogRef<ApplicationFormDialog, JobApplication>>(MatDialogRef);
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(ApplicationStore);
  private readonly ai = inject(AiService);

  protected readonly existing = this.data.application;
  protected readonly isEdit = !!this.existing;
  protected readonly statuses = STATUSES;
  protected readonly labels = STATUS_LABEL;

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly skills = signal<string[]>(this.existing?.skills ?? []);

  // AI auto-fill
  protected readonly aiOpen = signal(!this.isEdit);
  protected readonly aiText = signal('');
  protected readonly aiBusy = signal(false);
  protected readonly aiResult = signal<{ ok: boolean; text: string } | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    company: [this.existing?.company ?? '', [Validators.required, Validators.maxLength(150)]],
    role: [this.existing?.role ?? '', [Validators.required, Validators.maxLength(150)]],
    location: [this.existing?.location ?? '', Validators.maxLength(150)],
    jobUrl: [this.existing?.jobUrl ?? '', Validators.maxLength(500)],
    salaryRange: [this.existing?.salaryRange ?? '', Validators.maxLength(100)],
    status: [this.existing?.status ?? this.data.status ?? ('APPLIED' as ApplicationStatus)],
    appliedDate: new FormControl<Date | null>(parseIsoDate(this.existing?.appliedDate)),
    followUpDate: new FormControl<Date | null>(parseIsoDate(this.existing?.followUpDate)),
    notes: [this.existing?.notes ?? '', Validators.maxLength(10000)],
    jobDescription: [this.existing?.jobDescription ?? '', Validators.maxLength(20000)],
  });

  protected addSkill(event: MatChipInputEvent): void {
    const value = event.value.trim();
    if (value && !this.skills().includes(value) && this.skills().length < 20) {
      this.skills.update((list) => [...list, value.slice(0, 50)]);
    }
    event.chipInput.clear();
  }

  protected removeSkill(skill: string): void {
    this.skills.update((list) => list.filter((s) => s !== skill));
  }

  protected async extract(): Promise<void> {
    const text = this.aiText().trim();
    if (text.length < 40) {
      this.aiResult.set({ ok: false, text: 'Paste the whole job posting first (at least a few sentences).' });
      return;
    }
    this.aiBusy.set(true);
    this.aiResult.set(null);
    try {
      const r = await this.ai.parseJob(text);
      this.form.patchValue({
        company: r.company,
        role: r.role,
        location: r.location ?? this.form.controls.location.value,
        salaryRange: r.salaryRange ?? this.form.controls.salaryRange.value,
        jobDescription: text.slice(0, 20000),
      });
      if (r.skills.length > 0) {
        this.skills.set(r.skills.slice(0, 20));
      }
      this.aiResult.set({ ok: true, text: 'Form filled from the posting. Please review the details before saving.' });
    } catch (err) {
      this.aiResult.set({ ok: false, text: `${errorMessage(err)} You can still fill the form by hand.` });
    } finally {
      this.aiBusy.set(false);
    }
  }

  protected async save(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: ApplicationRequest = {
      company: v.company.trim(),
      role: v.role.trim(),
      location: v.location.trim() || null,
      jobUrl: v.jobUrl.trim() || null,
      salaryRange: v.salaryRange.trim() || null,
      status: v.status,
      appliedDate: toIsoDate(v.appliedDate),
      followUpDate: toIsoDate(v.followUpDate),
      skills: this.skills(),
      notes: v.notes.trim() || null,
      jobDescription: v.jobDescription.trim() || null,
    };
    this.error.set(null);
    this.saving.set(true);
    try {
      const saved = this.existing
        ? await this.store.update(this.existing.id, request)
        : await this.store.create(request);
      this.ref.close(saved);
    } catch (err) {
      this.error.set(errorMessage(err));
    } finally {
      this.saving.set(false);
    }
  }
}
