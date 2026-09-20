package com.hyper.spectral.config;

import com.hyper.spectral.config.fusion.FusionCompatibilityProperties;
import com.hyper.spectral.config.visualization.VisualizationCompatibilityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FusionCompatibilityProperties fusionCompatibilityProperties;
    private final VisualizationCompatibilityProperties visualizationCompatibilityProperties;

    public WebConfig(FusionCompatibilityProperties fusionCompatibilityProperties,
                     VisualizationCompatibilityProperties visualizationCompatibilityProperties) {
        this.fusionCompatibilityProperties = fusionCompatibilityProperties;
        this.visualizationCompatibilityProperties = visualizationCompatibilityProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 首版允许前后端分离调试，后续可切换为受控白名单。
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
        registry.addMapping("/get_mat_preview")
                .allowedOriginPatterns("*")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*");
        registry.addMapping("/get_png_by_mat")
                .allowedOriginPatterns("*")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*");
        registry.addMapping("/start_fusion_1")
                .allowedOriginPatterns("*")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*");
        registry.addMapping("/upload_and_preview")
                .allowedOriginPatterns("*")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*");
        registry.addMapping("/fusion/**")
                .allowedOriginPatterns("*")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String fusionResultLocation = "file:" + fusionCompatibilityProperties.getResultDir() + "/";
        String previewLocation = "file:" + visualizationCompatibilityProperties.getPreviewDir() + "/";
        registry.addResourceHandler("/static/fusion_results/**")
                .addResourceLocations(fusionResultLocation);
        registry.addResourceHandler("/static/visualization-previews/**")
                .addResourceLocations(previewLocation);
    }
}
