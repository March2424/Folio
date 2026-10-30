package hk.hkucs.memoryagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:httpserver;DB_CLOSE_DELAY=-1","agent.token=http-server-test-token", "agent.llm.api-key=", "agent.llm.model="})
class ServerHttpTest {
    @LocalServerPort int port;
    final RestTemplate http=new RestTemplate();
    @Test void actualServerAcceptsAuthenticatedRequestsAndPreservesRawTextWithoutModelConfig() {
        String root="http://127.0.0.1:"+port;
        assertThat(http.getForObject(root+"/health",Map.class)).containsEntry("status","ok");
        HttpHeaders headers=new HttpHeaders();headers.setBearerAuth("http-server-test-token");headers.setContentType(MediaType.APPLICATION_JSON);
        var saved=http.exchange(root+"/api/memories",HttpMethod.POST,new HttpEntity<>(Map.of("title","HTTP材料","kind","MATERIAL","content","我的阅读记录"),headers),Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        String id=saved.getBody().get("id").toString();
        var fetched=http.exchange(root+"/api/memories/"+id,HttpMethod.GET,new HttpEntity<>(headers),Map.class);
        assertThat(fetched.getBody()).containsEntry("content","我的阅读记录").containsEntry("status","PENDING");
        assertThatThrownBy(()->http.exchange(root+"/api/memories/"+id+"/analyze",HttpMethod.POST,new HttpEntity<>(Map.of(),headers),Map.class))
            .isInstanceOf(org.springframework.web.client.HttpServerErrorException.ServiceUnavailable.class);
        http.exchange(root+"/api/memories/"+id,HttpMethod.DELETE,new HttpEntity<>(headers),Map.class);
    }
}
