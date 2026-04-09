package com.karim.dto;

public class ImageUploadResponseDTO {

    private String imageUrl;
    private String publicId;

    public ImageUploadResponseDTO(String imageUrl, String publicId) {
        this.imageUrl = imageUrl;
        this.publicId = publicId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPublicId() {
        return publicId;
    }
}
