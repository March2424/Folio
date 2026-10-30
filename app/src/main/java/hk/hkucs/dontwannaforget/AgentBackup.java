package hk.hkucs.dontwannaforget;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Versioned ZIP backup of editable archive, media and agent state. Credentials/preferences are excluded. */
final class AgentBackup {
    private static final long MAX_BYTES=512L*1024*1024;
    private final Context context;private final AgentStore store;private final AppDb archive;private final File files;
    AgentBackup(Context context,AgentStore store,AppDb archive){this(context,store,archive,context.getFilesDir());}
    AgentBackup(Context context,AgentStore store,AppDb archive,File files){this.context=context;this.store=store;this.archive=archive;this.files=files;}
    void exportTo(OutputStream output) throws Exception {
        JSONArray records=new JSONArray(),media=new JSONArray();for(Record r:archive.records().allForBackup())records.put(encode(r));for(Media m:archive.media().allForBackup())media.put(encode(m));
        List<File> paths=new ArrayList<>();collect(files,paths,0);JSONObject checksums=new JSONObject();long size=0;
        for(File file:paths){String path=relative(file);size+=file.length();if(size>MAX_BYTES)throw new IOException("备份超过512MB，请先备份部分媒体或清理不用的文件");checksums.put(path,new JSONObject().put("sha256",hash(file)).put("size",file.length()));}
        JSONObject manifest=new JSONObject().put("format","dontwannaforget-memory").put("version",2).put("createdAt",System.currentTimeMillis()).put("credentialsIncluded",false)
            .put("archiveRecords",records).put("media",media).put("agent",store.exportData()).put("files",checksums);
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            byte[] buffer=new byte[32768];for(File file:paths){zip.putNextEntry(new ZipEntry("files/"+relative(file)));try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}zip.closeEntry();}
        }
    }
    Prepared prepare(InputStream input) throws Exception {
        File stage=new File(context.getCacheDir(),"agent-restore-"+UUID.randomUUID());if(!stage.mkdirs())throw new IOException("无法建立恢复临时目录");
        try{
            JSONObject manifest=null;Set<String> names=new HashSet<>();long total=0;byte[] buffer=new byte[32768];
            try(ZipInputStream zip=new ZipInputStream(input)){
                ZipEntry entry;while((entry=zip.getNextEntry())!=null){
                    String path=entry.getName();if(!names.add(path)||names.size()>5001||entry.isDirectory())throw new IOException("备份目录不正确");
                    if("manifest.json".equals(path)){
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream();int n;while((n=zip.read(buffer))!=-1){if(bytes.size()+n>32*1024*1024)throw new IOException("备份清单过大");bytes.write(buffer,0,n);}manifest=new JSONObject(bytes.toString("UTF-8"));
                    }else{
                        if(!path.startsWith("files/"))throw new IOException("不支持的备份文件");String relative=path.substring(6);safeRelative(relative);
                        File target=new File(stage,relative);File parent=target.getParentFile();if(!parent.exists()&&!parent.mkdirs())throw new IOException("无法创建临时目录");
                        try(OutputStream out=new FileOutputStream(target)){int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("备份解压超过512MB");out.write(buffer,0,n);}}
                    }
                    zip.closeEntry();
                }
            }
            if(manifest==null||!"dontwannaforget-memory".equals(manifest.optString("format"))||manifest.optInt("version")!=2||manifest.optBoolean("credentialsIncluded"))throw new IOException("不是受支持的记忆备份");
            JSONObject expected=manifest.getJSONObject("files");Iterator<String> paths=expected.keys();while(paths.hasNext()){
                String path=paths.next();safeRelative(path);File file=new File(stage,path);JSONObject info=expected.getJSONObject(path);
                if(!file.isFile()||file.length()!=info.getLong("size")||!hash(file).equals(info.getString("sha256")))throw new IOException("备份文件校验失败，尚未修改现有资料");
            }
            for(String path:names)if(path.startsWith("files/")&&!expected.has(path.substring(6)))throw new IOException("备份包含未登记文件");
            validate(manifest);return new Prepared(stage,manifest);
        }catch(Exception e){deleteTree(stage);throw e;}
    }
    JSONObject restore(Prepared prepared) throws Exception {
        JSONObject manifest=prepared.manifest;validate(manifest);
        JSONArray records=manifest.getJSONArray("archiveRecords"),media=manifest.getJSONArray("media");Set<String> imported=new HashSet<>();
        List<File> created=new ArrayList<>();Map<String,String> rewritten=new HashMap<>();boolean[] archiveCommitted={false};
        try{
            JSONObject expected=manifest.getJSONObject("files");Iterator<String> paths=expected.keys();String namespace="restored/"+UUID.randomUUID()+"/";
            while(paths.hasNext()){
                String path=paths.next();File source=new File(prepared.stage,path),destination=new File(files,path);
                if(destination.exists()&&!hash(destination).equals(expected.getJSONObject(path).getString("sha256")))destination=new File(files,namespace+path);
                String selected=files.toURI().relativize(destination.toURI()).getPath();rewritten.put(path,selected);
                if(!destination.exists()){
                    File parent=destination.getParentFile();if(!parent.exists()&&!parent.mkdirs())throw new IOException("无法恢复媒体目录");
                    created.add(destination);try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(destination)){byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
                }
            }
            synchronized(store){
            android.database.sqlite.SQLiteDatabase agentDb=store.getWritableDatabase();agentDb.beginTransaction();try{
            archive.runInTransaction(()->{
                try{
                    for(int i=0;i<records.length();i++){
                        Record r=decode(records.getJSONObject(i),Record.class);if(archive.records().get(r.id)!=null)continue;archive.records().insert(r);imported.add(r.id);
                    }
                    Set<String> existingMedia=new HashSet<>();for(Media m:archive.media().allForBackup())existingMedia.add(m.id);
                    for(int i=0;i<media.length();i++){
                        Media m=decode(media.getJSONObject(i),Media.class);if(!imported.contains(m.recordId)||existingMedia.contains(m.id))continue;
                        if(rewritten.containsKey(m.path))m.path=rewritten.get(m.path);archive.media().insert(m);
                    }
                    store.mergeData(manifest.getJSONObject("agent"));
                }catch(Exception e){throw new RestoreFailure(e);}
            });
            archiveCommitted[0]=true;agentDb.setTransactionSuccessful();
            }finally{agentDb.endTransaction();}}
            return new JSONObject().put("archiveRecordsAdded",imported.size()).put("archiveRecordsKept",records.length()-imported.size()).put("agentMemoriesInBackup",manifest.getJSONObject("agent").getJSONArray("memories").length());
        }catch(Exception e){if(!archiveCommitted[0])for(File file:created)file.delete();throw e;}finally{prepared.close();}
    }
    private void validate(JSONObject manifest) throws Exception {
        JSONArray records=manifest.getJSONArray("archiveRecords"),media=manifest.getJSONArray("media");Set<String> ids=new HashSet<>();
        if(records.length()>100000||media.length()>100000)throw new IOException("备份记录过多");
        for(int i=0;i<records.length();i++){Record r=decode(records.getJSONObject(i),Record.class);validId(r.id);if(!ids.add(r.id)||r.type<0||r.type>5||r.note==null||r.title==null||r.date==null)throw new IOException("备份记录格式不正确");}
        Set<String> mediaIds=new HashSet<>();for(int i=0;i<media.length();i++){Media m=decode(media.getJSONObject(i),Media.class);validId(m.id);safeRelative(m.path);if(!mediaIds.add(m.id)||!ids.contains(m.recordId)||!manifest.getJSONObject("files").has(m.path))throw new IOException("备份媒体引用不完整");}
        JSONObject agent=manifest.getJSONObject("agent");JSONArray memories=agent.getJSONArray("memories");Set<String> memoryIds=new HashSet<>();
        for(int i=0;i<memories.length();i++){
            JSONObject m=memories.getJSONObject(i);validId(m.getString("id"));if(!memoryIds.add(m.getString("id"))||m.getString("content").trim().isEmpty()||m.getString("content").indexOf(0)>=0||m.getString("title").trim().isEmpty()||m.getLong("revision")<1||!Arrays.asList("MOVIE","BOOK","MUSIC","VOICE","FOOTPRINT","TEXT","MATERIAL","CHAT").contains(m.getString("kind")))throw new IOException("记忆数据不完整");
            AgentStore.validateDate(AgentStore.nullable(m,"occurredOn"));m.getString("summary");m.getString("status");m.getLong("createdAt");m.getLong("updatedAt");m.getLong("recalledAt");m.getBoolean("pinned");
            if("ARCHIVE".equals(m.optString("origin"))&&!ids.contains(m.getString("id")))throw new IOException("记忆缺少原记录");
        }
        JSONArray traits=agent.getJSONArray("traits");for(int i=0;i<traits.length();i++){JSONObject t=traits.getJSONObject(i);validId(t.getString("id"));if(t.getString("description").trim().isEmpty()||t.getString("evidence").isEmpty()||!Arrays.asList("EXPLICIT","INFERRED","USER_CORRECTION").contains(t.getString("basis"))||!Arrays.asList("CANDIDATE","CONFIRMED","REJECTED").contains(t.getString("status"))||!memoryIds.contains(t.getString("memoryId")))throw new IOException("特征来源缺失");}
        JSONArray docs=agent.getJSONArray("documents");Set<String> documentIds=new HashSet<>();
        for(int i=0;i<docs.length();i++){
            JSONObject doc=docs.getJSONObject(i);String id=doc.getString("id"),type=doc.getString("type");validId(id);
            if(!documentIds.add(id)||!Arrays.asList("SESSION","TASK","PERIOD","PROFILE","PROPOSAL").contains(type))throw new IOException("不支持的备份资料类型");
            JSONObject body=doc.getJSONObject("data");if(!id.equals(body.getString("id")))throw new IOException("备份资料ID不一致");
            if("PERIOD".equals(type)){AgentStore.validateDate(body.getString("from"));AgentStore.validateDate(body.getString("to"));body.getString("summary");JSONObject versions=body.getJSONObject("sourceRevisions");Iterator<String> keys=versions.keys();while(keys.hasNext())if(!memoryIds.contains(keys.next())&&!"STALE".equals(body.optString("status")))throw new IOException("回顾来源缺失");}
            if("PROFILE".equals(type)){body.getJSONArray("members");body.getJSONArray("traitIds");}
            if("TASK".equals(type)){body.getJSONObject("payload");body.getJSONObject("checkpoint");if(!Arrays.asList("ANALYZE","PERIOD","CHAT","PROFILE").contains(body.getString("type")))throw new IOException("任务类型不支持");}
            if("PROPOSAL".equals(type)){body.getString("content");body.getString("sessionId");body.getLong("messageId");}
        }
        JSONArray messages=agent.getJSONArray("messages");for(int i=0;i<messages.length();i++){JSONObject m=messages.getJSONObject(i);if(!Arrays.asList("user","assistant").contains(m.getString("role")))throw new IOException("会话格式不正确");validId(m.getString("session"));m.getString("content");JSONObject details=m.getJSONObject("data");JSONArray sources=details.optJSONArray("sources");if(sources!=null)for(int j=0;j<sources.length();j++){JSONObject source=sources.getJSONObject(j);source.getString("id");source.getString("title");}}
    }
    private static void validId(String id) throws IOException {if(!id.matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("备份ID不正确");}
    private static void safeRelative(String path) throws IOException {if(path==null||path.isEmpty()||path.startsWith("/")||path.contains("\\")||Arrays.asList(path.split("/",-1)).contains("..")||Arrays.asList(path.split("/",-1)).contains(".")||Arrays.asList(path.split("/",-1)).contains("")||path.contains(":"))throw new IOException("不安全的备份路径");}
    private String relative(File file) throws IOException {String value=files.toURI().relativize(file.toURI()).getPath();safeRelative(value);return value;}
    private static String hash(File file) throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private void collect(File root,List<File> out,int depth) throws IOException {
        if(depth>20)throw new IOException("文件目录过深");File[] children=root.listFiles();if(children==null)return;
        for(File file:children){if(!file.getCanonicalPath().startsWith(files.getCanonicalPath()+File.separator))throw new IOException("备份目录含外部链接");if(file.isDirectory())collect(file,out,depth+1);else{out.add(file);if(out.size()>5000)throw new IOException("备份文件过多");}}
    }
    static JSONObject encode(Object entity) throws Exception {
        JSONObject out=new JSONObject();for(Field f:entity.getClass().getFields()){
            if(java.lang.reflect.Modifier.isStatic(f.getModifiers()))continue;Object value=f.get(entity);out.put(f.getName(),value instanceof String[]?new JSONArray(Arrays.asList((String[])value)):value==null?JSONObject.NULL:value);
        }return out;
    }
    static <T> T decode(JSONObject value,Class<T> type) throws Exception {
        T out=type.getDeclaredConstructor().newInstance();for(Field f:type.getFields()){
            if(java.lang.reflect.Modifier.isStatic(f.getModifiers())||!value.has(f.getName()))continue;
            String key=f.getName();if(f.getType()==String.class)f.set(out,value.isNull(key)?null:value.getString(key));
            else if(f.getType()==int.class)f.setInt(out,value.getInt(key));else if(f.getType()==long.class)f.setLong(out,value.getLong(key));
            else if(f.getType()==String[].class){if(value.isNull(key)){f.set(out,null);continue;}JSONArray a=value.getJSONArray(key);String[] strings=new String[a.length()];for(int i=0;i<a.length();i++)strings[i]=a.getString(i);f.set(out,strings);}
        }return out;
    }
    static void deleteTree(File root){File[] children=root.listFiles();if(children!=null)for(File child:children)deleteTree(child);root.delete();}
    static final class Prepared implements AutoCloseable {
        final File stage;final JSONObject manifest;Prepared(File stage,JSONObject manifest){this.stage=stage;this.manifest=manifest;}
        JSONObject preview() throws Exception {return new JSONObject().put("records",manifest.getJSONArray("archiveRecords").length()).put("media",manifest.getJSONArray("media").length()).put("memories",manifest.getJSONObject("agent").getJSONArray("memories").length());}
        @Override public void close(){deleteTree(stage);}
    }
    private static final class RestoreFailure extends RuntimeException {RestoreFailure(Exception cause){super(cause);}}
}
