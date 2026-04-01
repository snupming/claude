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
        log.warn("Google reverse image search DISABLED (google 프로필 비활성). "
                + "활성화: spring.profiles.active=google 또는 SPRING_PROFILES_ACTIVE=google");
    }

    @Override
    public List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults) {
        log.debug("NoOp searchByImage called — google 프로필이 비활성이므로 빈 결과 반환 (imageBytes={}KB)",
                imageBytes != null ? imageBytes.length / 1024 : 0);
        return List.of();
    }
}
