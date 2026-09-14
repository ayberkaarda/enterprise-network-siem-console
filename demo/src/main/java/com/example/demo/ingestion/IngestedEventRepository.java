package com.example.demo.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestedEventRepository
        extends JpaRepository<IngestedEvent, Long>, JpaSpecificationExecutor<IngestedEvent> {
}
