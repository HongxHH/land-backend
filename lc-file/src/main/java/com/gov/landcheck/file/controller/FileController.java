package com.gov.landcheck.file.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.dto.SystemRuntimeStatusDTO;
import com.gov.landcheck.core.bo.dto.ThreadPoolResizeDTO;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.common.UserTypeConstants;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.dto.BulkParseEnqueueResultDTO;
import com.gov.landcheck.file.dto.FileQueryDTO;
import com.gov.landcheck.file.dto.FileUploadDTO;
import com.gov.landcheck.file.dto.TaskStatusDTO;
import com.gov.landcheck.file.service.FileService;
import com.gov.landcheck.file.service.ITaskExecuteService;
import com.gov.landcheck.file.service.parse.GlobalParseEnqueueService;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.io.IoUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/file")
public class FileController {

    @Resource
    private FileService fileService;

    @Resource
    private ITaskExecuteService taskExecuteService;

    @Resource
    private GlobalParseEnqueueService globalParseEnqueueService;

    @Resource
    private GridFSBucket gridFSBucket;

    @Operation(summary = "上传单个文件", description = "multipart 接收完成后同步落库（GridFS 等）并提交异步后处理；"
            + "自请求发出后处理不可由客户端取消。")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxJson upload(@Parameter(description = "项目ID") @RequestParam @NotNull Long projectId,
            @Parameter(description = "文件内容类型") @RequestParam @NotNull String fileContextType,
            @Parameter(description = "上传文件") @RequestParam MultipartFile file,
            @Parameter(description = "期数（实测文件时必填）") @RequestParam(required = false) Integer phase,
            @Parameter(description = "指定归档夹ID（可选，仅 fileContextType=OTHER 时生效）") @RequestParam(required = false) Long archiveId) {
        FileUploadDTO uploadDTO = new FileUploadDTO();
        uploadDTO.setProjectId(projectId);
        uploadDTO.setFileContextType(FileContextType.getByCode(fileContextType));
        uploadDTO.setFile(file);
        uploadDTO.setPhase(phase);
        uploadDTO.setArchiveId(archiveId);
        return fileService.uploadFile(uploadDTO);
    }

    @GetMapping("/download/{id}")
    @Operation(summary = "根据文件id下载文件", description = "通过在url的路径上添加文件id即可")
    public ResponseEntity<Object> downloadFileById(@Parameter(description = "文件id") @PathVariable String id)
            throws UnsupportedEncodingException {
        Optional<FileRecord> file = fileService.getById(id);
        if (file.isPresent()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; fileName=" + URLEncoder.encode(file.get().getOriginalName(), "utf-8"))
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .header(HttpHeaders.CONTENT_LENGTH, file.get().getFileSize() + "")
                    .header("Connection", "close")
                    .body(file.get().getFileContent());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(MessageConstant.FILE_NOT_FOUND);
        }
    }

    @PostMapping("/parse/{id}")
    @Operation(summary = "解析文件", description = "提交文件进行异步解析，包括OCR识别和数据提取")
    public AjaxJson parseFile(@Parameter(description = "文件id") @PathVariable String id) {
        return fileService.parseFile(id);
    }

    @GetMapping("/parse/status/{id}")
    @Operation(summary = "根据文件id获取解析状态", description = "查询文件的解析进度和状态")
    public AjaxJson getParseStatus(@Parameter(description = "文件id") @PathVariable String id) {
        return fileService.getParseStatus(id);
    }

    @GetMapping("/download/gridfs/{gridFsId}")
    @Operation(summary = "根据GridFS ID下载文件", description = "仅允许下载已登记在 FileRecord 上的主文件或缩略图 GridFS")
    public ResponseEntity<Object> downloadFileByGridFsId(
            @Parameter(description = "GridFS文件id") @PathVariable String gridFsId) throws IOException {
        if (!fileService.isRegisteredGridFsId(gridFsId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(MessageConstant.FILE_NOT_FOUND);
        }
        try {
            // 将字符串ID转为MongoDB的ObjectId
            ObjectId objectId = new ObjectId(gridFsId);

            // 打开下载流（从GridFS中读取文件）
            GridFSFile gridFSFile = gridFSBucket.find(new org.bson.Document("_id", objectId)).first();
            if (gridFSFile == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(MessageConstant.FILE_NOT_FOUND);
            }

            GridFsResource resource = new GridFsResource(gridFSFile, gridFSBucket.openDownloadStream(objectId));

            // 获取文件名，如果没有则使用默认名称
            String fileName = gridFSFile.getFilename();
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "file_" + gridFsId;
            }

            // 获取文件大小
            long fileSize = gridFSFile.getLength();

            // 读取文件内容为字节数组
            byte[] fileContent = IoUtil.readBytes(resource.getInputStream());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; fileName=" + URLEncoder.encode(fileName, "utf-8"))
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .header("Connection", "close")
                    .body(fileContent);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("文件下载失败");
        }
    }

    @GetMapping("/info/{id}")
    @Operation(summary = "根据文件id获取文件信息", description = "获取文件的基本信息，不包含文件内容")
    public AjaxJson getFileInfo(@Parameter(description = "文件id") @PathVariable String id) {
        try {
            Optional<FileRecord> fileOptional = fileService.getFileInfoById(id);
            if (fileOptional.isPresent()) {
                return AjaxJson.getSuccess("获取文件信息成功").setData(fileOptional.get());
            } else {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, MessageConstant.FILE_NOT_FOUND);
            }
        } catch (Exception e) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "获取文件信息失败");
        }
    }

    @PostMapping("/cancel/{fileId}")
    @Operation(summary = "取消文件解析任务", description = "取消指定文件的解析任务")
    public AjaxJson cancelParseTask(@Parameter(description = "文件id") @PathVariable String fileId,
            @Parameter(description = "取消原因") @RequestParam(value = "reason", defaultValue = "用户主动取消") String reason) {
        return fileService.cancelParseTask(fileId, reason);
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "根据项目id查看该项目的文件信息", description = "不传 archiveId 返回全部；传 archiveId 返回该归档夹下文件；传 unarchived=true 返回未归档文件（老数据）")
    public AjaxJson getFilesByProject(@Parameter(description = "项目id") @PathVariable Long projectId,
            @Parameter(description = "归档夹ID，不传则返回全部") @RequestParam(value = "archiveId", required = false) Long archiveId,
            @Parameter(description = "为 true 时仅返回未归档文件（archive_id 为空）") @RequestParam(value = "unarchived", required = false) Boolean unarchived) {
        try {
            List<FileRecord> files;
            if (Boolean.TRUE.equals(unarchived)) {
                files = fileService.getFilesByProjectIdAndArchiveId(projectId, null);
            } else if (archiveId != null) {
                files = fileService.getFilesByProjectIdAndArchiveId(projectId, archiveId);
            } else {
                files = fileService.getFilesByProjectId(projectId);
            }
            files.forEach(file -> file.setFileContent(null));
            return AjaxJson.getSuccess("获取项目文件列表成功").setData(files);
        } catch (Exception e) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "获取项目文件列表失败");
        }
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除文件及其所有相关数据", description = "删除文件及其所有相关数据，包括解析结果、业务数据和GridFS文件")
    public AjaxJson deleteFile(@Parameter(description = "文件id") @PathVariable String fileId) {
        return fileService.deleteFile(fileId);
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除文件及其所有相关数据", description = "根据文件ID列表批量删除文件及其所有相关数据，包括解析结果、业务数据和GridFS文件")
    public AjaxJson batchDeleteFiles(@Parameter(description = "文件ID列表") @RequestBody List<String> fileIds) {
        return fileService.batchDeleteFiles(fileIds);
    }

    @PostMapping("/query")
    @Operation(summary = "通用文件查询", description = "根据多个条件组合查询文件信息，支持分页和排序")
    public AjaxJson queryFiles(@Parameter(description = "查询条件") @RequestBody FileQueryDTO queryDTO) {
        return fileService.queryFiles(queryDTO);
    }

    @GetMapping("/task/status")
    @SaCheckRole(value = UserTypeConstants.DEVELOPER)
    @Operation(summary = "查询任务执行状态", description = "获取当前正在执行的任务、排队等待的任务以及线程池状态信息")
    public AjaxJson getTaskStatus() {
        try {
            TaskStatusDTO taskStatus = taskExecuteService.getTaskStatus();
            return AjaxJson.getSuccess("获取任务状态成功").setData(taskStatus);
        } catch (Exception e) {
            return AjaxJson.get(500, "获取任务状态失败");
        }
    }

    @GetMapping("/task/detail/{taskId}")
    @SaCheckRole(value = UserTypeConstants.DEVELOPER)
    @Operation(summary = "查询单任务详细进度", description = "返回单个任务当前阶段、进度和阶段轨迹")
    public AjaxJson getTaskDetail(@Parameter(description = "任务ID") @PathVariable String taskId) {
        try {
            TaskStatusDTO.RunningTaskInfo detail = taskExecuteService.getTaskDetail(taskId);
            if (detail == null) {
                return AjaxJson.get(404, "未找到任务或解析记录");
            }
            return AjaxJson.getSuccess("获取任务详情成功").setData(detail);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return AjaxJson.get(500, "获取任务详情失败");
        }
    }

    @GetMapping("/parse-job/{parseJobId}/flow")
    @Operation(summary = "解析任务流水线视图", description = "基于 Mongo 中 parse_job 的阶段字段，任务结束后仍可查看各阶段状态与耗时")
    public AjaxJson getParseJobFlow(@Parameter(description = "parse_job.id") @PathVariable Long parseJobId) {
        try {
            TaskStatusDTO.RunningTaskInfo detail = taskExecuteService.getParseJobFlowDetail(parseJobId);
            if (detail == null) {
                return AjaxJson.get(404, "解析任务不存在");
            }
            return AjaxJson.getSuccess("获取解析流水线成功").setData(detail);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return AjaxJson.get(500, "获取解析流水线失败");
        }
    }

    @PostMapping("/task/cancel/{taskId}")
    @SaCheckRole(value = UserTypeConstants.DEVELOPER)
    @Operation(summary = "按任务ID取消任务", description = "通过taskId直接取消任务，适合任务监控面板使用")
    public AjaxJson cancelTaskByTaskId(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "取消原因") @RequestParam(value = "reason", defaultValue = "用户主动取消") String reason) {
        try {
            return fileService.cancelParseTaskByTaskId(taskId, reason);
        } catch (Exception e) {
            return AjaxJson.get(500, "取消任务失败");
        }
    }

    @GetMapping("/task/system-status")
    @SaCheckRole(value = UserTypeConstants.DEVELOPER)
    @Operation(summary = "查询系统运行状态", description = "获取系统CPU/内存/线程/连接池/GPU等运行信息")
    public AjaxJson getSystemRuntimeStatus() {
        try {
            SystemRuntimeStatusDTO status = taskExecuteService.getSystemRuntimeStatus();
            return AjaxJson.getSuccess("获取系统运行状态成功").setData(status);
        } catch (Exception e) {
            return AjaxJson.get(500, "获取系统运行状态失败");
        }
    }

    @PostMapping("/task/enqueue-pending-parse")
    @SaCheckRole(value = UserTypeConstants.DEVELOPER)
    @Operation(summary = "全库待解析/失败文件入队", description = "将所有项目中 file_state 为 WAITING_PARSE 或 PARSE_FAIL 的可解析文件提交到解析线程池")
    public AjaxJson enqueuePendingAndFailedParse() {
        try {
            BulkParseEnqueueResultDTO result = globalParseEnqueueService.enqueuePendingAndFailed();
            String msg = buildBulkEnqueueMessage(result);
            return AjaxJson.getSuccess(msg).setData(result);
        } catch (Exception e) {
            return AjaxJson.get(500, "批量入队解析失败");
        }
    }

    private static String buildBulkEnqueueMessage(BulkParseEnqueueResultDTO result) {
        if (result == null) {
            return "批量入队完成";
        }
        if (result.isQueueFull() && result.getSubmitted() == 0) {
            if (result.getRemainingEstimate() > 0) {
                return String.format("解析线程池队列已满，约 %d 个文件仍待入队，请稍后再试", result.getRemainingEstimate());
            }
            return "解析线程池队列已满，请稍后再试";
        }
        if (result.getSubmitted() == 0 && result.getScanned() == 0) {
            return "当前没有待解析或解析失败的文件";
        }
        String base = String.format("已提交 %d 个解析任务（跳过 %d 个）", result.getSubmitted(), result.getSkipped());
        if (result.isQueueFull() && result.getRemainingEstimate() > 0) {
            return base + String.format("；线程池队列已满，约 %d 个文件仍待入队，请稍后再次操作", result.getRemainingEstimate());
        }
        return base;
    }

    @PostMapping("/task/pool-size")
    @SaCheckRole(value = UserTypeConstants.DEVELOPER)
    @Operation(summary = "动态调整解析并行度", description = "设置并行解析路数 N，同步更新线程池 core/max 与 parse-pipeline gate")
    public AjaxJson updateTaskPoolSize(@Valid @RequestBody ThreadPoolResizeDTO resizeDTO) {
        try {
            taskExecuteService.updateTaskPoolSize(resizeDTO);
            return AjaxJson.getSuccess("解析并行度更新成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, e.getMessage());
        } catch (Exception e) {
            return AjaxJson.get(500, "解析并行度更新失败");
        }
    }
}
