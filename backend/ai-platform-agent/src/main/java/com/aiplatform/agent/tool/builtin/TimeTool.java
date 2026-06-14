package com.aiplatform.agent.tool.builtin;

import com.aiplatform.agent.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class TimeTool implements AgentTool {

    @Override
    public String name() {
        return "current_time";
    }

    @Override
    public String description() {
        return "Return the current server time in ISO-8601 format.";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String execute(Map<String, Object> args) {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }
}
