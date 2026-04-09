package com.karim.service;

import org.springframework.web.multipart.MultipartFile;

import com.karim.dto.ImageUploadResponseDTO;

public interface CloudinaryService {

	ImageUploadResponseDTO uploadImage(MultipartFile file);

	void deleteImage(String publicId);
}