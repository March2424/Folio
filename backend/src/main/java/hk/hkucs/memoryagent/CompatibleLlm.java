package hk.hkucs.memoryagent;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** OpenAI-compatible chat/completions protocol; no provider key ever reaches Android. */
@Component
public class CompatibleLlm implements Llm {
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String apiKey, model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public CompatibleLlm(ObjectMapper mapper, @Value("${agent.llm.base-url}") String base,
            @Value("${agent.llm.api-key}") String apiKey, @Value("${agent.llm.model}") String model) {
        this.mapper = mapper; this.apiKey = apiKey; this.model = model;
        endpoint = URI.create(base.replaceAll("/+$", "") + "/chat/completions");
        if (!Set.of("http", "https").contains(endpoint.getScheme())) throw new IllegalArgumentException("LLM URL must use HTTP(S)");
    }
    public JsonNode complete(List<Map<String,Object>> messages, List<Map<String,Object>> tools) {
        if (apiKey.isBlank() || model.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请在后端配置 LLM_API_KEY 与 LLM_MODEL；原始材料已保留，可稍后重试整理");
        try {
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("model", model); body.put("messages", messages); body.put("max_tokens", 1800);
            if (!tools.isEmpty()) { body.put("tools", tools); body.put("tool_choice", "auto"); }
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var stream = response.body()) { bytes = stream.readNBytes(2_000_001); }
            if (response.statusCode() < 200 || response.statusCode() >= 300 || bytes.length > 2_000_000)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型服务返回异常，请检查后端模型配置或稍后重试");
            JsonNode message = mapper.readTree(bytes).path("choices").path(0).path("message");
            if (!message.isObject()) throw new IllegalArgumentException("invalid model response");
            return message;
        } catch (ResponseStatusException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型请求被中断");
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型连接超时或返回格式不正确，请重试"); }
    }
    public JsonNode json(String text) {
        if (text == null || text.length() > 100000) throw invalid();
        String clean = text.strip().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        try {
            JsonNode result = mapper.readTree(clean);
            if (result == null || !result.isObject()) throw invalid();
            return result;
        } catch (Exception e) { throw invalid(); }
    }
    public static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型返回格式不正确，请重新尝试");
    }
}
