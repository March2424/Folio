package hk.hkucs.dontwannaforget;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Device tests use isolated databases and a device-local model stub, never user credentials or paid APIs. */
@RunWith(AndroidJUnit4.class)
public class LocalMemoryAgentTest {
    Context context;
    AgentStore store;
    String databaseName;
    FakeLlm llm;
    LocalMemoryAgent agent;
    @Before public void setup(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();databaseName="agent-test-"+UUID.randomUUID()+".db";
        store=new AgentStore(context,databaseName);llm=new FakeLlm();agent=new LocalMemoryAgent(store,llm);
    }
    @After public void cleanup(){store.close();context.deleteDatabase(databaseName);}
    JSONObject save(String id,String content) throws Exception {
        return agent.save(new JSONObject().put("id",id).put("title","我的阅读").put("kind","MATERIAL").put("content",content));
    }
    JSONObject extraction(String quote) throws Exception {
        return new JSONObject().put("summary","用户谈到了安静阅读的习惯。")
            .put("traits",new JSONArray().put(new JSONObject().put("description","在这份材料中，用户喜欢安静地阅读")
                .put("basis","EXPLICIT").put("evidence",quote))
                .put(new JSONObject().put("description","用户是一名医生").put("basis","INFERRED").put("evidence","原文不存在的证据")));
    }
    static final class FakeLlm implements AgentLlm {
        final Queue<JSONObject> replies=new ArrayDeque<>();
        final List<JSONArray> requests=new ArrayList<>();
        boolean cancelled;
        void answer(JSONObject body) throws Exception{replies.add(new JSONObject().put("content",body.toString()));}
        @Override public JSONObject complete(JSONArray messages,JSONArray tools) throws Exception {
            if(messages.toString().contains("SEMANTIC_RETRIEVAL"))return new JSONObject().put("content","{\"queries\":[],\"matches\":[]}");
            requests.add(new JSONArray(messages.toString()));if(cancelled||replies.isEmpty())throw new IOException("测试模型未返回结果");return replies.remove();
        }
        @Override public void cancel(){cancelled=true;}
    }
    @Test public void rawMaterialsPersistWithoutModelSettingsAndSurviveReopen() throws Exception {
        save("offline","我的原始文字");assertEquals(0,llm.requests.size());
        store.close();store=new AgentStore(context,databaseName);
        assertEquals("我的原始文字",store.get("offline").getString("content"));assertEquals("PENDING",store.get("offline").getString("status"));
    }
    @Test public void failedAnalysisKeepsRawMaterialAndCanRetry() throws Exception {
        save("retry","我喜欢安静阅读");
        try{agent.analyze("retry");fail("expected failure");}catch(IOException expected){}
        assertEquals("我喜欢安静阅读",store.get("retry").getString("content"));assertEquals("PENDING",store.get("retry").getString("status"));
        llm.answer(extraction("我喜欢安静阅读"));assertEquals("READY",agent.analyze("retry").getString("status"));
        int calls=llm.requests.size();agent.analyze("retry");assertEquals(calls,llm.requests.size());
    }
    @Test public void traitsNeedVerbatimEvidenceAndConfirmationBeforeUse() throws Exception {
        save("source","我喜欢安静阅读");llm.answer(extraction("我喜欢安静阅读"));agent.analyze("source");
        JSONArray traits=agent.profile();assertEquals(1,traits.length());assertEquals("CANDIDATE",traits.getJSONObject(0).getString("status"));
        llm.answer(new JSONObject().put("answer","从记录看，你喜欢安静阅读。[source]").put("sourceIds",new JSONArray().put("source")));
        agent.chat("before","我有什么习惯？");
        assertEquals(0,reference(llm.requests.get(1)).getJSONArray("confirmedTraits").length());
        String traitId=traits.getJSONObject(0).getString("id");agent.setTrait(traitId,"CONFIRMED");
        llm.answer(new JSONObject().put("answer","你写过喜欢安静阅读。[source]").put("sourceIds",new JSONArray().put("source")));
        JSONObject answer=agent.chat("after","我有什么习惯？");
        assertEquals(1,reference(llm.requests.get(2)).getJSONArray("confirmedTraits").length());assertEquals("你写过喜欢安静阅读。[1]",answer.getString("answer"));
        agent.setTrait(traitId,"REJECTED");assertEquals(0,store.history("after").length());
    }
    private JSONObject reference(JSONArray request) throws Exception {
        String content=request.getJSONObject(1).getString("content");return new JSONObject(content.substring(content.indexOf('\n')+1));
    }
    @Test public void originalUpdateInvalidatesTraitsSummaryAndOldAnalysis() throws Exception {
        JSONObject snapshot=save("source","我喜欢安静阅读");llm.answer(extraction("我喜欢安静阅读"));agent.analyze("source");
        assertEquals(1,agent.profile().length());save("source","我的习惯改变了");
        assertEquals(0,agent.profile().length());assertEquals("",agent.memory("source").getString("summary"));
        try{store.finishAnalysis(snapshot,"过期摘要",new JSONArray());fail("stale snapshot accepted");}catch(IOException expected){}
        assertEquals("我的习惯改变了",agent.memory("source").getString("content"));
    }
    @Test public void repeatedSaveDoesNotResetExistingAnalysis() throws Exception {
        save("same","我喜欢安静阅读");llm.answer(extraction("我喜欢安静阅读"));agent.analyze("same");
        long revision=agent.memory("same").getLong("revision");
        assertEquals("READY",save("same","我喜欢安静阅读").getString("status"));assertEquals(revision,agent.memory("same").getLong("revision"));
        assertEquals(1,agent.memories().length());
    }
    @Test public void deletionCascadesAndBlocksInFlightOldAnswer() throws Exception {
        save("remove","我喜欢安静阅读");llm.answer(extraction("我喜欢安静阅读"));agent.analyze("remove");
        store.commitChat(store.epoch(),"session","问题","旧回答",new HashSet<>(Collections.singleton("remove")));
        long epoch=store.epoch();agent.delete("remove");assertEquals(0,agent.profile().length());assertEquals(0,store.history("session").length());
        try{store.commitChat(epoch,"session","问题","旧回答",Collections.emptySet());fail("stale answer persisted");}catch(IOException expected){}
        assertEquals(0,agent.memories().length());
    }
    @Test public void toolLoopReadsOriginalAndUnverifiedSourcesAreOmitted() throws Exception {
        save("book","我重读了小王子");
        llm.replies.add(new JSONObject().put("tool_calls",new JSONArray().put(new JSONObject().put("id","call1").put("type","function")
            .put("function",new JSONObject().put("name","read_memory").put("arguments","{\"id\":\"book\"}")))));
        llm.answer(new JSONObject().put("answer","你重读了小王子。[book]").put("sourceIds",new JSONArray().put("book")));
        JSONObject result=agent.chat("tools","我读过什么？");assertEquals("book",result.getJSONArray("sources").getJSONObject(0).getString("id"));
        assertTrue(llm.requests.get(1).toString().contains("tool_call_id"));assertTrue(llm.requests.get(1).toString().contains("我重读了小王子"));
        llm.answer(new JSONObject().put("answer","猜测").put("sourceIds",new JSONArray().put("invented")));
        JSONObject unverified=agent.chat("invented","我读过什么？");assertEquals(0,unverified.getJSONArray("sources").length());assertTrue(unverified.getString("answer").contains("未能核实"));
        assertEquals(2,store.history("invented").length());
    }
    @Test public void toolBudgetIsEnforced() throws Exception {
        JSONArray calls=new JSONArray();for(int i=0;i<7;i++)calls.put(new JSONObject().put("id","c"+i));
        llm.replies.add(new JSONObject().put("tool_calls",calls));
        try{agent.chat("budget","帮我找记忆");fail("unbounded tools");}catch(IOException expected){assertTrue(expected.getMessage().contains("步骤过多"));}
        assertEquals(0,store.history("budget").length());
    }
    @Test public void searchFindsOldChineseMemoriesAndValidatesDates() throws Exception {
        JSONObject old=save("old","重读小王子");save("new","爵士音乐");
        old.put("createdAt",1).put("occurredOn","2016-01-01");ContentValues row=new ContentValues();row.put("data",old.toString());
        store.getWritableDatabase().update("memories",row,"id=?",new String[]{"old"});store.rebuildIndexes();
        AgentSearch search=new AgentSearch(store);assertEquals("old",search.search("小王子",null,null,null,6).getJSONObject(0).getString("id"));
        assertEquals(1,search.search("","2016-01-01","2016-12-31",null,6).length());assertEquals(0,search.search("","2026-01-01",null,null,6).length());
        try{search.search("","2026-02-31",null,null,6);fail("impossible date accepted");}catch(IOException expected){}
    }
    @Test public void longMaterialDoesNotSplitEmojiOrLoseOriginal() throws Exception {
        char[] chars=new char[3999];Arrays.fill(chars,'a');String raw=new String(chars)+"😀b";save("long",raw);
        llm.answer(new JSONObject().put("summary","第一段").put("traits",new JSONArray()));llm.answer(new JSONObject().put("summary","第二段").put("traits",new JSONArray()));
        agent.analyze("long");assertEquals(2,llm.requests.size());assertTrue(llm.requests.get(1).toString().contains("😀b"));assertEquals(raw,agent.memory("long").getString("content"));
    }
    @Test public void cancellationCannotCommitAnalysis() throws Exception {
        save("cancel","我的文字");agent.cancel();
        try{agent.analyze("cancel");fail("cancelled operation ran");}catch(IOException expected){}
        assertEquals("PENDING",store.get("cancel").getString("status"));assertEquals(0,llm.requests.size());
    }
    @Test public void credentialsAreEncryptedAndModelEndpointIsNormalized() throws Exception {
        String name="llm-test-"+UUID.randomUUID(),alias="llm-test-key-"+UUID.randomUUID();ModelSettings settings=new ModelSettings(context,name,alias);
        try{
            settings.save("https://example.com/v1/chat/completions/","my-model","short-key");
            assertEquals("https://example.com/v1/chat/completions",settings.snapshot().endpoint);assertEquals("my-model",settings.model());
            assertEquals("short-key",settings.apiKey());
            String ciphertext=context.getSharedPreferences(name,Context.MODE_PRIVATE).getString("apiKey","");assertNotEquals("short-key",ciphertext);
            try{settings.save("https://example.com/v1?key=bad","model","short-key");fail("query accepted");}catch(IllegalArgumentException expected){}
            settings.clearKey();assertFalse(settings.hasKey());assertEquals("my-model",settings.model());
            try{settings.snapshot();fail("missing credentials accepted");}catch(IOException expected){assertTrue(expected.getMessage().contains("API Key"));}
        }finally{
            context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit();context.deleteSharedPreferences(name);
            java.security.KeyStore keys=java.security.KeyStore.getInstance("AndroidKeyStore");keys.load(null);keys.deleteEntry(alias);
        }
    }
    @Test public void fullAgentTalksDirectlyToDeviceLocalModelOverHttp() throws Exception {
        List<String> requests=new ArrayList<>();
        try(ModelStub stub=new ModelStub(4,(index,body)->{
            requests.add(body);
            if(index==0)return new JSONObject().put("content",extraction("我喜欢安静阅读").toString());
            if(index==1)return new JSONObject().put("content","{\"queries\":[],\"matches\":[]}");
            if(index==2)return new JSONObject().put("tool_calls",new JSONArray().put(new JSONObject().put("id","read1").put("type","function")
                .put("function",new JSONObject().put("name","read_memory").put("arguments","{\"id\":\"http\"}"))));
            return new JSONObject().put("content",new JSONObject().put("answer","你写过喜欢安静阅读。[http]").put("sourceIds",new JSONArray().put("http")).toString());
        })){
            LocalMemoryAgent real=new LocalMemoryAgent(store,new AgentModelClient(new ModelSettings.Snapshot(stub.endpoint(),"test-model","test-key")));
            save("http","我喜欢安静阅读");real.analyze("http");
            real.setTrait(real.profile().getJSONObject(0).getString("id"),"CONFIRMED");
            JSONObject answer=real.chat("real-http","我的阅读习惯？");assertEquals("你写过喜欢安静阅读。[1]",answer.getString("answer"));
            stub.await();assertEquals(4,requests.size());assertTrue(requests.get(3).contains("tool_call_id"));
            assertFalse(requests.get(0).contains("test-key"));assertEquals("Bearer test-key",stub.auth);
        }
    }
    @Test public void providerErrorsDoNotExposeKeyOrResponseBody() throws Exception {
        try(ModelStub stub=new ModelStub(1,(index,body)->new JSONObject())){
            stub.status=401;stub.errorBody="upstream test-key private error";
            AgentModelClient client=new AgentModelClient(new ModelSettings.Snapshot(stub.endpoint(),"test-model","test-key"));
            try{client.complete(new JSONArray(),new JSONArray());fail("401 accepted");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("API Key"));assertFalse(expected.getMessage().contains("test-key"));assertFalse(expected.getMessage().contains("upstream"));}
            stub.await();
        }
    }
    interface Reply {JSONObject message(int index,String body) throws Exception;}
    static final class ModelStub implements AutoCloseable {
        final ServerSocket socket;
        final ExecutorService executor=Executors.newSingleThreadExecutor();
        final Future<?> future;
        volatile int status=200;
        volatile String errorBody="error",auth="";
        ModelStub(int count,Reply reply) throws Exception {
            socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));socket.setSoTimeout(10000);
            future=executor.submit(()->{
                try{
                    for(int index=0;index<count;index++)try(Socket peer=socket.accept()){
                        peer.setSoTimeout(10000);InputStream in=peer.getInputStream();String line=readLine(in);
                        if(!line.startsWith("POST /v1/chat/completions "))throw new IOException("Wrong endpoint: "+line);
                        int length=0;
                        while(!(line=readLine(in)).isEmpty()){
                            if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
                            if(line.toLowerCase(Locale.ROOT).startsWith("authorization:"))auth=line.substring(14).trim();
                        }
                        byte[] body=new byte[length];int offset=0,n;while(offset<length&&(n=in.read(body,offset,length-offset))!=-1)offset+=n;
                        if(offset!=length)throw new EOFException();String text=new String(body,StandardCharsets.UTF_8);
                        byte[] response=(status==200?new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",reply.message(index,text)).put("finish_reason","stop"))).toString():errorBody).getBytes(StandardCharsets.UTF_8);
                        OutputStream out=peer.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: "+response.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(response);out.flush();
                    }
                }catch(Exception e){throw new RuntimeException(e);}
            });
        }
        String endpoint(){return "http://127.0.0.1:"+socket.getLocalPort()+"/v1/chat/completions";}
        void await() throws Exception{future.get(20,TimeUnit.SECONDS);}
        static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b=='\n')return bytes.toString("US-ASCII").replace("\r","");if(bytes.size()>8192)throw new IOException("header too large");bytes.write(b);}throw new EOFException();
        }
        @Override public void close() throws Exception{socket.close();executor.shutdownNow();}
    }
}
