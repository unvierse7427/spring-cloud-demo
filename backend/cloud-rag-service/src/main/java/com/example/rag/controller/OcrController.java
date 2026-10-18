package com.example.rag.controller;

import com.example.rag.config.RagProperties;
import com.example.rag.dto.OcrText;
import com.example.rag.service.OcrClient;
import com.example.rag.support.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片文字识别工具：只做识别并把文本返回给调用方。
 *
 * <p>与知识库上传不同，这里的图片即用即弃：不写入 MinIO、不落库、不向量化，
 * 因此不需要经过 RocketMQ，直接同步调用 Python(PaddleOCR) 服务。
 *
 * <p>对外访问路径：网关 /api/rag/** -> 本服务 /**，因此这里以 /ocr 暴露。
 */
@RestController
@RequestMapping("/ocr")
public class OcrController {

    private final OcrClient ocrClient;

    private final RagProperties properties;

    public OcrController(OcrClient ocrClient, RagProperties properties) {
        this.ocrClient = ocrClient;
        this.properties = properties;
    }

    /**
     * 上传单张图片，返回识别出的文字。
     * 图片体积上限与支持格式由 cloud.rag.ocr 配置决定，超限直接拒绝。
     */
    @PostMapping
    public Result<OcrText> recognize(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要识别的图片");
        }
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim() : "image";

        RagProperties.Ocr ocr = properties.getOcr();
        if (!ocr.isImage(fileName)) {
            throw new IllegalArgumentException("仅支持 jpg / jpeg / png / bmp / webp 格式的图片");
        }
        if (file.getSize() > ocr.getMaxImageBytes()) {
            throw new IllegalArgumentException(String.format("图片体积 %.2fMB 超出上限 %.0fMB",
                    file.getSize() / 1024.0 / 1024.0, ocr.getMaxImageMb()));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传图片失败：" + e.getMessage(), e);
        }
        return Result.success("识别完成", ocrClient.recognize(fileName, bytes, file.getContentType()));
    }
}
