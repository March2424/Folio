package hk.hkucs.memoryagent;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:agenttest;DB_CLOSE_DELAY=-1","agent.token=integration-test-token"})
@AutoConfigureMockMvc
class AgentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired ScriptedLlm llm;
    @Autowired MemoryStore store;
    @Autowired MemorySearch search;
    static final String AUTH="Bearer integration-test-token";
    @TestConfiguration static class Config {
        @Bean @Primary ScriptedLlm scriptedLlm(ObjectMapper mapper) { return new ScriptedLlm(mapper); }
    }
    static class ScriptedLlm implements Llm {
        final Queue<JsonNode> responses=new ArrayDeque<>();
        final List<List<Map<String,Object>>> requests=new ArrayList<>();
        final ObjectMapper mapper;
        ScriptedLlm(ObjectMapper mapper) { this.mapper=mapper; }
        public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools) {
            requests.add(new ArrayList<>(messages));
            if (responses.isEmpty()) throw CompatibleLlm.invalid();
            return responses.remove();
        }
        public JsonNode json(String text) {
            try { return mapper.readTree(text); } catch(Exception e) { throw CompatibleLlm.invalid(); }
        }
        void answer(Object body) { responses.add(mapper.valueToTree(Map.of("content",write(body)))); }
        String write(Object body) { try{return mapper.writeValueAsString(body);}catch(Exception e){throw new RuntimeException(e);} }
    }
    @BeforeEach void reset() {
        db.update("DELETE FROM chat_messages");db.update("DELETE FROM traits");db.update("DELETE FROM memories");
        llm.responses.clear();llm.requests.clear();
    }
    JsonNode save(String id,String text) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/memories").header("Authorization",AUTH).contentType("application/json")
            .content(llm.write(Map.of("id",id,"title","我的阅读","kind","MATERIAL","content",text))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    void extract(String quote) {
        llm.answer(Map.of("summary","用户谈到重读小王子和安静的阅读习惯。","traits",List.of(
            Map.of("description","在这份材料中，用户喜欢安静地阅读","basis","EXPLICIT","evidence",quote),
            Map.of("description","用户是一名医生","basis","INFERRED","evidence","没有出现在原文中的话"))));
    }
    @Test void authenticationAndValidation() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/api/memories")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/memories").header("Authorization",AUTH).contentType("application/json")
            .content("{\"title\":\"x\",\"kind\":\"MATERIAL\",\"content\":\"hello\",\"occurredOn\":\"2026-02-31\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/memories").header("Authorization",AUTH).contentType("application/json")
            .content(llm.write(Map.of("title","x","kind","INVALID","content","hello")))).andExpect(status().isBadRequest());
    }
    @Test void uploadExtractConfirmChatAndDelete() throws Exception {
        String raw="我喜欢在安静的地方读书。今天重读了小王子。";
        var file=new MockMultipartFile("file","reading.md","text/markdown",raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String id=mapper.readTree(mvc.perform(multipart("/api/materials").file(file).header("Authorization",AUTH))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
        extract("我喜欢在安静的地方读书");
        mvc.perform(post("/api/memories/"+id+"/analyze").header("Authorization",AUTH)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"));
        assertThat(store.traits()).hasSize(1);
        assertThat(store.traits().get(0).status()).isEqualTo("CANDIDATE");
        String trait=store.traits().get(0).id();
        mvc.perform(patch("/api/profile/"+trait).header("Authorization",AUTH).contentType("application/json")
            .content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
        llm.answer(Map.of("answer","你在材料里写过喜欢安静地读书。["+id+"]","sourceIds",List.of(id)));
        mvc.perform(post("/api/chat").header("Authorization",AUTH).contentType("application/json")
            .content("{\"sessionId\":\"test\",\"message\":\"我有什么阅读习惯？\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].id").value(id))
            .andExpect(jsonPath("$.answer").value("你在材料里写过喜欢安静地读书。[1]"));
        assertThat(llm.requests.get(llm.requests.size()-1).toString()).contains("confirmedTraits","EXPLICIT",trait);
        assertThat(store.history("test").get(1).get("content").toString()).contains("安静地读书");
        mvc.perform(delete("/api/memories/"+id).header("Authorization",AUTH)).andExpect(status().isOk());
        assertThat(store.traits()).isEmpty();assertThat(store.history("test")).isEmpty();
        mvc.perform(get("/api/memories/"+id).header("Authorization",AUTH)).andExpect(status().isNotFound());
    }
    @Test void sourceUpdateInvalidatesTraitsAndStaleAnalysis() throws Exception {
        save("stable","我喜欢阅读"); var snapshot=store.get("stable");
        extract("我喜欢阅读");
        mvc.perform(post("/api/memories/stable/analyze").header("Authorization",AUTH)).andExpect(status().isOk());
        assertThat(store.traits()).hasSize(1);
        save("stable","我的兴趣发生了变化");
        assertThat(store.traits()).isEmpty();assertThat(store.get("stable").summary()).isEmpty();
        assertThatThrownBy(()->store.analysis(snapshot,"过期摘要",List.of())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(store.get("stable").content()).isEqualTo("我的兴趣发生了变化");
    }
    @Test void failedAnalysisPreservesOriginalAndCanRetry() throws Exception {
        save("retry","我喜欢阅读");
        mvc.perform(post("/api/memories/retry/analyze").header("Authorization",AUTH)).andExpect(status().isBadGateway());
        assertThat(store.get("retry").content()).isEqualTo("我喜欢阅读");
        assertThat(store.get("retry").status()).isEqualTo("PENDING");
        extract("我喜欢阅读");
        mvc.perform(post("/api/memories/retry/analyze").header("Authorization",AUTH)).andExpect(status().isOk());
        int calls=llm.requests.size();
        mvc.perform(post("/api/memories/retry/analyze").header("Authorization",AUTH)).andExpect(status().isOk());
        assertThat(llm.requests).hasSize(calls);
    }
    @Test void toolLoopReadsOriginalAndRejectsInventedSources() throws Exception {
        save("book","我重读了小王子");
        llm.responses.add(mapper.valueToTree(Map.of("tool_calls",List.of(Map.of("id","call1","type","function",
            "function",Map.of("name","read_memory","arguments","{\"id\":\"book\"}"))))));
        llm.answer(Map.of("answer","你重读了小王子。[book]","sourceIds",List.of("book")));
        mvc.perform(post("/api/chat").header("Authorization",AUTH).contentType("application/json")
            .content("{\"message\":\"读过什么？\"}")).andExpect(status().isOk());
        assertThat(llm.requests.get(1).toString()).contains("tool_call_id=call1","我重读了小王子");
        llm.answer(Map.of("answer","无依据","sourceIds",List.of("invented")));
        mvc.perform(post("/api/chat").header("Authorization",AUTH).contentType("application/json")
            .content("{\"message\":\"读过什么？\"}")).andExpect(status().isBadGateway());
    }
    @Test void oldRelevantMemorySurvivesFadingAndDateFiltersWork() throws Exception {
        save("old","十年前重读小王子");save("new","今天听爵士音乐");
        db.update("UPDATE memories SET created_at=1,occurred_on='2016-01-01' WHERE id='old'");
        assertThat(search.search("小王子",null,null,null,6)).extracting(Models.Memory::id).containsExactly("old");
        assertThat(search.search("","2026-01-01",null,null,6)).isEmpty();
        assertThat(search.search("","2016-01-01","2016-12-31",null,6)).extracting(Models.Memory::id).containsExactly("old");
    }
    @Test void rejectsNonTextFilesAndBadUtf8() throws Exception {
        mvc.perform(multipart("/api/materials").file(new MockMultipartFile("file","x.pdf","application/pdf",new byte[]{1}))
            .header("Authorization",AUTH)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/materials").file(new MockMultipartFile("file","x.txt","text/plain",new byte[]{(byte)0xff}))
            .header("Authorization",AUTH)).andExpect(status().isBadRequest());
    }
    @Test void mutationDuringGenerationPreventsPersistingStaleAnswer() throws Exception {
        save("removed","旧记忆");long epoch=store.epoch();store.delete("removed");
        assertThatThrownBy(()->store.commitChat(epoch,"session","问题","过期回答",Set.of("removed")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(store.history("session")).isEmpty();
    }
}
