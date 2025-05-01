package com.jv.roadstatus.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@RequiredArgsConstructor
@NoArgsConstructor
public class WaterLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private BigDecimal currentWaterLevel;

    private LocalDateTime createdAt;
    // Automatically set the createdAt timestamp when the entity is persisted
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }


    // Other fields and methods
}



