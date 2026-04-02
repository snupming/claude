package com.ownpic.evidence;

import com.ownpic.detection.domain.DetectionScanRepository;
import com.ownpic.detection.domain.InternetDetectionResultRepository;
import com.ownpic.evidence.dto.CertifiedLetterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CertifiedLetterService {

    private final DetectionScanRepository scanRepository;
    private final InternetDetectionResultRepository resultRepository;
    private final PdfGenerator pdfGenerator;

    public CertifiedLetterService(DetectionScanRepository scanRepository,
                                  InternetDetectionResultRepository resultRepository,
                                  PdfGenerator pdfGenerator) {
        this.scanRepository = scanRepository;
        this.resultRepository = resultRepository;
        this.pdfGenerator = pdfGenerator;
    }

    public byte[] generatePdf(Long scanId, UUID userId, CertifiedLetterRequest req) {
        return generate(scanId, userId, req, "pdf");
    }

    public byte[] generateDocx(Long scanId, UUID userId, CertifiedLetterRequest req) {
        return generate(scanId, userId, req, "docx");
    }

    private byte[] generate(Long scanId, UUID userId, CertifiedLetterRequest req, String format) {
        var scan = scanRepository.findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        var results = resultRepository.findByScanIdOrderByCreatedAt(scanId);
        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "도용 의심 건이 없습니다.");
        }

        Map<String, Object> data = new HashMap<>();
        // 발신인 정보 (사용자 입력)
        data.put("senderName", req.senderName());
        data.put("senderAddress", req.senderAddress());
        data.put("senderPhone", req.senderPhone());

        // 수신인 정보 (사용자 입력)
        data.put("recipientName", req.recipientName());
        data.put("recipientAddress", req.recipientAddress());

        // 저작물 정보 (사용자 입력)
        data.put("workTitle", req.workTitle());
        data.put("creationDate", req.creationDate());
        data.put("firstPublicationInfo", req.firstPublicationInfo() != null ? req.firstPublicationInfo() : "");
        data.put("copyrightRegNumber", req.copyrightRegNumber() != null ? req.copyrightRegNumber() : "");

        // 손해배상 (사용자 입력)
        data.put("damageAmount", req.damageAmount());
        data.put("bankName", req.bankName() != null ? req.bankName() : "");
        data.put("accountNumber", req.accountNumber() != null ? req.accountNumber() : "");
        data.put("accountHolder", req.accountHolder() != null ? req.accountHolder() : "");
        data.put("complianceDays", req.complianceDays());

        // 침해 사실 (자동 — 탐지 데이터)
        List<Map<String, String>> infringements = new ArrayList<>();
        for (var r : results) {
            Map<String, String> inf = new HashMap<>();
            inf.put("url", r.getFoundImageUrl());
            inf.put("pageUrl", r.getSourcePageUrl() != null ? r.getSourcePageUrl() : "");
            inf.put("pageTitle", r.getSourcePageTitle() != null ? r.getSourcePageTitle() : "");
            inf.put("similarity", r.getSscdSimilarity() != null
                    ? String.format("%.1f%%", r.getSscdSimilarity() * 100) : "-");
            inf.put("detectedAt", formatDateTime(r.getCreatedAt()));
            infringements.add(inf);
        }
        data.put("infringements", infringements);

        // 발송일자
        data.put("sendDate", DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")
                .withZone(ZoneId.of("Asia/Seoul")).format(Instant.now()));

        String html = pdfGenerator.renderTemplate("certified-letter", data);

        if ("docx".equals(format)) {
            return pdfGenerator.htmlToDocx(html, "내용증명");
        }
        return pdfGenerator.htmlToPdf(html);
    }

    private String formatDateTime(Instant instant) {
        if (instant == null) return "-";
        return DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")
                .withZone(ZoneId.of("Asia/Seoul")).format(instant);
    }
}
