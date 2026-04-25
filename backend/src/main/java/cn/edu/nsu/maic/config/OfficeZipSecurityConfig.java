package cn.edu.nsu.maic.config;

import jakarta.annotation.PostConstruct;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OfficeZipSecurityConfig {

    private static final double MIN_INFLATE_RATIO_FOR_OFFICE_MEDIA = 0.001d;

    @PostConstruct
    public void configurePoiZipSecurity() {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO_FOR_OFFICE_MEDIA);
    }
}
