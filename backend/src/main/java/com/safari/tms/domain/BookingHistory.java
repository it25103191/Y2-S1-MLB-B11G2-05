package com.safari.tms.domain;

import com.safari.tms.domain.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** One entry on a booking's timeline: what changed, who changed it and when. */
@Entity
@Table(name = "booking_history", indexes = @Index(name = "ix_booking_history_booking", columnList = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
public class BookingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Deleting an unpaid booking deletes its timeline too (ON DELETE CASCADE in the database). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Booking booking;

    /** The booking event that produced this entry: CREATED, UPDATED, CANCELLED or STATUS_CHANGED. */
    @Column(nullable = false, length = 20)
    private String event;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 500)
    private String detail;

    /** The booking's status straight after the change. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    /** Who made the change; {@code null} for automatic changes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public BookingHistory(Booking booking, String event, String title, String detail, User actor) {
        this.booking = booking;
        this.event = event;
        this.title = title;
        this.detail = detail;
        this.status = booking.getStatus();
        this.actor = actor;
    }
}
