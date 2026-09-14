package com.safari.tms.repo;

import com.safari.tms.domain.Guide;
import com.safari.tms.domain.enums.GuideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    List<Guide> findAllByOrderByFullNameAsc();
    List<Guide> findByStatusOrderByFullNameAsc(GuideStatus status);
    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);
    long countByStatus(GuideStatus status);
}
