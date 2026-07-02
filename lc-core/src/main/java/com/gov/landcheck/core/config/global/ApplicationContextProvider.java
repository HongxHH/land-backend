package com.gov.landcheck.core.config.global;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 应用上下文提供者，用于在非 Spring 管理类中获取容器中的 Bean。
 * <p>
 * 仅应在 Spring 容器完成 {@link #setApplicationContext} 之后使用；在此之前调用
 * {@link #getApplicationContext()} 或 {@link #getBean(Class)} 将抛出
 * {@link IllegalStateException}。
 * </p>
 *
 * @author system
 * @date 2025/01/20
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextProvider.applicationContext = applicationContext;
    }

    /**
     * 获取应用上下文
     *
     * @return ApplicationContext
     * @throws IllegalStateException 上下文尚未就绪
     */
    public static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化，请勿在容器启动前调用 ApplicationContextProvider");
        }
        return applicationContext;
    }

    /**
     * 根据 bean 名称获取 bean
     *
     * @param name bean 名称
     * @return bean 实例
     */
    public static Object getBean(String name) {
        return getApplicationContext().getBean(name);
    }

    /**
     * 根据 bean 类型获取 bean
     *
     * @param clazz bean 类型
     * @return bean 实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    /**
     * 根据 bean 名称和类型获取 bean
     *
     * @param name  bean 名称
     * @param clazz bean 类型
     * @return bean 实例
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }
}
