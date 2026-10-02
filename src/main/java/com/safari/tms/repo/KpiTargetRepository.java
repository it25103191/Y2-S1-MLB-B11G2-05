package com.safari.tms.repo;

import com.safari.tms.domain.KpiTarget;
import com.safari.tms.domain.enums.KpiMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KpiTargetRepository extends JpaRepository<KpiTarget, Long> {

    @Query("""
            select t from KpiTarget t
              left join fetch t.createdBy
              left join fetch t.updatedBy
             order by t.periodMonth desc, t.metric asc
            """)
    List<KpiTarget> findAllDetailed();

    @Query("""
            select t from KpiTarget t
              left join fetch t.createdBy
              left join fetch t.updatedBy
             where t.id = :id
            """)
    Optional<KpiTarget> findDetailById(@Param("id") Long id);

    List<KpiTarget> findByPeriodMonthBetween(LocalDate from, LocalDate to);

    Optional<KpiTarget> findByMetricAndPeriodMonth(KpiMetric metric, LocalDate periodMonth);
}
