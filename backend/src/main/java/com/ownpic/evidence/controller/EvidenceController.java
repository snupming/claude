package com.ownpic.evidence.controller;

import com.ownpic.evidence.CertifiedLetterService;
import com.ownpic.evidence.EvidenceReportService;
import com.ownpic.evidence.dto.CertifiedLetterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {

    private final EvidenceReportService reportService;
    private final CertifiedLetterService letterService;

    public EvidenceController(EvidenceReportService reportService, CertifiedLetterService letterService) {
        this.reportService = reportService;
        this.letterService = letterService;
    }

    @GetMapping("/{scanId}/report.pdf")
    public ResponseEntity<byte[]> getReportPdf(@PathVariable Long scanId,
                                                @AuthenticationPrincipal UUID userId) {
        byte[] pdf = reportService.generatePdf(scanId, userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=evidence_report_%d.pdf".formatted(scanId))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{scanId}/report.docx")
    public ResponseEntity<byte[]> getReportDocx(@PathVariable Long scanId,
                                                 @AuthenticationPrincipal UUID userId) {
        byte[] docx = reportService.generateDocx(scanId, userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=evidence_report_%d.docx".formatted(scanId))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    @PostMapping("/{scanId}/letter.pdf")
    public ResponseEntity<byte[]> getLetterPdf(@PathVariable Long scanId,
                                                @AuthenticationPrincipal UUID userId,
                                                @Valid @RequestBody CertifiedLetterRequest request) {
        byte[] pdf = letterService.generatePdf(scanId, userId, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certified_letter_%d.pdf".formatted(scanId))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/{scanId}/letter.docx")
    public ResponseEntity<byte[]> getLetterDocx(@PathVariable Long scanId,
                                                 @AuthenticationPrincipal UUID userId,
                                                 @Valid @RequestBody CertifiedLetterRequest request) {
        byte[] docx = letterService.generateDocx(scanId, userId, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certified_letter_%d.docx".formatted(scanId))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }
}
