import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Device {
  id?: number;
  name: string;
  ipAddress: string;
  status?: string;
  latency?: number;
  deviceType?: string; // YENİ: Cihaz tipi alanı modele eklendi
}

// 1. Yeni eklediğimiz Güvenlik Günlüğü (Log) yapısını tanımlıyoruz
export interface AuditLog {
  id?: number;
  message: string;
  timestamp: string;
}

@Injectable({
  providedIn: 'root',
})
export class DeviceService {
  private apiUrl = 'http://localhost:8080/api/devices';

  constructor(private http: HttpClient) {}

  // 1. Tüm cihazları getir
  getDevices(): Observable<Device[]> {
    return this.http.get<Device[]>(this.apiUrl);
  }

  // 2. Yeni cihaz ekle
  addDevice(device: Device): Observable<Device> {
    return this.http.post<Device>(this.apiUrl, device);
  }

  // 3. Durum kontrolünü tetikle (Ping)
  checkStatus(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/check`, {});
  }

  // 4. Tüm sistem loglarını getiren yeni fonksiyonumuz
  getLogs(): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(`${this.apiUrl}/logs`);
  }
  // 5. Cihazı sistemden silen HTTP DELETE fonksiyonu
  deleteDevice(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
  // 6. Tüm ağı anında tarayan global HTTP POST fonksiyonu
  scanAllDevices(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/scan`, {});
  }
  simulateCyberAttack(id: number) {
    return this.http.post(`${this.apiUrl}/${id}/attack`, {});
  }

  simulateSshBruteForce(id: number) {
    return this.http.post(`${this.apiUrl}/${id}/ssh-bruteforce`, {});
  }
}
