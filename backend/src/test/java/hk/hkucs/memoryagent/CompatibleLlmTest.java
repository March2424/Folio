package hk.hkucs.memoryagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CompatibleLlmTest {
    HttpServer server;
    ObjectMapper mapper=new ObjectMapper();
    String requestBody, authorization;
    int responseStatus=200;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            authorization=exchange.getRequestHeaders().getFirst("Authorization");
            requestBody=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            byte[] body=(responseStatus==200 ? "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"answer\\\":\\\"你好\\\",\\\"sourceIds\\\":[]}\"}}]}" : "upstream-secret-error")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(responseStatus,body.length);
            try(var out=exchange.getResponseBody()){out.write(body);}
        });
        server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    CompatibleLlm client(){return new CompatibleLlm(mapper,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1/","fake-key","test-model");}
    @Test void sendsCompatibleRequestAndParsesActualHttpResponse() throws Exception {
        var response=client().complete(List.of(Map.of("role","user","content","你好")),List.of());
        assertThat(client().json(response.path("content").asText()).path("answer").asText()).isEqualTo("你好");
        assertThat(authorization).isEqualTo("Bearer fake-key");
        assertThat(mapper.readTree(requestBody).path("model").asText()).isEqualTo("test-model");
    }
    @Test void doesNotExposeProviderErrorOrSecrets(){
        responseStatus=401;
        assertThatThrownBy(()->client().complete(List.of(Map.of("role","user","content","x")),List.of()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageNotContaining("upstream-secret-error");
    }
    @Test void missingConfigFailsExplicitlyAndInvalidJsonIsRejected(){
        var missing=new CompatibleLlm(mapper,"https://example.com/v1","","");
        assertThatThrownBy(()->missing.complete(List.of(),List.of())).hasMessageContaining("LLM_API_KEY");
        assertThatThrownBy(()->client().json("[]")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(client().json("```json\n{\"ok\":true}\n```").path("ok").asBoolean()).isTrue();
    }
}
