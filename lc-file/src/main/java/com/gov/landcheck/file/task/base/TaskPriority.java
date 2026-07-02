package com.gov.landcheck.file.task.base;

/**
 * 任务优先级枚举
 *
 * @author system
 * @date 2026/01/27
 */
public enum TaskPriority {
    HIGH(1, "高优先级"),
    NORMAL(2, "普通优先级");

    private final int value;
    private final String description;

    TaskPriority(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}