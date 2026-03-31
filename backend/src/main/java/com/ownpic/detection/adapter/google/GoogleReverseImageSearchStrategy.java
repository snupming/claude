package com.ownpic.detection.adapter.google;

import com.ownpic.detection.port.ReverseImageSearchPort.ReverseSearchResult;

import java.util.List;

public interface GoogleReverseImageSearchStrategy {

    String name();

    List<ReverseSearchResult> search(byte[] imageBytes, int maxResults) throws GoogleSearchException;
}
