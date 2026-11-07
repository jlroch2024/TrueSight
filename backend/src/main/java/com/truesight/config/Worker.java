package com.truesight.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One running server, renewed every 30 seconds. Background work records the id of the server that owns it; an owner
 * with no recent row here is a server that has gone, and its work can be recovered.
 */
@Entity
@Table(name = "workers")
public class Worker {

    @Id
    private UUID id;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** For JPA only. */
    protected Worker() {
    }

    public Worker(UUID id, Instant lastSeenAt) {
        this.id = id;
        this.lastSeenAt = lastSeenAt;
    }

    public UUID getId() { return id; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
