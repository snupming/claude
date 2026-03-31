package com.ownpic.detection.adapter;

import com.ownpic.detection.port.InternetImageSearchPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!naver")
public class NoOpInternetImageSearchAdapter implements InternetImageSearchPort {

    @Override
    public List<SearchResult> searchByKeyword(String keyword, int maxResults) {
        return List.of();
    }
}
