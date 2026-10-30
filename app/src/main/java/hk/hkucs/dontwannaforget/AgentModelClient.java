package hk.hkucs.dontwannaforget;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** The app's only network boundary: sends HTTPS directly to the user's model provider. */
final class AgentModelClient implements AgentLlm {
    private final ModelSettings settings;
    private final ModelSettings.Snapshot fixedSettings;
    private volatile HttpURLConnection active;
    private volatile boolean cancelled;
    AgentModelClient(ModelSettings settings){this.settings=settings;this.fixedSettings=null;}
    AgentModelClient(ModelSettings.Snapshot snapshot){this.settings=null;this.fixedSettings=snapshot;}
    @Override public JSONObject complete(JSONArray messages,JSONArray tools) throws Exception {
        checkCancelled();
        ModelSettings.Snapshot config=fixedSettings!=null?fixedSettings:settings.snapshot();
        HttpURLConnection conn=(HttpURLConnection)new URL(config.endpoint).openConnection();active=conn;
        try {
            checkCancelled();conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(15000);conn.setReadTimeout(90000);
            conn.setRequestMethod("POST");conn.setDoOutput(true);
            conn.setRequestProperty("Authorization","Bearer "+config.apiKey);
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.setRequestProperty("Accept","application/json");
            JSONObject body=new JSONObject().put("model",config.model).put("messages",AgentContextBudget.fit(messages,AgentContextBudget.DEFAULT_TOKENS)).put("max_tokens",1800);
            if(tools.length()>0)body.put("tools",tools).put("tool_choice","auto");
            try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=conn.getResponseCode();
            if(code<200||code>=300)throw new IOException(httpError(code));
            String text;
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buf=new byte[4096];int count;
                while((count=in.read(buf))!=-1){checkCancelled();if(bytes.size()+count>2000000)throw new IOException("模型响应过大，请缩小问题范围");bytes.write(buf,0,count);}
                text=bytes.toString("UTF-8");
            }
            checkCancelled();JSONObject result=new JSONObject(text);
            JSONArray choices=result.optJSONArray("choices");
            if(choices==null||choices.length()==0)throw invalid();
            JSONObject choice=choices.getJSONObject(0);
            if("length".equals(choice.optString("finish_reason")))throw new IOException("模型回复超出长度限制，请缩短材料或问题后重试");
            JSONObject message=choice.optJSONObject("message");if(message==null)throw invalid();if(message.has("content"))message.put("content",AgentChatReply.text(message.opt("content")));if(message.optString("content").isEmpty()&&message.opt("refusal") instanceof String)message.put("content",message.getString("refusal"));return message;
        } catch(SocketTimeoutException e){throw new IOException("模型连接超时，请检查网络及模型 API 地址后重试");}
        catch(JSONException e){throw invalid();}
        finally{conn.disconnect();active=null;}
    }
    static JSONObject parseJson(String text) throws IOException {
        if(text==null||text.length()>100000)throw invalid();
        String clean=text.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
        try{return new JSONObject(clean);}catch(JSONException ignored){}
        // Extract a balanced object from explanatory prose or Markdown, respecting braces inside strings.
        int attempts=0;for(int start=clean.indexOf('{');start>=0&&attempts++<32;start=clean.indexOf('{',start+1)){
            boolean quoted=false,escaped=false;int depth=0;
            for(int i=start;i<clean.length();i++){char c=clean.charAt(i);
                if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}
                else if(c=='"')quoted=true;else if(c=='{')depth++;else if(c=='}'&&--depth==0){try{return new JSONObject(clean.substring(start,i+1));}catch(JSONException ignored){}break;}
            }
        }throw invalid();
    }

    static IOException invalid(){return new IOException("模型返回格式不正确，请重试；原始材料已保留");}
    private String httpError(int code) {
        if(code==401||code==403)return "模型服务拒绝访问，请检查 API Key 和模型使用权限（"+code+"）";
        if(code==404)return "没有找到模型接口，请检查 API 地址和模型名称（404）";
        if(code==429)return "模型请求额度不足或过于频繁，请检查账户额度或稍后再试（429）";
        if(code==400||code==422)return "模型不接受当前请求，请确认模型支持 Chat Completions 和工具调用（"+code+"）";
        return "模型服务暂时无法响应，请稍后重试（"+code+"）";
    }
    private void checkCancelled() throws IOException {if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("操作已取消，已保存的原文会保留");}
    @Override public void cancel(){cancelled=true;HttpURLConnection connection=active;if(connection!=null)connection.disconnect();}
}
