package com.interview.platform.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
import java.util.Arrays;
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
    private static final long MAX_DOCUMENT_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final Set<String> ALLOWED_DOCUMENT_EXTENSIONS = Set.of(".pdf", ".docx");

    private final RestTemplate restTemplate;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;
    private final String resumeFolder;

    public CloudinaryImageService(
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.cloud-name:}") String cloudName,
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.api-key:}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.api-secret:}") String apiSecret,
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.folder:interviewprep/avatars}") String folder,
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.resume-folder:interviewprep/resumes}") String resumeFolder,
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.connect-timeout-ms:5000}") int connectTimeoutMs,
            @org.springframework.beans.factory.annotation.Value("${app.cloudinary.read-timeout-ms:15000}") int readTimeoutMs
    ) {
        this.cloudName = cloudName == null ? "" : cloudName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.folder = folder == null || folder.isBlank() ? "interviewprep/avatars" : folder.trim();
        this.resumeFolder = resumeFolder == null || resumeFolder.isBlank() ? "interviewprep/resumes" : resumeFolder.trim();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1000, readTimeoutMs));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public String uploadProfileImage(String userId, MultipartFile file) {
        validateConfiguration();
        validateImageFile(file);
        long timestamp = Instant.now().getEpochSecond();
        String publicId = "user-" + sanitizeUserId(userId) + "-" + Instant.now().toEpochMilli();
        String signature = signatureFor(timestamp, folder, publicId);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", namedResource(file));
        body.add("api_key", apiKey);
        body.add("timestamp", String.valueOf(timestamp));
        body.add("signature", signature);
        body.add("folder", folder);
        body.add("public_id", publicId);
        return upload("image", body, "Profile image upload did not return a usable URL", "Profile image upload failed. Please try again.");
    }

    public UploadedAsset uploadResumeDocument(String userId, MultipartFile file) {
        validateConfiguration();
        validateDocumentFile(file);
        long timestamp = Instant.now().getEpochSecond();
        String publicId = "resume-" + sanitizeUserId(userId) + "-" + Instant.now().toEpochMilli();
        String signature = signatureFor(timestamp, resumeFolder, publicId);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", namedResource(file));
        body.add("api_key", apiKey);
        body.add("timestamp", String.valueOf(timestamp));
        body.add("signature", signature);
        body.add("folder", resumeFolder);
        body.add("public_id", publicId);

        Map<String, Object> response = uploadMap("raw", body, "Resume upload failed. Please try again.");
        String secureUrl = response == null ? null : stringValue(response.get("secure_url"));
        if (secureUrl == null || secureUrl.isBlank()) {
            throw new IllegalArgumentException("Resume upload did not return a usable URL");
        }
        return new UploadedAsset(
                secureUrl,
                file.getOriginalFilename(),
                stringValue(file.getContentType())
        );
    }

    private void validateConfiguration() {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalArgumentException("Profile image uploads are not configured yet.");
        }
    }

    private void validateImageFile(MultipartFile file) {
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
        byte[] bytes = firstBytes(file);
        if (!hasSupportedImageSignature(bytes)) {
            throw new IllegalArgumentException("Image contents do not match the selected file type.");
        }
    }

    private void validateDocumentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a resume to upload.");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new IllegalArgumentException("Choose a resume under 10 MB.");
        }
        String filename = stringValue(file.getOriginalFilename());
        String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean hasAllowedExtension = ALLOWED_DOCUMENT_EXTENSIONS.stream().anyMatch(lowerFilename::endsWith);
        if (!hasAllowedExtension) {
            throw new IllegalArgumentException("Only PDF and DOCX resumes are supported.");
        }
        String contentType = stringValue(file.getContentType());
        if (contentType != null && !contentType.isBlank()
                && !ALLOWED_DOCUMENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Unsupported resume file type. Upload PDF or DOCX.");
        }
        byte[] bytes = firstBytes(file);
        if (lowerFilename.endsWith(".pdf") && !startsWith(bytes, new byte[]{0x25, 0x50, 0x44, 0x46})) {
            throw new IllegalArgumentException("Resume contents do not match a PDF file.");
        }
        if (lowerFilename.endsWith(".docx") && !startsWith(bytes, new byte[]{0x50, 0x4B, 0x03, 0x04})) {
            throw new IllegalArgumentException("Resume contents do not match a DOCX file.");
        }
    }

    private ByteArrayResource namedResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "avatar-upload"
                    : sanitizeFilename(file.getOriginalFilename());
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

    private String signatureFor(long timestamp, String cloudinaryFolder, String publicId) {
        String payload = "folder=" + cloudinaryFolder + "&public_id=" + publicId + "&timestamp=" + timestamp + apiSecret;
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

    private String upload(String resourceType, MultiValueMap<String, Object> body, String emptyMessage, String errorMessage) {
        Map<String, Object> response = uploadMap(resourceType, body, errorMessage);
        String secureUrl = response == null ? null : stringValue(response.get("secure_url"));
        if (secureUrl == null || secureUrl.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        return secureUrl;
    }

    private Map<String, Object> uploadMap(String resourceType, MultiValueMap<String, Object> body, String errorMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    "https://api.cloudinary.com/v1_1/" + cloudName + "/" + resourceType + "/upload",
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            return response;
        } catch (RestClientException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private byte[] firstBytes(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return bytes.length <= 16 ? bytes : Arrays.copyOf(bytes, 16);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the selected file.");
        }
    }

    private boolean hasSupportedImageSignature(byte[] bytes) {
        return startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})
                || startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})
                || startsWith(bytes, "GIF8".getBytes(StandardCharsets.UTF_8))
                || (startsWith(bytes, "RIFF".getBytes(StandardCharsets.UTF_8))
                        && containsAt(bytes, "WEBP".getBytes(StandardCharsets.UTF_8), 8))
                || containsAt(bytes, "ftypavif".getBytes(StandardCharsets.UTF_8), 4)
                || containsAt(bytes, "ftypmif1".getBytes(StandardCharsets.UTF_8), 4);
    }

    private boolean startsWith(byte[] actual, byte[] expected) {
        if (actual == null || expected == null || actual.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i += 1) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAt(byte[] actual, byte[] expected, int offset) {
        if (actual == null || expected == null || offset < 0 || actual.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i += 1) {
            if (actual[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private String sanitizeFilename(String fileName) {
        return fileName.trim().replaceAll("[\\r\\n\\\\/]+", "_");
    }

    public record UploadedAsset(String url, String fileName, String contentType) {}
}
