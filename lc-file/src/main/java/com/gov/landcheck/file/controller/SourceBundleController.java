package com.gov.landcheck.file.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.gov.landcheck.core.audit.OperatorContext;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.file.bundle.DownloadContentDisposition;
import com.gov.landcheck.file.bundle.SourceBundleDownloadGate;
import com.gov.landcheck.file.bundle.SourceBundlePlan;
import com.gov.landcheck.file.bundle.SourceBundleService;
import com.gov.landcheck.file.bundle.SourceBundleTicket;
import com.gov.landcheck.file.bundle.SourceBundleTicketStore;
import com.gov.landcheck.file.dto.SourceBundleManifestDTO;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 把一个项目的全部原件打成一个 zip。清单和票据走登录态；真正的下载走一次性票据，
 * 因为浏览器保存文件时不会带上 satoken 请求头。
 */
@Slf4j
@RestController
@RequestMapping("/file/project/{projectId}/source-bundle")
@Tag(name = "项目原件打包下载")
public class SourceBundleController {

    static final long DOWNLOAD_TIMEOUT_MILLIS = 30L * 60L * 1000L;

    private final SourceBundleService sourceBundleService;
    private final SourceBundleTicketStore ticketStore;
    private final SourceBundleDownloadGate downloadGate;

    public SourceBundleController(SourceBundleService sourceBundleService, SourceBundleTicketStore ticketStore,
            SourceBundleDownloadGate downloadGate) {
        this.sourceBundleService = sourceBundleService;
        this.ticketStore = ticketStore;
        this.downloadGate = downloadGate;
    }

    @GetMapping("/manifest")
    @Operation(summary = "项目原件打包清单", description = "返回可下载原件数量和精确压缩包大小，不读取文件内容")
    public AjaxJson manifest(@PathVariable Long projectId) {
        try {
            SourceBundlePlan plan = sourceBundleService.plan(projectId);
            if (plan.status() == SourceBundlePlan.Status.MISSING_PROJECT) {
                return AjaxJson.get(HttpStatus.NOT_FOUND.value(), "项目不存在");
            }
            if (plan.status() == SourceBundlePlan.Status.REJECTED) {
                return AjaxJson.get(HttpStatus.PAYLOAD_TOO_LARGE.value(), plan.rejectMessage());
            }
            SourceBundleManifestDTO dto = SourceBundleManifestDTO.builder()
                    .projectName(plan.projectName())
                    .fileCount(plan.fileCount())
                    .totalBytes(plan.totalBytes())
                    .missingCount(plan.missingCount())
                    .zipBytes(plan.zipBytes())
                    .build();
            return AjaxJson.getSuccess("获取原件清单成功", dto);
        } catch (IllegalStateException ex) {
            log.warn("获取原件清单失败: projectId={}, error={}", projectId, ex.getMessage());
            return AjaxJson.get(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage());
        }
    }

    @PostMapping("/ticket")
    @Operation(summary = "签发原件打包下载票据", description = "票据约 90 秒内一次性有效，并绑定当前用户和项目")
    public AjaxJson ticket(@PathVariable Long projectId) {
        long userId = StpUtil.getLoginIdAsLong();
        if (downloadGate.isBusy(userId)) {
            return AjaxJson.get(HttpStatus.TOO_MANY_REQUESTS.value(),
                    "上一次原件包还在下载。请查看浏览器的下载列表；结束后即可再次点击。");
        }
        String userName = OperatorContext.getOperatorName();
        if (userName == null || userName.isBlank()) {
            Object sessionName = StpUtil.getSession().get("username");
            userName = sessionName == null ? null : String.valueOf(sessionName);
        }
        String token = ticketStore.issue(userId, userName, projectId);
        return AjaxJson.getSuccess("可以开始下载", Map.of("ticket", token));
    }

    @GetMapping
    @Operation(summary = "下载项目全部原件", description = "使用一次性票据流式下载 zip，仅存储不压缩")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long projectId,
            @RequestParam(required = false) String ticket, HttpServletRequest request, HttpServletResponse response) {
        SourceBundleTicket peeked = ticketStore.peek(ticket, projectId);
        if (peeked == null) {
            return plain(HttpStatus.UNAUTHORIZED, "下载链接已失效，请重新发起下载");
        }
        if (!downloadGate.tryAcquire(peeked.userId())) {
            return plain(HttpStatus.TOO_MANY_REQUESTS, "上一次原件包还在下载。请查看浏览器的下载列表；结束后即可再次点击。");
        }
        SourceBundleTicket consumed = ticketStore.consume(ticket, projectId);
        if (consumed == null) {
            downloadGate.release(peeked.userId());
            return plain(HttpStatus.UNAUTHORIZED, "下载链接已失效，请重新发起下载");
        }

        AtomicBoolean released = new AtomicBoolean(false);
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                downloadGate.release(consumed.userId());
            }
        };
        boolean handedOff = false;
        try {
            SourceBundlePlan plan = sourceBundleService.plan(projectId);
            if (plan.status() == SourceBundlePlan.Status.MISSING_PROJECT) {
                return plain(HttpStatus.NOT_FOUND, "项目不存在");
            }
            if (plan.status() == SourceBundlePlan.Status.REJECTED) {
                return plain(HttpStatus.PAYLOAD_TOO_LARGE, plan.rejectMessage());
            }
            if (!plan.downloadable()) {
                return plain(HttpStatus.NOT_FOUND, "当前项目没有可下载的原件");
            }
            sourceBundleService.auditDownload(plan, projectId, consumed);
            extendDownloadTimeout(request, response, release);
            StreamingResponseBody body = output -> {
                try {
                    sourceBundleService.write(plan, output);
                } catch (RuntimeException | IOException ex) {
                    log.warn("原件打包写出失败: projectId={}, userId={}, error={}", projectId, consumed.userId(),
                            ex.getMessage());
                    throw ex;
                } finally {
                    release.run();
                }
            };
            // 必须声明 ResponseEntity<StreamingResponseBody>。返回 ResponseEntity<?> 时 Spring 会把
            // lambda
            // 交给消息转换器，并因 Content-Type=application/zip 抛出 HttpMessageNotWritableException。
            ResponseEntity<StreamingResponseBody> entity = ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            DownloadContentDisposition.attachment(plan.downloadFileName()))
                    .header(HttpHeaders.CONTENT_LENGTH, Long.toString(plan.zipBytes()))
                    .cacheControl(CacheControl.noStore())
                    .body(body);
            handedOff = true;
            return entity;
        } catch (RuntimeException ex) {
            log.warn("原件打包失败: projectId={}, userId={}, error={}", projectId, consumed.userId(), ex.getMessage());
            String message = ex instanceof IllegalStateException ? ex.getMessage() : "原件打包失败，请稍后再试";
            return plain(HttpStatus.INTERNAL_SERVER_ERROR, message);
        } finally {
            if (!handedOff) {
                release.run();
            }
        }
    }

    private static void extendDownloadTimeout(HttpServletRequest request, HttpServletResponse response,
            Runnable release) {
        WebAsyncManager manager = WebAsyncUtils.getAsyncManager(request);
        AsyncWebRequest asyncWebRequest = manager.getAsyncWebRequest();
        if (asyncWebRequest == null) {
            asyncWebRequest = new StandardServletAsyncWebRequest(request, response);
            manager.setAsyncWebRequest(asyncWebRequest);
        }
        asyncWebRequest.setTimeout(DOWNLOAD_TIMEOUT_MILLIS);
        asyncWebRequest.addErrorHandler(error -> release.run());
        asyncWebRequest.addCompletionHandler(release);
    }

    private static ResponseEntity<StreamingResponseBody> plain(HttpStatus status, String message) {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, DownloadContentDisposition.attachment("下载失败.txt"))
                .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(body.length))
                .cacheControl(CacheControl.noStore())
                .body(output -> output.write(body));
    }
}
