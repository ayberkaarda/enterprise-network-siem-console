package com.example.demo.device;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LatencySampleRepository extends JpaRepository<LatencySample, Long> {

    /**
     * Newest-first page of samples for one device. The API response is
     * chronological (ascending); callers reverse the page returned here rather
     * than this query doing the reversal, since "give me the N most recent
     * rows" is what an index-friendly {@code ORDER BY ... LIMIT} expresses
     * directly, and reversing a small, already-fetched list is cheap in
     * comparison.
     */
    List<LatencySample> findByDeviceIdOrderByRecordedAtDesc(Long deviceId, Pageable pageable);

    /**
     * Bulk-deletes every sample older than {@code cutoff} for the retention
     * purge, backing {@link com.example.demo.device.DeviceService}'s daily
     * cleanup job. A derived {@code deleteBy...} method would load each row as
     * an entity before removing it one at a time; this table is written every
     * scan cycle for every device, so a single bulk {@code DELETE} statement is
     * used instead.
     *
     * @return the number of rows removed, for the purge job's log line
     */
    @Modifying
    @Query("delete from LatencySample sample where sample.recordedAt < :cutoff")
    int deleteByRecordedAtBefore(@Param("cutoff") Instant cutoff);
}
