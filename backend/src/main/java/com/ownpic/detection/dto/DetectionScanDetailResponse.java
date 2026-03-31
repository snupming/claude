package com.ownpic.detection.dto;

import java.util.List;

public record DetectionScanDetailResponse(
        DetectionScanResponse scan,
        List<DetectionResultResponse> results
) {}
