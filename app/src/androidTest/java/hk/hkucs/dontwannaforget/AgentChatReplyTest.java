package hk.hkucs.dontwannaforget;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AgentChatReplyTest {
    Map<String,JSONObject> sources;
    @Before public void setup() throws Exception {sources=new LinkedHashMap<>();sources.put("movie-one",new JSONObject().put("id","movie-one").put("title","花样年华"));sources.put("movie-two",new JSONObject().put("id","movie-two").put("title","另一部电影"));}
    JSONObject parse(Object content) throws Exception {return AgentChatReply.parse(content,sources);}
    @Test public void naturalClarificationNeedsNeitherJsonNorCitations() throws Exception {
        JSONObject reply=parse("你说的是刚才提到的《花样年华》吗？你没有记录具体原因，我不能确定你的动机。");assertTrue(reply.getString("answer").contains("不能确定"));assertEquals(0,reply.getJSONArray("sources").length());
    }
    @Test public void markdownAndExplanatoryProseAroundJsonAreAccepted() throws Exception {
        JSONObject reply=parse("以下是回答：\n```json\n{\"answer\":\"你写过喜欢电影的气氛。[movie-one]\",\"sourceIds\":[\"movie-one\"]}\n```\n以上供参考。");assertEquals("你写过喜欢电影的气氛。[1]",reply.getString("answer"));assertEquals(1,reply.getJSONArray("sources").length());
    }
    @Test public void missingSourceListCanRecoverInlineIdsAndTitles() throws Exception {
        assertEquals(1,parse("{\"answer\":\"你记录过这部片。[movie-one]\"}").getJSONArray("sources").length());
        assertEquals("你记录过这部片。[1]",parse("你记录过这部片。【花样年华】").getString("answer"));
    }
    @Test public void aliasesObjectsNullAndStringSourcesAreTolerated() throws Exception {
        JSONObject reply=parse("{\"response\":{\"text\":\"你提过这部电影。[1]\"},\"sources\":[{\"id\":\"movie-one\"}]}");assertEquals("movie-one",reply.getJSONArray("sources").getJSONObject(0).getString("id"));
        assertEquals("正常回答",parse("{\"reply\":\"正常回答\",\"sourceIds\":null}").getString("answer"));
        assertEquals(1,parse("{\"answer\":\"有记录。\",\"source_ids\":\"movie-one\"}").getJSONArray("sources").length());
    }
    @Test public void numberedCitationsUseDeclaredOrderAndDoNotChangeTwice() throws Exception {
        JSONObject reply=parse("{\"answer\":\"后一部[1]，前一部[2]，后一部[movie-two]。\",\"sourceIds\":[\"movie-two\",\"movie-one\"]}");assertEquals("后一部[1]，前一部[2]，后一部[1]。",reply.getString("answer"));assertEquals("movie-two",reply.getJSONArray("sources").getJSONObject(0).getString("id"));
    }
    @Test public void invalidAndDuplicateSourcesDoNotDiscardReadableAnswer() throws Exception {
        JSONObject reply=parse("{\"answer\":\"有效记录[movie-one]和无法核实的记录[fake-source]。\",\"sourceIds\":[\"movie-one\",null,{},\"movie-one\",\"fake-source\",99]}");assertEquals(1,reply.getJSONArray("sources").length());assertTrue(reply.getString("answer").contains("未能核实"));assertFalse(reply.getString("answer").contains("fake-source"));
    }
    @Test public void textContentPartsAreAcceptedAndReasoningIsExcluded() throws Exception {
        JSONArray content=new JSONArray().put(new JSONObject().put("type","reasoning").put("text","private reasoning"))
            .put(new JSONObject().put("type","text").put("text","第一段"))
            .put(new JSONObject().put("type","text").put("text","第二段"));assertEquals("第一段\n第二段",parse(content).getString("answer"));
        assertEquals("不知道你当时的动机。",parse("<think>private reasoning</think>不知道你当时的动机。").getString("answer"));
    }
    @Test public void cutOffMetadataDoesNotLoseCompleteAnswer() throws Exception {
        JSONObject reply=parse("{\"answer\":\"你当时写过\\\"喜欢氛围\\\"。[movie-one]\",\"sourceIds\":[");assertEquals("你当时写过\"喜欢氛围\"。[1]",reply.getString("answer"));
    }
    @Test public void emptyAndReasoningOnlyRepliesStillFailClearly() throws Exception {
        for(Object content:new Object[]{JSONObject.NULL," ","<think>private reasoning</think>","{\"reasoning_content\":\"private reasoning\"}"})try{parse(content);fail();}catch(IOException expected){assertFalse(expected.getMessage().contains("格式不正确"));}
    }
    @Test public void ambiguousTitlesAreNotInventedCitations() throws Exception {
        sources.get("movie-two").put("title","花样年华");assertEquals(0,parse("哪一版的《花样年华》？").getJSONArray("sources").length());
    }
    @Test public void followUpIncludesActualLastMovieAndPersistsPlainAnswer() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="follow-up-"+UUID.randomUUID()+".db";
        try(AgentStore store=new AgentStore(context,name)){
            store.save(new JSONObject().put("id","movie-one").put("title","花样年华").put("kind","MOVIE").put("content","我想再看一次那种克制的表达。"));
            store.commitChat(store.epoch(),"followup","我看过什么电影？","花样年华[1]",Collections.singleton("movie-one"));
            store.commitChat(store.epoch(),"followup","为什么？","你是想问当时的观影原因吗？",Collections.emptySet());
            LocalMemoryAgentTest.FakeLlm llm=new LocalMemoryAgentTest.FakeLlm();llm.replies.add(new JSONObject().put("content","你写过想再看那种克制的表达。[1]"));
            JSONObject result=new LocalMemoryAgent(store,llm).chat("followup","我为什么要看这个电影？");
            String ref=llm.requests.get(0).getJSONObject(1).getString("content");JSONObject data=new JSONObject(ref.substring(ref.indexOf('\n')+1));
            assertEquals("movie-one",data.getJSONArray("conversationMemories").getJSONObject(0).getString("id"));assertTrue(data.toString().contains("克制的表达"));
            assertEquals("movie-one",result.getJSONArray("sources").getJSONObject(0).getString("id"));assertEquals(6,store.transcript("followup").length());
        }finally{context.deleteDatabase(name);}
    }
    @Test public void changedSourcesNeverReenterFollowUpContext() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="follow-up-"+UUID.randomUUID()+".db";
        try(AgentStore store=new AgentStore(context,name)){
            JSONObject input=new JSONObject().put("id","movie-one").put("title","花样年华").put("kind","MOVIE").put("content","旧动机");store.save(input);store.commitChat(store.epoch(),"followup","电影？","旧答案[1]",Collections.singleton("movie-one"));store.save(input.put("content","修改后的记录"));
            LocalMemoryAgentTest.FakeLlm llm=new LocalMemoryAgentTest.FakeLlm();llm.replies.add(new JSONObject().put("content","你指的是哪一部电影？"));new LocalMemoryAgent(store,llm).chat("followup","为什么看这个？");String ref=llm.requests.get(0).getJSONObject(1).getString("content");assertEquals(0,new JSONObject(ref.substring(ref.indexOf('\n')+1)).getJSONArray("conversationMemories").length());
        }finally{context.deleteDatabase(name);}
    }
    @Test public void compatibleHttpTextPartsCanBeDisplayedWithoutJsonAnswerEnvelope() throws Exception {
        try(LocalMemoryAgentTest.ModelStub stub=new LocalMemoryAgentTest.ModelStub(1,(index,body)->new JSONObject().put("content",new JSONArray()
            .put(new JSONObject().put("type","text").put("text","你指的是刚才的《花样年华》吗？"))))){
            AgentModelClient client=new AgentModelClient(new ModelSettings.Snapshot(stub.endpoint(),"test-model","test-key"));
            JSONObject message=client.complete(new JSONArray().put(new JSONObject().put("role","user").put("content","我为什么要看这个电影？")),new JSONArray());
            assertEquals("你指的是刚才的《花样年华》吗？",parse(message.opt("content")).getString("answer"));stub.await();
        }
    }
    @Test public void structuredExtractionStillRejectsPlainProse() throws Exception {
        try{AgentModelClient.parseJson("一段普通说明，不是结构化材料。");fail();}catch(IOException expected){}
        assertEquals("大括号 {仍然是字符串}",AgentModelClient.parseJson("说明：{\"summary\":\"大括号 {仍然是字符串}\"} 结束").getString("summary"));
    }
}
