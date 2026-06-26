package com.aiplatform.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ModelStatus {

    DRAFT("draft", "草稿"),
    TRAINING("training", "训练中"),
    READY("ready", "就绪"),
    FAILED("failed", "失败"),
    ARCHIVED("archived", "已归档");

    private final String code;
    private final String description;
}
