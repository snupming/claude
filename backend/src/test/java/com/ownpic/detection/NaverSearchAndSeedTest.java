package com.ownpic.detection;

import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.detection.domain.DetectionScanRepository;
import com.ownpic.detection.domain.InternetDetectionResultRepository;
import com.ownpic.detection.port.InternetImageSearchPort;
import com.ownpic.detection.port.InternetImageSearchPort.SearchResult;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.port.ImageStoragePort;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네이버 이미지 검색 API로 상품 이미지를 수집하고,
 * dhdldgkgk@naver.com 계정에 등록하여 인터넷 탐지 테스트를 준비한다.
 *
 * 실행: gradle test --tests "*.NaverSearchAndSeedTest" -Dspring.profiles.active=naver
 *
 * ⚠️ 이 테스트는 실제 DB(Supabase)와 네이버 API를 사용합니다.
 */
@SpringBootTest
@ActiveProfiles("naver")
class NaverSearchAndSeedTest {

    private static final Logger log = LoggerFactory.getLogger(NaverSearchAndSeedTest.class);

    private static final String TEST_EMAIL = "dhdldgkgk@naver.com";
    private static final String TEST_NAME = "테스트유저";
    private static final String TEST_PASSWORD = "test1234!";

    // 검색할 상품 키워드들
    private static final List<String> SEARCH_KEYWORDS = List.of(
            "나이키 에어맥스 신발",
            "아이폰 16 프로",
            "스타벅스 텀블러"
    );

    @Autowired UserRepository userRepository;
    @Autowired ImageRepository imageRepository;
    @Autowired ImageStoragePort storagePort;
    @Autowired InternetImageSearchPort searchPort;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DetectionScanRepository scanRepository;
    @Autowired InternetDetectionResultRepository resultRepository;
    @Autowired InternetDetectionService internetDetectionService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 1단계: 네이버 API로 상품 이미지 검색 + 결과 확인
     */
    @Test
    void step1_naverSearchReturnsResults() {
        for (String keyword : SEARCH_KEYWORDS) {
            List<SearchResult> results = searchPort.searchByKeyword(keyword, 5);

            log.info("=== [{}] 검색 결과: {}건 ===", keyword, results.size());
            for (SearchResult r : results) {
                log.info("  이미지: {}", r.imageUrl());
                log.info("  출처: {}", r.sourcePageUrl());
                log.info("  제목: {}", r.title());
                log.info("  ---");
            }

            assertThat(results).isNotEmpty()
                    .as("'%s' 키워드로 최소 1건 이상 검색되어야 함", keyword);
        }
    }

    /**
     * 2단계: 유저 생성 + 이미지 다운로드/등록 + 탐지 스캔
     */
    @Test
    void step2_seedImagesAndRunDetection() throws Exception {
        // 1. 테스트 유저 생성 (이미 있으면 재사용)
        User user = userRepository.findByEmail(TEST_EMAIL).orElseGet(() -> {
            User newUser = new User();
            newUser.setName(TEST_NAME);
            newUser.setEmail(TEST_EMAIL);
            newUser.setPassword(passwordEncoder.encode(TEST_PASSWORD));
            newUser.setImageQuota(100);
            return userRepository.save(newUser);
        });
        log.info("✅ 유저 준비완료: {} (ID: {})", user.getEmail(), user.getId());

        // 2. 키워드별로 네이버 검색 → 이미지 다운로드 → DB 등록
        List<Image> seededImages = new ArrayList<>();

        for (String keyword : SEARCH_KEYWORDS) {
            List<SearchResult> results = searchPort.searchByKeyword(keyword, 3);
            log.info("[{}] {}건 검색됨 → 다운로드 시작", keyword, results.size());

            for (SearchResult sr : results) {
                try {
                    Image image = downloadAndSeedImage(user, sr, keyword);
                    if (image != null) {
                        seededImages.add(image);
                        log.info("  ✅ 등록: {} (ID: {})", image.getName(), image.getId());
                    }
                } catch (Exception e) {
                    log.warn("  ❌ 실패: {} - {}", sr.imageUrl(), e.getMessage());
                }
            }
        }

        log.info("=== 총 {}개 이미지 등록 완료 ===", seededImages.size());
        assertThat(seededImages).isNotEmpty();

        // 3. 인터넷 탐지 스캔 실행
        log.info("=== 인터넷 탐지 스캔 시작 ===");
        var scanResponse = internetDetectionService.startInternetScan(user.getId());
        log.info("스캔 생성: ID={}, 상태={}, 대상={}개 이미지",
                scanResponse.id(), scanResponse.status(), scanResponse.totalImages());

        // 비동기 스캔 대기 (최대 5분)
        long scanId = scanResponse.id();
        for (int i = 0; i < 60; i++) {
            Thread.sleep(5000);
            var scan = scanRepository.findById(scanId).orElseThrow();
            log.info("  스캔 진행중: {}/{} ({} matches) [{}]",
                    scan.getScannedImages(), scan.getTotalImages(),
                    scan.getMatchesFound(), scan.getStatus());

            if ("COMPLETED".equals(scan.getStatus()) || "FAILED".equals(scan.getStatus())) {
                break;
            }
        }

        // 4. 결과 출력
        var scan = scanRepository.findById(scanId).orElseThrow();
        log.info("=== 스캔 완료: {} ===", scan.getStatus());
        log.info("  총 이미지: {}", scan.getTotalImages());
        log.info("  스캔된 이미지: {}", scan.getScannedImages());
        log.info("  매칭 수: {}", scan.getMatchesFound());

        var detectionResults = resultRepository.findByScanIdOrderByCreatedAt(scanId);
        for (var result : detectionResults) {
            log.info("  [매칭] 이미지ID={}, 엔진={}, SSCD={}, DINO={}, URL={}",
                    result.getSourceImageId(),
                    result.getSearchEngine(),
                    String.format("%.4f", result.getSscdSimilarity()),
                    String.format("%.4f", result.getDinoSimilarity()),
                    result.getFoundImageUrl());
        }
    }

    /**
     * 이미지 다운로드 → 저장 → DB 등록 (INDEXED 상태, 더미 임베딩)
     */
    private Image downloadAndSeedImage(User user, SearchResult sr, String keyword) throws Exception {
        // 이미지 다운로드
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sr.imageUrl()))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) return null;

        byte[] imageBytes = response.body();
        if (imageBytes.length < 1000 || imageBytes.length > 10_000_000) return null;

        // SHA256 계산
        String sha256 = sha256Hex(imageBytes);

        // 중복 체크
        if (imageRepository.existsByUserIdAndSha256(user.getId(), sha256)) {
            log.debug("  중복 스킵: {}", sr.imageUrl());
            return null;
        }

        // 파일 저장
        String storagePath = storagePort.save(user.getId(), imageBytes);

        // 더미 임베딩 생성 (512 floats for SSCD, 384 floats for DINOv2)
        byte[] sscdEmbedding = generateDummyEmbedding(512);
        byte[] dinoEmbedding = generateDummyEmbedding(384);

        // Image 엔티티 생성
        Image image = new Image();
        image.setUser(user);
        image.setName(cleanTitle(sr.title(), keyword));
        image.setSha256(sha256);
        image.setFileSize(imageBytes.length);
        image.setWidth(300);  // 실제 크기 대신 플레이스홀더
        image.setHeight(300);
        image.setStatus(ImageStatus.INDEXED);
        image.setGcsPath(storagePath);
        image.setEmbedding(sscdEmbedding);
        image.setEmbeddingDino(dinoEmbedding);
        image.setKeywords(keyword);
        image.setIndexedAt(Instant.now());

        return imageRepository.save(image);
    }

    private String cleanTitle(String title, String fallback) {
        if (title != null && !title.isBlank()) {
            // HTML 태그 제거, 50자 제한
            String clean = title.replaceAll("<[^>]*>", "").trim();
            return clean.length() > 50 ? clean.substring(0, 50) : clean;
        }
        return fallback + ".jpg";
    }

    private byte[] generateDummyEmbedding(int dims) {
        Random rand = new Random(42);
        ByteBuffer buf = ByteBuffer.allocate(dims * 4);
        for (int i = 0; i < dims; i++) {
            buf.putFloat((float) rand.nextGaussian() * 0.1f);
        }
        return buf.array();
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
