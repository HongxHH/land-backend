package com.gov.landcheck.file.task.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 管道构建器
 * 用于按需组装 Command 序列并构建 Pipeline 实例
 *
 * @author system
 * @date 2026/02/28
 */
public class PipelineBuilder {

    private final String pipelineName;
    private final List<Command> commands = new ArrayList<>();

    public PipelineBuilder(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public PipelineBuilder add(Command command) {
        this.commands.add(command);
        return this;
    }

    public PipelineBuilder addAll(Command... commands) {
        this.commands.addAll(Arrays.asList(commands));
        return this;
    }

    public PipelineBuilder addAll(List<Command> commands) {
        this.commands.addAll(commands);
        return this;
    }

    public Pipeline build() {
        return new Pipeline(pipelineName, commands);
    }
}
