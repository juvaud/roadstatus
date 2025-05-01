package com.jv.roadstatus.repository;

import com.jv.roadstatus.domain.RoadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadStatusRepository extends JpaRepository<RoadStatus, Long> {

    /**
     * Find the most recent road status entry
     */
    Optional<RoadStatus> findFirstByOrderByTimestampDesc();

    /**
     * Find the 10 most recent road status entries
     */
    List<RoadStatus> findTop10ByOrderByTimestampDesc();
}
