package hk.hkucs.dontwannaforget;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.io.IOException;
import java.text.*;
import java.util.*;

/** Private, durable on-device agent memory. Kept separate from the existing Room schema. */
final class AgentStore extends SQLiteOpenHelper {
    private static AgentStore instance;
    private long epoch;
    static synchronized AgentStore get(Context context) {
        if(instance==null)instance=new AgentStore(context.getApplicationContext(),"agent_memory.db");return instance;
    }
    AgentStore(Context context,String name){super(context,name,null,2);setWriteAheadLoggingEnabled(true);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE memories(id TEXT PRIMARY KEY NOT NULL,data TEXT NOT NULL)");
        db.execSQL("CREATE TABLE traits(id TEXT PRIMARY KEY NOT NULL,memory_id TEXT NOT NULL REFERENCES memories(id) ON DELETE CASCADE,data TEXT NOT NULL)");
        db.execSQL("CREATE INDEX trait_source ON traits(memory_id)");
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id TEXT NOT NULL,role TEXT NOT NULL,content TEXT NOT NULL)");
        db.execSQL("CREATE INDEX session_messages ON messages(session_id,id)");
        createV2(db);
    }
    private void createV2(SQLiteDatabase db){
        db.execSQL("ALTER TABLE messages ADD COLUMN data TEXT NOT NULL DEFAULT '{}'");
        db.execSQL("CREATE TABLE documents(id TEXT PRIMARY KEY NOT NULL,type TEXT NOT NULL,data TEXT NOT NULL)");
        db.execSQL("CREATE INDEX document_type ON documents(type)");
        db.execSQL("CREATE TABLE memory_index(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,kind TEXT NOT NULL,occurred_on TEXT,revision INTEGER,brief TEXT NOT NULL,test_data INTEGER,origin TEXT,pinned INTEGER,created_at INTEGER,recalled_at INTEGER)");
        db.execSQL("CREATE INDEX memory_type_date ON memory_index(kind,occurred_on)");
        db.execSQL("CREATE VIRTUAL TABLE memory_fts USING fts4(memory_id,terms,notindexed=memory_id,tokenize=unicode61)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){createV2(db);try{rebuildIndexes(db);
            try(Cursor sessions=db.rawQuery("SELECT session_id,MIN(content),MAX(id) FROM messages GROUP BY session_id",null)){while(sessions.moveToNext()){
                JSONObject metadata=new JSONObject().put("id",sessions.getString(0)).put("title",sessions.getString(1)).put("status","SOURCES_CHANGED").put("historyAfterId",sessions.getLong(2));
                ContentValues row=new ContentValues();row.put("id",sessions.getString(0));row.put("type","SESSION");row.put("data",metadata.toString());db.insertOrThrow("documents",null,row);
            }}
        }catch(Exception e){throw new IllegalStateException("Memory migration failed; originals retained",e);}}
    }
    synchronized long epoch(){return epoch;}
    synchronized JSONObject get(String id) throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM memories WHERE id=?",new String[]{id})) {
            if(!c.moveToFirst())throw new IOException("没有找到这段记忆，可能已删除");return new JSONObject(c.getString(0));
        }
    }
    synchronized JSONArray all() throws Exception {
        List<JSONObject> values=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM memories",null)){
            while(c.moveToNext())values.add(new JSONObject(c.getString(0)));
        }
        values.sort((a,b)->{int order=Long.compare(b.optLong("createdAt"),a.optLong("createdAt"));return order!=0?order:a.optString("id").compareTo(b.optString("id"));});
        JSONArray result=new JSONArray();for(JSONObject value:values)result.put(value);return result;
    }
    synchronized JSONArray list() throws Exception {
        JSONArray result=new JSONArray(),all=all();
        for(int i=0;i<all.length();i++){JSONObject m=all.getJSONObject(i);m.remove("content");result.put(m);}return result;
    }
    synchronized JSONObject save(JSONObject input) throws Exception { return save(input,false); }
    private JSONObject save(JSONObject input,boolean archive) throws Exception {
        String id=input.optString("id",UUID.randomUUID().toString()),title=input.optString("title","").trim();
        String content=input.optString("content",""),kind=input.optString("kind","");
        if(!id.matches("[A-Za-z0-9_-]{1,80}")||title.isEmpty()||(!archive&&title.length()>160)||content.trim().isEmpty()||(!archive&&content.length()>24000)||content.indexOf('\0')>=0||
            !Arrays.asList("MATERIAL","BOOK","MUSIC","MOVIE","TEXT","VOICE","FOOTPRINT","CHAT").contains(kind))
            throw new IOException("请检查标题（最多160字）、文字（最多24000字）和记忆类型");
        String date=input.isNull("occurredOn")?null:input.optString("occurredOn",null);validateDate(date);
        JSONObject old=null;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM memories WHERE id=?",new String[]{id})){
            if(c.moveToFirst())old=new JSONObject(c.getString(0));
        }
        if(old!=null&&old.getString("title").equals(title)&&old.getString("kind").equals(kind)&&old.getString("content").equals(content)&&
            Objects.equals(nullable(old,"occurredOn"),date)&&(!archive||("ARCHIVE".equals(old.optString("origin"))&&old.optBoolean("testData")==input.optBoolean("testData"))))return old;
        long now=System.currentTimeMillis();
        JSONObject memory=new JSONObject().put("id",id).put("title",title).put("kind",kind).put("occurredOn",date==null?JSONObject.NULL:date)
            .put("content",content).put("summary","").put("status","PENDING").put("pinned",old!=null&&old.optBoolean("pinned"))
            .put("revision",old==null?1:old.optLong("revision")+1).put("createdAt",old==null?now:old.getLong("createdAt"))
            .put("updatedAt",now).put("recalledAt",old==null?0:old.optLong("recalledAt"))
            .put("testData",archive?input.optBoolean("testData"):input.optBoolean("testData",old!=null&&old.optBoolean("testData")))
            .put("sleeping",old!=null&&old.optBoolean("sleeping")).put("recallCount",old==null?0:old.optInt("recallCount"))
            .put("origin",archive?"ARCHIVE":old==null?"MATERIAL":old.optString("origin","MATERIAL"));
        if(archive){
            memory.put("recordType",input.getInt("recordType"));
            if(input.optLong("createdAt")>0)memory.put("createdAt",input.getLong("createdAt"));
            if(input.optLong("updatedAt")>0)memory.put("updatedAt",input.getLong("updatedAt"));
        }
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(old==null){ContentValues row=new ContentValues();row.put("id",id);row.put("data",memory.toString());db.insertOrThrow("memories",null,row);}
            else{writeMemory(db,memory);db.delete("traits","memory_id=?",new String[]{id});}
            invalidateDerived(db,id,date);indexMemory(db,memory);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        epoch++;return memory;
    }
    /** Atomic refresh from a successfully read Room snapshot. Standalone materials are never pruned. */
    synchronized void syncArchive(JSONArray inputs) throws Exception {
        SQLiteDatabase db=getWritableDatabase();long before=epoch;db.beginTransaction();
        try{
            Set<String> active=new HashSet<>();
            for(int i=0;i<inputs.length();i++){
                JSONObject input=inputs.getJSONObject(i);active.add(input.getString("id"));save(input,true);
            }
            JSONArray existing=all();
            for(int i=0;i<existing.length();i++){
                JSONObject memory=existing.getJSONObject(i);
                // Before origin metadata existed, the only non-MATERIAL rows created by the UI were manual archive copies.
                boolean legacyArchive=!memory.has("origin")&&!"MATERIAL".equals(memory.optString("kind"));
                if(("ARCHIVE".equals(memory.optString("origin"))||legacyArchive)&&!active.contains(memory.getString("id")))delete(memory.getString("id"));
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();if(epoch!=before)epoch=before+1;}
    }
    synchronized JSONObject finishAnalysis(JSONObject snapshot,String summary,JSONArray traits) throws Exception {
        JSONObject current=get(snapshot.getString("id"));
        if(current.getLong("revision")!=snapshot.getLong("revision"))throw new IOException("材料已修改，请重新整理");
        current.put("summary",summary).put("status","READY").put("revision",current.getLong("revision")+1);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            writeMemory(db,current);db.delete("traits","memory_id=?",new String[]{current.getString("id")});
            for(int i=0;i<traits.length();i++){
                JSONObject trait=traits.getJSONObject(i);ContentValues row=new ContentValues();row.put("id",trait.getString("id"));
                row.put("memory_id",current.getString("id"));row.put("data",trait.toString());db.insertOrThrow("traits",null,row);
            }
            invalidateDerived(db,current.getString("id"),nullable(current,"occurredOn"));db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        epoch++;return current;
    }
    synchronized JSONArray traits() throws Exception {
        JSONArray result=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM traits ORDER BY memory_id,id",null)){
            while(c.moveToNext())result.put(new JSONObject(c.getString(0)));
        }return result;
    }
    synchronized void setTrait(String id,String status) throws Exception {
        if(!Arrays.asList("CONFIRMED","REJECTED").contains(status))throw new IOException("特征状态不正确");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            JSONObject trait;
            try(Cursor c=db.rawQuery("SELECT data FROM traits WHERE id=?",new String[]{id})){
                if(!c.moveToFirst())throw new IOException("这条特征已失效");trait=new JSONObject(c.getString(0));
            }
            trait.put("status",status);ContentValues row=new ContentValues();row.put("data",trait.toString());
            db.update("traits",row,"id=?",new String[]{id});invalidateSessions(db,trait.getString("memoryId"));db.delete("documents","type=?",new String[]{"PROFILE"});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        epoch++;
    }
    synchronized void pin(String id,boolean pinned) throws Exception {
        JSONObject memory=get(id);memory.put("pinned",pinned);writeMemory(getWritableDatabase(),memory);epoch++;
    }
    synchronized void delete(String id) throws Exception {
        get(id);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{String date=nullable(get(id),"occurredOn");invalidateDerived(db,id,date);db.delete("memories","id=?",new String[]{id});db.delete("memory_index","id=?",new String[]{id});db.delete("memory_fts","memory_id=?",new String[]{id});db.setTransactionSuccessful();}
        finally{db.endTransaction();}epoch++;
    }
    synchronized JSONArray history(String session) throws Exception {
        List<JSONObject> reversed=new ArrayList<>();JSONObject metadata=document("SESSION",session);long cutoff=metadata==null?0:metadata.optLong("historyAfterId");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT role,content FROM messages WHERE session_id=? AND id>? ORDER BY id DESC LIMIT 10",new String[]{session,String.valueOf(cutoff)})){
            while(c.moveToNext())reversed.add(new JSONObject().put("role",c.getString(0)).put("content",c.getString(1)));
        }
        Collections.reverse(reversed);JSONArray result=new JSONArray();for(JSONObject message:reversed)result.put(message);return result;
    }
    synchronized void commitChat(long expected,String session,String question,String answer,Set<String> recalled) throws Exception {
        JSONArray sources=new JSONArray();for(String id:recalled){JSONObject m=get(id);sources.put(new JSONObject().put("id",id).put("title",m.getString("title")).put("excerpt",m.getString("content").substring(0,Math.min(400,m.getString("content").length()))));}
        commitChat(expected,session,question,answer,recalled,sources);
    }
    synchronized void commitChat(long expected,String session,String question,String answer,Set<String> recalled,JSONArray sources) throws Exception {
        if(epoch!=expected)throw new IOException("记忆刚刚更新，请重新提问");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            addMessage(db,session,"user",question);
            ContentValues response=new ContentValues();response.put("session_id",session);response.put("role","assistant");response.put("content",answer);
            response.put("data",new JSONObject().put("sources",sources).toString());db.insertOrThrow("messages",null,response);
            JSONObject metadata=document("SESSION",session);
            if(metadata==null)metadata=new JSONObject().put("id",session).put("title",question.substring(0,Math.min(40,question.length()))).put("createdAt",System.currentTimeMillis());
            metadata.put("updatedAt",System.currentTimeMillis()).put("status","ACTIVE");putDocument("SESSION",session,metadata);
            for(String id:recalled){JSONObject memory=get(id);memory.put("recalledAt",System.currentTimeMillis()).put("sleeping",false).put("recallCount",memory.optInt("recallCount")+1);writeMemory(db,memory);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized void clearConversations(){getWritableDatabase().delete("messages",null,null);getWritableDatabase().delete("documents","type=?",new String[]{"SESSION"});epoch++;}
    private void addMessage(SQLiteDatabase db,String session,String role,String content){
        ContentValues row=new ContentValues();row.put("session_id",session);row.put("role",role);row.put("content",content);db.insertOrThrow("messages",null,row);
    }
    private void writeMemory(SQLiteDatabase db,JSONObject memory) throws Exception {
        ContentValues values=new ContentValues();values.put("data",memory.toString());db.update("memories",values,"id=?",new String[]{memory.getString("id")});indexMemory(db,memory);
    }
    synchronized void replaceProfileGroups(JSONArray groups) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            JSONArray previous=documents("PROFILE");db.delete("documents","type=?",new String[]{"PROFILE"});
            for(int i=0;i<groups.length();i++){JSONObject group=groups.getJSONObject(i);
                Set<String> ids=new HashSet<>();JSONArray members=group.getJSONArray("traitIds");for(int j=0;j<members.length();j++)ids.add(members.getString(j));
                for(int j=0;j<previous.length();j++){JSONObject old=previous.getJSONObject(j);String preferred=old.optString("preferredTraitId");if(old.optBoolean("resolvedByUser")&&ids.contains(preferred)){group.put("preferredTraitId",preferred).put("resolvedByUser",true);break;}}
                putDocument("PROFILE",group.getString("id"),group);
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}epoch++;
    }
    synchronized void profilePreferenceChanged(JSONObject group) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            JSONArray members=group.getJSONArray("members");for(int i=0;i<members.length();i++){
                JSONObject member=members.getJSONObject(i);if(group.optString("preferredTraitId").equals(member.optString("id"))){
                    JSONObject actual=null;try(Cursor c=db.rawQuery("SELECT data FROM traits WHERE id=?",new String[]{member.getString("id")})){if(c.moveToFirst())actual=new JSONObject(c.getString(0));}
                    if(actual==null)throw new IOException("特征已变化，请重新整理");actual.put("status","CONFIRMED");ContentValues row=new ContentValues();row.put("data",actual.toString());db.update("traits",row,"id=?",new String[]{actual.getString("id")});member.put("status","CONFIRMED");
                }
                invalidateSessions(db,member.getString("memoryId"));
            }
            putDocument("PROFILE",group.getString("id"),group);db.setTransactionSuccessful();
        }finally{db.endTransaction();}epoch++;
    }
    synchronized JSONObject document(String type,String id) throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM documents WHERE type=? AND id=?",new String[]{type,id})){
            return c.moveToFirst()?new JSONObject(c.getString(0)):null;
        }
    }
    synchronized JSONArray documents(String type) throws Exception {
        JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM documents WHERE type=? ORDER BY rowid DESC",new String[]{type})){
            while(c.moveToNext())result.put(new JSONObject(c.getString(0)));
        }return result;
    }
    synchronized void putDocument(String type,String id,JSONObject data) throws Exception {
        if(!Arrays.asList("SESSION","TASK","PERIOD","PROFILE","PROPOSAL","META").contains(type))throw new IOException("Invalid document type");
        ContentValues row=new ContentValues();row.put("id",id);row.put("type",type);row.put("data",data.toString());getWritableDatabase().insertWithOnConflict("documents",null,row,SQLiteDatabase.CONFLICT_REPLACE);
    }
    synchronized void removeDocument(String id){getWritableDatabase().delete("documents","id=?",new String[]{id});}
    synchronized JSONArray transcript(String session) throws Exception {return transcriptPage(session,0,200).getJSONArray("items");}
    synchronized JSONObject transcriptPage(String session,int offset,int limit) throws Exception {
        if(offset<0)throw new IOException("对话分页位置无效");JSONArray result=new JSONArray();int count;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages WHERE session_id=?",new String[]{session})){c.moveToFirst();count=c.getInt(0);}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,role,content,data FROM messages WHERE session_id=? ORDER BY id DESC LIMIT ? OFFSET ?",new String[]{session,String.valueOf(Math.min(200,Math.max(1,limit))),String.valueOf(offset)})){
            List<JSONObject> reverse=new ArrayList<>();while(c.moveToNext())reverse.add(new JSONObject(c.getString(3)).put("id",c.getLong(0)).put("role",c.getString(1)).put("content",c.getString(2)));
            Collections.reverse(reverse);for(JSONObject message:reverse)result.put(message);
        }
        JSONObject metadata=document("SESSION",session);
        return new JSONObject().put("items",result).put("total",count).put("hasOlder",(long)offset+result.length()<count).put("sourcesChanged",metadata!=null&&metadata.optLong("historyAfterId")>0);
    }
    synchronized JSONObject userMessage(String session,long id) throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT content FROM messages WHERE id=? AND session_id=? AND role='user'",new String[]{String.valueOf(id),session})){
            if(!c.moveToFirst())throw new IOException("原始用户消息不存在");return new JSONObject().put("content",c.getString(0));
        }
    }
    synchronized void sleep(String id,boolean sleeping) throws Exception {
        JSONObject memory=get(id);memory.put("sleeping",sleeping);if(sleeping)memory.put("pinned",false);if(!sleeping)memory.put("recalledAt",System.currentTimeMillis());writeMemory(getWritableDatabase(),memory);epoch++;
    }
    static String memoryState(JSONObject memory,long now){
        if(memory.optBoolean("pinned"))return "CLEAR";
        if(memory.optBoolean("sleeping"))return "DORMANT";
        long last=Math.max(memory.optLong("createdAt"),memory.optLong("recalledAt"));long age=Math.max(0,now-last)/86400000L;
        return age>=180?"DORMANT":age>=30?"FADING":"CLEAR";
    }
    synchronized void correctTrait(String id,String description) throws Exception {
        description=description.trim();if(description.isEmpty()||description.length()>300)throw new IOException("特征文字需在1至300字之间");
        SQLiteDatabase db=getWritableDatabase();JSONObject trait=null;
        try(Cursor c=db.rawQuery("SELECT data FROM traits WHERE id=?",new String[]{id})){if(c.moveToFirst())trait=new JSONObject(c.getString(0));}
        if(trait==null)throw new IOException("特征已失效");
        if(!trait.has("originalDescription"))trait.put("originalDescription",trait.getString("description"));
        trait.put("description",description).put("basis","USER_CORRECTION").put("status","CONFIRMED").put("correctedAt",System.currentTimeMillis());
        ContentValues row=new ContentValues();row.put("data",trait.toString());db.update("traits",row,"id=?",new String[]{id});
        invalidateSessions(db,trait.getString("memoryId"));db.delete("documents","type=?",new String[]{"PROFILE"});epoch++;
    }
    private void invalidateSessions(SQLiteDatabase db,String memoryId) throws Exception {
        Set<String> affected=new HashSet<>();
        try(Cursor c=db.rawQuery("SELECT session_id,data FROM messages WHERE role='assistant'",null)){
            while(c.moveToNext()){
                JSONArray sources=new JSONObject(c.getString(1)).optJSONArray("sources");
                if(sources!=null)for(int i=0;i<sources.length();i++)if(memoryId.equals(sources.getJSONObject(i).optString("id")))affected.add(c.getString(0));
            }
        }
        for(String session:affected){JSONObject metadata=document("SESSION",session);if(metadata!=null){long cutoff=0;
            try(Cursor c=db.rawQuery("SELECT MAX(id) FROM messages WHERE session_id=?",new String[]{session})){if(c.moveToFirst())cutoff=c.getLong(0);}
            putDocument("SESSION",session,metadata.put("status","SOURCES_CHANGED").put("historyAfterId",cutoff));
        }}
    }
    private void invalidateDerived(SQLiteDatabase db,String id,String date) throws Exception {
        invalidateSessions(db,id);db.delete("documents","type=?",new String[]{"PROFILE"});
        JSONArray periods=documents("PERIOD");
        for(int i=0;i<periods.length();i++){
            JSONObject period=periods.getJSONObject(i);JSONObject versions=period.optJSONObject("sourceRevisions");
            boolean inRange=date!=null&&date.compareTo(period.optString("from"))>=0&&date.compareTo(period.optString("to"))<=0;
            if(inRange||(versions!=null&&versions.has(id)))putDocument("PERIOD",period.getString("id"),period.put("status","STALE"));
        }
    }
    private void indexMemory(SQLiteDatabase db,JSONObject memory) throws Exception {
        String id=memory.getString("id");boolean changed=true;
        try(Cursor c=db.rawQuery("SELECT revision FROM memory_index WHERE id=?",new String[]{id})){if(c.moveToFirst())changed=c.getLong(0)!=memory.optLong("revision");}
        ContentValues row=new ContentValues();row.put("id",id);row.put("title",memory.getString("title"));row.put("kind",memory.getString("kind"));row.put("occurred_on",nullable(memory,"occurredOn"));row.put("revision",memory.optLong("revision"));
        String brief=memory.optString("summary");if(brief.isEmpty())brief=memory.getString("content");row.put("brief",brief.substring(0,Math.min(800,brief.length())));
        row.put("test_data",memory.optBoolean("testData")?1:0);row.put("origin",memory.optString("origin","MATERIAL"));row.put("pinned",memory.optBoolean("pinned")?1:0);row.put("created_at",memory.optLong("createdAt"));row.put("recalled_at",memory.optLong("recalledAt"));db.insertWithOnConflict("memory_index",null,row,SQLiteDatabase.CONFLICT_REPLACE);
        if(changed){db.delete("memory_fts","memory_id=?",new String[]{id});ContentValues terms=new ContentValues();terms.put("memory_id",id);terms.put("terms",searchTokens(memory.getString("title")+" "+memory.getString("content")+" "+memory.optString("summary")));db.insertOrThrow("memory_fts",null,terms);}
    }
    static String searchTokens(String text){
        StringBuilder out=new StringBuilder();java.util.regex.Matcher words=java.util.regex.Pattern.compile("[a-zA-Z0-9]+|[\\p{IsHan}]+").matcher(text.toLowerCase(Locale.ROOT));
        while(words.find()){String word=words.group();if(word.matches("[a-z0-9]+"))out.append(word).append(' ');else{if(word.length()==1)out.append(word).append(' ');for(int i=0;i<word.length()-1;i++)out.append(word,i,i+2).append(' ');}}
        return out.toString();
    }
    synchronized JSONArray indexedCandidates(String query,String kind,int limit) throws Exception {
        LinkedHashSet<String> ids=new LinkedHashSet<>();String tokens=searchTokens(query).trim();
        if(!tokens.isEmpty()){
            StringBuilder expression=new StringBuilder();Set<String> unique=new LinkedHashSet<>(Arrays.asList(tokens.split(" +")));
            for(String token:unique){if(expression.length()>0)expression.append(" OR ");expression.append('"').append(token.replace("\"","\"\"")).append('"');if(expression.length()>2000)break;}
            try(Cursor c=getReadableDatabase().rawQuery("SELECT memory_id FROM memory_fts WHERE terms MATCH ? LIMIT ?",new String[]{expression.toString(),String.valueOf(limit)})){while(c.moveToNext())ids.add(c.getString(0));}
        }
        if(kind!=null)try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM memory_index WHERE kind=? ORDER BY pinned DESC,MAX(created_at,recalled_at) DESC,id LIMIT ?",new String[]{kind,String.valueOf(limit)})){while(c.moveToNext())ids.add(c.getString(0));}
        JSONArray result=new JSONArray();for(String id:ids)result.put(get(id));return result;
    }
    synchronized JSONObject catalog(String from,String to,String kind,int offset,int limit) throws Exception {
        String where="1=1";List<String> args=new ArrayList<>();
        if(kind!=null){where+=" AND kind=?";args.add(kind);}if(from!=null){where+=" AND occurred_on>=?";args.add(from);}if(to!=null){where+=" AND occurred_on<=?";args.add(to);}
        int count;try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM memory_index WHERE "+where,args.toArray(new String[0]))){c.moveToFirst();count=c.getInt(0);}
        args.add(String.valueOf(limit));args.add(String.valueOf(offset));JSONArray items=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM memory_index WHERE "+where+" ORDER BY pinned DESC,MAX(created_at,recalled_at) DESC,id LIMIT ? OFFSET ?",args.toArray(new String[0]))){while(c.moveToNext())items.put(get(c.getString(0)));}
        return new JSONObject().put("total",count).put("offset",offset).put("hasMore",(long)offset+items.length()<count).put("items",items);
    }
    synchronized JSONArray briefCatalog(int limit) throws Exception {
        JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,kind,occurred_on,brief FROM memory_index ORDER BY MAX(created_at,recalled_at) DESC,id LIMIT ?",new String[]{String.valueOf(limit)})){
            while(c.moveToNext())out.put(new JSONObject().put("id",c.getString(0)).put("title",c.getString(1)).put("kind",c.getString(2)).put("occurredOn",c.isNull(3)?JSONObject.NULL:c.getString(3)).put("brief",c.getString(4)));
        }return out;
    }
    synchronized void rebuildIndexes() throws Exception {rebuildIndexes(getWritableDatabase());}
    private void rebuildIndexes(SQLiteDatabase db) throws Exception {
        db.delete("memory_index",null,null);db.delete("memory_fts",null,null);
        try(Cursor c=db.rawQuery("SELECT data FROM memories",null)){while(c.moveToNext())indexMemory(db,new JSONObject(c.getString(0)));}
    }
    synchronized JSONObject counts() throws Exception {
        JSONObject counts=new JSONObject();int total=0,archive=0,examples=0;
        for(String kind:new String[]{"MOVIE","BOOK","MUSIC","VOICE","FOOTPRINT","TEXT","MATERIAL","CHAT"})counts.put(kind,0);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT kind,COUNT(*),SUM(CASE WHEN origin='ARCHIVE' THEN 1 ELSE 0 END),SUM(test_data) FROM memory_index GROUP BY kind",null)){
            while(c.moveToNext()){counts.put(c.getString(0),c.getInt(1));total+=c.getInt(1);archive+=c.getInt(2);examples+=c.getInt(3);}
        }
        return new JSONObject().put("total",total).put("archiveCount",archive).put("materialCount",total-archive).put("exampleCount",examples).put("realCount",total-examples).put("excludedExamples",0).put("byKind",counts);
    }
    synchronized JSONObject exportData() throws Exception {
        JSONObject out=new JSONObject().put("version",2).put("memories",all()).put("traits",traits());JSONArray docs=new JSONArray(),messages=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,type,data FROM documents",null)){while(c.moveToNext())if(!"META".equals(c.getString(1)))docs.put(new JSONObject().put("id",c.getString(0)).put("type",c.getString(1)).put("data",new JSONObject(c.getString(2))));}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT session_id,role,content,data,id FROM messages ORDER BY id",null)){while(c.moveToNext())messages.put(new JSONObject().put("id",c.getLong(4)).put("session",c.getString(0)).put("role",c.getString(1)).put("content",c.getString(2)).put("data",new JSONObject(c.getString(3))));}
        return out.put("documents",docs).put("messages",messages);
    }
    synchronized void mergeData(JSONObject data) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            JSONArray memories=data.getJSONArray("memories");Set<String> imported=new HashSet<>(),compatible=new HashSet<>();
            for(int i=0;i<memories.length();i++){
                JSONObject m=memories.getJSONObject(i);String id=m.getString("id");
                try(Cursor c=db.rawQuery("SELECT 1 FROM memories WHERE id=?",new String[]{id})){if(c.moveToFirst()){JSONObject current=get(id);if(current.optString("content").equals(m.optString("content"))&&current.optString("summary").equals(m.optString("summary"))&&current.optLong("revision")==m.optLong("revision")&&current.optBoolean("testData")==m.optBoolean("testData"))compatible.add(id);continue;}}
                compatible.add(id);ContentValues row=new ContentValues();row.put("id",id);row.put("data",m.toString());db.insertOrThrow("memories",null,row);indexMemory(db,m);imported.add(id);
            }
            JSONArray traits=data.getJSONArray("traits");for(int i=0;i<traits.length();i++){
                JSONObject t=traits.getJSONObject(i);if(!imported.contains(t.getString("memoryId")))continue;ContentValues row=new ContentValues();row.put("id",t.getString("id"));row.put("memory_id",t.getString("memoryId"));row.put("data",t.toString());db.insertWithOnConflict("traits",null,row,SQLiteDatabase.CONFLICT_IGNORE);
            }
            JSONArray docs=data.getJSONArray("documents");Set<String> newSessions=new HashSet<>();Map<Long,Long> messageIds=new HashMap<>();
            for(int i=0;i<docs.length();i++){JSONObject d=docs.getJSONObject(i);if("META".equals(d.getString("type")))continue;if(document(d.getString("type"),d.getString("id"))==null){JSONObject body=new JSONObject(d.getJSONObject("data").toString());if("TASK".equals(d.getString("type"))&&"RUNNING".equals(body.optString("status")))body.put("status","PAUSED");
                if("SESSION".equals(d.getString("type")))body.remove("historyAfterId");
                if("TASK".equals(d.getString("type"))&&compatible.size()!=memories.length())body.put("fingerprint","restored-source-changed").put("checkpoint",new JSONObject());
                if("PROFILE".equals(d.getString("type"))&&compatible.size()!=memories.length())continue;
                if("PERIOD".equals(d.getString("type"))&&compatible.size()!=memories.length())body.put("status","STALE");
                putDocument(d.getString("type"),d.getString("id"),body);if("SESSION".equals(d.getString("type")))newSessions.add(d.getString("id"));}}
            JSONArray messages=data.getJSONArray("messages");for(int i=0;i<messages.length();i++){
                JSONObject m=messages.getJSONObject(i);if(!newSessions.contains(m.getString("session")))continue;ContentValues row=new ContentValues();row.put("session_id",m.getString("session"));row.put("role",m.getString("role"));row.put("content",m.getString("content"));row.put("data",m.getJSONObject("data").toString());long newId=db.insertOrThrow("messages",null,row);if(m.has("id"))messageIds.put(m.getLong("id"),newId);
            }
            for(int i=0;i<docs.length();i++){JSONObject doc=docs.getJSONObject(i);if(!"PROPOSAL".equals(doc.optString("type")))continue;JSONObject proposal=document("PROPOSAL",doc.getString("id"));if(proposal!=null&&newSessions.contains(proposal.optString("sessionId"))&&messageIds.containsKey(proposal.optLong("messageId")))putDocument("PROPOSAL",doc.getString("id"),proposal.put("messageId",messageIds.get(proposal.getLong("messageId"))));}
            for(String session:newSessions){JSONObject metadata=document("SESSION",session);boolean stale="SOURCES_CHANGED".equals(metadata.optString("status"));
                JSONArray turns=transcript(session);for(int i=0;i<turns.length();i++){JSONArray sources=turns.getJSONObject(i).optJSONArray("sources");if(sources!=null)for(int j=0;j<sources.length();j++)if(!compatible.contains(sources.getJSONObject(j).optString("id")))stale=true;}
                if(stale){long cutoff=0;try(Cursor c=db.rawQuery("SELECT MAX(id) FROM messages WHERE session_id=?",new String[]{session})){if(c.moveToFirst())cutoff=c.getLong(0);}putDocument("SESSION",session,metadata.put("status","SOURCES_CHANGED").put("historyAfterId",cutoff));}
            }
            removeDocument("meta-archive-version");db.setTransactionSuccessful();
        }finally{db.endTransaction();}epoch++;
    }
    static String nullable(JSONObject object,String key){return object.isNull(key)?null:object.optString(key,null);}
    static void validateDate(String date) throws IOException {
        if(date==null)return;
        if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IOException("日期格式应为 yyyy-MM-dd");
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);format.setLenient(false);
        try{format.parse(date);}catch(ParseException e){throw new IOException("日期不存在，请检查记录日期");}
    }
}
