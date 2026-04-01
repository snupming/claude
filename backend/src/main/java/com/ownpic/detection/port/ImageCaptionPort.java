package com.ownpic.detection.port;

/**
 * 이미지 바이트에서 검색용 키워드를 자동 생성하는 포트.
 * Vision AI 모델을 사용하여 이미지 내용을 추론한다.
 */
public interface ImageCaptionPort {

    /**
     * 이미지 바이트로부터 검색에 적합한 한국어 키워드 문자열을 생성한다.
     *
     * @param imageBytes 이미지 원본 바이트
     * @return 쉼표 또는 공백으로 구분된 키워드 문자열, 실패 시 null
     */
    String generateKeywords(byte[] imageBytes);
}
