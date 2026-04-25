package cn.edu.nsu.maic.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

record StoredMultipartFile(
        String originalFilename,
        String contentType,
        byte[] bytes
) implements MultipartFile {
    @Override
    public String getName() {
        return originalFilename;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return bytes == null || bytes.length == 0;
    }

    @Override
    public long getSize() {
        return bytes == null ? 0 : bytes.length;
    }

    @Override
    public byte[] getBytes() {
        return bytes == null ? new byte[0] : bytes;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException {
        java.nio.file.Files.write(dest.toPath(), bytes == null ? new byte[0] : bytes);
    }
}
