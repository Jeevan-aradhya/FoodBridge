package com.foodbridge.service;

import com.foodbridge.dto.AuditDto;
import com.foodbridge.entity.AuditLog;
import com.foodbridge.entity.User;
import com.foodbridge.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes the audit trail (who did what, when). Runs inside the caller's transaction. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    @Transactional
    public void log(User actor, String action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        if (actor != null) {
            log.setActorId(actor.getId());
            log.setActorEmail(actor.getEmail());
        } else {
            log.setActorEmail("system");
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details == null ? null : (details.length() > 500 ? details.substring(0, 500) : details));
        repo.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditDto> list(int page, int size) {
        return repo.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(a -> new AuditDto(a.getId(), a.getActorEmail(), a.getAction(), a.getEntityType(),
                        a.getEntityId(), a.getDetails(), a.getCreatedAt()));
    }
}
