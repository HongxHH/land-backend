package com.gov.landcheck.file.task.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.base.Command;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;
import com.gov.landcheck.file.task.processor.command.FillCommand;
import com.gov.landcheck.file.task.processor.command.OCRCommand;
import com.gov.landcheck.file.task.processor.command.ParseCommand;
import com.gov.landcheck.file.task.processor.command.PreprocessCommand;
import com.gov.landcheck.file.task.processor.command.ValidateCommand;

class ParseFileExecutorCommandsTest {

        private static final Map<FileContextType, List<String>> EXPECTED_STAGES = Map.of(
                        FileContextType.PROJECT_PARTY_SURVEY_SUMMARY, List.of("PARSE", "FILL"),
                        FileContextType.SURVEY_REPORT, List.of("PREPROCESS", "OCR", "PARSE", "FILL", "VALIDATE"),
                        FileContextType.CAPACITY_INDICATOR, List.of("OCR", "PARSE", "FILL"),
                        FileContextType.CONTRACT, List.of("PREPROCESS", "OCR", "PARSE", "FILL"),
                        FileContextType.PLANNING_REVIEW, List.of("PREPROCESS", "OCR", "PARSE", "FILL"),
                        FileContextType.DATA_FILE, List.of("PREPROCESS", "OCR", "PARSE", "FILL"),
                        FileContextType.OTHER, List.of("PREPROCESS", "OCR", "PARSE", "FILL"));

        @Test
        void eachFileTypeKeepsItsCurrentStages() {
                Map<String, Command> commands = realCommands();
                assertEquals(FileContextType.values().length, EXPECTED_STAGES.size());
                for (FileContextType contextType : FileContextType.values()) {
                        List<String> planned = ParsePipelinePlan.stagesFor(contextType).stream()
                                        .map(ParsePipelinePlan.StageDef::code)
                                        .toList();
                        List<String> resolved = ParseFileExecutor.commandsFor(contextType, commands).stream()
                                        .map(Command::getStage)
                                        .toList();
                        assertEquals(EXPECTED_STAGES.get(contextType), planned, contextType.name());
                        assertEquals(planned, resolved, contextType.name());
                }
        }

        @Test
        void missingFileTypeFailsBeforeAnyStage() {
                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> ParseFileExecutor.commandsFor(null, realCommands()));
                assertEquals("解析管道缺少文件内容类型，无法确定执行阶段", error.getMessage());
        }

        @Test
        void unknownStageCodeFailsBeforeExecution() {
                Map<String, Command> commands = Map.of(
                                "PARSE", new ParseCommand(),
                                "FILL", new FillCommand());
                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> ParseFileExecutor.commandsFor(FileContextType.CAPACITY_INDICATOR, commands));
                assertEquals("解析管道无法执行阶段 OCR，文件类型 CAPACITY_INDICATOR", error.getMessage());
        }

        private static Map<String, Command> realCommands() {
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
