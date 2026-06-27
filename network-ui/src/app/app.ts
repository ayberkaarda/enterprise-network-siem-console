import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DeviceService, Device, AuditLog } from './services/device';
import { Subscription } from 'rxjs';
import { WebsocketService } from './services/websocket.service';

// Canlı alarm yapısı için arayüz
interface Incident {
  deviceId: number;
  deviceName: string;
  ipAddress: string;
  detectedTime: Date;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit, OnDestroy {
  devices: Device[] = [];
  logs: AuditLog[] = [];

  // Form girdileri
  deviceName: string = '';
  deviceIp: string = '';
  selectedType: string = 'SERVER'; // Varsayılan cihaz tipi

  // Arama ve Filtreleme
  searchTerm: string = '';
  statusFilter: string = 'ALL';
  logSearchTerm: string = '';
  isScanning: boolean = false;

  // 1. ÖZELLİK: CANLI OLAY (INCIDENT) KUYRUĞU
  incidents: Incident[] = [];

  // 2. ÖZELLİK: SATIR BAZLI SESSİON TREND HAFIZASI
  // Cihaz ID'sine göre son 5 tarama sonucunu (ACTIVE/INACTIVE) dizide tutar
  deviceTrends: { [key: number]: string[] } = {};

  private wsSubscription?: Subscription;

  constructor(
      private deviceService: DeviceService,
      private websocketService: WebsocketService
  ) {}

  ngOnInit() {
    this.loadDevices();
    this.loadLogs();

    // HTTP Polling yerine WebSocket üzerinden anlık dinleme yapıyoruz
    this.wsSubscription = this.websocketService.alerts$.subscribe((newAlert) => {
      this.loadDevices();
      this.loadLogs();
    });
  }

  ngOnDestroy() {
    if (this.wsSubscription) {
      this.wsSubscription.unsubscribe();
    }
  }

  loadDevices() {
    this.deviceService.getDevices().subscribe({
      next: (data) => {
        this.devices = data;
        this.processIncidentsAndTrends(); // Gelen yeni verileri SOC kurallarına göre işle
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

  // ALARM VE TREND MOTORU
  private processIncidentsAndTrends() {
    this.devices.forEach((device) => {
      if (device.id === undefined) return;

      // A) Canlı Trend Geçmişini Güncelle (Maksimum son 5 kayıt)
      if (!this.deviceTrends[device.id]) {
        this.deviceTrends[device.id] = [];
      }
      const currentHistory = this.deviceTrends[device.id];
      if (
          device.status &&
          (currentHistory.length === 0 ||
              currentHistory[currentHistory.length - 1] !== device.status ||
              Math.random() > 0.6)
      ) {
        currentHistory.push(device.status);
        if (currentHistory.length > 5) {
          currentHistory.shift(); // En eskiyi at, yeniye yer aç
        }
      }

      // B) Canlı Olay (Incident) İncelemesi
      if (device.status === 'INACTIVE') {
        const alreadyFired = this.incidents.some((i) => i.deviceId === device.id);
        if (!alreadyFired) {
          this.incidents.push({
            deviceId: device.id,
            deviceName: device.name,
            ipAddress: device.ipAddress,
            detectedTime: new Date(),
          });
        }
      } else if (device.status === 'ACTIVE') {
        // Cihaz ayağa kalktıysa alarmı otomatik kuyruktan düşür
        this.incidents = this.incidents.filter((i) => i.deviceId !== device.id);
      }
    });
  }

  // ALARMI MANUEL ONAYLAMA / KALDIRMA
  acknowledgeIncident(deviceId: number) {
    this.incidents = this.incidents.filter((i) => i.deviceId !== deviceId);
  }

  // Cihaz Canlı Filtreleme
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

  // Sayaçlar
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

  get threatLevel() {
    if (this.totalDevices === 0)
      return { status: 'GÜVENLİ (YÜKSÜZ)', color: '#38bdf8', bg: 'rgba(56, 189, 248, 0.2)' };
    const downRatio = this.inactiveDevices / this.totalDevices;
    if (downRatio >= 0.5)
      return {
        status: 'KRİTİK (TEHDİT SEVİYESİ YÜKSEK)',
        color: '#ef4444',
        bg: 'rgba(239, 68, 68, 0.2)',
      };
    if (downRatio > 0)
      return {
        status: 'UYARI (KISMİ ERİŞİM SORUNU)',
        color: '#f59e0b',
        bg: 'rgba(245, 158, 11, 0.2)',
      };
    return {
      status: 'GÜVENLİ (STABİL EKO-SİSTEM)',
      color: '#22c55e',
      bg: 'rgba(34, 197, 94, 0.2)',
    };
  }

  exportLogsAsTxt() {
    if (this.logs.length === 0) return;
    let fileContent =
        '==================================================\n        KRON AUDIT LOG GÜVENLİK RAPORU\n==================================================\n\n';
    this.logs.forEach((l) => {
      fileContent += `[${l.timestamp}] - ${l.message}\n`;
    });
    const blob = new Blob([fileContent], { type: 'text/plain;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `kron_audit_report_${Date.now()}.txt`;
    a.click();
  }

  clearConsoleDisplay() {
    this.logs = [];
  }

  scanAll() {
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
    if (id === undefined) return;
    this.deviceService.checkStatus(id).subscribe(() => {
      this.loadDevices();
      this.loadLogs();
    });
  }

  deleteDevice(id: number | undefined) {
    if (id === undefined) return;
    if (confirm('Bu cihazı silmek istediğinize emin misiniz?')) {
      this.deviceService.deleteDevice(id).subscribe(() => {
        this.loadDevices();
        this.loadLogs();
      });
    }
  }
}
