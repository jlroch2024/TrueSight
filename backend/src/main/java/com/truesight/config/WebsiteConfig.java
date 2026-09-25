package com.truesight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the website from the backend, on the live site.
 *
 * <p>On the live site, the React build is copied into the backend's {@code static} folder (see the Dockerfile), so
 * one service answers both the website and {@code /api/}. Files that exist, such as {@code index.html},
 * {@code eye-logo.png} and the built JavaScript, are sent as they are.
 *
 * <p>The website's pages, such as {@code /portfolios/1}, are not files: React Router draws them in the browser once
 * {@code index.html} has loaded. So when a browser asks for an address that is not a file and not under
 * {@code /api/}, this sends {@code index.html}, and React shows the right page. Without it, opening or refreshing any
 * page other than the home page would say "Not found."
 *
 * <p>Addresses under {@code /api/}, and missing files (anything with a dot, such as {@code missing.js}), still answer
 * "Not found." in the shared error shape. On a laptop there is no website build in the backend, so nothing changes:
 * the website runs separately with {@code npm run dev}.
 */
@Configuration
public class WebsiteConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource file = location.createRelative(resourcePath);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                            return null;
                        }
                        Resource website = location.createRelative("index.html");
                        return website.exists() && website.isReadable() ? website : null;
                    }
                });
    }
}
