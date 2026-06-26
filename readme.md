# ⚡ Enterprise Network Security & SIEM Console

Bu proje, kurum içi ağ cihazlarının durumlarını, ping gecikmelerini (latency) ve erişilebilirlik trendlerini canlı olarak izlemek amacıyla geliştirilmiş dinamik bir **Ağ Yönetimi ve SOC (Security Operations Center) İzleme** panelidir.

## 🚀 Öne Çıkan Mühendislik Özellikleri
* **Olay Güdümlü Mimari (Event-Driven):** Cihaz durum değişikliklerinde asenkron olay fırlatma (ApplicationEventPublisher) ve bağımsız loglama.
* **Canlı Olay (Incident) Yönetimi:** Erişimi kesilen cihazlar için SOC standartlarında anlık alarm kuyruğu ve manuel onay (Acknowledge) mekanizması.
* **Global Exception Handling:** `@RestControllerAdvice` ile merkezi ve kurumsal REST API hata yönetimi.
* **Gelişmiş Tipoloji & Trend Analizi:** Firewall, Switch, Router sınıflandırması ve satır bazlı Sparkline trend izleme.

## 🛠️ Kullanılan Teknolojiler
* **Backend:** Java, Spring Boot, Spring Data JPA
* **Frontend:** Angular, TypeScript, HTML5, Modern CSS
* **Veritabanı:** H2 Database (In-Memory) / MySQL uyumlu

## 👨‍💻 Geliştirici
**Ayberk Arda**
*Software Developer | Computer Programming, Istanbul Kültür University (İKÜ)*