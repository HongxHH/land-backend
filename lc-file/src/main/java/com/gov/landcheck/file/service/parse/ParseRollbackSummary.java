package com.gov.landcheck.file.service.parse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

/**
 * 解析管道回滚执行摘要，用于日志与可观测性。
 */
@Getter
public class ParseRollbackSummary {

    private final List<String> successes = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();

    public void recordSuccess(String message) {
        if (message != null && !message.isBlank()) {
            successes.add(message);
        }
    }

    public void recordFailure(String stage, String error) {
        failures.add(stage + ": " + (error != null ? error : "unknown"));
    }

    public void merge(ParseRollbackSummary other) {
        if (other == null) {
            return;
        }
        successes.addAll(other.successes);
        failures.addAll(other.failures);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public List<String> getSuccessesView() {
        return Collections.unmodifiableList(successes);
    }

    public List<String> getFailuresView() {
        return Collections.unmodifiableList(failures);
    }

    @Override
    public String toString() {
        return "ParseRollbackSummary{successes=" + successes.size() + ", failures=" + failures + "}";
    }
}
