package com.ownpic.image.controller;

import com.ownpic.image.StoreService;
import com.ownpic.image.dto.ConnectPlatformRequest;
import com.ownpic.image.dto.PlatformConnectionResponse;
import com.ownpic.shared.dto.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping("/connect")
    public ResponseEntity<PlatformConnectionResponse> connect(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ConnectPlatformRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeService.connect(userId, request.platform()));
    }

    @GetMapping
    public ResponseEntity<List<PlatformConnectionResponse>> getConnections(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(storeService.getConnections(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disconnect(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long id
    ) {
        storeService.disconnect(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<Void> syncProducts(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long id
    ) {
        storeService.syncProducts(userId, id);
        return ResponseEntity.ok().build();
    }
}
