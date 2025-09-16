package com.treishvaam.financeapi.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStream;

@Controller
public class LogoController {

    @GetMapping(value = {"/api/logo", "/logo512.png", "/logo.png", "/favicon.ico"}, produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> getLogo() throws IOException {
        ClassPathResource imgFile = new ClassPathResource("static/logo512.png");
        
        if (imgFile.exists()) {
            try (InputStream inputStream = imgFile.getInputStream()) {
                byte[] bytes = inputStream.readAllBytes();
                return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes);
            }
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}