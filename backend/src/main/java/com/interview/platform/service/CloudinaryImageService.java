package com.interview.platform.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryImageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/avif"
    );

    private final RestTemplate restTemplate = new RestTemplate();
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;

    public CloudinaryImageService(
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.cloud-name:}") String cloudName,
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.api-key:}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.api-secret:}") String apiSecret,
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.folder:interviewprep/avatars}") String folder
    ) {
        this.cloudName = cloudName == null ? "" : cloudName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.folder = folder == null || folder.isBlank() ? "interviewprep/avatars" : folder.trim();
    }

    public String uploadProfileImage(String userId, MultipartFile file) {
        validateConfiguration();
        validateFile(file);
        long timestamp = Instant.now().getEpochSecond();
        String publicId = "user-" + sanitizeUserId(userId) + "-" + Instant.now().toEpochMilli();
        String signature = signatureFor(timestamp, publicId);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", namedResource(file));
        body.add("api_key", apiKey);
        body.add("timestamp", String.valueOf(timestamp));
        body.add("signature", signature);
        body.add("folder", folder);
        body.add("public_id", publicId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload",
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            String secureUrl = response == null ? null : stringValue(response.get("secure_url"));
            if (secureUrl == null || secureUrl.isBlank()) {
                throw new IllegalArgumentException("Profile image upload did not return a usable URL");
            }
            return secureUrl;
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("Profile image upload failed. Please try again.");
        }
    }

    private void validateConfiguration() {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalArgumentException("Profile image uploads are not configured yet.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an image to upload.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Choose an image under 5 MB.");
        }
        String contentType = stringValue(file.getContentType());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPG, PNG, WEBP, GIF, and AVIF images are supported.");
        }
    }

    private ByteArrayResource namedResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "avatar-upload"
                    : file.getOriginalFilename().trim();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the selected image.");
        }
    }

    private String signatureFor(long timestamp, String publicId) {
        String payload = "folder=" + folder + "&public_id=" + publicId + "&timestamp=" + timestamp + apiSecret;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 signature generation is unavailable", ex);
        }
    }

    private String sanitizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "profile";
        }
        return userId.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
