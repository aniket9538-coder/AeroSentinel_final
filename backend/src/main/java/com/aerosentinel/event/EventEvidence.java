package com.aerosentinel.event;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "PollutionEventEvidence")
@Table(name = "event_evidence")
public class EventEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pollution_event_id")
    private PollutionEvent pollutionEvent;

    @Column(name = "event_id")
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type")
    private EvidenceType evidenceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "value_summary")
    private String valueSummary;

    @Column(name = "strength")
    private Double strength = 1.0;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public PollutionEvent getPollutionEvent() { return pollutionEvent; }
    public void setPollutionEvent(PollutionEvent pollutionEvent) {
        this.pollutionEvent = pollutionEvent;
        if (pollutionEvent != null && pollutionEvent.getEventId() != null) {
            this.eventId = pollutionEvent.getEventId();
        }
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public EvidenceType getEvidenceType() { return evidenceType; }
    public void setEvidenceType(EvidenceType evidenceType) { this.evidenceType = evidenceType; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getValueSummary() { return valueSummary; }
    public void setValueSummary(String valueSummary) { this.valueSummary = valueSummary; }

    public Double getStrength() { return strength; }
    public void setStrength(Double strength) { this.strength = strength; }

    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}