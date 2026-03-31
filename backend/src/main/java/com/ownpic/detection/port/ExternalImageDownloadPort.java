package com.ownpic.detection.port;

public interface ExternalImageDownloadPort {

    /**
     * 외부 URL에서 이미지 바이트를 다운로드. 실패 시 null 반환.
     */
    byte[] download(String imageUrl, int timeoutMs);
}
