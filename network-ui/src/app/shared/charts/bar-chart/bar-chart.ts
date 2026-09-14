import { Component, computed, input } from '@angular/core';

/**
 * One bar's worth of data. `colorVar` is a CSS custom-property *name*
 * (e.g. `--color-severity-critical`), never a literal colour — the
 * component resolves it through `var()` in the template so every hue this
 * chart can show still comes from the token layer in tokens.css.
 */
export interface BarChartDatum {
  readonly label: string;
  readonly value: number;
  readonly colorVar?: string;
}

/**
 * Minimal, dependency-free bar chart built from flex boxes rather than a
 * charting library. Used both as a horizontal ranked list (severity
 * distribution, device-type uptime) and as a vertical column strip
 * (incident trend over time) via the `orientation` input.
 */
@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [],
  templateUrl: './bar-chart.html',
  styleUrl: './bar-chart.css',
})
export class BarChart {
  readonly data = input<readonly BarChartDatum[]>([]);
  readonly orientation = input<'horizontal' | 'vertical'>('horizontal');
  readonly valueSuffix = input<string>('');
  readonly emptyLabel = input<string>('Veri yok');

  /** Scale ceiling: the largest value in the series, never below 1. */
  readonly maxValue = computed(() => Math.max(1, ...this.data().map((d) => d.value), 0));

  fillPercent(datum: BarChartDatum): number {
    const max = this.maxValue();
    if (max <= 0) return 0;
    return Math.max(0, Math.min(100, (datum.value / max) * 100));
  }

  barColor(datum: BarChartDatum): string {
    return datum.colorVar ? `var(${datum.colorVar})` : 'var(--color-accent)';
  }
}
