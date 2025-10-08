package com.treishvaam.financeapi.service;

// A simple record to hold all the metadata we extract from an image.
public record ImageMetadata(
    String baseFilename,
    int width,
    int height,
    String mimeType,
    String blurHash
) {}
