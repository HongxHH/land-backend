package com.gov.landcheck.file.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.gov.landcheck.file.service.StaleUploadRecoveryService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用启动时清理因进程崩溃而遗留的 UPLOADING / WAITING_POST_PROCESS。
 * 带 startup-grace-ms 宽限期，避免误杀启动瞬间新写入的记录。
 */
@Slf4j
@Component
@Order(1)
public class StaleUploadRecoveryRunner implements ApplicationRunner {

    @Resource
    private StaleUploadRecoveryService staleUploadRecoveryService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            staleUploadRecoveryService.recoverOnStartup();
            log.info("僵死上传状态启动恢复完成");
        } catch (Exception e) {
            log.error("僵死上传状态启动恢复失败: {}", e.getMessage(), e);
        }
    }
}
