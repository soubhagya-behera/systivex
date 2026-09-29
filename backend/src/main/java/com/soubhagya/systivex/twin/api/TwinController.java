package com.soubhagya.systivex.twin.api;

import com.soubhagya.systivex.twin.service.TwinService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal Phase 1 twin surface: entity and relationship CRUD used to prove
 * the persistence layer. No filtering, pagination, or traversal yet.
 */
@RestController
@RequestMapping("/api/v1/twin")
public class TwinController {

    private final TwinService twinService;

    public TwinController(TwinService twinService) {
        this.twinService = twinService;
    }

    @PostMapping("/entities")
    @ResponseStatus(HttpStatus.CREATED)
    public SystemEntityResponse createEntity(@Valid @RequestBody CreateSystemEntityRequest request) {
        return SystemEntityResponse.from(twinService.createEntity(request));
    }

    @GetMapping("/entities/{id}")
    public SystemEntityResponse getEntity(@PathVariable UUID id) {
        return SystemEntityResponse.from(twinService.getEntity(id));
    }

    @GetMapping("/entities")
    public List<SystemEntityResponse> listEntities() {
        return twinService.listEntities().stream().map(SystemEntityResponse::from).toList();
    }

    @PostMapping("/relationships")
    @ResponseStatus(HttpStatus.CREATED)
    public SystemRelationshipResponse createRelationship(
            @Valid @RequestBody CreateSystemRelationshipRequest request) {
        return SystemRelationshipResponse.from(twinService.createRelationship(request));
    }

    @GetMapping("/relationships/{id}")
    public SystemRelationshipResponse getRelationship(@PathVariable UUID id) {
        return SystemRelationshipResponse.from(twinService.getRelationship(id));
    }

    @GetMapping("/relationships")
    public List<SystemRelationshipResponse> listRelationships() {
        return twinService.listRelationships().stream()
                .map(SystemRelationshipResponse::from)
                .toList();
    }
}
