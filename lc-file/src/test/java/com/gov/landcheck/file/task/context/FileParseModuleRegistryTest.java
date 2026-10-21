package com.gov.landcheck.file.task.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.base.Command;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;
import com.gov.landcheck.file.task.processor.command.FillCommand;
import com.gov.landcheck.file.task.processor.command.OCRCommand;
import com.gov.landcheck.file.task.processor.command.ParseCommand;
import com.gov.landcheck.file.task.processor.command.PreprocessCommand;
import com.gov.landcheck.file.task.processor.command.ValidateCommand;

class FileParseModuleRegistryTest {

    @Test
    void eachAutoParseContextHasExactlyOneModuleAndKnownStageCodes() {
        List<FileParseModule> modules = FileParseModulesFixture.modules();
        new FileParseModuleRegistry(modules);

        Set<FileContextType> expected = Arrays.stream(FileContextType.values())
                .filter(type -> type.isAutoParseContext())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<FileContextType> registered = modules.stream()
                .map(FileParseModule::type)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(expected, registered);

        Map<String, Command> commands = stageCommands();
        for (FileParseModule module : modules) {
            for (StageDef stage : module.stages()) {
                assertNotNull(commands.get(stage.code()), module.type() + " 缺少阶段命令 " + stage.code());
            }
            boolean hasValidate = module.stages().stream().anyMatch(s -> "VALIDATE".equals(s.code()));
            assertEquals(module.type() == FileContextType.SURVEY_REPORT, hasValidate, module.type().name());
        }
    }

    @Test
    void duplicateTypeFailsAtConstruction() {
        List<FileParseModule> modules = new ArrayList<>(FileParseModulesFixture.modules());
        modules.add(FileParseModulesFixture.modules().getFirst());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new FileParseModuleRegistry(modules));
        assertEquals("重复的文件解析模块: CONTRACT", error.getMessage());
    }

    @Test
    void missingAutoParseTypeFailsAtConstruction() {
        List<FileParseModule> modules = new ArrayList<>(FileParseModulesFixture.modules());
        modules.removeIf(module -> module.type() == FileContextType.SURVEY_REPORT);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new FileParseModuleRegistry(modules));
        assertEquals("缺少文件解析模块: SURVEY_REPORT", error.getMessage());
    }

    @Test
    void unregisteredTypesUseDefaultStages() {
        FileParseModuleRegistry registry = FileParseModulesFixture.registry();
        List<String> expected = ParsePipelinePlan.defaultStages().stream().map(StageDef::code).toList();
        assertEquals(expected, codes(registry.stagesFor(null)));
        assertEquals(expected, codes(registry.stagesFor(FileContextType.DATA_FILE)));
        assertEquals(expected, codes(registry.stagesFor(FileContextType.OTHER)));
    }

    private static List<String> codes(List<StageDef> stages) {
        return stages.stream().map(StageDef::code).toList();
    }

    private static Map<String, Command> stageCommands() {
        PreprocessCommand preprocessCommand = new PreprocessCommand();
        OCRCommand ocrCommand = new OCRCommand();
        ParseCommand parseCommand = new ParseCommand();
        FillCommand fillCommand = new FillCommand();
        ValidateCommand validateCommand = new ValidateCommand();
        return Map.of(
                preprocessCommand.getStage(), preprocessCommand,
                ocrCommand.getStage(), ocrCommand,
                parseCommand.getStage(), parseCommand,
                fillCommand.getStage(), fillCommand,
                validateCommand.getStage(), validateCommand);
    }
}
