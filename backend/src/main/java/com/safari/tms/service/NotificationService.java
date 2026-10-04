package com.safari.tms.service;

import com.safari.tms.domain.NotificationLog;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.NotificationChannel;
import com.safari.tms.domain.enums.NotificationStatus;
import com.safari.tms.repo.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Stand-in for a real email/SMS provider: every outbound message is persisted to
 * {@code notification_logs} so staff can see exactly what a customer was told and when.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationLogRepository notifications;

    public NotificationService(NotificationLogRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public NotificationLog email(User recipient, String subject, String body, String relatedEntity, Long relatedId) {
        return send(recipient, NotificationChannel.EMAIL, recipient.getEmail(), subject, body, relatedEntity, relatedId);
    }

    @Transactional
    public NotificationLog sms(User recipient, String body, String relatedEntity, Long relatedId) {
        String number = recipient.getPhone();
        if (number == null || number.isBlank()) {
            return null;
        }
        return send(recipient, NotificationChannel.SMS, number, null, body, relatedEntity, relatedId);
    }

    private NotificationLog send(User recipient, NotificationChannel channel, String address,
                                 String subject, String body, String relatedEntity, Long relatedId) {
        NotificationLog entry = new NotificationLog();
        entry.setRecipient(recipient);
        entry.setChannel(channel);
        entry.setRecipientAddress(address);
        entry.setSubject(subject);
        entry.setBody(body);
        entry.setRelatedEntity(relatedEntity);
        entry.setRelatedId(relatedId);
        // No external provider is wired in, so delivery is recorded as immediately successful.
        entry.setStatus(NotificationStatus.SENT);
        entry.setSentAt(Instant.now());

        NotificationLog saved = notifications.save(entry);
        log.info("[{}] to {} :: {}", channel, address, subject == null ? body : subject);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> findAll() {
        return notifications.findAllDetailed();
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> findForUser(Long userId) {
        return notifications.findForUser(userId);
    }
}
