import { describe, expect, it } from 'vitest';

import {
  computeDeviceTypeUptime,
  countIncidentsBySeverity,
  groupIncidentsByDay,
} from './chart-data';
import { Device } from '../../services/device';
import { Incident } from '../../services/siem.models';

function incident(overrides: Partial<Incident> & { id: number }): Incident {
  return {
    title: 'Port scan detected',
    severity: 'HIGH',
    status: 'OPEN',
    createdAt: '2026-09-14T10:00:00Z',
    updatedAt: '2026-09-14T10:00:00Z',
    ...overrides,
  };
}

function device(overrides: Partial<Device> = {}): Device {
  return {
    name: 'dev',
    ipAddress: '10.0.0.1',
    ...overrides,
  };
}

describe('groupIncidentsByDay', () => {
  const now = new Date('2026-09-14T12:00:00Z');

  it('returns a fixed-length, oldest-first day axis with today last', () => {
    const buckets = groupIncidentsByDay([], 14, now);
    expect(buckets).toHaveLength(14);
    expect(buckets[13].label).toBe('14.09');
    expect(buckets[0].label).toBe('01.09');
  });

  it('buckets each incident onto its UTC calendar day', () => {
    const incidents = [
      incident({ id: 1, createdAt: '2026-09-14T09:00:00Z' }),
      incident({ id: 2, createdAt: '2026-09-14T23:00:00Z' }),
      incident({ id: 3, createdAt: '2026-09-13T01:00:00Z' }),
    ];
    const buckets = groupIncidentsByDay(incidents, 14, now);
    const today = buckets.find((b) => b.label === '14.09');
    const yesterday = buckets.find((b) => b.label === '13.09');
    expect(today?.value).toBe(2);
    expect(yesterday?.value).toBe(1);
  });

  it('drops incidents that fall outside the requested window', () => {
    const tooOld = [incident({ id: 1, createdAt: '2026-01-01T00:00:00Z' })];
    const buckets = groupIncidentsByDay(tooOld, 14, now);
    expect(buckets.reduce((sum, b) => sum + b.value, 0)).toBe(0);
  });

  it('ignores an unparsable timestamp instead of throwing', () => {
    const bad = [incident({ id: 1, createdAt: 'not-a-date' })];
    expect(() => groupIncidentsByDay(bad, 14, now)).not.toThrow();
  });
});

describe('countIncidentsBySeverity', () => {
  it('returns every severity in a fixed most-severe-first order, zero included', () => {
    const result = countIncidentsBySeverity([]);
    expect(result.map((d) => d.label)).toEqual(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']);
    expect(result.every((d) => d.value === 0)).toBe(true);
  });

  it('counts each incident under its own severity and tags it with the matching token', () => {
    const incidents = [
      incident({ id: 1, severity: 'CRITICAL' }),
      incident({ id: 2, severity: 'CRITICAL' }),
      incident({ id: 3, severity: 'LOW' }),
    ];
    const result = countIncidentsBySeverity(incidents);
    const critical = result.find((d) => d.label === 'CRITICAL');
    const low = result.find((d) => d.label === 'LOW');
    expect(critical?.value).toBe(2);
    expect(critical?.colorVar).toBe('--color-severity-critical');
    expect(low?.value).toBe(1);
  });
});

describe('computeDeviceTypeUptime', () => {
  it('returns an empty series for no devices', () => {
    expect(computeDeviceTypeUptime([])).toEqual([]);
  });

  it('computes the percentage of ACTIVE devices per type, sorted alphabetically', () => {
    const devices: Device[] = [
      device({ deviceType: 'ROUTER', status: 'ACTIVE' }),
      device({ deviceType: 'ROUTER', status: 'INACTIVE' }),
      device({ deviceType: 'FIREWALL', status: 'ACTIVE' }),
    ];
    const result = computeDeviceTypeUptime(devices);
    expect(result.map((d) => d.label)).toEqual(['FIREWALL', 'ROUTER']);
    expect(result.find((d) => d.label === 'FIREWALL')?.value).toBe(100);
    expect(result.find((d) => d.label === 'ROUTER')?.value).toBe(50);
  });

  it('falls back to a placeholder type for a device with no deviceType', () => {
    const result = computeDeviceTypeUptime([device({ deviceType: undefined, status: 'ACTIVE' })]);
    expect(result).toHaveLength(1);
    expect(result[0].value).toBe(100);
  });
});
