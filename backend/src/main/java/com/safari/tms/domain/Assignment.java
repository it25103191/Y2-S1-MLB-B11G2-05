package com.safari.tms.domain;

import com.safari.tms.domain.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_assignments_booking", columnNames = "booking_id"),
        indexes = {
                @Index(name = "ix_assignments_date", columnList = "assignmentDate"),
                @Index(name = "ix_assignments_guide", columnList = "guide_id"),
                @Index(name = "ix_assignments_vehicle", columnList = "vehicle_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guide_id", nullable = false)
    private Guide guide;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false)
    private LocalDate assignmentDate;

    @Column(nullable = false)
    private Integer durationDays = 1;

    /** Inclusive last day of the assignment. Persisted so overlap detection runs in SQL. */
    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.SCHEDULED;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean manualOverride = false;

    @Column(length = 500)
    private String overrideReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    private User assignedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Recomputes {@link #endDate} from the start date and duration. */
    public void syncEndDate() {
        int days = durationDays == null || durationDays < 1 ? 1 : durationDays;
        this.endDate = assignmentDate == null ? null : assignmentDate.plusDays(days - 1L);
    }

    @PrePersist
    @PreUpdate
    void beforeSave() {
        syncEndDate();
    }
}
