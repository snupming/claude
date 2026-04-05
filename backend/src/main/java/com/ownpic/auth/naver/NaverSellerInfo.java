package com.ownpic.auth.naver;

public record NaverSellerInfo(
        String solutionId,
        String accountUid,
        String roleGroupType,
        String channelName,
        Long defaultChannelNo,
        String type,
        String url,
        String categoryId,
        String representType,
        String businessType,
        String businessRegistrationNumber,
        String actionGrade,
        String planId,
        String subscriptionId,
        String status
) {}
