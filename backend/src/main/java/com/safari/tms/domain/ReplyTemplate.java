package com.safari.tms.domain;

import com.safari.tms.domain.enums.ComplaintCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A canned response customer relations staff can drop into a case reply. The body may contain
 * {@code {{customerName}}}, {@code {{caseReference}}} and {@code {{bookingReference}}}, which are
 * filled in from the case when the template is inserted.
 */
@Entity
@Table(name = "reply_templates",
        uniqueConstraints = @UniqueConstraint(name = "uk_reply_templates_title", columnNames = "title"))
@Getter
@Setter
@NoArgsConstructor
public class ReplyTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    /** Complaint category this template suits best; {@code null} means any category. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ComplaintCategory category;

    @Column(length = 200)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false)
    private boolean active = true;

    /** How many replies have been posted from this template. */
    @Column(nullable = false)
    private Integer usageCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}
