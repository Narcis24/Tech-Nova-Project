package com.neueda.app.models;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_events")
@NoArgsConstructor
@Getter
public class OutboxEvent {

    @Id
    private UUID id;

    private String eventType;

    private String aggregateId;

    @Lob
    private String payload;

    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    private String status;
}