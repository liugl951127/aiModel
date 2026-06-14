package com.aiplatform.workflow.engine;

/**
 * Append-only log used by a step handler during a workflow run.
 * Implementations typically wrap a slf4j logger.
 */
public interface StepLog {
    void info(String msg);
    void error(String msg, Throwable t);
}
