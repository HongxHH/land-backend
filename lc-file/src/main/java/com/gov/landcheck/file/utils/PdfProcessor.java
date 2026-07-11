package com.gov.landcheck.file.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import com.gov.landcheck.file.config.FileProcessingProperties;

import jakarta.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * PDF处理工具类
 * 负责 PDF 预览图生成与按页渲染等
 */
@Component
public class PdfProcessor {

    @Resource
    private GridFsTemplate gridFsTemplate;

    private final int thumbnailDpi;

    public PdfProcessor(FileProcessingProperties fileProcessingProperties) {
        this.thumbnailDpi = fileProcessingProperties != null
                ? fileProcessingProperties.getThumbnailDpi()
                : 120;
    }

    /**
     * 根据 PDF 首页生成预览图并存储到 MongoDB GridFS。
     */
    public String generateThumbnail(InputStream pdfInputStream) {
        try {
            return generateThumbnail(readInputStreamToBytes(pdfInputStream));
        } catch (IOException e) {
            throw new RuntimeException("生成PDF预览图失败", e);
        }
    }

    /**
     * 根据 PDF 字节数组生成预览图，避免 InputStream 二次读取。
     */
    public String generateThumbnail(byte[] pdfBytes) {
        PDDocument document = null;
        BufferedImage image = null;
        try {
            document = Loader.loadPDF(pdfBytes);
            if (document.getNumberOfPages() == 0) {
                throw new RuntimeException("PDF文件没有页面");
            }
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            image = pdfRenderer.renderImageWithDPI(0, thumbnailDpi);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            try (InputStream imageStream = new ByteArrayInputStream(imageBytes)) {
                ObjectId objectId = gridFsTemplate.store(imageStream, "thumbnail_image/png");
                return objectId.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException("生成PDF预览图失败", e);
        } finally {
            if (image != null) {
                image.flush();
            }
            if (document != null) {
                try {
                    document.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    /**
     * 将 PDF 每一页渲染为 PNG 字节（用于多模态大模型识别）
     */
    public java.util.List<byte[]> renderPdfToPngBytesPerPage(byte[] pdfBytes, int dpi) {
        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfBytes);
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int n = document.getNumberOfPages();
            java.util.List<byte[]> pages = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(i, dpi);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", baos);
                pages.add(baos.toByteArray());
                image.flush();
            }
            return pages;
        } catch (Exception e) {
            throw new RuntimeException("PDF按页渲染PNG失败", e);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private byte[] readInputStreamToBytes(InputStream inputStream) throws IOException {
        return inputStream.readAllBytes();
    }
}
