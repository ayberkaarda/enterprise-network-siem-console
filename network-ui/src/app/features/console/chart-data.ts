/* ==========================================================================
   Pure client-side derivations feeding the Overview charts. Kept free of
   Angular so they are trivial to unit test: given the same REST data the
   console already holds (incidents, devices), compute what each chart shows.
   ========================================================================== */

import { Device } from '../../services/device';
import { Incident, IncidentSeverity } from '../../services/siem.models';
import { BarChartDatum } from '../../shared/charts/bar-chart/bar-chart';

const SEVERITY_ORDER: readonly IncidentSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

const SEVERITY_COLOR_VAR: Readonly<Record<IncidentSeverity, string>> = {
  CRITICAL: '--color-severity-critical',
  HIGH: '--color-severity-high',
  MEDIUM: '--color-severity-medium',
  LOW: '--color-severity-low',
  INFO: '--color-severity-info',
};

/** UTC calendar-day key, so bucketing an incident and building the day axis agree. */
function dayKey(iso: string): string | null {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString().slice(0, 10);
}

function dayLabel(key: string): string {
  const [, month, day] = key.split('-');
  return `${day}.${month}`;
}

/**
 * Count of incidents created per UTC day for the last `days` days
 * (oldest first, today last). Days with no incidents still appear as a
 * zero bar so the trend reads as a continuous 14-day strip.
 */
export function groupIncidentsByDay(
  incidents: readonly Incident[],
  days = 14,
  now: Date = new Date(),
): BarChartDatum[] {
  const buckets: { key: string; value: number }[] = [];
  for (let i = days - 1; i >= 0; i -= 1) {
    const d = new Date(now.getTime());
    d.setUTCDate(d.getUTCDate() - i);
    buckets.push({ key: d.toISOString().slice(0, 10), value: 0 });
  }
  const byKey = new Map(buckets.map((b) => [b.key, b]));

  for (const incident of incidents) {
    const key = dayKey(incident.createdAt);
    const bucket = key ? byKey.get(key) : undefined;
    if (bucket) {
      bucket.value += 1;
    }
  }

  return buckets.map(({ key, value }) => ({ label: dayLabel(key), value }));
}

/** Count of incidents by severity, fixed order (most severe first), zero included. */
export function countIncidentsBySeverity(incidents: readonly Incident[]): BarChartDatum[] {
  const counts = new Map<IncidentSeverity, number>(SEVERITY_ORDER.map((s) => [s, 0]));
  for (const incident of incidents) {
    counts.set(incident.severity, (counts.get(incident.severity) ?? 0) + 1);
  }
  return SEVERITY_ORDER.map((severity) => ({
    label: severity,
    value: counts.get(severity) ?? 0,
    colorVar: SEVERITY_COLOR_VAR[severity],
  }));
}

/** Percentage of ACTIVE devices per deviceType, types sorted alphabetically. */
export function computeDeviceTypeUptime(devices: readonly Device[]): BarChartDatum[] {
  const totals = new Map<string, { active: number; total: number }>();
  for (const device of devices) {
    const type = (device.deviceType || 'DİĞER').toUpperCase();
    const entry = totals.get(type) ?? { active: 0, total: 0 };
    entry.total += 1;
    if (device.status === 'ACTIVE') {
      entry.active += 1;
    }
    totals.set(type, entry);
  }
  return [...totals.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([type, { active, total }]) => ({
      label: type,
      value: total === 0 ? 0 : Math.round((active / total) * 100),
      colorVar: '--color-ok',
    }));
}
