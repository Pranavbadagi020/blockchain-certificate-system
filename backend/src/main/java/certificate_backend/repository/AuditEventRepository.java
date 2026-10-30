package certificate_backend.repository;

import certificate_backend.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    long countByEventType(String eventType);

    List<AuditEvent> findTop50ByOrderByTimestampDesc();

    Page<AuditEvent> findAllByOrderByTimestampDesc(Pageable pageable);

    List<AuditEvent> findByEventTypeOrderByTimestampDesc(String eventType);
}
