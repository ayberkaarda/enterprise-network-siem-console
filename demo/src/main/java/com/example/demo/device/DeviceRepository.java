package com.example.demo.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

    long countByStatus(String status);

    /**
     * Mean last-known latency across the devices in a given status.
     *
     * <p>Returns null when no device qualifies, which the caller reports as
     * zero. Averaging in the database rather than in Java keeps the live
     * snapshot from loading the whole device table every few seconds.
     */
    @Query("select avg(d.latency) from Device d where d.status = :status and d.latency >= 0")
    Double averageLatencyByStatus(@Param("status") String status);
}
