package com.aiplatform.agent.engine;

import com.aiplatform.agent.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String answer;
    private Integer steps;
    private List<Message> trace;
}
