package hk.hkucs.dontwannaforget;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

/** Isolated integration tests for the complete local memory lifecycle. No external model calls. */
@RunWith(AndroidJUnit4.class)
public class AgentMemoryV2Test {
    Context context;AgentStore store;AppDb archive;LocalMemoryAgentTest.FakeLlm llm;LocalMemoryAgent agent;
    String name;File files;
    @Before public void setup(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();name="agent-v2-"+UUID.randomUUID()+".db";
        store=new AgentStore(context,name);archive=Room.inMemoryDatabaseBuilder(context,AppDb.class).build();
        llm=new LocalMemoryAgentTest.FakeLlm();agent=new LocalMemoryAgent(store,llm);files=new File(context.getCacheDir(),"backup-test-"+UUID.randomUUID());files.mkdirs();
    }
    @After public void cleanup(){archive.close();store.close();context.deleteDatabase(name);AgentBackup.deleteTree(files);}
    JSONObject save(String id,String text,String date) throws Exception {return store.save(new JSONObject().put("id",id).put("kind","MATERIAL").put("title",id).put("content",text).put("occurredOn",date==null?JSONObject.NULL:date));}
    JSONObject extraction(String quote,String trait){return make("summary","保留的摘要","traits",new JSONArray().put(make("description",trait,"evidence",quote,"basis","EXPLICIT")));}
    static JSONObject make(Object... values){JSONObject out=new JSONObject();try{for(int i=0;i<values.length;i+=2)out.put((String)values[i],values[i+1]);return out;}catch(Exception e){throw new AssertionError(e);}}
    static String repeat(String text,int count){StringBuilder out=new StringBuilder();for(int i=0;i<count;i++)out.append(text);return out.toString();}
    JSONObject reference(JSONArray messages) throws Exception {String content=messages.getJSONObject(1).getString("content");return new JSONObject(content.substring(content.indexOf('\n')+1));}
    @Test public void migrationKeepsV1OriginalsTraitsAndOldConversations() throws Exception {
        JSONObject memory=save("legacy","在海边听见汽笛",null);String legacyName="legacy-"+UUID.randomUUID()+".db";
        try{
            SQLiteDatabase old=context.openOrCreateDatabase(legacyName,0,null);
            old.execSQL("CREATE TABLE memories(id TEXT PRIMARY KEY NOT NULL,data TEXT NOT NULL)");old.execSQL("CREATE TABLE traits(id TEXT PRIMARY KEY NOT NULL,memory_id TEXT NOT NULL REFERENCES memories(id) ON DELETE CASCADE,data TEXT NOT NULL)");
            old.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id TEXT NOT NULL,role TEXT NOT NULL,content TEXT NOT NULL)");
            old.execSQL("INSERT INTO memories VALUES(?,?)",new Object[]{"legacy",memory.toString()});old.execSQL("INSERT INTO messages(session_id,role,content) VALUES('past','user','旧问题')");old.setVersion(1);old.close();
            try(AgentStore migrated=new AgentStore(context,legacyName)){
                assertEquals("在海边听见汽笛",migrated.get("legacy").getString("content"));assertEquals(1,migrated.counts().getInt("total"));
                assertEquals(1,new AgentSearch(migrated).search("汽笛",null,null,null,6).length());assertEquals(1,migrated.transcript("past").length());assertEquals(0,migrated.history("past").length());
            }
        }finally{context.deleteDatabase(legacyName);}
    }
    @Test public void everyExistingSampleIsAvailableByDefaultWithSourceLabels() throws Exception {
        archive.records().insertAll(Record.sample());LocalMemoryAgent bridge=new LocalMemoryAgent(store,llm,()->archive.records().getActiveOnce());
        JSONObject inventory=bridge.inventory();assertTrue(inventory.getInt("total")>0);assertEquals(inventory.getInt("total"),inventory.getInt("exampleCount"));assertEquals(0,inventory.getInt("realCount"));
        JSONArray movies=new AgentSearch(store).page(null,null,"MOVIE",0,20).getJSONArray("items");assertTrue(movies.length()>0);String id=movies.getJSONObject(0).getString("id");
        llm.answer(make("answer","从示例记录看，有这部电影。["+id+"]","sourceIds",new JSONArray().put(id)));
        JSONObject answer=bridge.chat("examples","what movie I have watched");assertTrue(answer.getJSONArray("sources").getJSONObject(0).getBoolean("testData"));
        assertEquals(0,reference(llm.requests.get(0)).getJSONObject("memoryInventory").getInt("realCount"));
    }
    @Test public void archiveSyncSkipsReadingUnchangedLargeBodiesAndUpdatesOnVersionChange() throws Exception {
        final int[] reads={0};final String[] version={"one"};Record r=new Record();r.id="sample-sync";r.title="old";r.type=Record.BOOK;
        ArchiveMemoryBridge bridge=new ArchiveMemoryBridge(store,new ArchiveMemoryBridge.Records(){public List<Record> active(){reads[0]++;return Collections.singletonList(r);}public String version(){return version[0];}});
        bridge.refresh();bridge.refresh();assertEquals(1,reads[0]);r.title="new";version[0]="two";bridge.refresh();assertEquals(2,reads[0]);assertEquals("new",store.get(r.id).getString("title"));
    }
    @Test public void analysisResumesAtLastCompletedChunkAfterDatabaseReopen() throws Exception {
        String text=repeat("甲",4000)+"最后一段文字";save("long",text,null);llm.answer(make("summary","第一段摘要","traits",new JSONArray()));
        try{agent.analyze("long");fail();}catch(IOException expected){}
        JSONObject task=agent.pendingTasks().getJSONObject(0);assertEquals(4000,task.getJSONObject("checkpoint").getInt("offset"));
        store.close();store=new AgentStore(context,name);llm=new LocalMemoryAgentTest.FakeLlm();agent=new LocalMemoryAgent(store,llm);llm.answer(make("summary","尾段摘要","traits",new JSONArray()));
        agent.resumeTask(task.getString("id"));assertEquals(1,llm.requests.size());assertFalse(llm.requests.get(0).toString().contains(repeat("甲",100)));assertEquals(text,store.get("long").getString("content"));assertEquals(0,agent.pendingTasks().length());
    }
    @Test public void sourceChangesReplaceObsoleteAnalysisCheckpoints() throws Exception {
        save("edit",repeat("旧",4500),null);llm.answer(make("summary","旧摘要","traits",new JSONArray()));try{agent.analyze("edit");fail();}catch(IOException expected){}
        save("edit","现在的新内容",null);llm.answer(make("summary","新的摘要","traits",new JSONArray()));agent.analyze("edit");
        assertEquals("新的摘要",store.get("edit").getString("summary"));assertEquals(0,agent.pendingTasks().length());
    }
    @Test public void periodSummaryReducesMultipleLevelsAndInvalidatesOnSourceMutation() throws Exception {
        for(int i=0;i<65;i++)save("day-"+i,"那天读过的作品"+i,"2026-10-06");save("unknown","未知日期的文字",null);
        for(int i=0;i<12;i++)llm.answer(make("summary","这个阶段的作品与感受"));
        JSONObject period=agent.summarizePeriod("2026-10-01","2026-10-31","十月");assertEquals(65,period.getInt("sourceCount"));assertEquals(12,llm.requests.size());assertEquals("MONTH",period.getString("scope"));
        assertEquals(1,new AgentMemoryServices(store,llm).validPeriods().length());save("day-0","我修改了原来的感想","2026-10-06");assertEquals("STALE",agent.periods().getJSONObject(0).getString("status"));
    }
    @Test public void periodTaskResumesWithoutRepeatingFinishedBatches() throws Exception {
        for(int i=0;i<9;i++)save("period-"+i,"记忆"+i,"2026-10-06");llm.answer(make("summary","第一批"));try{agent.summarizePeriod("2026-10-01","2026-10-31","十月");fail();}catch(IOException expected){}
        JSONObject task=agent.pendingTasks().getJSONObject(0);assertEquals(1,task.getJSONObject("checkpoint").getJSONArray("batches").length());
        llm.answer(make("summary","第二批"));llm.answer(make("summary","合并的十月"));agent.resumeTask(task.getString("id"));assertEquals(4,llm.requests.size());assertEquals("合并的十月",agent.periods().getJSONObject(0).getString("summary"));
    }
    @Test public void periodRejectsArchiveChangesThatOccurDuringModelCall() throws Exception {
        Record r=new Record();r.id="first";r.title="第一条";r.date="2026.10.06";r.type=Record.BOOK;archive.records().insert(r);
        AgentLlm changing=new AgentLlm(){public JSONObject complete(JSONArray m,JSONArray t){Record next=new Record();next.id="second";next.title="新增";next.date="2026.10.07";next.type=Record.BOOK;archive.records().insert(next);return make("content",make("summary","旧回顾").toString());}public void cancel(){}};
        LocalMemoryAgent bridge=new LocalMemoryAgent(store,changing,()->archive.records().getActiveOnce());try{bridge.summarizePeriod("2026-10-01","2026-10-31","十月");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("来源数量"));}assertEquals(0,agent.periods().length());
    }
    @Test public void profileConsolidatesConflictPreservesDatesAndUserCorrectionWins() throws Exception {
        save("early","我以前喜欢热闹","2020-01-01");llm.answer(extraction("我以前喜欢热闹","以前喜欢热闹"));agent.analyze("early");
        save("now","我现在喜欢安静","2026-10-01");llm.answer(extraction("我现在喜欢安静","现在喜欢安静"));agent.analyze("now");
        JSONArray traits=agent.profile(),ids=new JSONArray();for(int i=0;i<traits.length();i++){String id=traits.getJSONObject(i).getString("id");ids.put(id);agent.setTrait(id,"CONFIRMED");}
        JSONObject result=make("groups",new JSONArray().put(make("topic","环境偏好","description","偏好随时间变化","relation","CHANGED","traitIds",ids)));
        llm.answer(result);JSONArray groups=agent.groupProfile();assertEquals(1,groups.length());assertEquals(2,groups.getJSONObject(0).getJSONArray("members").length());
        agent.preferTrait(groups.getJSONObject(0).getString("id"),ids.getString(1));assertTrue(agent.profileGroups().getJSONObject(0).getBoolean("resolvedByUser"));
        llm.answer(result);agent.groupProfile();assertEquals(1,agent.profileGroups().length());
        agent.correctTrait(ids.getString(0),"我喜欢热闹，但也需要安静");JSONObject corrected=agent.profile().getJSONObject(0);assertEquals("USER_CORRECTION",corrected.getString("basis"));assertEquals("CONFIRMED",corrected.getString("status"));assertTrue(corrected.has("originalDescription"));assertEquals(0,agent.profileGroups().length());
    }
    @Test public void candidateProfileGroupsDoNotEnterChatBeforeConfirmation() throws Exception {
        save("candidate","我喜欢散步",null);llm.answer(extraction("我喜欢散步","喜欢散步"));agent.analyze("candidate");String id=agent.profile().getJSONObject(0).getString("id");
        llm.answer(make("groups",new JSONArray().put(make("topic","习惯","description","散步","relation","CONSISTENT","traitIds",new JSONArray().put(id)))));agent.groupProfile();
        llm.answer(make("answer","从材料看有一条线索。","sourceIds",new JSONArray()));agent.chat("candidate-session","我喜欢什么？");JSONObject ref=reference(llm.requests.get(2));assertEquals(0,ref.getJSONArray("confirmedTraits").length());assertEquals(0,ref.getJSONArray("profileGroups").length());
    }
    @Test public void semanticRecallAssociatesEmotionAcrossRecordTypesAndRejectsInventedIds() throws Exception {
        save("music","蓝色旋律陪伴我走过寒冬",null);save("voice","港口的汽笛让人平静",null);
        AgentLlm semantic=new AgentLlm(){public JSONObject complete(JSONArray messages,JSONArray tools){
            if(messages.toString().contains("SEMANTIC_RETRIEVAL"))return make("content",make("queries",new JSONArray().put("汽笛"),"matches",new JSONArray().put("music").put("fabricated")).toString());
            try{JSONArray retrieved=reference(messages).getJSONArray("retrievedMemories");assertEquals(2,retrieved.length());}catch(Exception e){throw new AssertionError(e);}
            return make("content",make("answer","这些声音陪伴过低谷。[music][voice]","sourceIds",new JSONArray().put("music").put("voice")).toString());
        }public void cancel(){}};
        JSONObject answer=new LocalMemoryAgent(store,semantic).chat("meaning","低谷时有什么陪伴我？");assertEquals(2,answer.getJSONArray("sources").length());
    }
    @Test public void dormantMemoryCanBeAwakenedAndPinnedWithoutDeletingOriginal() throws Exception {
        JSONObject memory=save("sleep","很久以前的记忆",null);long now=System.currentTimeMillis();memory.put("createdAt",now-200L*86400000);assertEquals("DORMANT",AgentStore.memoryState(memory,now));memory.put("createdAt",now-40L*86400000);assertEquals("FADING",AgentStore.memoryState(memory,now));
        agent.sleep("sleep",true);assertEquals("DORMANT",agent.memories().getJSONObject(0).getString("memoryState"));
        store.commitChat(store.epoch(),"awake","问题","回答",Collections.singleton("sleep"));assertEquals("CLEAR",AgentStore.memoryState(store.get("sleep"),now));assertEquals(1,store.get("sleep").getInt("recallCount"));
        agent.sleep("sleep",true);agent.pin("sleep",true);assertEquals("CLEAR",AgentStore.memoryState(store.get("sleep"),now));assertEquals("很久以前的记忆",store.get("sleep").getString("content"));
    }
    @Test public void transcriptsAndCitationsSurviveReopenAndOnlyAffectedHistoryIsInvalidated() throws Exception {
        save("a","一段话",null);save("b","另一段话",null);
        for(int i=0;i<8;i++)store.commitChat(store.epoch(),"long-session","我的问题"+i,"来源[a]",Collections.singleton("a"));
        store.commitChat(store.epoch(),"separate","其他问题","其他回答",Collections.singleton("b"));assertEquals(16,store.transcript("long-session").length());assertEquals(10,store.history("long-session").length());
        store.close();store=new AgentStore(context,name);agent=new LocalMemoryAgent(store,llm);assertEquals(2,agent.sessions().length());assertEquals("a",store.transcript("long-session").getJSONObject(1).getJSONArray("sources").getJSONObject(0).getString("id"));
        save("a","修订后的一段话",null);assertEquals(0,store.history("long-session").length());assertEquals(16,store.transcript("long-session").length());assertEquals(2,store.history("separate").length());
        store.commitChat(store.epoch(),"long-session","新问题","新回答",Collections.emptySet());assertEquals(2,store.history("long-session").length());
    }
    @Test public void conversationMemoriesRequireActualUserQuoteAndExplicitAcceptance() throws Exception {
        store.commitChat(store.epoch(),"speech","我每周日都会去海边散步","你喜欢海边",Collections.emptySet());JSONArray turns=store.transcript("speech");
        llm.answer(make("title","周日习惯","memory","每周日都会去海边散步"));JSONObject proposal=agent.proposeMemory("speech",turns.getJSONObject(0).getLong("id"));assertEquals(0,store.counts().getInt("total"));
        JSONObject saved=agent.acceptProposal(proposal.getString("id"),"我喜欢周日下午到海边散步");assertEquals("CHAT",saved.getString("kind"));assertEquals(1,store.counts().getInt("total"));
        try{agent.proposeMemory("speech",turns.getJSONObject(1).getLong("id"));fail();}catch(IOException expected){}
        llm.answer(make("title","编造","memory","用户有一条船"));try{agent.proposeMemory("speech",turns.getJSONObject(0).getLong("id"));fail();}catch(IOException expected){}
    }
    @Test public void contextBudgetPreservesToolProtocolAndOriginals() throws Exception {
        String longText=repeat("记忆与时间😀",2000);save("large",longText,null);
        JSONArray messages=new JSONArray().put(make("role","system","content","必须依据原文回答"))
            .put(make("role","user","content","参考资料：\n"+make("retrievedMemories",new JSONArray().put(make("id","large","excerpt",longText))).toString()));
        for(int i=0;i<10;i++)messages.put(make("role",i%2==0?"user":"assistant","content",longText));
        messages.put(make("role","user","content","此刻的具体问题"));messages.put(make("role","assistant","content",JSONObject.NULL,"tool_calls",new JSONArray().put(make("id","call1","type","function","function",make("name","read_memory","arguments","{\"id\":\"large\"}")))));
        messages.put(make("role","tool","tool_call_id","call1","content",make("excerpt",longText).toString()));
        JSONArray fitted=AgentContextBudget.fit(messages,7000);assertTrue(AgentContextBudget.estimate(fitted.toString())<=7000);assertTrue(fitted.toString().contains("此刻的具体问题"));assertEquals("call1",fitted.getJSONObject(fitted.length()-1).getString("tool_call_id"));assertEquals("call1",fitted.getJSONObject(fitted.length()-2).getJSONArray("tool_calls").getJSONObject(0).getString("id"));assertEquals(longText,store.get("large").getString("content"));
    }
    @Test public void largeCatalogPaginatesAndIndexesTextAfterUpdatesWithoutLosingOldMatches() throws Exception {
        for(int i=0;i<301;i++)save("index-"+i,"普通文字"+i,null);save("index-0","我在海边看见了蓝鲸",null);
        AgentSearch search=new AgentSearch(store);assertEquals("index-0",search.search("蓝鲸",null,null,null,8).getJSONObject(0).getString("id"));assertEquals(301,search.page(null,null,null,300,20).getInt("total"));assertEquals(1,search.page(null,null,null,300,20).getJSONArray("items").length());
        save("index-0","现在写的是雪山",null);assertEquals(0,search.search("蓝鲸",null,null,null,8).length());assertEquals(1,search.search("雪山",null,null,null,8).length());
    }
    @Test public void backupRoundTripRestoresArchiveMediaSourcesAndAgentStatePreservingExistingRows() throws Exception {
        Record r=new Record();r.id="archive-item";r.type=Record.VOICE;r.title="海边";r.date="2026.10.06";r.note="汽笛的故事";archive.records().insert(r);
        File mediaFile=new File(files,"media/sound.m4a");mediaFile.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(mediaFile)){out.write(new byte[]{1,2,3,4});}
        Media media=new Media();media.id="audio-file";media.recordId=r.id;media.path="media/sound.m4a";media.role="audio";archive.media().insert(media);
        new ArchiveMemoryBridge(store,()->archive.records().getActiveOnce()).refresh();save("keep","备份中的旧内容",null);store.commitChat(store.epoch(),"backed-chat","我的提问","我的回答",Collections.singleton(r.id));
        llm.answer(extraction("汽笛的故事","我喜欢汽笛"));agent.analyze(r.id); // Analysis invalidates model history, retains transcript.
        ByteArrayOutputStream output=new ByteArrayOutputStream();new AgentBackup(context,store,archive,files).exportTo(output);byte[] bytes=output.toByteArray();
        AppDb restoredArchive=Room.inMemoryDatabaseBuilder(context,AppDb.class).build();String restoredName="restored-"+UUID.randomUUID()+".db";File restoredFiles=new File(files,"restored-target");restoredFiles.mkdirs();
        try(AgentStore restored=new AgentStore(context,restoredName)){
            restored.save(make("id","keep","title","keep","kind","MATERIAL","content","手机已有的新内容"));AgentBackup backup=new AgentBackup(context,restored,restoredArchive,restoredFiles);
            AgentBackup.Prepared prepared=backup.prepare(new ByteArrayInputStream(bytes));assertEquals(1,prepared.preview().getInt("media"));backup.restore(prepared);
            assertEquals("汽笛的故事",restoredArchive.records().get(r.id).note);assertEquals(1,restoredArchive.media().allForBackup().size());assertEquals(4,new File(restoredFiles,"media/sound.m4a").length());
            assertEquals("手机已有的新内容",restored.get("keep").getString("content"));assertEquals(1,restored.traits().length());assertEquals(2,restored.transcript("backed-chat").length());assertEquals(0,restored.history("backed-chat").length());
            backup.restore(backup.prepare(new ByteArrayInputStream(bytes)));assertEquals(2,restored.transcript("backed-chat").length());assertEquals(1,restoredArchive.records().allForBackup().size());
        }finally{restoredArchive.close();context.deleteDatabase(restoredName);}
    }
    @Test public void backupRejectsCorruptionAndTraversalBeforeChangingCurrentData() throws Exception {
        save("safe","现有数据",null);AgentBackup backup=new AgentBackup(context,store,archive,files);
        JSONObject manifest=make("format","dontwannaforget-memory","version",2,"credentialsIncluded",false,"archiveRecords",new JSONArray(),"media",new JSONArray(),"agent",store.exportData(),"files",make("broken.txt",make("sha256","wrong","size",3)));
        try{backup.prepare(new ByteArrayInputStream(zip(manifest,"files/broken.txt",new byte[]{1,2,3})));fail();}catch(IOException expected){}
        try{backup.prepare(new ByteArrayInputStream(zip(manifest,"files/../escape",new byte[]{1})));fail();}catch(IOException expected){}
        assertEquals("现有数据",store.get("safe").getString("content"));assertEquals(1,store.counts().getInt("total"));assertFalse(new File(context.getCacheDir(),"escape").exists());
    }
    @Test public void backupExcludesPreferencesAndCredentialsAndSupportsNullLegacyFields() throws Exception {
        Record r=new Record();r.id="nullable";r.fields=null;r.rating=null;archive.records().insert(r);save("safe","文字",null);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();AgentBackup backup=new AgentBackup(context,store,archive,files);backup.exportTo(bytes);
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){assertFalse(entry.getName().contains("shared_prefs"));if(entry.getName().equals("manifest.json")){ByteArrayOutputStream content=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=zip.read(b))!=-1)content.write(b,0,n);JSONObject manifest=new JSONObject(content.toString("UTF-8"));assertFalse(manifest.getBoolean("credentialsIncluded"));assertFalse(manifest.has("apiKey"));}}}
        try(AgentBackup.Prepared prepared=backup.prepare(new ByteArrayInputStream(bytes.toByteArray()))){assertEquals(1,prepared.preview().getInt("records"));}
    }
    @Test public void fullConversationCanPageToItsBeginning() throws Exception {
        for(int i=0;i<125;i++)store.commitChat(store.epoch(),"paged","问题"+i,"回答"+i,Collections.emptySet());
        JSONObject recent=agent.transcriptPage("paged",0),first=agent.transcriptPage("paged",240);
        assertEquals(250,recent.getInt("total"));assertEquals(40,recent.getJSONArray("items").length());assertTrue(recent.getBoolean("hasOlder"));
        assertEquals(10,first.getJSONArray("items").length());assertEquals("问题0",first.getJSONArray("items").getJSONObject(0).getString("content"));assertFalse(first.getBoolean("hasOlder"));
    }
    @Test public void choosingCandidateDescriptionConfirmsItAndSurvivesRegrouping() throws Exception {
        save("choice","我更喜欢安静",null);llm.answer(extraction("我更喜欢安静","偏好安静"));agent.analyze("choice");String id=agent.profile().getJSONObject(0).getString("id");
        JSONObject grouping=make("groups",new JSONArray().put(make("topic","环境","description","更喜欢安静","relation","CONSISTENT","traitIds",new JSONArray().put(id))));
        llm.answer(grouping);JSONObject group=agent.groupProfile().getJSONObject(0);agent.preferTrait(group.getString("id"),id);assertEquals("CONFIRMED",agent.profile().getJSONObject(0).getString("status"));
        llm.answer(grouping);JSONObject regrouped=agent.groupProfile().getJSONObject(0);assertEquals(id,regrouped.getString("preferredTraitId"));assertTrue(regrouped.getBoolean("resolvedByUser"));
    }
    @Test public void rejectedConversationProposalCannotBeAcceptedLater() throws Exception {
        store.commitChat(store.epoch(),"rejected","我喜欢雨天散步","一段回复",Collections.emptySet());long messageId=store.transcript("rejected").getJSONObject(0).getLong("id");
        llm.answer(make("title","雨天","memory","我喜欢雨天散步"));JSONObject proposal=agent.proposeMemory("rejected",messageId);agent.rejectProposal(proposal.getString("id"));
        try{agent.acceptProposal(proposal.getString("id"),"我喜欢雨天散步");fail();}catch(IOException expected){}assertEquals(0,store.counts().getInt("total"));
    }
    @Test public void profileResumeKeepsCompletedGroupsAndNeverMixesExampleWithPersonalTraits() throws Exception {
        save("personal","我喜欢海风",null);llm.answer(extraction("我喜欢海风","偏好海风"));agent.analyze("personal");
        Record r=new Record();r.id="sample-profile";r.title="示例";r.note="我喜欢海风";r.type=Record.BOOK;archive.records().insert(r);new ArchiveMemoryBridge(store,()->archive.records().getActiveOnce()).refresh();llm.answer(extraction("我喜欢海风","偏好海风"));agent.analyze(r.id);
        JSONArray traits=agent.profile();String personal="",example="";for(int i=0;i<traits.length();i++){JSONObject t=traits.getJSONObject(i);if(t.optBoolean("testData"))example=t.getString("id");else personal=t.getString("id");}
        llm.answer(make("groups",new JSONArray().put(make("topic","风","description","海风","relation","CONSISTENT","traitIds",new JSONArray().put(personal)))));
        try{agent.groupProfile();fail();}catch(IOException expected){}assertEquals(0,agent.profileGroups().length());String task=agent.pendingTasks().getJSONObject(0).getString("id");
        llm.answer(make("groups",new JSONArray().put(make("topic","风","description","示例海风","relation","CONSISTENT","traitIds",new JSONArray().put(example)))));agent.resumeTask(task);
        JSONArray groups=agent.profileGroups();assertEquals(2,groups.length());assertNotEquals(groups.getJSONObject(0).getBoolean("testData"),groups.getJSONObject(1).getBoolean("testData"));assertEquals(0,agent.pendingTasks().length());
    }
    static byte[] zip(JSONObject manifest,String name,byte[] content) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new ZipEntry(name));zip.write(content);zip.closeEntry();}return output.toByteArray();
    }
}
