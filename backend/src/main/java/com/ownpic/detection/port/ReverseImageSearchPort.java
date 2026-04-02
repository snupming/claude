package com.ownpic.detection.port;

import java.util.List;

public interface ReverseImageSearchPort {

    List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults);

    record ReverseSearchResult(
            String imageUrl,
            String sourcePageUrl,
            String title,
            String bestGuessLabel,
            String topEntity
    ) {
        /** 기존 3인자 호환 생성자 */
        public ReverseSearchResult(String imageUrl, String sourcePageUrl, String title) {
            this(imageUrl, sourcePageUrl, title, null, null);
        }
    }
}
