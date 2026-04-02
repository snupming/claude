package com.ownpic.detection.adapter.seller;

public record SellerInfo(
        String platformType,
        String platformCategory,
        String sellerName,
        String businessRegNumber,
        String representativeName,
        String businessAddress,
        String contactPhone,
        String contactEmail,
        String storeUrl
) {
    public static SellerInfo empty(String platformType, String category) {
        return new SellerInfo(platformType, category, null, null, null, null, null, null, null);
    }

    public boolean hasBusinessInfo() {
        return businessRegNumber != null || representativeName != null;
    }
}
