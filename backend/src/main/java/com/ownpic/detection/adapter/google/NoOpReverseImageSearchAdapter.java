package com.ownpic.detection.adapter.google;

import com.ownpic.detection.port.ReverseImageSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!google")
public class NoOpReverseImageSearchAdapter implements ReverseImageSearchPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpReverseImageSearchAdapter.class);

    public NoOpReverseImageSearchAdapter() {
        log.warn("Google Cloud Vision DISABLED (google 프로필 비활성). "
                + "활성화: SPRING_PROFILES_ACTIVE=google + GOOGLE_APPLICATION_CREDENTIALS 설정");
    }

    @Override
    public List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults) {
        return List.of();
    }
}
