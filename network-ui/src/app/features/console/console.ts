import { Component, OnInit, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../services/auth.service';
import { DeviceService, Device, AuditLog } from '../../services/device';
import { RealtimeService } from '../../services/realtime.service';
import { ThemeId, ThemeService } from '../../services/theme.service';
import {
  SiemApiService,
  describeProblem,
  problemErrorCode,
} from '../../services/siem-api.service';
import {
  DeviceRealtimeUpdate,
  INCIDENT_LIFECYCLE,
  Incident,
  IncidentSeverity,
  IncidentStatus,
  IngestedEvent,
  LatencySample,
} from '../../services/siem.models';
import { BarChart, BarChartDatum } from '../../shared/charts/bar-chart/bar-chart';
import { SparklineChart, SparklinePoint } from '../../shared/charts/sparkline-chart/sparkline-chart';
import { computeDeviceTypeUptime, countIncidentsBySeverity, groupIncidentsByDay } from './chart-data';
import { IncidentComments } from './incident-comments/incident-comments';
import { RulesPanel } from './rules/rules-panel';

/** Which feed the audit panel is showing. */
type StreamSource = 'audit' | 'events';

/** Turkish label for the transition that moves an incident one stage forward. */
const TRANSITION_LABELS: Readonly<Record<IncidentStatus, string>> = {
  OPEN: 'Yeniden Aç',
  ACKNOWLEDGED: 'Onayla (Ack)',
  IN_PROGRESS: 'İncelemeye Al',
  RESOLVED: 'Çözüldü İşaretle',
  CLOSED: 'Kaydı Kapat',
};

const INCIDENT_STATUS_LABELS: Readonly<Record<IncidentStatus, string>> = {
  OPEN: 'AÇIK',
  ACKNOWLEDGED: 'ONAYLANDI',
  IN_PROGRESS: 'İNCELEMEDE',
  RESOLVED: 'ÇÖZÜLDÜ',
  CLOSED: 'KAPALI',
};

/** Statuses that still need an analyst; everything else leaves the active queue. */
const ACTIVE_INCIDENT_STATUSES: readonly IncidentStatus[] = ['OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS'];

/** A device push for an unknown id triggers at most one list read per window. */
const UNKNOWN_DEVICE_RELOAD_WINDOW_MS = 10_000;

/** Shown on every control a VIEWER is not allowed to trigger. */
const READ_ONLY_ROLE_HINT = 'VIEWER rolü bu aksiyonu tetikleyemez; sunucu da reddeder.';

@Component({
  selector: 'app-console',
  standalone: true,
  imports: [CommonModule, FormsModule, BarChart, SparklineChart, RulesPanel, IncidentComments],
  templateUrl: './console.html',
  styleUrl: './console.css',
})
export class ConsolePage implements OnInit {
  private readonly deviceService = inject(DeviceService);
  private readonly api = inject(SiemApiService);

  /** Single real-time entry point; the template reads its signals directly. */
  readonly realtime = inject(RealtimeService);

  /** Session owner; drives the identity chip and every role-gated control. */
  readonly auth = inject(AuthService);

  /** Colour theme; the settings screen reads and writes through this. */
  readonly theme = inject(ThemeService);

  devices: Device[] = [];
  logs: AuditLog[] = [];

  // Form girdileri
  deviceName = '';
  deviceIp = '';
  selectedType = 'SERVER'; // Varsayılan cihaz tipi

  // Arama ve Filtreleme
  searchTerm = '';
  statusFilter = 'ALL';
  logSearchTerm = '';
  isScanning = false;

  // Olay kuyruğu artık sunucudan gelir; burada yalnızca görünüm filtresi tutulur.
  incidentScope: 'ACTIVE' | 'ALL' = 'ACTIVE';
  transitionPendingId: number | null = null;
  transitionErrors: Record<number, string> = {};

  // Denetim akışı: eski audit log'u ya da ingest edilmiş olay akışı.
  streamSource: StreamSource = 'audit';
  events: IngestedEvent[] = [];
  eventsError: string | null = null;
  eventsLoaded = false;

  // SATIR BAZLI SESSİON TREND HAFIZASI
  // Cihaz ID'sine göre son 5 tarama sonucunu (ACTIVE/INACTIVE) dizide tutar
  deviceTrends: Record<number, string[]> = {};

  private unknownDeviceReloadAt = 0;
  private devicesLoaded = false;

  // Per-device gecikme geçmişi: tek seferde en fazla bir cihaz genişletilir.
  expandedDeviceId: number | null = null;
  latencyLoading = false;
  latencyError: string | null = null;
  latencySamples: LatencySample[] = [];

  constructor() {
    // Applies every device push in place. The table is patched row by row, so
    // a status change lands immediately without a full list read per event.
    effect(() => {
      this.applyDevicePatches(this.realtime.devicePatches());
    });
  }

  ngOnInit() {
    this.loadDevices();
    this.loadLogs();
    this.realtime.start();
  }

  loadDevices() {
    this.deviceService.getDevices().subscribe({
      next: (data) => {
        this.devices = data;
        this.devicesLoaded = true;
        this.updateDeviceTrends();
        this.applyDevicePatches(this.realtime.devicePatches());
      },
      error: (err) => console.error('Veri çekme hatası:', err),
    });
  }

  loadLogs() {
    this.deviceService.getLogs().subscribe({
      next: (data) => (this.logs = data.reverse()),
      error: (err) => console.error('Log çekme hatası:', err),
    });
  }

  // ======================================================== REALTIME MERGE

  /** Folds the latest pushed/polled device state onto the rendered rows. */
  private applyDevicePatches(patches: ReadonlyMap<number, DeviceRealtimeUpdate>) {
    // Nothing to fold onto until the inventory read has come back; acting
    // earlier would mistake an in-flight list for an empty one.
    if (patches.size === 0 || !this.devicesLoaded) {
      return;
    }

    let changed = false;
    const merged = this.devices.map((device) => {
      const patch = device.id === undefined ? undefined : patches.get(device.id);
      if (!patch) {
        return device;
      }
      const next: Device = {
        ...device,
        name: patch.name ?? device.name,
        ipAddress: patch.ipAddress ?? device.ipAddress,
        status: patch.status ?? device.status,
        latency: patch.latency ?? device.latency,
        deviceType: patch.deviceType ?? device.deviceType,
      };
      if (
        next.name === device.name &&
        next.ipAddress === device.ipAddress &&
        next.status === device.status &&
        next.latency === device.latency &&
        next.deviceType === device.deviceType
      ) {
        return device;
      }
      changed = true;
      return next;
    });

    if (changed) {
      this.devices = merged;
      this.updateDeviceTrends();
    }

    // A push for a device the table has never seen means the inventory itself
    // changed. That is rare, so one throttled list read is cheaper than
    // guessing the missing row from a partial payload.
    const known = new Set(this.devices.map((device) => device.id));
    const hasUnknown = [...patches.keys()].some((id) => !known.has(id));
    if (hasUnknown) {
      const now = Date.now();
      if (now - this.unknownDeviceReloadAt > UNKNOWN_DEVICE_RELOAD_WINDOW_MS) {
        this.unknownDeviceReloadAt = now;
        this.deviceService.getDevices().subscribe({
          next: (data) => {
            this.devices = data;
            this.updateDeviceTrends();
          },
          error: () => undefined,
        });
      }
    }
  }

  /** Keeps the last five observed statuses per row for the inline trend strip. */
  private updateDeviceTrends() {
    this.devices.forEach((device) => {
      if (device.id === undefined) return;

      if (!this.deviceTrends[device.id]) {
        this.deviceTrends[device.id] = [];
      }
      const currentHistory = this.deviceTrends[device.id];
      if (
        device.status &&
        (currentHistory.length === 0 || currentHistory[currentHistory.length - 1] !== device.status)
      ) {
        currentHistory.push(device.status);
        if (currentHistory.length > 5) {
          currentHistory.shift(); // En eskiyi at, yeniye yer aç
        }
      }
    });
  }

  // ======================================================== CONNECTION STATE

  get connectionLabel(): string {
    switch (this.realtime.connectionState()) {
      case 'connected':
        return 'Canlı akış';
      case 'connecting':
        return 'Bağlanıyor…';
      case 'reconnecting':
        return `Yeniden bağlanıyor (${this.realtime.reconnectAttempts()}. deneme)`;
      case 'polling-fallback':
        return 'Yedek mod · REST sorgulama';
      default:
        return 'Bağlantı kapalı';
    }
  }

  get connectionClass(): string {
    return `link-${this.realtime.connectionState()}`;
  }

  // ======================================================== OTURUM / ROL

  /**
   * Whether the signed-in role may trigger state-changing actions.
   *
   * This mirrors the server-side authorisation on incident transitions,
   * incident comments and the device scan/check/attack/bruteforce/delete
   * endpoints. It is presentation only: the server rejects the same calls
   * regardless of what the console renders.
   */
  get canRespond(): boolean {
    return this.auth.canRespond();
  }

  /**
   * Read-only roles keep the whole console visible and get a disabled control
   * with a reason. Hiding the buttons would leave a VIEWER unable to tell
   * whether an action is missing or merely not theirs to take.
   */
  get roleHint(): string {
    return this.canRespond ? '' : READ_ONLY_ROLE_HINT;
  }

  get sessionUsername(): string {
    return this.auth.currentUser()?.username ?? '—';
  }

  get sessionRole(): string {
    return this.auth.currentUser()?.role ?? '—';
  }

  logout() {
    this.realtime.stop();
    this.auth.logout();
  }

  // ======================================================== GÖRÜNÜM

  selectTheme(id: ThemeId) {
    this.theme.setTheme(id);
  }

  // ======================================================== INCIDENTS

  get allIncidents(): readonly Incident[] {
    return this.realtime.incidents();
  }

  get activeIncidents(): readonly Incident[] {
    return this.realtime.incidents().filter((i) => ACTIVE_INCIDENT_STATUSES.includes(i.status));
  }

  get visibleIncidents(): readonly Incident[] {
    return this.incidentScope === 'ALL' ? this.allIncidents : this.activeIncidents;
  }

  get criticalIncidentCount(): number {
    return this.activeIncidents.filter((i) => i.severity === 'CRITICAL' || i.severity === 'HIGH')
      .length;
  }

  severityClass(severity: IncidentSeverity): string {
    return `sev-${severity.toLowerCase()}`;
  }

  stripeClass(severity: IncidentSeverity): string {
    return `sev-stripe-${severity.toLowerCase()}`;
  }

  statusLabel(status: IncidentStatus): string {
    return INCIDENT_STATUS_LABELS[status];
  }

  /** The only transition offered is the next stage of the lifecycle. */
  nextStatus(incident: Incident): IncidentStatus | null {
    const index = INCIDENT_LIFECYCLE.indexOf(incident.status);
    if (index < 0 || index >= INCIDENT_LIFECYCLE.length - 1) {
      return null;
    }
    return INCIDENT_LIFECYCLE[index + 1];
  }

  transitionLabel(incident: Incident): string {
    const target = this.nextStatus(incident);
    return target ? TRANSITION_LABELS[target] : '';
  }

  advanceIncident(incident: Incident) {
    const target = this.nextStatus(incident);
    if (!target || this.transitionPendingId !== null || !this.canRespond) {
      return;
    }
    this.transitionPendingId = incident.id;
    this.transitionErrors = { ...this.transitionErrors, [incident.id]: '' };

    this.api.transitionIncident(incident.id, target).subscribe({
      next: (updated) => {
        this.transitionPendingId = null;
        this.realtime.applyIncidentUpdate(updated);
      },
      error: (error: unknown) => {
        this.transitionPendingId = null;
        this.transitionErrors = {
          ...this.transitionErrors,
          [incident.id]: describeProblem(error),
        };
        // A rejected transition means the local copy is behind the server;
        // re-reading is what makes the card show the real current stage.
        if (problemErrorCode(error) === 'INVALID_STATE_TRANSITION') {
          this.realtime.refreshIncidents();
        }
      },
    });
  }

  dismissTransitionError(id: number) {
    this.transitionErrors = { ...this.transitionErrors, [id]: '' };
  }

  refreshIncidents() {
    this.realtime.refreshIncidents();
  }

  // ======================================================== FILTRELER

  get filteredDevices(): Device[] {
    return this.devices.filter((d) => {
      const matchesSearch =
        d.name.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        d.ipAddress.includes(this.searchTerm);
      const matchesStatus = this.statusFilter === 'ALL' || d.status === this.statusFilter;
      return matchesSearch && matchesStatus;
    });
  }

  get filteredLogs(): AuditLog[] {
    return this.logs.filter(
      (l) =>
        l.message.toLowerCase().includes(this.logSearchTerm.toLowerCase()) ||
        l.timestamp.includes(this.logSearchTerm),
    );
  }

  get filteredEvents(): IngestedEvent[] {
    const term = this.logSearchTerm.toLowerCase();
    if (!term) return this.events;
    return this.events.filter(
      (e) =>
        e.source.toLowerCase().includes(term) ||
        e.category.toLowerCase().includes(term) ||
        e.severity.toLowerCase().includes(term) ||
        (e.rawPayload ?? '').toLowerCase().includes(term),
    );
  }

  refreshStream() {
    if (this.streamSource === 'audit') {
      this.loadLogs();
    } else {
      this.loadEvents();
    }
  }

  selectStreamSource(source: StreamSource) {
    this.streamSource = source;
    if (source === 'events' && !this.eventsLoaded) {
      this.loadEvents();
    }
  }

  loadEvents() {
    this.api.getEvents({ page: 0, size: 100 }).subscribe({
      next: (result) => {
        this.events = result.content ?? [];
        this.eventsLoaded = true;
        this.eventsError = null;
      },
      error: (error: unknown) => {
        this.eventsError = describeProblem(error);
        this.eventsLoaded = true;
      },
    });
  }

  eventSeverityClass(severity: string): string {
    const normalised = (severity || 'INFO').toLowerCase();
    return ['critical', 'high', 'medium', 'low', 'info'].includes(normalised)
      ? `sev-${normalised}`
      : 'sev-info';
  }

  // ======================================================== CİHAZ GEÇMİŞİ

  /**
   * Expands or collapses the inline latency-history row for one device.
   * A read-only action available to every authenticated role — there is no
   * canRespond gate here, only the state-changing device actions need one.
   */
  toggleDeviceHistory(device: Device) {
    if (device.id === undefined) return;
    if (this.expandedDeviceId === device.id) {
      this.expandedDeviceId = null;
      return;
    }
    this.expandedDeviceId = device.id;
    this.loadDeviceLatency(device.id);
  }

  private loadDeviceLatency(deviceId: number) {
    this.latencyLoading = true;
    this.latencyError = null;
    this.latencySamples = [];
    this.api.getDeviceLatency(deviceId, 100).subscribe({
      next: (samples) => {
        this.latencySamples = samples;
        this.latencyLoading = false;
      },
      error: (error: unknown) => {
        this.latencyError = describeProblem(error);
        this.latencyLoading = false;
      },
    });
  }

  get latencyChartPoints(): SparklinePoint[] {
    return this.latencySamples.map((sample) => ({
      label: new Date(sample.recordedAt).toLocaleTimeString('tr-TR', {
        hour: '2-digit',
        minute: '2-digit',
      }),
      value: sample.latency,
    }));
  }

  // ======================================================== SAYAÇLAR

  get totalDevices(): number {
    return this.devices.length;
  }
  get activeDevices(): number {
    return this.devices.filter((d) => d.status === 'ACTIVE').length;
  }
  get inactiveDevices(): number {
    return this.devices.filter((d) => d.status === 'INACTIVE').length;
  }
  get avgLatency(): number {
    const activeWithLatency = this.devices.filter(
      (d) => d.status === 'ACTIVE' && d.latency !== undefined && d.latency >= 0,
    );
    if (activeWithLatency.length === 0) return 0;
    const sum = activeWithLatency.reduce((acc, d) => acc + (d.latency || 0), 0);
    return Math.round(sum / activeWithLatency.length);
  }

  // ======================================================== ANALİZ (GRAFİKLER)

  /** Günlük olay sayısı, son 14 gün — mevcut olay verisinden istemci tarafında türetilir. */
  get incidentTrendData(): BarChartDatum[] {
    return groupIncidentsByDay(this.allIncidents, 14);
  }

  /** Şiddete göre olay sayısı — mevcut olay verisinden istemci tarafında türetilir. */
  get severityDistributionData(): BarChartDatum[] {
    return countIncidentsBySeverity(this.allIncidents);
  }

  /** Cihaz tipine göre erişilebilirlik yüzdesi — mevcut cihaz verisinden türetilir. */
  get deviceTypeUptimeData(): BarChartDatum[] {
    return computeDeviceTypeUptime(this.devices);
  }

  // ======================================================== TEHDİT SEVİYESİ

  /**
   * Whether the indicator is reading the server-computed snapshot or the
   * locally derived number. The snapshot only arrives over the push channel,
   * so REST fallback always means local.
   */
  get threatSource(): 'realtime' | 'local' {
    return this.realtime.metrics() !== null &&
      this.realtime.connectionState() !== 'polling-fallback'
      ? 'realtime'
      : 'local';
  }

  /** Numerator of the threat score. */
  get threatCount(): number {
    const metrics = this.realtime.metrics();
    return this.threatSource === 'realtime' && metrics
      ? metrics.criticalOrHighIncidents
      : this.inactiveDevices;
  }

  /** Denominator of the threat score — the size of the estate in scope. */
  get threatScope(): number {
    const metrics = this.realtime.metrics();
    return this.threatSource === 'realtime' && metrics ? metrics.totalDevices : this.totalDevices;
  }

  get threatFormula(): string {
    return this.threatSource === 'realtime'
      ? 'skor = açık KRİTİK + YÜKSEK olay / toplam cihaz'
      : 'skor = erişilemez cihaz / toplam cihaz';
  }

  get threatSourceNote(): string {
    return this.threatSource === 'realtime'
      ? 'sunucu anlık görüntüsü · /topic/metrics'
      : 'yerel hesap · anlık görüntü yok';
  }

  // Tehdit oranı — bantların tek girdisi. Bantlar ve eşikler değişmedi;
  // yalnızca sayacın kaynağı canlı metriklere taşındı.
  get threatRatio(): number {
    const scope = this.threatScope;
    if (scope === 0) return 0;
    return this.threatCount / scope;
  }

  // Tehdit seviyesi: eşikler ve anlamlar aynı, sunum sınıfa (band) taşındı.
  get threatLevel(): { band: string; label: string; detail: string } {
    if (this.threatScope === 0)
      return { band: 'idle', label: 'GÜVENLİ', detail: 'Sistemde kayıtlı cihaz yok' };
    const downRatio = this.threatRatio;
    if (downRatio >= 0.5)
      return { band: 'severe', label: 'KRİTİK', detail: 'Tehdit seviyesi yüksek' };
    if (downRatio > 0) return { band: 'high', label: 'UYARI', detail: 'Kısmi erişim sorunu' };
    return { band: 'nominal', label: 'GÜVENLİ', detail: 'Stabil eko-sistem' };
  }

  // Segmentli tehdit göstergesi (24 bölme) — yalnızca görselleştirme.
  readonly meterSegments: number[] = Array.from({ length: 24 }, (_, i) => i);

  get meterLit(): number {
    if (this.threatScope === 0) return 0;
    return Math.max(1, Math.round(this.threatRatio * this.meterSegments.length));
  }

  // ======================================================== AKSİYONLAR

  exportLogsAsTxt() {
    if (this.logs.length === 0) return;
    let fileContent =
      '==================================================\n        ENTERPRISE NETWORK SIEM CONSOLE AUDIT LOG GÜVENLİK RAPORU\n==================================================\n\n';
    this.logs.forEach((l) => {
      fileContent += `[${l.timestamp}] - ${l.message}\n`;
    });
    const blob = new Blob([fileContent], { type: 'text/plain;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `siem_audit_report_${Date.now()}.txt`;
    a.click();
  }

  clearConsoleDisplay() {
    this.logs = [];
  }

  scanAll() {
    if (!this.canRespond) {
      return;
    }
    this.isScanning = true;
    this.deviceService.scanAllDevices().subscribe({
      next: () => {
        this.loadDevices();
        this.loadLogs();
        this.isScanning = false;
      },
      error: () => (this.isScanning = false),
    });
  }

  simulateAttack(id: number | undefined) {
    if (id === undefined || !this.canRespond) return;
    this.deviceService.simulateCyberAttack(id).subscribe(() => {
      this.loadDevices();
      this.loadLogs();
    });
  }

  simulateSshBruteforce(id: number | undefined) {
    if (id === undefined || !this.canRespond) return;
    this.deviceService.simulateSshBruteForce(id).subscribe(() => {
      this.loadDevices();
      this.loadLogs();
    });
  }

  exportLogsToCSV() {
    if (this.logs.length === 0) return;
    const csvContent =
      'Tarih,Aksiyon\n' +
      this.logs.map((log) => `"${log.timestamp}","${log.message}"`).join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `siem_guvenlik_raporu_${Date.now()}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  }

  createDevice() {
    if (!this.deviceName || !this.deviceIp) return;
    const ipPattern =
      /^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
    if (!ipPattern.test(this.deviceIp)) {
      alert('HATA: Lütfen geçerli bir IPv4 adresi girin!');
      return;
    }

    const newDevice: Device = {
      name: this.deviceName,
      ipAddress: this.deviceIp,
      deviceType: this.selectedType, // Seçilen cihaz tipini backend'e yolla
    };

    this.deviceService.addDevice(newDevice).subscribe(() => {
      this.deviceName = '';
      this.deviceIp = '';
      this.loadDevices();
      this.loadLogs();
    });
  }

  pingDevice(id: number | undefined) {
    if (id === undefined || !this.canRespond) return;
    this.deviceService.checkStatus(id).subscribe(() => {
      this.loadDevices();
      this.loadLogs();
    });
  }

  deleteDevice(id: number | undefined) {
    if (id === undefined || !this.canRespond) return;
    if (confirm('Bu cihazı silmek istediğinize emin misiniz?')) {
      this.deviceService.deleteDevice(id).subscribe(() => {
        this.loadDevices();
        this.loadLogs();
      });
    }
  }
}
