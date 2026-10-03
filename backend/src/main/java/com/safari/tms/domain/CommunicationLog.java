package com.safari.tms.domain;

import com.safari.tms.domain.enums.CommunicationDirection;
import com.safari.tms.domain.enums.CommunicationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "communication_logs", indexes = {
        @Index(name = "ix_comm_customer", columnList = "customer_id"),
        @Index(name = "ix_comm_complaint", columnList = "complaint_id")
})
@Getter
@Setter
@NoArgsConstructor
public class CommunicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id")
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommunicationType type = CommunicationType.IN_APP_NOTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationDirection direction = CommunicationDirection.OUTBOUND;

    @Column(length = 200)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
