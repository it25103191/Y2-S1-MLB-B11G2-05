package com.safari.tms.repo;

import com.safari.tms.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query("""
            select a from Assignment a
              join fetch a.booking b
              join fetch b.safariPackage p
              join fetch p.park
              join fetch b.customer
              join fetch a.guide
              join fetch a.vehicle
             order by a.assignmentDate asc
            """)
    List<Assignment> findAllDetailed();

    @Query("""
            select a from Assignment a
              join fetch a.booking b
              join fetch b.safariPackage p
              join fetch p.park
              join fetch b.customer
              join fetch a.guide
              join fetch a.vehicle
             where a.id = :id
            """)
    Optional<Assignment> findDetailById(@Param("id") Long id);

    @Query("""
            select a from Assignment a
              join fetch a.booking b
              join fetch b.safariPackage p
              join fetch p.park
              join fetch b.customer
              join fetch a.guide
              join fetch a.vehicle
             where a.assignmentDate <= :to and a.endDate >= :from
             order by a.assignmentDate asc
            """)
    List<Assignment> findOverlappingRangeDetailed(@Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<Assignment> findByBookingId(Long bookingId);

    /** Non-cancelled assignments for this guide whose date span overlaps the given window. */
    @Query("""
            select a from Assignment a
              join fetch a.booking b
             where a.guide.id = :guideId
               and a.status <> com.safari.tms.domain.enums.AssignmentStatus.CANCELLED
               and a.assignmentDate <= :end and a.endDate >= :start
               and (:excludeId is null or a.id <> :excludeId)
            """)
    List<Assignment> findGuideConflicts(@Param("guideId") Long guideId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end,
                                        @Param("excludeId") Long excludeId);

    /** Non-cancelled assignments for this vehicle whose date span overlaps the given window. */
    @Query("""
            select a from Assignment a
              join fetch a.booking b
             where a.vehicle.id = :vehicleId
               and a.status <> com.safari.tms.domain.enums.AssignmentStatus.CANCELLED
               and a.assignmentDate <= :end and a.endDate >= :start
               and (:excludeId is null or a.id <> :excludeId)
            """)
    List<Assignment> findVehicleConflicts(@Param("vehicleId") Long vehicleId,
                                          @Param("start") LocalDate start,
                                          @Param("end") LocalDate end,
                                          @Param("excludeId") Long excludeId);

    @Query("""
            select a.guide.id from Assignment a
             where a.status <> com.safari.tms.domain.enums.AssignmentStatus.CANCELLED
               and a.assignmentDate <= :end and a.endDate >= :start
               and (:excludeId is null or a.id <> :excludeId)
            """)
    List<Long> findBusyGuideIds(@Param("start") LocalDate start,
                                @Param("end") LocalDate end,
                                @Param("excludeId") Long excludeId);

    @Query("""
            select a.vehicle.id from Assignment a
             where a.status <> com.safari.tms.domain.enums.AssignmentStatus.CANCELLED
               and a.assignmentDate <= :end and a.endDate >= :start
               and (:excludeId is null or a.id <> :excludeId)
            """)
    List<Long> findBusyVehicleIds(@Param("start") LocalDate start,
                                  @Param("end") LocalDate end,
                                  @Param("excludeId") Long excludeId);

    @Query("""
            select count(a) from Assignment a
             where a.guide.id = :guideId
               and a.status <> com.safari.tms.domain.enums.AssignmentStatus.CANCELLED
               and a.assignmentDate between :from and :to
            """)
    long countGuideAssignments(@Param("guideId") Long guideId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    @Query("""
            select count(a) from Assignment a
             where a.vehicle.id = :vehicleId
               and a.status <> com.safari.tms.domain.enums.AssignmentStatus.CANCELLED
               and a.assignmentDate between :from and :to
            """)
    long countVehicleAssignments(@Param("vehicleId") Long vehicleId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
