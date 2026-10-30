package hk.hkucs.dontwannaforget;

import org.json.JSONArray;
import org.json.JSONObject;

interface AgentLlm {
    JSONObject complete(JSONArray messages, JSONArray tools) throws Exception;
    void cancel();
}
