package com.example.demo.ingestion;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IngestedEventMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "receivedAt", ignore = true)
    IngestedEvent toEntity(IngestedEventRequest request);

    IngestedEventResponse toResponse(IngestedEvent event);
}
