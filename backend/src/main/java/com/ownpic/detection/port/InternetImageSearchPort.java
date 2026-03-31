package com.ownpic.detection.port;

import java.util.List;

public interface InternetImageSearchPort {

    List<SearchResult> searchByKeyword(String keyword, int maxResults);

    record SearchResult(
            String imageUrl,
            String sourcePageUrl,
            String title
    ) {}
}
