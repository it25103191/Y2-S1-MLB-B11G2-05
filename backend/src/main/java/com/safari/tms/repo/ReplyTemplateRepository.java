package com.safari.tms.repo;

import com.safari.tms.domain.ReplyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReplyTemplateRepository extends JpaRepository<ReplyTemplate, Long> {

    @Query("select t from ReplyTemplate t left join fetch t.createdBy order by t.title asc")
    List<ReplyTemplate> findAllDetailed();

    @Query("select t from ReplyTemplate t left join fetch t.createdBy where t.id = :id")
    Optional<ReplyTemplate> findDetailById(@Param("id") Long id);

    Optional<ReplyTemplate> findByTitleIgnoreCase(String title);
}
