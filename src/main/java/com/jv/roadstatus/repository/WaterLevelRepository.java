package com.jv.roadstatus.repository;

import com.jv.roadstatus.domain.WaterLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaterLevelRepository extends JpaRepository<WaterLevel, Long> {

    // Use a query method based on the field you want to order by (e.g., createdAt)
    Optional<WaterLevel> findFirstByOrderByCreatedAtDesc();

    // Get historical water level data ordered by timestamp
    List<WaterLevel> findAllByOrderByCreatedAtAsc();

    // Get the most recent water levels, limited to a specific count
    List<WaterLevel> findTop50ByOrderByCreatedAtDesc();
}
