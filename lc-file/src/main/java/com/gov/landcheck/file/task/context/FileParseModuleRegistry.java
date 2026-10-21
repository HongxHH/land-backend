package com.gov.landcheck.file.task.context;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;

/**
 * 按 {@link FileContextType} 索引 {@link FileParseModule}。自动解析类型必须恰好有一个模块。
 */
@Component
public class FileParseModuleRegistry {

    private final Map<FileContextType, FileParseModule> modules;

    public FileParseModuleRegistry(List<FileParseModule> modules) {
        EnumMap<FileContextType, FileParseModule> byType = new EnumMap<>(FileContextType.class);
        for (FileParseModule module : modules) {
            FileParseModule previous = byType.put(module.type(), module);
            if (previous != null) {
                throw new IllegalStateException("重复的文件解析模块: " + module.type());
            }
        }
        for (FileContextType type : FileContextType.values()) {
            if (type.isAutoParseContext() && !byType.containsKey(type)) {
                throw new IllegalStateException("缺少文件解析模块: " + type);
            }
        }
        this.modules = Map.copyOf(byType);
    }

    public FileParseModule find(FileContextType type) {
        if (type == null) {
            return null;
        }
        return modules.get(type);
    }

    public FileParseModule require(FileContextType type) {
        FileParseModule module = find(type);
        if (module == null) {
            throw new IllegalStateException("不支持的文件解析类型: " + type);
        }
        return module;
    }

    public List<StageDef> stagesFor(FileContextType contextType) {
        FileParseModule module = find(contextType);
        if (module != null) {
            return module.stages();
        }
        return ParsePipelinePlan.defaultStages();
    }
}
