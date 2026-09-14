package com.safari.tms.domain;

import com.safari.tms.domain.enums.NotificationChannel;
import com.safari.tms.domain.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notification_logs", indexes = @Index(name = "ix_notif_recipient", columnList = "recipient_id"))
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel = NotificationChannel.EMAIL;

    @Column(nullable = false, length = 200)
    private String recipientAddress;

    @Column(length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.QUEUED;

    @Column(length = 60)
    private String relatedEntity;

    private Long relatedId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant sentAt;
}
