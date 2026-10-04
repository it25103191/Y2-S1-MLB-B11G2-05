package com.safari.tms.repo;

import com.safari.tms.domain.Vehicle;
import com.safari.tms.domain.enums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findAllByOrderByRegistrationNumberAsc();
    List<Vehicle> findByStatusOrderByRegistrationNumberAsc(VehicleStatus status);
    List<Vehicle> findByStatusAndCapacityGreaterThanEqualOrderByCapacityAsc(VehicleStatus status, Integer capacity);
    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);
    long countByStatus(VehicleStatus status);
}
