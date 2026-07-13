package com.qian.qianaiagent.evaluation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentEvalCase {
    private String name;
    private String prompt;
    private ExpectedBehavior expectedBehavior;
}
