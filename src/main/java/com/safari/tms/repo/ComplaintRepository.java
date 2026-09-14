package com.safari.tms.repo;

import com.safari.tms.domain.Complaint;
import com.safari.tms.domain.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    @Query("""
            select c from Complaint c
              join fetch c.customer
              left join fetch c.booking b
              left join fetch b.safariPackage
              left join fetch c.assignedTo
             order by c.createdAt desc
            """)
    List<Complaint> findAllDetailed();

    @Query("""
            select c from Complaint c
              join fetch c.customer
              left join fetch c.booking b
              left join fetch b.safariPackage
              left join fetch c.assignedTo
             where c.id = :id
            """)
    Optional<Complaint> findDetailById(@Param("id") Long id);

    @Query("""
            select c from Complaint c
              join fetch c.customer
              left join fetch c.booking b
              left join fetch b.safariPackage
              left join fetch c.assignedTo
             where c.customer.id = :customerId
             order by c.createdAt desc
            """)
    List<Complaint> findForCustomer(@Param("customerId") Long customerId);

    long countByStatus(ComplaintStatus status);

    long countByStatusIn(Collection<ComplaintStatus> statuses);
}
