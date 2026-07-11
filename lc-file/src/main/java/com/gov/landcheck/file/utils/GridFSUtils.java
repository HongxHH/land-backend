package com.gov.landcheck.file.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * GridFS 工具类
 * 提供统一的 GridFS 文件操作功能
 */
@Component
@Slf4j
public class GridFSUtils {

    @Resource
    private GridFSBucket gridFsBucket;

    /**
     * 检查 GridFS 文件是否存在
     *
     * @param gridfsId 文件ID
     * @return true 如果文件存在，false 如果不存在
     */
    public boolean exists(String gridfsId) {
        if (!ObjectId.isValid(gridfsId)) {
            log.warn("GridFS 文件ID格式无效: gridfsId={}", gridfsId);
            return false;
        }
        try {
            GridFSFile fsFile = gridFsBucket.find(new Document("_id", new ObjectId(gridfsId))).first();
            return fsFile != null;
        } catch (Exception e) {
            log.warn("检查 GridFS 文件存在性失败: gridfsId={}, error={}", gridfsId, e.getMessage(), e);
            throw new IllegalStateException("GridFS 文件存在性检查失败，请稍后再试", e);
        }
    }

    /**
     * 从 GridFS 获取文件内容为字节数组
     *
     * @param gridfsId 文件ID
     * @return 文件内容的字节数组
     * @throws Exception 如果文件不存在或读取失败
     */
    public byte[] getFileBytes(String gridfsId) throws Exception {
        GridFSFile fsFile = gridFsBucket.find(new Document("_id", new ObjectId(gridfsId))).first();
        if (fsFile == null) {
            throw new RuntimeException("GridFs resource [" + gridfsId + "] does not exist.");
        }

        try (GridFSDownloadStream downloadStream = gridFsBucket.openDownloadStream(fsFile.getObjectId());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = downloadStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return outputStream.toByteArray();
        }
    }

    /**
     * 从 GridFS 下载文件到指定路径
     *
     * @param gridfsId 文件ID
     * @param targetPath 目标文件路径
     * @throws Exception 如果文件不存在或下载失败
     */
    public void downloadToPath(String gridfsId, Path targetPath) throws Exception {
        GridFSFile fsFile = gridFsBucket.find(new Document("_id", new ObjectId(gridfsId))).first();
        if (fsFile == null) {
            throw new RuntimeException("GridFs resource [" + gridfsId + "] does not exist.");
        }

        try (GridFSDownloadStream downloadStream = gridFsBucket.openDownloadStream(fsFile.getObjectId());
             FileOutputStream outputStream = new FileOutputStream(targetPath.toFile())) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = downloadStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * 删除 GridFS 文件
     *
     * @param gridfsId 文件ID
     * @return true 如果删除成功，false 如果文件不存在或删除失败
     */
    public boolean deleteById(String gridfsId) {
        try {
            if (!exists(gridfsId)) {
                return false;
            }
            gridFsBucket.delete(new ObjectId(gridfsId));
            return true;
        } catch (Exception e) {
            log.warn("删除 GridFS 文件失败: gridfsId={}, error={}", gridfsId, e.getMessage());
            return false;
        }
    }

    /**
     * 上传字符串内容到 GridFS
     *
     * @param content 字符串内容
     * @param filename 文件名
     * @param contentType 内容类型（可选）
     * @return GridFS 文件ID
     * @throws Exception 如果上传失败
     */
    public String uploadString(String content, String filename, String contentType) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return uploadBytes(bytes, filename, contentType);
    }

    /**
     * 上传字节数组到 GridFS
     *
     * @param bytes 字节数组
     * @param filename 文件名
     * @param contentType 内容类型（可选）
     * @return GridFS 文件ID
     * @throws Exception 如果上传失败
     */
    public String uploadBytes(byte[] bytes, String filename, String contentType) throws Exception {
        try {
            // 设置上传选项
            GridFSUploadOptions options = new GridFSUploadOptions();

            // 设置元数据
            Document metadata = new Document();
            if (contentType != null && !contentType.trim().isEmpty()) {
                metadata.append("contentType", contentType);
            }
            metadata.append("uploadTime", System.currentTimeMillis());
            metadata.append("fileSize", bytes.length);
            options.metadata(metadata);

            // 使用带选项的上传方法
            ObjectId fileId = gridFsBucket.uploadFromStream(filename, new ByteArrayInputStream(bytes), options);
            String gridfsId = fileId.toHexString();

            return gridfsId;

        } catch (Exception e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

}