package hk.hkucs.dontwannaforget;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Search meaning includes structured record type; category questions do not require that word in the note. */
final class AgentSearch {
    private final AgentStore store;
    private static final String[][] CATEGORIES={
        {"MOVIE","电影|電影|票根|影片|片子|movies?|films?|cinema|tickets?"},
        {"BOOK","书籍|書籍|读书|讀書|阅读|閱讀|书|書|books?|reading"},
        {"MUSIC","音乐|音樂|歌曲|歌单|歌單|听歌|聽歌|歌|music|songs?|tracks?"},
        {"VOICE","声音|聲音|录音|錄音|音频|音頻|voice|audio|recordings?"},
        {"FOOTPRINT","足迹|足跡|旅行|旅游|旅遊|地点|地點|去过|去過|footprints?|trips?|travel|places?"},
        {"TEXT","写信|寫信|信件|文字|日记|日記|letters?|writing|writings?|written|wrote|diary|diaries"}
    };
    AgentSearch(AgentStore store){this.store=store;}
    JSONArray search(String query,String from,String to,String kind,int limit) throws Exception {
        List<Hit> hits=rank(query,from,to,kind);JSONArray result=new JSONArray();
        for(int i=0;i<Math.min(Math.max(1,Math.min(limit,8)),hits.size());i++)result.put(hits.get(i).memory);return result;
    }
    JSONObject page(String from,String to,String kind,int offset,int limit) throws Exception {
        if(offset<0)throw new IOException("分页位置不能为负数");
        AgentStore.validateDate(from);AgentStore.validateDate(to);String normalized=normalizeKind(kind);
        if(from!=null&&to!=null&&from.compareTo(to)>0)throw new IOException("开始日期不能晚于结束日期");
        return store.catalog(from,to,normalized,offset,Math.max(1,Math.min(limit,20)));
    }
    String overviewKind(String question){String kind=inferKind(question);return kind!=null&&residual(question,kind).isEmpty()?kind:null;}
    private List<Hit> rank(String query,String from,String to,String requestedKind) throws Exception {
        AgentStore.validateDate(from);AgentStore.validateDate(to);
        if(from!=null&&to!=null&&from.compareTo(to)>0)throw new IOException("开始日期不能晚于结束日期");
        if(query.length()>2000)throw new IOException("检索词过长");
        String filter=normalizeKind(requestedKind),inferred=inferKind(query),category=filter!=null?filter:inferred;
        String q=category==null?query.toLowerCase(Locale.ROOT).trim():residual(query,category);
        Set<String> terms=terms(q);List<Hit> hits=new ArrayList<>();JSONArray all=q.isEmpty()?store.catalog(from,to,category,0,250).getJSONArray("items"):store.indexedCandidates(q,category,250);
        if(q.isEmpty()&&filter==null&&category!=null){
            JSONArray extras=store.indexedCandidates(query,null,250);Set<String> seen=new HashSet<>();for(int i=0;i<all.length();i++)seen.add(all.getJSONObject(i).getString("id"));
            for(int i=0;i<extras.length();i++){JSONObject extra=extras.getJSONObject(i);if("MATERIAL".equals(extra.optString("kind"))&&seen.add(extra.getString("id")))all.put(extra);}
        }
        for(int i=0;i<all.length();i++){
            JSONObject memory=all.getJSONObject(i);String date=AgentStore.nullable(memory,"occurredOn"),kind=memory.getString("kind");
            if(from!=null&&(date==null||date.compareTo(from)<0))continue;
            if(to!=null&&(date==null||date.compareTo(to)>0))continue;
            if(filter!=null&&!filter.equals(kind))continue;
            String title=memory.getString("title").toLowerCase(Locale.ROOT);
            String text=(memory.getString("summary")+"\n"+memory.getString("content")+"\n"+kind).toLowerCase(Locale.ROOT);
            for(String[] aliases:CATEGORIES)if(aliases[0].equals(kind))text+=" "+aliases[1];
            double relevance=0;
            if(q.isEmpty()){
                if(category==null||category.equals(kind))relevance=8;
                else if(filter==null&&"MATERIAL".equals(kind)&&hasCategory(text,category))relevance=4;
            }else{
                relevance=(title.contains(q)?20:0)+(text.contains(q)?8:0);
                for(String term:terms)relevance+=(title.contains(term)?4:0)+(text.contains(term)?1:0);
                if(relevance>0&&category!=null&&category.equals(kind))relevance+=2;
            }
            if(relevance==0)continue;
            double age=Math.max(0,System.currentTimeMillis()-Math.max(memory.getLong("createdAt"),memory.getLong("recalledAt")))/86400000.0;
            relevance*= "DORMANT".equals(AgentStore.memoryState(memory,System.currentTimeMillis()))?0.75:"FADING".equals(AgentStore.memoryState(memory,System.currentTimeMillis()))?0.9:1;
            hits.add(new Hit(memory,relevance+(memory.getBoolean("pinned")?0.7:0)+0.3/(1+age/90)));
        }
        hits.sort((a,b)->{int order=Double.compare(b.score,a.score);return order!=0?order:a.memory.optString("id").compareTo(b.memory.optString("id"));});return hits;
    }
    static String normalizeKind(String value) throws IOException {
        if(value==null||value.trim().isEmpty())return null;
        String kind=value.trim().toUpperCase(Locale.ROOT);
        if("TICKET".equals(kind))return "MOVIE";
        if("LETTER".equals(kind))return "TEXT";
        if(Arrays.asList("MOVIE","BOOK","MUSIC","VOICE","FOOTPRINT","TEXT","MATERIAL","CHAT").contains(kind))return kind;
        String inferred=inferKind(value);if(inferred!=null)return inferred;
        throw new IOException("不支持的记忆类型");
    }
    private static String inferKind(String query){
        String found=null;
        for(String[] category:CATEGORIES)if(hasCategory(query,category[0])){
            if(found!=null&&!found.equals(category[0]))return null;found=category[0];
        }return found;
    }
    private static boolean hasCategory(String text,String kind){
        for(String[] category:CATEGORIES)if(category[0].equals(kind))return java.util.regex.Pattern.compile(categoryPattern(category[1]),java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text).find();
        return false;
    }
    private static String categoryPattern(String aliases){
        List<String> expressions=new ArrayList<>();
        for(String alias:aliases.split("\\|"))expressions.add(alias.matches(".*[\\p{IsHan}].*")?alias:"\\b(?:"+alias+")\\b");
        return android.text.TextUtils.join("|",expressions);
    }
    private String residual(String query,String kind){
        String value=query.toLowerCase(Locale.ROOT);
        for(String[] category:CATEGORIES)if(category[0].equals(kind))value=value.replaceAll(categoryPattern(category[1])," ");
        value=value.replaceAll("\\b(?:what|which|have|has|had|i|we|you|my|me|did|do|does|the|a|an|all|list|show|tell|of|that|watched|watch|seen|read|reading|listened|listen|to|written|write|wrote|visited|visit|recorded|saved|please)\\b"," ")
            .replaceAll("我们|我們|我|你|看过|看過|读过|讀過|听过|聽過|写过|寫過|看|读|讀|听|聽|写|寫|的|了|过|過|哪些|哪几|哪幾|什么|什麼|多少|所有|全部|列出|列一下|帮我|幫我|记录|記錄|保存|曾经|曾經|已经|已經|记得|記得|是|有|请|請"," ");
        return value.replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
    private Set<String> terms(String query){
        Set<String> result=new LinkedHashSet<>();
        for(String word:query.split("[^\\p{L}\\p{N}]+")){
            if(word.isEmpty())continue;
            if(word.matches(".*[\\p{IsHan}].*")){
                if(word.length()==1)result.add(word);
                else for(int i=0;i<word.length()-1;i++)result.add(word.substring(i,i+2));
            }else if(word.length()>=2)result.add(word);
        }return result;
    }
    private static final class Hit {final JSONObject memory;final double score;Hit(JSONObject memory,double score){this.memory=memory;this.score=score;}}
}
