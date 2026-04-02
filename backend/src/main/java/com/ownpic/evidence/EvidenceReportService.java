package com.ownpic.evidence;

import com.ownpic.detection.domain.DetectionScan;
import com.ownpic.detection.domain.DetectionScanRepository;
import com.ownpic.detection.domain.InternetDetectionResult;
import com.ownpic.detection.domain.InternetDetectionResultRepository;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.port.ImageStoragePort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class EvidenceReportService {

    private final DetectionScanRepository scanRepository;
    private final InternetDetectionResultRepository resultRepository;
    private final ImageRepository imageRepository;
    private final ImageStoragePort storagePort;
    private final PdfGenerator pdfGenerator;

    public EvidenceReportService(DetectionScanRepository scanRepository,
                                 InternetDetectionResultRepository resultRepository,
                                 ImageRepository imageRepository,
                                 ImageStoragePort storagePort,
                                 PdfGenerator pdfGenerator) {
        this.scanRepository = scanRepository;
        this.resultRepository = resultRepository;
        this.imageRepository = imageRepository;
        this.storagePort = storagePort;
        this.pdfGenerator = pdfGenerator;
    }

    public byte[] generatePdf(Long scanId, UUID userId) {
        return generate(scanId, userId, "pdf");
    }

    public byte[] generateDocx(Long scanId, UUID userId) {
        return generate(scanId, userId, "docx");
    }

    private byte[] generate(Long scanId, UUID userId, String format) {
        var scan = scanRepository.findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "스캔을 찾을 수 없습니다."));

        if (!"COMPLETED".equals(scan.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "완료된 스캔만 증거자료를 생성할 수 있습니다.");
        }

        var results = resultRepository.findByScanIdOrderByCreatedAt(scanId);
        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "도용 의심 건이 없습니다.");
        }

        // 원본 이미지 정보 수집
        Set<Long> sourceImageIds = new HashSet<>();
        for (var r : results) sourceImageIds.add(r.getSourceImageId());
        var sourceImages = imageRepository.findAllById(sourceImageIds);
        Map<Long, Image> imageMap = new HashMap<>();
        for (var img : sourceImages) imageMap.put(img.getId(), img);

        // HTML 데이터 구성
        Map<String, Object> data = new HashMap<>();
        data.put("documentId", UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        data.put("generatedAt", formatDateTime(Instant.now()));
        data.put("scanId", scanId);
        data.put("scanCompletedAt", formatDateTime(scan.getCompletedAt()));
        data.put("totalImages", scan.getTotalImages());
        data.put("matchesFound", results.size());

        // 원본 이미지 정보
        List<Map<String, Object>> originals = new ArrayList<>();
        for (var img : sourceImages) {
            Map<String, Object> orig = new HashMap<>();
            orig.put("id", img.getId());
            orig.put("name", img.getName());
            orig.put("width", img.getWidth());
            orig.put("height", img.getHeight());
            orig.put("fileSize", formatFileSize(img.getFileSize()));
            orig.put("sha256", img.getSha256());
            orig.put("createdAt", formatDateTime(img.getCreatedAt()));
            orig.put("watermarkPayload", img.getWatermarkPayload());
            originals.add(orig);
        }
        data.put("originals", originals);

        // 침해 결과
        List<Map<String, Object>> infringements = new ArrayList<>();
        for (var r : results) {
            Map<String, Object> inf = new HashMap<>();
            inf.put("sourceImageId", r.getSourceImageId());
            inf.put("sourceImageName", imageMap.containsKey(r.getSourceImageId())
                    ? imageMap.get(r.getSourceImageId()).getName() : "ID:" + r.getSourceImageId());
            inf.put("foundImageUrl", r.getFoundImageUrl());
            inf.put("sourcePageUrl", r.getSourcePageUrl());
            inf.put("sourcePageTitle", r.getSourcePageTitle());
            inf.put("searchEngine", r.getSearchEngine());
            inf.put("sscdSimilarity", r.getSscdSimilarity() != null
                    ? String.format("%.1f%%", r.getSscdSimilarity() * 100) : "-");
            inf.put("dinoSimilarity", r.getDinoSimilarity() != null
                    ? String.format("%.1f%%", r.getDinoSimilarity() * 100) : "-");
            inf.put("detectedAt", formatDateTime(r.getCreatedAt()));
            infringements.add(inf);
        }
        data.put("infringements", infringements);

        String html = pdfGenerator.renderTemplate("evidence-report", data);

        if ("docx".equals(format)) {
            return pdfGenerator.htmlToDocx(html, "증거자료");
        }
        return pdfGenerator.htmlToPdf(html);
    }

    private String formatDateTime(Instant instant) {
        if (instant == null) return "-";
        return DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm:ss")
                .withZone(ZoneId.of("Asia/Seoul"))
                .format(instant);
    }

    private String formatFileSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
