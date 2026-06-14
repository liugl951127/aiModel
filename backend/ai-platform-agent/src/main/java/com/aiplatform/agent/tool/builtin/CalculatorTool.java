package com.aiplatform.agent.tool.builtin;

import com.aiplatform.agent.tool.AgentTool;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;

@Component
public class CalculatorTool implements AgentTool {

    private final ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "Evaluate a math expression, e.g. {\"expression\":\"2+3*4\"}";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\"}},\"required\":[\"expression\"]}";
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object expr = args.get("expression");
        if (expr == null) return "error: missing expression";
        try {
            Object result = engine.eval(expr.toString());
            return String.valueOf(result);
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}
