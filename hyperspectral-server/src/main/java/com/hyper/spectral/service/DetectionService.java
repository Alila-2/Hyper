package com.hyper.spectral.service;

import com.hyper.spectral.vo.detection.DetectionPreviewResponse;
import com.hyper.spectral.vo.detection.DetectionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DetectionService {

    DetectionPreviewResponse uploadAndPreview(MultipartFile file) throws IOException;

    DetectionResponse detectTargets(MultipartFile file, String targetsJson, String algorithm) throws IOException;
}
