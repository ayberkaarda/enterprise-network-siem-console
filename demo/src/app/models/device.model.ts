export interface Device {
    id?: number; // Soru işareti, yeni cihaz eklerken ID'nin henüz var olmadığını belirtir
    name: string;
    ipAddress: string;
    status?: string;
}