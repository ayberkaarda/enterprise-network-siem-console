import { Component, computed, input } from '@angular/core';

export interface SparklinePoint {
  readonly label: string;
  readonly value: number;
}

const VIEW_WIDTH = 300;
const VIEW_HEIGHT = 90;
const PADDING = 6;

/**
 * Inline-SVG time-series line, used for the per-device latency history.
 *
 * This is a hand-rolled chart rather than a library: the path geometry is
 * plain arithmetic and the only colour it draws with is whatever CSS custom
 * property name is passed in via `strokeVar` (e.g. "--color-accent"),
 * resolved through `var(...)` in the template — never a literal hex value.
 */
@Component({
  selector: 'app-sparkline-chart',
  standalone: true,
  imports: [],
  templateUrl: './sparkline-chart.html',
  styleUrl: './sparkline-chart.css',
})
export class SparklineChart {
  readonly points = input<readonly SparklinePoint[]>([]);
  readonly valueSuffix = input<string>('');
  readonly emptyLabel = input<string>('Veri yok');
  readonly strokeVar = input<string>('--color-accent');

  readonly viewBox = `0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`;

  readonly minValue = computed(() => Math.min(...this.points().map((p) => p.value), 0));
  readonly maxValue = computed(() => Math.max(...this.points().map((p) => p.value), 1));

  readonly linePath = computed(() => this.buildPath(false));
  readonly areaPath = computed(() => this.buildPath(true));

  readonly lastPoint = computed<SparklinePoint | null>(() => {
    const pts = this.points();
    return pts.length > 0 ? pts[pts.length - 1] : null;
  });

  readonly strokeColor = computed(() => `var(${this.strokeVar()})`);

  private buildPath(asArea: boolean): string {
    const pts = this.points();
    if (pts.length === 0) return '';

    const min = this.minValue();
    const max = this.maxValue();
    const span = Math.max(1e-6, max - min);
    const innerWidth = VIEW_WIDTH - PADDING * 2;
    const innerHeight = VIEW_HEIGHT - PADDING * 2;
    const step = pts.length > 1 ? innerWidth / (pts.length - 1) : 0;

    const coords = pts.map((p, i) => {
      const x = PADDING + step * i;
      const ratio = (p.value - min) / span;
      const y = PADDING + innerHeight * (1 - ratio);
      return [x, y] as const;
    });

    const line = coords
      .map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`)
      .join(' ');
    if (!asArea) return line;

    const [firstX] = coords[0];
    const [lastX] = coords[coords.length - 1];
    const floorY = VIEW_HEIGHT - PADDING;
    return `${line} L${lastX.toFixed(1)},${floorY} L${firstX.toFixed(1)},${floorY} Z`;
  }
}
