package com.safari.tms.repo;

import com.safari.tms.domain.Park;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParkRepository extends JpaRepository<Park, Long> {
    List<Park> findByActiveTrueOrderByNameAsc();

    List<Park> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
