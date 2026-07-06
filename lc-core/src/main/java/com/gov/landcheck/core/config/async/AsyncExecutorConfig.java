package com.gov.landcheck.core.config.async;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import com.gov.landcheck.core.config.logging.ContextPropagatingExecutorService;
import com.gov.landcheck.core.config.logging.TraceContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 通用异步任务执行器配置，供整个项目 @Async 使用。
 * 基于 JDK 21 虚拟线程执行器，适合 I/O 阻塞型轻量异步任务。
 * <p>
 * {@link #applicationTaskExecutor()} 使用 {@code static} 工厂方法注册
 * Bean，避免配置类实例方法自调用导致
 * 重复创建执行器；{@link AsyncConfigurer#getAsyncExecutor()} 返回容器注入的同一单例。
 * </p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncExecutorConfig implements AsyncConfigurer {

    private static final String APPLICATION_TASK_EXECUTOR_BEAN_NAME = "applicationTaskExecutor";

    private ExecutorService applicationTaskExecutor;

    /**
     * 静态 @Bean 由容器直接调用，不经过配置类代理的自调用路径，保证单例与 destroy 生效。
     */
    @Bean(name = APPLICATION_TASK_EXECUTOR_BEAN_NAME, destroyMethod = "close")
    public static ExecutorService applicationTaskExecutor() {
        return new ContextPropagatingExecutorService(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Autowired
    public void setApplicationTaskExecutor(
            @Qualifier(APPLICATION_TASK_EXECUTOR_BEAN_NAME) ExecutorService applicationTaskExecutor) {
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Async method {} threw, traceId={}",
                method, TraceContext.getTraceId(), ex);
    }

}
