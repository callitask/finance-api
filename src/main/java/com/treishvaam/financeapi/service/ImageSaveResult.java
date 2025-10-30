package com.treishvaam.financeapi.service;

/**
 * Simple DTO returned by ImageService.saveImageAndGetMetadata(...)
 */
public class ImageSaveResult {
    private final String baseFileName; // stored name (UUID)
    private final Integer width;
    private final Integer height;
    private final String mimeType;
    private final String blurHash;

    public ImageSaveResult(String baseFileName, Integer width, Integer height, String mimeType, String blurHash) {
        this.baseFileName = baseFileName;
        this.width = width;
        this.height = height;
        this.mimeType = mimeType;
        this.blurHash = blurHash;
    }

    public String getBaseFileName() {
        return baseFileName;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getBlurHash() {
        return blurHash;
    }
}
