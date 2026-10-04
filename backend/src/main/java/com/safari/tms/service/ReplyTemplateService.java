package com.safari.tms.service.backend.src.main.java.com.safari.tms.service;

import com.safari.tms.domain.ReplyTemplate;
import com.safari.tms.domain.User;
import com.safari.tms.dto.RelationsDtos.ReplyTemplateRequest;
import com.safari.tms.dto.RelationsDtos.ReplyTemplateView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.ReplyTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Canned responses for customer relations staff. */
@Service
public class ReplyTemplateService {

    /** The only placeholders a template may use; they are filled in from the case on insert. */
    public static final List<String> PLACEHOLDERS = List.of("customerName", "caseReference", "bookingReference");

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z]+)\\s*}}");

    private final ReplyTemplateRepository templates;

    public ReplyTemplateService(ReplyTemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional(readOnly = true)
    public List<ReplyTemplateView> findAll(boolean activeOnly) {
        return templates.findAllDetailed().stream()
                .filter(t -> !activeOnly || t.isActive())
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReplyTemplateView findOne(Long id) {
        return view(require(id));
    }

    @Transactional
    public ReplyTemplateView create(ReplyTemplateRequest request, User actor) {
        validate(request, null);
        ReplyTemplate template = new ReplyTemplate();
        apply(template, request);
        template.setCreatedBy(actor);
        return view(templates.save(template));
    }

    @Transactional
    public ReplyTemplateView update(Long id, ReplyTemplateRequest request) {
        ReplyTemplate template = require(id);
        validate(request, id);
        apply(template, request);
        template.setUpdatedAt(Instant.now());
        return view(templates.save(template));
    }

    /** Replies already sent keep their text, so a template can always be removed. */
    @Transactional
    public void delete(Long id) {
        templates.delete(require(id));
    }

    /** Counts one more reply posted from this template. Unknown ids are ignored. */
    @Transactional
    public void recordUse(Long id) {
        templates.findById(id).ifPresent(t -> {
            t.setUsageCount(t.getUsageCount() + 1);
            templates.save(t);
        });
    }

    /* ------------------------------------------------------------ helpers */

    private void validate(ReplyTemplateRequest request, Long selfId) {
        String title = request.title().trim();
        templates.findByTitleIgnoreCase(title)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("A template called '" + existing.getTitle() + "' already exists.");
                });

        Set<String> unknown = new TreeSet<>();
        collectTokens(request.subject(), unknown);
        collectTokens(request.body(), unknown);
        unknown.removeAll(PLACEHOLDERS);
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("Unknown placeholder "
                    + unknown.stream().map(u -> "{{" + u + "}}").collect(Collectors.joining(", "))
                    + ". You can use {{customerName}}, {{caseReference}} and {{bookingReference}}.");
        }
    }

    private void apply(ReplyTemplate template, ReplyTemplateRequest request) {
        template.setTitle(request.title().trim());
        template.setCategory(request.category());
        template.setSubject(request.subject() == null || request.subject().isBlank() ? null : request.subject().trim());
        template.setBody(request.body().trim());
        if (request.active() != null) {
            template.setActive(request.active());
        }
    }

    private void collectTokens(String text, Set<String> into) {
        if (text == null) return;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            into.add(m.group(1));
        }
    }

    private ReplyTemplateView view(ReplyTemplate t) {
        Set<String> used = new TreeSet<>();
        collectTokens(t.getSubject(), used);
        collectTokens(t.getBody(), used);
        return new ReplyTemplateView(
                t.getId(), t.getTitle(), t.getCategory(), t.getSubject(), t.getBody(),
                t.isActive(), t.getUsageCount(), List.copyOf(used),
                t.getCreatedBy() == null ? null : t.getCreatedBy().getFullName(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private ReplyTemplate require(Long id) {
        return templates.findDetailById(id).orElseThrow(() -> ApiException.notFound("Reply template", id));
    }
}
