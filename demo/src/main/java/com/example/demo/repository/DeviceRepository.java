package com.example.demo.repository;

import com.example.demo.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository // Bu arayüzün veri tabanı işlemleri yapacağını Spring'e bildiririz
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // JpaRepository sayesinde; save(), findAll(), findById(), deleteById() gibi
    // tüm temel veri tabanı metotları arka planda otomatik olarak hazırlandı!
    // Şu an buraya tek bir satır bile kod yazmamıza gerek yok.

}