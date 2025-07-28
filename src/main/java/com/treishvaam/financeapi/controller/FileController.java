/**
     * MODIFIED: This endpoint now processes uploaded images into multiple .webp sizes
     * and returns URLs for each version.
     */
    @PostMapping("/files/upload")
    public ResponseEntity<Map<String, Object>> handleFileUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("errorMessage", "File is empty");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // The storeFile method now returns a base name (e.g., "uuid_original-name")
        String baseName = fileStorageService.storeFile(file);

        // --- FIX: Correctly construct the full URL path, including the "/api" prefix. ---
        // This ensures the generated URL matches the actual endpoint for serving files.
        Map<String, String> imageUrls = new HashMap<>();
        imageUrls.put("large", "/api/uploads/" + baseName + ".webp");
        imageUrls.put("medium", "/api/uploads/" + baseName + "-medium.webp");
        imageUrls.put("small", "/api/uploads/" + baseName + "-small.webp");

        // Prepare the response payload
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("urls", imageUrls);
        fileInfo.put("name", baseName);
        fileInfo.put("originalSize", file.getSize());

        Map<String, Object> response = new HashMap<>();
        response.put("result", Collections.singletonList(fileInfo));

        return ResponseEntity.ok(response);
    }