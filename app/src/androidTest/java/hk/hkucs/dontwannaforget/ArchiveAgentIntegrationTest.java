package hk.hkucs.dontwannaforget;

import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

/** Reads a real isolated Room database through the same bridge used by the app, never the user's DB. */
@RunWith(AndroidJUnit4.class)
public class ArchiveAgentIntegrationTest {
    Context context;
    AppDb archive;
    AgentStore store;
    String name;
    LocalMemoryAgentTest.FakeLlm llm;
    LocalMemoryAgent agent;
    @Before public void setup(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        archive=Room.inMemoryDatabaseBuilder(context,AppDb.class).build();
        name="archive-agent-test-"+UUID.randomUUID()+".db";store=new AgentStore(context,name);
        llm=new LocalMemoryAgentTest.FakeLlm();agent=new LocalMemoryAgent(store,llm,()->archive.records().getActiveOnce());
    }
    @After public void cleanup(){archive.close();store.close();context.deleteDatabase(name);}
    Record record(int type,String id){
        Record r=new Record();r.id=id;r.type=type;r.title="作品"+id;r.sub="相关人物";r.date="2026.10.23";
        r.note="用户写下的文字";r.createdAt=1;r.updatedAt=2;return r;
    }
    JSONObject reference(JSONArray messages) throws Exception {
        String text=messages.getJSONObject(1).getString("content");return new JSONObject(text.substring(text.indexOf('\n')+1));
    }
    void answer(String text,String id) throws Exception{
        llm.answer(new JSONObject().put("answer",text).put("sourceIds",id==null?new JSONArray():new JSONArray().put(id)));
    }
    @Test public void allSixModulesExposeTextIncludingThoughtsAndEveryFootprintPage() throws Exception {
        for(int type=0;type<6;type++){
            Record r=record(type,"module-"+type);
            if(type==Record.BOOK||type==Record.MUSIC)r.fields=new String[]{"{\"th\":\"我自己写的感想\",\"fid\":\"private-font\",\"sc\":\"layout-code\"}"};
            if(type==Record.TICKET)r.fields=new String[]{"海边影院","2号厅","A1","IMAX","60","18:30","{\"fid\":\"private-font\"}"};
            if(type==Record.VOICE)r.fields=new String[]{"02:15","现场录音"};
            if(type==Record.FOOTPRINT)r.fields=new String[]{"{\"time\":\"09:00\",\"boxes\":[{\"t\":\"第一页第二段文字\"},{\"t\":\"第一页第三段\"}]}",
                "{\"time\":\"17:00\",\"boxes\":[{\"t\":\"第二页的完整故事\"}]}"};
            archive.records().insert(r);
        }
        JSONObject stats=agent.inventory();assertEquals(6,stats.getInt("archiveCount"));assertEquals(0,stats.getInt("materialCount"));assertEquals(0,llm.requests.size());
        for(String kind:ArchiveMemoryBridge.KINDS)assertEquals(1,stats.getJSONObject("byKind").getInt(kind));
        for(int type=0;type<6;type++){
            JSONObject m=agent.memory("module-"+type);assertEquals("ARCHIVE",m.getString("origin"));assertEquals("2026-10-23",m.getString("occurredOn"));
            assertTrue(m.getString("content").contains("用户写下的文字"));assertFalse(m.getString("content").contains("private-font"));assertFalse(m.getString("content").contains("layout-code"));
        }
        assertTrue(agent.memory("module-1").getString("content").contains("用户自己的感想：我自己写的感想"));
        assertTrue(agent.memory("module-3").getString("content").contains("用户自己的感想：我自己写的感想"));
        assertTrue(agent.memory("module-4").getString("content").contains("第二页的完整故事"));
        assertTrue(agent.memory("module-4").getString("content").contains("第一页第三段"));
        assertTrue(agent.memory("module-2").getString("content").contains("02:15"));assertTrue(agent.memory("module-0").getString("content").contains("海边影院"));
    }
    @Test public void categoryQuestionsInBothLanguagesGetRecordsWithoutManualImportOrAnalysis() throws Exception {
        String[] questions={"what movie I have watched","我读过哪些书","what audio recordings have I saved","我听过哪些歌曲","which places have I visited","what letters have I written"};
        for(int type=0;type<6;type++)archive.records().insert(record(type,"r"+type));
        for(int type=0;type<6;type++){
            answer("记录来自这里。[r"+type+"]","r"+type);agent.chat("category-"+type,questions[type]);
            JSONObject context=reference(llm.requests.get(type)),overview=context.getJSONObject("categoryOverview");
            assertEquals(ArchiveMemoryBridge.KINDS[type],overview.getString("kind"));assertEquals(1,overview.getInt("total"));
            assertEquals("r"+type,overview.getJSONArray("items").getJSONObject(0).getString("id"));
            assertEquals(6,context.getJSONObject("memoryInventory").getInt("archiveCount"));
        }
    }
    @Test public void typedSearchWorksEvenWhenNeitherTitleNorBodySaysMovie() throws Exception {
        agent.save(new JSONObject().put("id","arrival").put("kind","MOVIE").put("title","Arrival").put("content","一段观后感"));
        AgentSearch search=new AgentSearch(store);
        for(String query:new String[]{"movie","movies","film","电影","我看过哪些电影","what movie I have watched"})
            assertEquals("arrival",search.search(query,null,null,null,8).getJSONObject(0).getString("id"));
        assertEquals(1,search.search("movie",null,null,"MOVIE",8).length());
        assertEquals(0,search.search("不存在的片名",null,null,"MOVIE",8).length());
    }
    @Test public void originalEditsTrashAndRestoreUpdateAgentButNeverRemoveStandaloneMaterials() throws Exception {
        Record r=record(Record.BOOK,"live");r.note="我更喜欢散文";archive.records().insert(r);
        agent.save(new JSONObject().put("id","material").put("title","我的自述").put("kind","MATERIAL").put("content","我写的材料"));
        agent.inventory();llm.answer(new JSONObject().put("summary","偏好散文").put("traits",new JSONArray().put(new JSONObject()
            .put("description","在记录中偏好散文").put("basis","EXPLICIT").put("evidence","我更喜欢散文"))));
        agent.analyze("live");String trait=agent.profile().getJSONObject(0).getString("id");agent.setTrait(trait,"CONFIRMED");agent.pin("live",true);
        answer("你写过偏好散文。[live]","live");agent.chat("history","我偏好什么？");
        long revision=agent.memory("live").getLong("revision");agent.inventory();assertEquals(revision,agent.memory("live").getLong("revision"));assertEquals(2,store.history("history").length());
        r.note="最近开始喜欢诗歌";r.updatedAt++;archive.records().insert(r);
        JSONObject changed=agent.memory("live");assertTrue(changed.getString("content").contains("诗歌"));assertEquals("",changed.getString("summary"));
        assertEquals(0,agent.profile().length());assertEquals(0,store.history("history").length());assertTrue(changed.getBoolean("pinned"));
        archive.records().setDeleted("live",123);assertEquals(0,agent.inventory().getInt("archiveCount"));assertEquals(1,agent.memories().length());
        assertEquals("material",agent.memories().getJSONObject(0).getString("id"));
        archive.records().setDeleted("live",0);assertEquals(1,agent.inventory().getInt("archiveCount"));assertEquals(2,agent.memories().length());
    }
    @Test public void failedArchiveReadCannotErasePreviouslyIndexedData() throws Exception {
        archive.records().insert(record(Record.MUSIC,"keep"));agent.inventory();
        LocalMemoryAgent failing=new LocalMemoryAgent(store,llm,()->{throw new IOException("archive unavailable");});
        try{failing.inventory();fail("read failure hidden");}catch(IOException expected){}
        assertEquals("keep",store.all().getJSONObject(0).getString("id"));
    }
    @Test public void examplesAreCountedSeparatelyAndNeverBecomeUserExperiences() throws Exception {
        archive.records().insert(record(Record.TICKET,"sample-test"));JSONObject inventory=agent.inventory();
        assertEquals(1,inventory.getInt("total"));assertEquals(1,inventory.getInt("exampleCount"));assertEquals(0,inventory.getInt("excludedExamples"));
        answer("记忆册目前只有示例记录。",null);agent.chat("examples","what movie I have watched");
        JSONObject context=reference(llm.requests.get(0));assertEquals(1,context.getJSONObject("categoryOverview").getInt("total"));assertTrue(context.getJSONObject("categoryOverview").getJSONArray("items").getJSONObject(0).getBoolean("testData"));
        assertEquals(1,context.getJSONObject("memoryInventory").getInt("exampleCount"));
    }
    @Test public void catalogProvidesAccurateTotalsAndPagingBeyondOldSearchLimit() throws Exception {
        for(int i=0;i<43;i++)archive.records().insert(record(Record.TICKET,String.format(Locale.ROOT,"movie-%02d",i)));
        agent.inventory();AgentSearch search=new AgentSearch(store);Set<String> ids=new HashSet<>();
        for(int offset=0;offset<43;offset+=20){
            JSONObject page=search.page(null,null,"MOVIE",offset,20);assertEquals(43,page.getInt("total"));assertEquals(offset+20<43,page.getBoolean("hasMore"));
            JSONArray items=page.getJSONArray("items");for(int i=0;i<items.length();i++)assertTrue(ids.add(items.getJSONObject(i).getString("id")));
        }
        assertEquals(43,ids.size());
        llm.replies.add(new JSONObject().put("tool_calls",new JSONArray().put(new JSONObject().put("id","page2").put("type","function")
            .put("function",new JSONObject().put("name","list_memories").put("arguments","{\"kind\":\"MOVIE\",\"offset\":20}")))));
        answer("还有这条记录。[movie-20]","movie-20");agent.chat("paging","我看过哪些电影");
        JSONObject first=reference(llm.requests.get(0)).getJSONObject("categoryOverview");assertEquals(43,first.getInt("total"));assertTrue(first.getBoolean("hasMore"));
        JSONArray messages=llm.requests.get(1);JSONObject page2=new JSONObject(messages.getJSONObject(messages.length()-1).getString("content"));
        assertEquals(20,page2.getInt("offset"));assertEquals(43,page2.getInt("total"));assertEquals(20,page2.getJSONArray("items").length());
    }
    @Test public void editsDuringGenerationCannotCommitOutdatedAnalysisOrAnswer() throws Exception {
        Record r=record(Record.LETTER,"race");r.note="过去写的文字";archive.records().insert(r);agent.inventory();
        AgentLlm changing=new AgentLlm(){
            @Override public JSONObject complete(JSONArray messages,JSONArray tools) throws Exception {
                r.note="刚刚修改的新文字";r.updatedAt++;archive.records().insert(r);
                return new JSONObject().put("content",new JSONObject().put("summary","过时摘要").put("traits",new JSONArray()).toString());
            }
            @Override public void cancel(){}
        };
        LocalMemoryAgent racing=new LocalMemoryAgent(store,changing,()->archive.records().getActiveOnce());
        try{racing.analyze("race");fail("outdated analysis saved");}catch(IOException expected){}
        assertEquals("",agent.memory("race").getString("summary"));assertTrue(agent.memory("race").getString("content").contains("刚刚修改"));
        AgentLlm deleting=new AgentLlm(){
            @Override public JSONObject complete(JSONArray messages,JSONArray tools) throws Exception {
                archive.records().setDeleted("race",123);
                return new JSONObject().put("content",new JSONObject().put("answer","过时回答。[race]").put("sourceIds",new JSONArray().put("race")).toString());
            }
            @Override public void cancel(){}
        };
        racing=new LocalMemoryAgent(store,deleting,()->archive.records().getActiveOnce());
        try{racing.chat("race-chat","我写过哪些文字");fail("outdated answer saved");}catch(IOException expected){}
        assertEquals(0,store.history("race-chat").length());
    }
    @Test public void longOriginalNotesAndLegacyFootprintPagesAreNotTruncated() throws Exception {
        Record r=record(Record.FOOTPRINT,"long-archive");char[] chars=new char[26000];Arrays.fill(chars,'文');r.note=new String(chars)+"末尾的独特故事";
        r.fields=new String[]{"17:40|旧版足迹手写文字","{\"boxes\":[{\"t\":\"新版最后一页的文字\"}]}"};archive.records().insert(r);
        String content=agent.memory("long-archive").getString("content");assertTrue(content.length()>26000);
        assertTrue(content.contains("末尾的独特故事"));assertTrue(content.contains("旧版足迹手写文字"));assertTrue(content.contains("新版最后一页的文字"));
        assertEquals(0,llm.requests.size());
    }
    @Test public void legacyArchiveCopyOfAlreadyDeletedRecordIsPrunedWithoutTouchingImportedText() throws Exception {
        JSONObject legacy=agent.save(new JSONObject().put("id","old-copy").put("title","旧手动副本").put("kind","MOVIE").put("content","已删除的原记录"));
        legacy.remove("origin");android.content.ContentValues row=new android.content.ContentValues();row.put("data",legacy.toString());
        store.getWritableDatabase().update("memories",row,"id=?",new String[]{"old-copy"});
        agent.save(new JSONObject().put("id","own-text").put("title","用户材料").put("kind","MATERIAL").put("content","独立保存的材料"));
        assertEquals(1,agent.memories().length());assertEquals("own-text",agent.memories().getJSONObject(0).getString("id"));
    }
    @Test public void previouslyManuallyImportedRecordIsUpdatedInPlaceWithoutDuplicate() throws Exception {
        Record r=record(Record.MUSIC,"previous");
        agent.save(new JSONObject().put("id",r.id).put("title",r.title).put("kind","MUSIC").put("content","上一版手动加入的文字"));
        archive.records().insert(r);assertEquals(1,agent.memories().length());assertEquals("ARCHIVE",agent.memory(r.id).getString("origin"));
        assertEquals(1,agent.inventory().getInt("archiveCount"));
        try{agent.delete(r.id);fail("archive index should not be independently deleted");}catch(IOException expected){}
        assertNotNull(archive.records().get(r.id));
    }
}
