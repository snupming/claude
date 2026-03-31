package com.ownpic.detection.controller;

import com.ownpic.detection.DetectionService;
import com.ownpic.detection.InternetDetectionService;
import com.ownpic.detection.dto.DetectionScanDetailResponse;
import com.ownpic.detection.dto.DetectionScanResponse;
import com.ownpic.shared.dto.ApiPaths;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/detections")
public class DetectionController {

    private final DetectionService detectionService;
    private final InternetDetectionService internetDetectionService;

    public DetectionController(DetectionService detectionService,
                               InternetDetectionService internetDetectionService) {
        this.detectionService = detectionService;
        this.internetDetectionService = internetDetectionService;
    }

    @PostMapping("/internet-scan")
    public ResponseEntity<DetectionScanResponse> startInternetScan(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(internetDetectionService.startInternetScan(userId));
    }

    @PostMapping("/scan")
    public ResponseEntity<DetectionScanResponse> startScan(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(detectionService.startScan(userId));
    }

    @GetMapping("/scans")
    public ResponseEntity<Page<DetectionScanResponse>> getScans(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(detectionService.getScans(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/scans/{id}")
    public ResponseEntity<DetectionScanDetailResponse> getScanDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(detectionService.getScanDetail(userId, id));
    }
}
