package hk.hkucs.dontwannaforget;

import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Lenient display parsing; only references to material actually supplied this turn become citations. */
final class AgentChatReply {
    static String text(Object value) throws JSONException {
        if(value==null||value==JSONObject.NULL)return "";
        if(value instanceof String)return (String)value;
        if(value instanceof JSONArray){StringBuilder out=new StringBuilder();JSONArray parts=(JSONArray)value;
            for(int i=0;i<parts.length();i++){Object part=parts.opt(i);String t="";if(part instanceof String)t=(String)part;else if(part instanceof JSONObject){JSONObject o=(JSONObject)part;String type=o.optString("type","text");if("text".equals(type)||"output_text".equals(type))t=text(o.opt("text"));}if(!t.isEmpty()){if(out.length()>0)out.append('\n');out.append(t);}}return out.toString();}
        if(value instanceof JSONObject)return value.toString();return "";
    }
    static JSONObject parse(Object content,Map<String,JSONObject> available) throws Exception {
        String raw=text(content).replaceFirst("^\\uFEFF","").trim();
        // Some compatible models place private reasoning in content rather than a separate field.
        raw=raw.replaceAll("(?is)<think>.*?</think>","").trim();if(raw.startsWith("<think>"))raw="";
        if(raw.isEmpty()||raw.length()>100000)throw new IOException("模型没有返回可显示的回答，请重试");
        JSONObject structured=null;try{structured=AgentModelClient.parseJson(raw);}catch(IOException ignored){}
        String answer=structured==null?raw:answer(structured,0);
        if(structured==null&&raw.matches("(?s)^```(?:json)?\\s*.*"))answer=raw.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","").trim();
        if(answer.isEmpty()||answer.trim().startsWith("{\"")||answer.trim().startsWith("{\n")){
            // Recover a complete answer string even when the trailing metadata/closing brace was cut off.
            Matcher field=Pattern.compile("\"(?:answer|reply|response|text|content)\"\\s*:").matcher(raw);answer="";
            if(field.find())try{Object recovered=new JSONTokener(raw.substring(field.end())).nextValue();if(recovered instanceof String)answer=(String)recovered;}catch(JSONException ignored){}
        }
        if(answer.trim().isEmpty())throw new IOException("模型没有返回可显示的回答，请重试");
        if(answer.length()>12000){int end=12000;if(Character.isHighSurrogate(answer.charAt(end-1)))end--;answer=answer.substring(0,end)+"\n（回答较长，已显示前半部分，可继续追问。）";}
        LinkedHashSet<String> cited=new LinkedHashSet<>();boolean[] unverified={false};Set<String> unknown=new HashSet<>();
        List<String> referenceOrder=new ArrayList<>(available.keySet());List<String> declared=new ArrayList<>();
        if(structured!=null){Object ids=null;for(String key:new String[]{"sourceIds","source_ids","sources","citations"})if(structured.has(key)&&!structured.isNull(key)){ids=structured.opt(key);break;}
            if(ids instanceof String){String s=((String)ids).trim();try{ids=new JSONArray(s);}catch(JSONException ignored){ids=new JSONArray(Arrays.asList(s.split("[,，;；]")));}}
            if(ids instanceof JSONObject||ids instanceof Number)ids=new JSONArray().put(ids);
            if(ids instanceof JSONArray){JSONArray list=(JSONArray)ids;for(int i=0;i<Math.min(120,list.length());i++){
                Object entry=list.opt(i);if(entry instanceof JSONObject){JSONObject o=(JSONObject)entry;entry=o.opt("id");if(entry==null)entry=o.opt("memoryId");if(entry==null)entry=o.opt("sourceId");}
                String key=entry==null?"":String.valueOf(entry).trim();if(key.isEmpty()||"null".equals(key))continue;
                String id=resolve(key,available,referenceOrder);if(id!=null){if(!declared.contains(id))declared.add(id);cited.add(id);}else{unverified[0]=true;unknown.add(key);}
            }}
        }
        List<String> numbers=declared.isEmpty()?referenceOrder:declared;
        Matcher markers=Pattern.compile("\\[([^\\[\\]\\n]{1,100})\\]|【([^【】\\n]{1,100})】").matcher(answer);StringBuffer rendered=new StringBuffer();
        while(markers.find()){
            String key=markers.group(1)==null?markers.group(2):markers.group(1);String id=resolve(key,available,numbers);String replacement=markers.group();
            if(id!=null){cited.add(id);replacement="["+(new ArrayList<>(cited).indexOf(id)+1)+"]";}
            else if(unknown.contains(key)||key.matches("[0-9]+|[A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)+")){replacement="";unverified[0]=true;}
            markers.appendReplacement(rendered,Matcher.quoteReplacement(replacement));
        }markers.appendTail(rendered);answer=rendered.toString().trim();
        JSONArray sources=new JSONArray();boolean examples=false;for(String id:cited){JSONObject source=available.get(id);sources.put(source);examples|=source.optBoolean("testData");}
        if(unverified[0])answer+="\n\n（部分来源未能核实，请以原文为准。）";
        if(examples&&!answer.contains("示例"))answer="以下内容包含示例记忆，供测试参考。\n\n"+answer;
        return new JSONObject().put("answer",answer).put("sources",sources);
    }
    private static String answer(JSONObject o,int depth) throws JSONException {
        if(depth>3)return "";for(String key:new String[]{"answer","reply","response","text","content","message"}){Object v=o.opt(key);if(v instanceof JSONObject){String nested=answer((JSONObject)v,depth+1);if(!nested.isEmpty())return nested;}
            else {String t=text(v).trim();if(!t.isEmpty())return t;}}
        return "";
    }
    private static String resolve(String key,Map<String,JSONObject> available,List<String> order){
        key=key.trim();if(available.containsKey(key))return key;
        if(key.matches("[0-9]{1,6}")){int i=Integer.parseInt(key)-1;return i>=0&&i<order.size()?order.get(i):null;}
        String match=null;for(Map.Entry<String,JSONObject> entry:available.entrySet())if(key.equals(entry.getValue().optString("title"))){if(match!=null)return null;match=entry.getKey();}return match;
    }
}
