package hk.hkucs.memoryagent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public interface Llm {
    JsonNode complete(List<Map<String,Object>> messages, List<Map<String,Object>> tools);
    JsonNode json(String text);
}
