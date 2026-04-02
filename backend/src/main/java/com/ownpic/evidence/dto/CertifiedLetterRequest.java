package com.ownpic.evidence.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 내용증명 생성 요청 DTO — 사용자가 폼에서 입력하는 필드들.
 * 나머지는 탐지 데이터에서 자동 작성.
 */
public record CertifiedLetterRequest(
        @NotBlank String senderName,
        @NotBlank String senderAddress,
        @NotBlank String senderPhone,
        @NotBlank String recipientName,
        @NotBlank String recipientAddress,
        @NotBlank String workTitle,
        @NotBlank String creationDate,
        String firstPublicationInfo,
        String copyrightRegNumber,
        @NotBlank String damageAmount,
        String bankName,
        String accountNumber,
        String accountHolder,
        int complianceDays  // 기본 14일
) {
    public CertifiedLetterRequest {
        if (complianceDays <= 0) complianceDays = 14;
    }
}
