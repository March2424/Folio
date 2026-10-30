package hk.hkucs.dontwannaforget;

import org.json.*;
import java.io.IOException;

/** Conservative token estimate, not a provider tokenizer. Originals in SQLite are never shortened. */
final class AgentContextBudget {
    static final int DEFAULT_TOKENS=7000;
    static int estimate(String text){int units=0;for(int i=0;i<text.length();i++)units+=text.charAt(i)<128?1:4;return (units+2)/3;}
    static String clip(String text,int maxTokens){
        if(estimate(text)<=maxTokens)return text;int units=0,end=0;while(end<text.length()&&units+(text.charAt(end)<128?1:4)<=Math.max(0,maxTokens-12)*3){units+=text.charAt(end)<128?1:4;end++;}
        if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;return text.substring(0,end)+"\n[已压缩，可调用工具继续阅读原文]";
    }
    static JSONArray fit(JSONArray original,int budget) throws Exception {
        JSONArray messages=new JSONArray(original.toString());
        if(messages.length()==0)return messages;
        int total=estimate(messages.toString());if(total<=budget)return messages;
        // Compress JSON reference fields while preserving every role and tool-call association.
        for(int i=1;i<messages.length();i++){
            JSONObject message=messages.getJSONObject(i);String role=message.optString("role");Object content=message.opt("content");
            if(!(content instanceof String))continue;String text=(String)content;
            int cap="tool".equals(role)?900:1200;
            if(i==messages.length()-1&&"user".equals(role))cap=2200;
            if(estimate(text)>cap){
                int start=text.indexOf('{');
                try{JSONObject data=new JSONObject(text.substring(Math.max(0,start)));compact(data,500);message.put("content",(start>0?text.substring(0,start):"")+data);}
                catch(JSONException e){message.put("content",clip(text,cap));}
            }
        }
        total=estimate(messages.toString());
        if(total>budget){
            // History has no tool calls. Keep the most recent conversation, main reference, system and current tool trace.
            for(int i=2;i<messages.length()-1&&total>budget;i++){
                JSONObject m=messages.getJSONObject(i);if(m.has("tool_calls")||"tool".equals(m.optString("role")))continue;
                String text=m.optString("content","");m.put("content",clip(text,80));total=estimate(messages.toString());
            }
        }
        if(estimate(messages.toString())>budget){
            for(int i=1;i<messages.length()-1;i++){
                JSONObject m=messages.getJSONObject(i);String text=m.optString("content","");if(!text.isEmpty())m.put("content",clip(text,250));
            }
        }
        if(estimate(messages.toString())>budget)throw new IOException("本次资料超出上下文预算，请缩小时间范围或分开提问");
        return messages;
    }
    private static void compact(Object node,int cap) throws JSONException {
        if(node instanceof JSONObject){
            JSONObject o=(JSONObject)node;java.util.Iterator<String> keys=o.keys();while(keys.hasNext()){
                String key=keys.next();Object value=o.opt(key);
                if(value instanceof String&&("excerpt".equals(key)||"summary".equals(key)||"brief".equals(key)||"content".equals(key)))o.put(key,clip((String)value,cap));else compact(value,cap);
            }
        }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)compact(a.opt(i),cap);}
    }
}
