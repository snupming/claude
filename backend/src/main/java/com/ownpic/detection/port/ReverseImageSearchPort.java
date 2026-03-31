package com.ownpic.detection.port;

import java.util.List;

public interface ReverseImageSearchPort {

    List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults);

    record ReverseSearchResult(
            String imageUrl,
            String sourcePageUrl,
            String title
    ) {}
}
