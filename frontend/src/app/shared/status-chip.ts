import { Component, computed, input } from '@angular/core';
import { ApplicationStatus, STATUS_LABEL } from '../core/models';

@Component({
  selector: 'app-status-chip',
  template: `<span class="status-chip" [class]="'status-chip status-' + status()">{{ label() }}</span>`,
})
export class StatusChip {
  readonly status = input.required<ApplicationStatus>();
  protected readonly label = computed(() => STATUS_LABEL[this.status()]);
}
