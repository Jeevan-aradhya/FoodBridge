package com.foodbridge.service;

import com.foodbridge.dto.NotificationDto;
import com.foodbridge.entity.Notification;
import com.foodbridge.entity.User;
import com.foodbridge.exception.ResourceNotFoundException;
import com.foodbridge.repository.NotificationRepository;
import com.foodbridge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-app notifications (always) plus optional e-mail (only when foodbridge.mail.enabled=true). */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;
    private final UserRepository users;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${foodbridge.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${foodbridge.mail.from:no-reply@foodbridge.test}")
    private String mailFrom;

    @Transactional
    public void notify(User user, String title, String message, String link) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
        n.setLink(link);
        repo.save(n);
        sendEmail(user, title, message);
    }

    /** Sends the same notification to every user with the given role (used for admin alerts). */
    @Transactional
    public void notifyRole(com.foodbridge.enums.Role role, String title, String message, String link) {
        for (User u : users.findByRole(role)) {
            notify(u, title, message, link);
        }
    }

    private void sendEmail(User user, String title, String message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!mailEnabled || sender == null) {
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(mailFrom);
            mail.setTo(user.getEmail());
            mail.setSubject("[FoodBridge] " + title);
            mail.setText(message);
            sender.send(mail);
        } catch (Exception ex) {
            // E-mail must never break the main action.
            log.warn("Could not send e-mail notification: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(Long userId, int page, int size) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(n -> new NotificationDto(n.getId(), n.getTitle(), n.getMessage(), n.getLink(), n.isSeen(), n.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repo.countByUserIdAndSeenFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification n = repo.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        n.setSeen(true);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repo.markAllRead(userId);
    }
}
