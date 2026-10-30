package hk.hkucs.dontwannaforget;

import org.json.*;
import java.util.*;

/** Durable checkpoints contain data and progress only, never model credentials. */
final class AgentTasks {
    private final AgentStore store;
    AgentTasks(AgentStore store){this.store=store;}
    JSONObject begin(String type,String target,String fingerprint,JSONObject payload) throws Exception {
        JSONArray tasks=store.documents("TASK");
        for(int i=0;i<tasks.length();i++){
            JSONObject task=tasks.getJSONObject(i);
            if(!type.equals(task.optString("type"))||!target.equals(task.optString("target"))||!Arrays.asList("RUNNING","PAUSED").contains(task.optString("status")))continue;
            if(fingerprint.equals(task.optString("fingerprint")))return update(task,"RUNNING",null);
            if(!"CHAT".equals(type))update(task,"SUPERSEDED",null);
        }
        return update(new JSONObject().put("id","task-"+UUID.randomUUID()).put("type",type).put("target",target).put("fingerprint",fingerprint)
            .put("payload",payload).put("checkpoint",new JSONObject()).put("createdAt",System.currentTimeMillis()),"RUNNING",null);
    }
    JSONObject update(JSONObject task,String state,String error) throws Exception {
        task.put("status",state).put("updatedAt",System.currentTimeMillis());
        if(error!=null)task.put("error",error);else task.remove("error");store.putDocument("TASK",task.getString("id"),task);return task;
    }
    void checkpoint(JSONObject task,JSONObject checkpoint,int done,int total) throws Exception {
        task.put("checkpoint",checkpoint).put("completed",done).put("total",total);update(task,"RUNNING",null);
    }
    void finish(JSONObject task,JSONObject result) throws Exception {task.put("result",result);update(task,"DONE",null);}
    void failed(JSONObject task,Exception error) throws Exception {update(task,"PAUSED",error.getMessage()==null?"任务已暂停，可继续":error.getMessage());}
    JSONArray pending() throws Exception {
        JSONArray all=store.documents("TASK"),out=new JSONArray();for(int i=0;i<all.length();i++){JSONObject task=all.getJSONObject(i);if(Arrays.asList("RUNNING","PAUSED").contains(task.optString("status")))out.put(task);}return out;
    }
}
