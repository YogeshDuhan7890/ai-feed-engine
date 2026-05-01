package com.yogesh.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class UploadInitializer {

    @Value("${upload.path}")
    private String uploadPath;

    @PostConstruct
    public void init() {

        File directory = new File(uploadPath);

        if (!directory.exists()) {
            boolean created = directory.mkdirs();

            if (created) {
                System.out.println("Uploads folder created: " + uploadPath);
            } else {
                System.out.println("Failed to create uploads folder!");
            }
        }
    }
}