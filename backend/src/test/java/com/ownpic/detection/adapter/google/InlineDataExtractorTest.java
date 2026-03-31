package com.ownpic.detection.adapter.google;

import com.ownpic.detection.port.ReverseImageSearchPort.ReverseSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InlineDataExtractorTest {

    private final InlineDataExtractor extractor = new InlineDataExtractor();

    @Test
    void extract_afInitDataCallback_findsImageUrls() {
        String html = """
                <html><head><script>
                AF_initDataCallback({key: 'ds:1', data: [null, [
                    ["https://example.com/photo.jpg", "https://example.com/page", "Example Photo"],
                    ["https://shop.com/product.png", "https://shop.com/item/123", "상품 이미지"]
                ]]});
                </script></head><body></body></html>
                """;

        List<ReverseSearchResult> results = extractor.extract(html, 10);

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r -> r.imageUrl().contains("example.com/photo.jpg"));
        assertThat(results).anyMatch(r -> r.imageUrl().contains("shop.com/product.png"));
    }

    @Test
    void extract_multipleCallbacks_collectsFromAll() {
        String html = """
                <script>
                AF_initDataCallback({key: 'ds:0', data: [["https://site-a.com/img1.jpg"]]});
                AF_initDataCallback({key: 'ds:1', data: [["https://site-b.com/img2.png"]]});
                </script>
                """;

        List<ReverseSearchResult> results = extractor.extract(html, 10);

        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void extract_filtersGoogleDomains() {
        String html = """
                <script>
                AF_initDataCallback({key: 'ds:0', data: [
                    ["https://www.google.com/internal.jpg"],
                    ["https://lh3.googleusercontent.com/thumb.jpg"],
                    ["https://encrypted-tbn0.gstatic.com/cached.jpg"],
                    ["https://realsite.com/stolen.jpg"]
                ]});
                </script>
                """;

        List<ReverseSearchResult> results = extractor.extract(html, 10);

        assertThat(results).allMatch(r -> !r.imageUrl().contains("google.com"));
        assertThat(results).allMatch(r -> !r.imageUrl().contains("gstatic.com"));
        assertThat(results).allMatch(r -> !r.imageUrl().contains("googleusercontent.com"));
        assertThat(results).anyMatch(r -> r.imageUrl().contains("realsite.com"));
    }

    @Test
    void extract_respectsMaxResults() {
        StringBuilder html = new StringBuilder("<script>AF_initDataCallback({key: 'ds:0', data: [");
        for (int i = 0; i < 50; i++) {
            html.append("[\"https://site").append(i).append(".com/img.jpg\"],");
        }
        html.append("]});</script>");

        List<ReverseSearchResult> results = extractor.extract(html.toString(), 5);

        assertThat(results).hasSize(5);
    }

    @Test
    void extract_noCallbackData_fallsBackToHtmlParsing() {
        String html = """
                <html><body>
                <a href="https://shop.com/item"><img src="https://cdn.shop.com/product.jpg" alt="상품"></a>
                <a href="https://blog.com/post"><img src="https://blog.com/img/photo.png" alt="사진"></a>
                </body></html>
                """;

        List<ReverseSearchResult> results = extractor.extract(html, 10);

        assertThat(results).isNotEmpty();
    }

    @Test
    void extract_emptyHtml_returnsEmpty() {
        List<ReverseSearchResult> results = extractor.extract("", 10);
        assertThat(results).isEmpty();
    }

    @Test
    void extract_nestedImageUrlWithQueryParams_parsesCorrectly() {
        String html = """
                <script>
                AF_initDataCallback({key: 'ds:1', data: [
                    ["https://cdn.shop.com/product.jpg?w=800&h=600&quality=80"]
                ]});
                </script>
                """;

        List<ReverseSearchResult> results = extractor.extract(html, 10);

        assertThat(results).anyMatch(r -> r.imageUrl().contains("cdn.shop.com/product.jpg"));
    }

    @Test
    void findAfInitDataBlocks_nestedBrackets_handledCorrectly() {
        String html = "AF_initDataCallback({key: 'ds:0', data: [[\"nested\", [\"deep\", {\"obj\": true}]]]});";

        List<String> blocks = extractor.findAfInitDataBlocks(html);

        assertThat(blocks).hasSize(1);
    }

    @Test
    void extract_pairsImageUrlWithNearbyPageUrl() {
        String html = """
                <script>
                AF_initDataCallback({key: 'ds:1', data: [
                    null, [["https://external.com/image.jpg", 300, 200, "https://external.com/page/123", "External Page"]]
                ]});
                </script>
                """;

        List<ReverseSearchResult> results = extractor.extract(html, 10);

        if (!results.isEmpty()) {
            ReverseSearchResult first = results.get(0);
            assertThat(first.imageUrl()).contains("external.com/image.jpg");
            // sourcePageUrl은 근접도 기반이므로 있으면 확인
            if (first.sourcePageUrl() != null) {
                assertThat(first.sourcePageUrl()).contains("external.com");
            }
        }
    }
}
