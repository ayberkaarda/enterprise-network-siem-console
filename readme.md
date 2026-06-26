# ⚡ Enterprise Network Security & SIEM Console

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-DD0031?style=for-the-badge&logo=angular&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-007ACC?style=for-the-badge&logo=typescript&logoColor=white)

Kurum içi ağ altyapısındaki (Firewall, Server, Router, Switch) cihazların erişilebilirlik durumlarını, ping gecikmelerini (latency) ve stabilite trendlerini **gerçek zamanlı** olarak analiz eden dinamik bir **SOC (Security Operations Center)** ve Ağ İzleme paneli.

Bu proje, bir izleme aracı olmasının ötesinde; **Temiz Kod (Clean Code)** prensipleri, asenkron olay yönetimi ve kurumsal hata yakalama mekanizmaları kullanılarak ölçeklenebilir bir mühendislik vizyonuyla geliştirilmiştir.

---

## 🚀 Öne Çıkan Mühendislik Özellikleri & Mimari

### 1. Olay Güdümlü Mimari (Event-Driven Architecture)
Servisler arası sıkı bağımlılığı (Tight Coupling) kırmak amacıyla Spring Boot `ApplicationEventPublisher` kullanılmıştır. Bir ağ cihazının durumu değiştiğinde tarama servisi doğrudan log yazmak yerine `DeviceStatusChangedEvent` fırlatır; bağımsız bir `@EventListener` bu olayı yakalayarak asenkron loglama ve veritabanı kayıt işlemlerini gerçekleştirir.

### 2. Merkezi ve Kurumsal Hata Yönetimi (Global Exception Handling)
REST API üzerinde oluşabilecek geçersiz IP formatları veya sunucu bazlı hatalar `@RestControllerAdvice` ve özel Hata (Custom Exception) sınıfları ile (örn: `InvalidIpException`) yakalanır. Frontend'e her zaman formatlanmış, standart ve güvenli bir JSON hata objesi (`Timestamp, Status, ErrorMessage`) dönülür.

### 3. İlişkisel Veri Bütünlüğü (JPA One-To-Many)
Cihazlar (`Device`) ve Güvenlik Logları (`AuditLog`) arasında JPA seviyesinde ilişkisel bağlar kurularak tam veri bütünlüğü sağlanmıştır. Cihaz silindiğinde veya güncellendiğinde Hibernate üzerinden kaskad (Cascade) operasyonları güvenle yönetilir.

### 4. Canlı Olay (Incident) ve Alarm Yönetim Kuyruğu
Erişimi kesilen (INACTIVE) cihazlar, sistemde anlık bir kritik durum (Incident) yaratır. Ağ mühendisi/sistem yöneticisi arayüz üzerinden bu alarmı manuel olarak onaylayana (Acknowledge) kadar sistem teyakkuz durumunda (Critical Threat Level) kalır.

---

## 📂 Proje Yapısı (Monorepo)

Proje, tek bir depo üzerinden yönetilen modern bir monorepo yapısına sahiptir:

* **`demo/`**: Spring Boot ile geliştirilen ve H2 Database üzerinde koşan asenkron REST API motorunu barındırır.
* **`network-ui/`**: Angular 17+ ile geliştirilen, RxJS ile canlı veri akışı sağlayan ve modern CSS ile tasarlanmış bağımsız önyüz bileşenlerini barındırır.

---

## 🛠️ Kullanılan Teknolojiler

**Backend (Core System)**
* Java 17+
* Spring Boot 3.x (Web, Data JPA)
* Spring Application Events (Event-Driven Design)
* H2 Database (In-Memory)

**Frontend (Client)**
* Angular (Standalone Components)
* TypeScript
* RxJS (Reactive Programming & Polling)
* Modern CSS (Custom Animations & Sparklines)

---

## ⚙️ Kurulum ve Çalıştırma

Projeyi yerel ortamınızda (localhost) ayağa kaldırmak için aşağıdaki adımları izleyebilirsiniz.

### 1. Backend'i Başlatma
Terminalinizi `demo/` dizininde açın ve Maven Wrapper aracılığıyla projeyi derleyip çalıştırın:
````bash
cd demo
./mvnw spring-boot:run
````
### 2. Frontend'i Başlatma
Yeni bir terminal sekmesi açarak network-ui/ dizinine gidin, gerekli Node paketlerini kurun ve Angular sunucusunu başlatın:
````bash
cd network-ui
npm install
npm start
````

👨‍💻 Geliştirici İletişim
Ayberk Arda

Software Developer | Computer Programming, Istanbul Kültür University (İKÜ)
