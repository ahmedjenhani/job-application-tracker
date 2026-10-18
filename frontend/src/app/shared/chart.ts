import { Component, DestroyRef, ElementRef, effect, inject, input, viewChild } from '@angular/core';
import { Chart, ChartConfiguration, registerables } from 'chart.js';

Chart.register(...registerables);

/**
 * Chart.js draws on a canvas and cannot resolve CSS custom properties (Material's colours use light-dark()),
 * so we resolve a variable to a concrete rgb() value by letting the browser compute it on a probe element.
 */
export function resolveCssColor(variable: string, fallback = '#888'): string {
  const probe = document.createElement('span');
  probe.style.color = `var(${variable}, ${fallback})`;
  probe.style.display = 'none';
  document.body.appendChild(probe);
  const color = getComputedStyle(probe).color;
  probe.remove();
  return color || fallback;
}

@Component({
  selector: 'app-chart',
  template: `<div class="box"><canvas #canvas [attr.aria-label]="label()" role="img"></canvas></div>`,
  styles: `
    .box {
      position: relative;
      height: 100%;
      width: 100%;
    }
  `,
})
export class ChartView {
  readonly config = input.required<ChartConfiguration>();
  readonly label = input<string>('Chart');

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;

  constructor() {
    effect(() => {
      const config = this.config();
      this.chart?.destroy();
      this.chart = new Chart(this.canvas().nativeElement, config);
    });
    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }
}
