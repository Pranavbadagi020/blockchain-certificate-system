package certificate_backend.service;

import certificate_backend.model.AuditEvent;
import certificate_backend.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void logEvent(String eventType, String actor, String ipAddress, String status, Map<String, Object> details) {
        try {
            AuditEvent event = new AuditEvent(eventType, actor, ipAddress, status, details != null ? details : Collections.emptyMap());
            auditEventRepository.save(event);
            log.info("AUDIT [{}]: actor={} status={} ip={}", eventType, actor, status, ipAddress);
        } catch (Exception e) {
            log.error("Failed to persist audit event [{}]: {}", eventType, e.getMessage());
        }
    }

    public long countByEventType(String eventType) {
        try {
            return auditEventRepository.countByEventType(eventType);
        } catch (Exception e) {
            log.error("Failed to count audit events for {}: {}", eventType, e.getMessage());
            return 0;
        }
    }

    public List<AuditEvent> getRecentEvents(int limit) {
        try {
            int boundedLimit = Math.min(Math.max(limit, 1), 100);
            return auditEventRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, boundedLimit)).getContent();
        } catch (Exception e) {
            log.error("Failed to fetch recent audit events: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
