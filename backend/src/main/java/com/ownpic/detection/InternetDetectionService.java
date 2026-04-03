package com.ownpic.detection;

import com.ownpic.detection.adapter.seller.SellerInfoExtractor;
import com.ownpic.detection.domain.*;
import com.ownpic.detection.dto.DetectionScanResponse;
import com.ownpic.detection.port.*;
import com.ownpic.detection.port.InternetImageSearchPort.SearchResult;
import com.ownpic.detection.port.ReverseImageSearchPort.ReverseSearchResult;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.port.ImageStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

@Service
public class InternetDetectionService {

    private static final Logger log = LoggerFactory.getLogger(InternetDetectionService.class);

    private static final double SSCD_THRESHOLD = 0.30;
    private static final double DINO_THRESHOLD = 0.70;
    private static final double SSCD_MIN_FOR_DINO = 0.15;
    private static final int DOWNLOAD_TIMEOUT_MS = 10_000;
    private static final int MAX_SEARCH_RESULTS = 50;
    private static final int MAX_MATCHES_PER_IMAGE = 20;

    private final DetectionScanRepository scanRepository;
    private final InternetDetectionResultRepository resultRepository;
    private final ImageRepository imageRepository;
    private final InternetImageSearchPort searchPort;
    private final ReverseImageSearchPort reverseSearchPort;
    private final ExternalImageDownloadPort downloadPort;
    private final ImageStoragePort storagePort;
    private final SscdEmbeddingPort sscdPort;
    private final DinoEmbeddingPort dinoPort;
    private final ImageCaptionPort captionPort;
    private final SellerInfoExtractor sellerInfoExtractor;

    public InternetDetectionService(DetectionScanRepository scanRepository,
                                    InternetDetectionResultRepository resultRepository,
                                    ImageRepository imageRepository,
                                    InternetImageSearchPort searchPort,
                                    ReverseImageSearchPort reverseSearchPort,
                                    ExternalImageDownloadPort downloadPort,
                                    ImageStoragePort storagePort,
                                    SscdEmbeddingPort sscdPort,
                                    DinoEmbeddingPort dinoPort,
                                    ImageCaptionPort captionPort,
                                    SellerInfoExtractor sellerInfoExtractor) {
        this.scanRepository = scanRepository;
        this.resultRepository = resultRepository;
        this.imageRepository = imageRepository;
        this.searchPort = searchPort;
        this.reverseSearchPort = reverseSearchPort;
        this.downloadPort = downloadPort;
        this.storagePort = storagePort;
        this.sscdPort = sscdPort;
        this.dinoPort = dinoPort;
        this.captionPort = captionPort;
        this.sellerInfoExtractor = sellerInfoExtractor;
    }

    @Transactional
    public DetectionScanResponse startInternetScan(UUID userId) {
        List<Image> indexed = imageRepository.findByUserIdAndStatus(userId, ImageStatus.INDEXED);
        if (indexed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인덱싱된 이미지가 없습니다.");
        }

        var scan = new DetectionScan(userId, indexed.size());
        scan.setScanType("INTERNET");
        scan = scanRepository.save(scan);

        executeInternetScanAsync(scan.getId(), userId, indexed);

        return DetectionScanResponse.from(scan);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeInternetScanAsync(Long scanId, UUID userId, List<Image> images) {
        try {
            List<InternetDetectionResult> allResults = new ArrayList<>();
            int scannedCount = 0;

            for (Image image : images) {
                log.info("[Scan:{}] 이미지 {} 처리 시작 — name={}, gcsPath={}",
                        scanId, image.getId(), image.getName(), image.getGcsPath());

                float[] sourceSSCD = bytesToFloats(image.getEmbedding());
                float[] sourceDINO = bytesToFloats(image.getEmbeddingDino());
                log.info("[Scan:{}] 이미지 {} 임베딩 — SSCD={}, DINO={}",
                        scanId, image.getId(),
                        sourceSSCD != null ? sourceSSCD.length + "dim" : "null",
                        sourceDINO != null ? sourceDINO.length + "dim" : "null");

                List<InternetDetectionResult> imageResults = new ArrayList<>();

                // 1단계: 네이버 키워드 검색 (키워드가 있을 때만)
                String keyword = buildSearchKeyword(image);
                log.info("[Scan:{}] 이미지 {} 키워드 — '{}'", scanId, image.getId(), keyword);

                if (keyword != null && !keyword.isBlank()) {
                    List<SearchResult> keywordResults = searchPort.searchByKeyword(keyword, MAX_SEARCH_RESULTS);
                    log.info("[Scan:{}] 이미지 {} 네이버 검색 — {}건 발견 (keyword='{}')", scanId, image.getId(), keywordResults.size(), keyword);
                    for (int i = 0; i < Math.min(5, keywordResults.size()); i++) {
                        var sr = keywordResults.get(i);
                        log.info("[Scan:{}] 이미지 {} 네이버 결과[{}] — img={} page={} title={}",
                                scanId, image.getId(), i, sr.imageUrl(),
                                sr.sourcePageUrl() != null ? sr.sourcePageUrl() : "(없음)",
                                sr.title() != null ? sr.title() : "(없음)");
                    }

                    int naverMatches = 0;
                    for (SearchResult sr : keywordResults) {
                        InternetDetectionResult result = processSearchResult(
                                scanId, image.getId(), sr, sourceSSCD, sourceDINO, "NAVER");
                        if (result != null) {
                            imageResults.add(result);
                            naverMatches++;
                        }
                    }
                    log.info("[Scan:{}] 이미지 {} 네이버 매칭 — {}건 (임계값 통과)", scanId, image.getId(), naverMatches);
                } else {
                    log.info("[Scan:{}] 이미지 {} 키워드 없음 — 네이버 검색 스킵", scanId, image.getId());
                }

                // 2단계: 구글 리버스 이미지 검색 (항상 실행)
                if (image.getGcsPath() != null) {
                    byte[] imageBytes = storagePort.load(image.getGcsPath());
                    if (imageBytes != null && imageBytes.length > 0) {
                        log.info("[Scan:{}] 이미지 {} 구글 리버스 검색 시작 — {}KB",
                                scanId, image.getId(), imageBytes.length / 1024);

                        List<ReverseSearchResult> reverseResults =
                                reverseSearchPort.searchByImage(imageBytes, MAX_SEARCH_RESULTS);
                        long withPageUrl = reverseResults.stream().filter(r -> r.sourcePageUrl() != null).count();
                        log.info("[Scan:{}] 이미지 {} 구글 리버스 검색 — {}건 발견 (pageUrl 있는 결과: {}건)",
                                scanId, image.getId(), reverseResults.size(), withPageUrl);
                        for (int i = 0; i < Math.min(5, reverseResults.size()); i++) {
                            var rr = reverseResults.get(i);
                            log.info("[Scan:{}] 이미지 {} 구글 결과[{}] — img={} page={} title={}",
                                    scanId, image.getId(), i, rr.imageUrl(),
                                    rr.sourcePageUrl() != null ? rr.sourcePageUrl() : "(없음)",
                                    rr.title() != null ? rr.title() : "(없음)");
                        }

                        int googleMatches = 0;
                        for (ReverseSearchResult rr : reverseResults) {
                            SearchResult sr = new SearchResult(rr.imageUrl(), rr.sourcePageUrl(), rr.title());
                            InternetDetectionResult result = processSearchResult(
                                    scanId, image.getId(), sr, sourceSSCD, sourceDINO, "GOOGLE");
                            if (result != null) {
                                // Vision API bestGuessLabel / topEntity 저장
                                if (rr.bestGuessLabel() != null) result.setBestGuessLabel(rr.bestGuessLabel());
                                if (rr.topEntity() != null) result.setDetectedEntity(rr.topEntity());
                                imageResults.add(result);
                                googleMatches++;
                            }
                        }
                        log.info("[Scan:{}] 이미지 {} 구글 매칭 — {}건 (임계값 통과)", scanId, image.getId(), googleMatches);
                    } else {
                        log.warn("[Scan:{}] 이미지 {} 스토리지 로드 실패 — gcsPath={}", scanId, image.getId(), image.getGcsPath());
                    }
                } else {
                    log.warn("[Scan:{}] 이미지 {} gcsPath 없음 — 구글 리버스 검색 스킵", scanId, image.getId());
                }

                // SSCD 스코어 높은 순 정렬 → 상위 20개만 유지
                imageResults.sort((a, b) -> {
                    double scoreA = a.getSscdSimilarity() != null ? a.getSscdSimilarity() : 0;
                    double scoreB = b.getSscdSimilarity() != null ? b.getSscdSimilarity() : 0;
                    return Double.compare(scoreB, scoreA);
                });
                if (imageResults.size() > MAX_MATCHES_PER_IMAGE) {
                    imageResults = imageResults.subList(0, MAX_MATCHES_PER_IMAGE);
                }
                log.info("[Scan:{}] 이미지 {} 최종 — {}건 (상위 {}개 제한)",
                        scanId, image.getId(), imageResults.size(), MAX_MATCHES_PER_IMAGE);
                allResults.addAll(imageResults);

                scannedCount++;
                updateProgress(scanId, scannedCount);
            }

            resultRepository.saveAll(allResults);

            // 판매자 정보 비동기 추출 (탐지 속도에 영향 없음)
            if (!allResults.isEmpty()) {
                log.info("[Scan:{}] 판매자 정보 추출 시작 — {}건", scanId, allResults.size());
                for (var r : allResults) {
                    log.info("[Scan:{}] 판매자 추출 대상 — foundImg={} pageUrl={} engine={}",
                            scanId,
                            r.getFoundImageUrl().length() > 80 ? r.getFoundImageUrl().substring(0, 80) + "..." : r.getFoundImageUrl(),
                            r.getSourcePageUrl() != null ? r.getSourcePageUrl() : "(없음)",
                            r.getSearchEngine());
                }
                sellerInfoExtractor.extractAndSave(allResults);
            }

            completeScan(scanId, allResults.size());

            log.info("[Scan:{}] ===== 스캔 완료 ===== {}개 이미지 검사, {}건 도용 의심",
                    scanId, images.size(), allResults.size());
        } catch (Exception e) {
            log.error("Internet scan {} failed", scanId, e);
            failScan(scanId);
        }
    }

    private InternetDetectionResult processSearchResult(
            Long scanId, Long sourceImageId, SearchResult sr,
            float[] sourceSSCD, float[] sourceDINO, String searchEngine) {

        // 1. 외부 이미지 다운로드
        log.info("[Scan:{}][{}] 다운로드 시도: {}", scanId, searchEngine, sr.imageUrl());
        byte[] foundBytes = downloadPort.download(sr.imageUrl(), DOWNLOAD_TIMEOUT_MS);
        if (foundBytes == null) {
            log.info("[Scan:{}][{}] 다운로드 실패: {}", scanId, searchEngine, sr.imageUrl());
            return null;
        }
        log.info("[Scan:{}][{}] 다운로드 완료: {}KB — pageUrl={}, title={}",
                scanId, searchEngine, foundBytes.length / 1024, sr.sourcePageUrl(), sr.title());

        // 2. 임베딩 생성 + 코사인 유사도 비교
        Double sscdSim = null;
        Double dinoSim = null;

        try {
            if (sourceSSCD != null) {
                float[] foundSSCD = sscdPort.generateEmbedding(foundBytes);
                if (foundSSCD != null) {
                    sscdSim = cosineSimilarity(sourceSSCD, foundSSCD);
                }
            }

            if (sourceDINO != null) {
                float[] foundDINO = dinoPort.generateEmbedding(foundBytes);
                if (foundDINO != null) {
                    dinoSim = cosineSimilarity(sourceDINO, foundDINO);
                }
            }
        } catch (Exception e) {
            log.info("[Scan:{}][{}] 임베딩 실패: {} — {}", scanId, searchEngine, sr.imageUrl(), e.getMessage());
            return null;
        }

        // 3. 듀얼 판정 — SSCD 메인, DINO 보조 (bg_swap 등 변형 탐지)
        boolean sscdMatch = sscdSim != null && sscdSim >= SSCD_THRESHOLD;
        boolean dinoAssist = sscdSim != null && sscdSim >= SSCD_MIN_FOR_DINO
                && dinoSim != null && dinoSim >= DINO_THRESHOLD;

        String sscdStr = sscdSim != null ? String.format("%.1f%%", sscdSim * 100) : "null";
        String dinoStr = dinoSim != null ? String.format("%.1f%%", dinoSim * 100) : "null";

        if (!sscdMatch && !dinoAssist) {
            log.info("[Scan:{}][{}] 임계값 미달 — SSCD={} DINO={} — {}",
                    scanId, searchEngine, sscdStr, dinoStr, sr.imageUrl());
            return null; // 임계값 미달 → 스킵
        }

        log.info("[Scan:{}][{}] ★ 도용 의심 — SSCD={} DINO={} — imgUrl={} pageUrl={} title={}",
                scanId, searchEngine, sscdStr, dinoStr,
                sr.imageUrl(),
                sr.sourcePageUrl() != null ? sr.sourcePageUrl() : "(없음)",
                sr.title() != null ? sr.title() : "(없음)");

        return new InternetDetectionResult(
                scanId, sourceImageId,
                sr.imageUrl(), sr.sourcePageUrl(), sr.title(),
                sscdSim, dinoSim, "INFRINGEMENT", searchEngine);
    }

    /**
     * 이미지의 검색 키워드를 결정한다.
     * 우선순위: 사용자 입력 키워드 → AI 이미지 캡션 → null (리버스 이미지 검색으로 fallback)
     */
    private String buildSearchKeyword(Image image) {
        // 1. 사용자가 직접 입력한 키워드
        if (image.getKeywords() != null && !image.getKeywords().isBlank()) {
            return image.getKeywords();
        }

        // 2. AI Vision으로 이미지 내용 추론하여 키워드 생성
        if (image.getGcsPath() != null) {
            try {
                byte[] imageBytes = storagePort.load(image.getGcsPath());
                if (imageBytes != null && imageBytes.length > 0) {
                    String caption = captionPort.generateKeywords(imageBytes);
                    if (caption != null && !caption.isBlank()) {
                        log.info("AI-generated keywords for image {}: {}", image.getId(), caption);
                        image.setKeywords(caption);
                        imageRepository.save(image);
                        return caption;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to generate AI keywords for image {}: {}", image.getId(), e.getMessage());
            }
        }

        // 키워드 없으면 null → 키워드 검색 스킵, 리버스 이미지 검색으로 진행
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long scanId, int scannedImages) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setScannedImages(scannedImages);
            scanRepository.save(scan);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeScan(Long scanId, int matchesFound) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setStatus("COMPLETED");
            scan.setMatchesFound(matchesFound);
            scan.setScannedImages(scan.getTotalImages());
            scan.setCompletedAt(Instant.now());
            scanRepository.save(scan);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failScan(Long scanId) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setStatus("FAILED");
            scan.setCompletedAt(Instant.now());
            scanRepository.save(scan);
        });
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    private static float[] bytesToFloats(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        float[] result = new float[bytes.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.getFloat();
        }
        return result;
    }
}
