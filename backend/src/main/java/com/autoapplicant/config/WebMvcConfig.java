package com.autoapplicant.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppDataDirectory dataDirectory;

    // Takes the property rather than the AppDataDirectory bean: @WebMvcTest slices load every
    // WebMvcConfigurer but not plain components, so a bean dependency here breaks them all.
    public WebMvcConfig(@Value(AppDataDirectory.PROPERTY) String dataDirectory) {
        this.dataDirectory = new AppDataDirectory(dataDirectory);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(UploadsLocation.URL_PREFIX + "**")
                .addResourceLocations(directoryLocation(dataDirectory.uploads()));
    }

    // A URI rather than "file:" + path: a Windows path has backslashes and a drive letter, which
    // a file: location string does not resolve reliably. The trailing slash marks a directory;
    // toUri() only adds it when the directory already exists, and uploads is created lazily.
    private static String directoryLocation(Path directory) {
        String uri = directory.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
