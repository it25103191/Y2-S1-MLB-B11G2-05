package com.safari.tms.repo;

import com.safari.tms.domain.CommunicationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommunicationLogRepository extends JpaRepository<CommunicationLog, Long> {

    @Query("""
            select cl from CommunicationLog cl
              join fetch cl.customer
              left join fetch cl.author
             where cl.complaint.id = :complaintId
             order by cl.createdAt asc
            """)
    List<CommunicationLog> findForComplaint(@Param("complaintId") Long complaintId);

    @Query("""
            select cl from CommunicationLog cl
              join fetch cl.customer
              left join fetch cl.author
             where cl.customer.id = :customerId
             order by cl.createdAt desc
            """)
    List<CommunicationLog> findForCustomer(@Param("customerId") Long customerId);
}
