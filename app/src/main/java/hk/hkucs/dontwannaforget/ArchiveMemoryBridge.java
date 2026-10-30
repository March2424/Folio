package hk.hkucs.dontwannaforget;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Connects every archive module's human-readable fields, not its presentation JSON, to agent memory. */
final class ArchiveMemoryBridge {
    interface Records { List<Record> active() throws Exception; default String version() throws Exception {return null;} }
    static final String[] KINDS={"MOVIE","BOOK","VOICE","MUSIC","FOOTPRINT","TEXT"};
    static final String[] NAMES={"电影票根","书籍","声音","音乐","足迹","写信"};
    private final AgentStore store;
    private final Records records;
    private int excludedExamples;
    ArchiveMemoryBridge(AgentStore store,Records records){this.store=store;this.records=records;}
    void refresh() throws Exception {
        String version=records.version();JSONObject cached=store.document("META","meta-archive-version");
        if(version!=null&&cached!=null&&version.equals(cached.optString("version")))return;
        List<Record> snapshot=records.active();
        if(snapshot==null)throw new IOException("记忆册暂时无法读取，请重试");
        JSONArray inputs=new JSONArray();int examples=0;
        for(Record record:snapshot){
            if(record==null||record.deletedAt!=0)continue;
            if(record.id.startsWith(Record.SAMPLE_PREFIX))examples++;
            inputs.put(serialize(record));
        }
        store.syncArchive(inputs);excludedExamples=examples;if(version!=null)store.putDocument("META","meta-archive-version",new JSONObject().put("version",version));
    }
    int excludedExamples(){return excludedExamples;}
    static JSONObject serialize(Record r) throws Exception {
        if(r.type<0||r.type>=KINDS.length)throw new IOException("记忆册包含尚不支持的记录类型");
        StringBuilder text=new StringBuilder();
        append(text,"记录类型",NAMES[r.type]);append(text,"标题",r.title);append(text,"记录日期",r.date);
        String[] subtitles={"导演/相关人物","作者","声音来源/地点","歌手","地点","收信人/相关人物"};
        append(text,subtitles[r.type],r.sub);append(text,"评分",r.rating);
        String label=r.type==Record.BOOK?"书摘/记录文字（摘录不等于用户观点）":r.type==Record.MUSIC?"歌词/记录文字（歌词不等于用户观点）":"用户记录";
        append(text,label,r.note);
        if(r.type==Record.TICKET){
            String[] names={"影院","影厅","座位","版本","票价","场次时间"};
            for(int i=0;i<names.length;i++)append(text,names[i],field(r,i));
        }else if(r.type==Record.BOOK||r.type==Record.MUSIC){
            for(String field:fields(r)){
                if(!field.trim().startsWith("{"))continue;
                try{append(text,"用户自己的感想",new JSONObject(field).optString("th",""));}catch(JSONException ignored){}
            }
        }else if(r.type==Record.VOICE){
            append(text,"录音时长",field(r,0));append(text,"来源描述",field(r,1));
            append(text,"媒体说明","这里只接入声音的标题、说明和文字故事，未转写或听取音频文件。");
        }else if(r.type==Record.FOOTPRINT){
            String[] pages=fields(r);
            for(int page=0;page<pages.length;page++){
                String raw=pages[page];
                if(raw.trim().startsWith("{")){
                    try{
                        JSONObject state=new JSONObject(raw);append(text,"第"+(page+1)+"页时间",state.optString("time",""));
                        JSONArray boxes=state.optJSONArray("boxes");
                        if(boxes!=null)for(int i=0;i<boxes.length();i++){
                            JSONObject box=boxes.optJSONObject(i);if(box!=null)append(text,"第"+(page+1)+"页文字",box.optString("t",""));
                        }
                    }catch(JSONException ignored){}
                }else append(text,"第"+(page+1)+"页文字",raw);
            }
        }
        String iso=r.date==null?null:r.date.replace('.','-');
        try{AgentStore.validateDate(iso);}catch(IOException e){iso=null;}
        String title=r.title==null||r.title.trim().isEmpty()?NAMES[r.type]:r.title;
        return new JSONObject().put("id",r.id).put("title",title).put("kind",KINDS[r.type]).put("occurredOn",iso==null?JSONObject.NULL:iso)
            .put("content",text.toString()).put("origin","ARCHIVE").put("testData",r.id.startsWith(Record.SAMPLE_PREFIX)).put("recordType",r.type)
            .put("createdAt",r.createdAt).put("updatedAt",r.updatedAt);
    }
    private static String[] fields(Record record){return record.fields==null?new String[0]:record.fields;}
    private static String field(Record record,int index){String[] values=fields(record);return index<values.length?values[index]:null;}
    private static void append(StringBuilder out,String label,String value){
        if(value!=null&&!value.trim().isEmpty()){if(out.length()>0)out.append('\n');out.append(label).append("：").append(value);}
    }
}
