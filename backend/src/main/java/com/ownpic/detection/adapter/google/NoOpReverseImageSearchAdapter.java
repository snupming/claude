package com.ownpic.detection.adapter.google;

import com.ownpic.detection.port.ReverseImageSearchPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!google")
public class NoOpReverseImageSearchAdapter implements ReverseImageSearchPort {

    @Override
    public List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults) {
        return List.of();
    }
}
