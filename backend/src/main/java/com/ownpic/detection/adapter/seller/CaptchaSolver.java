package com.ownpic.detection.adapter.seller;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vision API OCR로 CAPTCHA 이미지 자동 풀기.
 *
 * 네이버 스마트스토어 CAPTCHA: 영수증 이미지 → "구매한 물건은 총 몇 개입니까?"
 * → OCR로 영수증 텍스트 읽기 → 품목 수 카운트
 */
@Component
public class CaptchaSolver {

    private static final Logger log = LoggerFactory.getLogger(CaptchaSolver.class);

    @Value("${ownpic.google.vision-api-key:}")
    private String visionApiKey;

    // 수량 패턴: "2", "1개", "x3" 등
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(\\d+)\\s*[개EA]?");
    // 가격 패턴: "12,000", "15000" 등
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\d{1,3}[,.]?\\d{3}");
    // 품목 라인: 품명 + 수량 + 가격 조합
    private static final Pattern ITEM_LINE = Pattern.compile("\\d{1,3}[,.]\\d{3}");

    /**
     * 영수증 CAPTCHA 이미지에서 품목 수를 계산.
     *
     * @param captchaImageBytes CAPTCHA 이미지 바이트
     * @return 품목 수 (실패 시 -1)
     */
    public int solveReceiptCaptcha(byte[] captchaImageBytes) {
        try {
            List<String> ocrLines = performOcr(captchaImageBytes);
            if (ocrLines.isEmpty()) {
                log.warn("[CaptchaSolver] OCR 결과 없음");
                return -1;
            }

            log.info("[CaptchaSolver] OCR 라인 수: {}", ocrLines.size());
            for (String line : ocrLines) {
                log.info("[CaptchaSolver]   OCR: {}", line);
            }

            int itemCount = countReceiptItems(ocrLines);
            log.info("[CaptchaSolver] 계산된 품목 수: {}", itemCount);
            return itemCount;
        } catch (Exception e) {
            log.error("[CaptchaSolver] CAPTCHA 풀기 실패", e);
            return -1;
        }
    }

    /**
     * Vision API TEXT_DETECTION으로 이미지 OCR 수행.
     */
    private List<String> performOcr(byte[] imageBytes) throws Exception {
        try (ImageAnnotatorClient client = createClient()) {
            Image image = Image.newBuilder()
                    .setContent(ByteString.copyFrom(imageBytes))
                    .build();

            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.DOCUMENT_TEXT_DETECTION) // 문서 수준 OCR (영수증에 최적)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(image)
                    .build();

            BatchAnnotateImagesResponse response = client.batchAnnotateImages(List.of(request));
            AnnotateImageResponse imgResponse = response.getResponses(0);

            if (imgResponse.hasError()) {
                log.error("[CaptchaSolver] Vision API 에러: {}", imgResponse.getError().getMessage());
                return List.of();
            }

            TextAnnotation fullText = imgResponse.getFullTextAnnotation();
            if (fullText == null || fullText.getText().isBlank()) {
                return List.of();
            }

            return List.of(fullText.getText().split("\n"));
        }
    }

    /**
     * OCR 텍스트에서 영수증 품목 수 카운트.
     *
     * 한국 영수증 구조:
     * - 품명 | 수량 | 단가 | 금액
     * - 또는 품명만 나열 후 합계
     *
     * 카운팅 전략:
     * 1. 가격 패턴(N,NNN)이 있는 라인 = 품목 라인
     * 2. "합계", "총", "부가세", "카드", "현금" 등은 제외
     * 3. 남은 가격 라인 수 = 품목 수
     */
    private int countReceiptItems(List<String> lines) {
        int itemCount = 0;
        boolean inItemSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 헤더/푸터 라인 제외
            if (isHeaderOrFooter(trimmed)) {
                if (inItemSection) break; // 품목 섹션이 끝남
                continue;
            }

            // 가격 패턴이 있으면 품목 라인
            if (PRICE_PATTERN.matcher(trimmed).find()) {
                // "합계", "부가세", "받은 금액" 등은 제외
                if (!isSummaryLine(trimmed)) {
                    itemCount++;
                    inItemSection = true;
                }
            }
        }

        return Math.max(itemCount, 1); // 최소 1개
    }

    private boolean isHeaderOrFooter(String line) {
        return line.contains("영수증") || line.contains("사업자") || line.contains("대표")
                || line.contains("전화") || line.contains("주소") || line.contains("TEL")
                || line.contains("가맹점") || line.contains("카드번호") || line.contains("승인번호")
                || line.contains("거래일") || line.contains("IC") || line.contains("POS");
    }

    private boolean isSummaryLine(String line) {
        return line.contains("합계") || line.contains("총액") || line.contains("부가세")
                || line.contains("봉사료") || line.contains("받은") || line.contains("거스름")
                || line.contains("카드") || line.contains("현금") || line.contains("할인")
                || line.contains("결제") || line.contains("잔액") || line.contains("포인트");
    }

    private ImageAnnotatorClient createClient() throws Exception {
        if (visionApiKey != null && !visionApiKey.isBlank()) {
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(NoCredentialsProvider.create())
                    .setHeaderProvider((HeaderProvider) () -> Map.of("x-goog-api-key", visionApiKey))
                    .build();
            return ImageAnnotatorClient.create(settings);
        }
        return ImageAnnotatorClient.create();
    }
}
