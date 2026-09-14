package com.safari.tms.repo;

import com.safari.tms.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    @Query("""
            select n from NotificationLog n
              left join fetch n.recipient
             order by n.createdAt desc
            """)
    List<NotificationLog> findAllDetailed();

    @Query("""
            select n from NotificationLog n
              left join fetch n.recipient
             where n.recipient.id = :userId
             order by n.createdAt desc
            """)
    List<NotificationLog> findForUser(@Param("userId") Long userId);
}
