package com.safari.tms.repo;

import com.safari.tms.domain.ExchangeRate;
import com.safari.tms.domain.enums.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("select r from ExchangeRate r left join fetch r.updatedBy order by r.currency asc")
    List<ExchangeRate> findAllDetailed();

    @Query("select r from ExchangeRate r left join fetch r.updatedBy where r.currency = :currency")
    Optional<ExchangeRate> findByCurrency(@Param("currency") Currency currency);
}
