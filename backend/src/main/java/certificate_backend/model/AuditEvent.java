package certificate_backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "audit_events")
public class AuditEvent {

    @Id
    private String id;

    @Indexed
    private String eventType;

    @Indexed
    private String actor;

    private String ipAddress;

    @Indexed
    private Instant timestamp;

    private Map<String, Object> details;

    private String status;

    public AuditEvent() {
        this.timestamp = Instant.now();
    }

    public AuditEvent(String eventType, String actor, String ipAddress, String status, Map<String, Object> details) {
        this.eventType = eventType;
        this.actor = actor != null ? actor : "ANONYMOUS";
        this.ipAddress = ipAddress;
        this.status = status;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
