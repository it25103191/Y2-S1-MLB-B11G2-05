package com.safari.tms.controller;

import com.safari.tms.dto.RelationsDtos.ReplyTemplateRequest;
import com.safari.tms.dto.RelationsDtos.ReplyTemplateView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AuthService;
import com.safari.tms.service.ReplyTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reply-templates")
@PreAuthorize(Roles.RELATIONS)
public class ReplyTemplateController {

    private final ReplyTemplateService templateService;
    private final AuthService authService;

    public ReplyTemplateController(ReplyTemplateService templateService, AuthService authService) {
        this.templateService = templateService;
        this.authService = authService;
    }

    @GetMapping
    public List<ReplyTemplateView> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return templateService.findAll(activeOnly);
    }

    @GetMapping("/{id}")
    public ReplyTemplateView detail(@PathVariable Long id) {
        return templateService.findOne(id);
    }

    @PostMapping
    public ResponseEntity<ReplyTemplateView> create(@Valid @RequestBody ReplyTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.create(request, authService.requireCurrentUser()));
    }

    @PutMapping("/{id}")
    public ReplyTemplateView update(@PathVariable Long id, @Valid @RequestBody ReplyTemplateRequest request) {
        return templateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
