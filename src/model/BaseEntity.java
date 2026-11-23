package model;

import java.time.LocalDateTime;

public abstract class BaseEntity {
    protected final String id;
    protected final LocalDateTime createdAt;

    protected BaseEntity(String id, LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}