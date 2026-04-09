package com.karim.service.impl;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.karim.dto.ImageUploadResponseDTO;
import com.karim.service.CloudinaryService;

@Service
public class CloudinaryServiceImpl implements CloudinaryService {

	@Autowired
	private Cloudinary cloudinary;

	@Override
	public ImageUploadResponseDTO uploadImage(MultipartFile file) {
		try {

			// 🔒 Validate file size (extra safety)
			if (file.getSize() > 5 * 1024 * 1024) {
				throw new RuntimeException("File size exceeds 5MB limit");
			}

			Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());

			String imageUrl = uploadResult.get("secure_url").toString();
			String publicId = uploadResult.get("public_id").toString();

			return new ImageUploadResponseDTO(imageUrl, publicId);

		} catch (IOException e) {
			throw new RuntimeException("Image upload failed", e);
		}
	}

	@Override
	public void deleteImage(String publicId) {
		try {
			cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
		} catch (IOException e) {
			throw new RuntimeException("Image deletion failed", e);
		}
	}
}
