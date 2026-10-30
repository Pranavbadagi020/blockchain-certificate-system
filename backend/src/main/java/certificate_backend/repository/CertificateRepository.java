package certificate_backend.repository;

import certificate_backend.model.Certificate;
import certificate_backend.model.CertificateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CertificateRepository extends MongoRepository<Certificate, String> {

    Optional<Certificate> findByCertificateId(String certificateId);

    boolean existsByCertificateId(String certificateId);

    long countByStatus(CertificateStatus status);

    Page<Certificate> findByStatus(CertificateStatus status, Pageable pageable);

    @Query("{ '$or': [ " +
           "{ 'certificateId': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'studentName': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'studentRollNumber': { '$regex': ?0, '$options': 'i' } } ] }")
    Page<Certificate> searchAll(String searchRegex, Pageable pageable);

    @Query("{ 'status': ?0, '$or': [ " +
           "{ 'certificateId': { '$regex': ?1, '$options': 'i' } }, " +
           "{ 'studentName': { '$regex': ?1, '$options': 'i' } }, " +
           "{ 'studentRollNumber': { '$regex': ?1, '$options': 'i' } } ] }")
    Page<Certificate> searchByStatus(CertificateStatus status, String searchRegex, Pageable pageable);
}
