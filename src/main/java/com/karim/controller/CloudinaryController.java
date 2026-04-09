package com.karim.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.karim.dto.ImageUploadResponseDTO;
import com.karim.service.CloudinaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;

@RestController
@RequestMapping("/api/images")
public class CloudinaryController {

	@Autowired
	private CloudinaryService cloudinaryService;

	@PostMapping("/upload")
	public ResponseEntity<ImageUploadResponseDTO> uploadImage(@RequestParam("file") MultipartFile file) {

		ImageUploadResponseDTO response = cloudinaryService.uploadImage(file);
		return ResponseEntity.ok(response);
	}

//	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//	@Operation(summary = "Upload Image", description = "Upload image to Cloudinary")
//	public ResponseEntity<ImageUploadResponseDTO> uploadImage(
//	        @Parameter(
//	            description = "Image file",
//	            required = true,
//	            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
//	        )
//	        @RequestPart("file") MultipartFile file) {
//
//	    ImageUploadResponseDTO response = cloudinaryService.uploadImage(file);
//	    return ResponseEntity.ok(response);
//	}

	@DeleteMapping("/delete")
	public ResponseEntity<String> deleteImage(@RequestParam String publicId) {

		cloudinaryService.deleteImage(publicId);
		return ResponseEntity.ok("Image deleted successfully");
	}
}
