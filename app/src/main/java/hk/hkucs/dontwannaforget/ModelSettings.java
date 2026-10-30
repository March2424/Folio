package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.net.URI;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

/** User-owned LLM credentials; unrelated to the retired server's access token. */
final class ModelSettings {
    static final String DEFAULT_BASE = "https://api.openai.com/v1";
    private final Context context;
    private final SharedPreferences prefs;
    private final String alias;
    ModelSettings(Context context) { this(context, "llm_settings", "memory_llm_api_key"); }
    ModelSettings(Context context, String preferenceName, String alias) {
        this.context=context.getApplicationContext();this.alias=alias;
        prefs=context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
    }
    String baseUrl() { return prefs.getString("baseUrl", DEFAULT_BASE); }
    String model() { return prefs.getString("model", ""); }
    boolean hasKey() { return prefs.contains("apiKey"); }
    String apiKey() throws Exception {
        String encrypted=prefs.getString("apiKey", "");if(encrypted.isEmpty())return "";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    void save(String base, String model, String apiKey) throws Exception {
        base=normalizeBase(base);model=model.trim();apiKey=apiKey.trim();
        validateModel(model);
        if(apiKey.isEmpty()||apiKey.length()>8192||apiKey.contains("\n")||apiKey.contains("\r"))
            throw new IllegalArgumentException("请填写有效的模型 API Key");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
        if(!prefs.edit().putString("baseUrl",base).putString("model",model)
            .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("apiKey",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())
            throw new java.io.IOException("模型设置保存失败");
    }
    void clearKey() throws Exception {
        if(!prefs.edit().remove("apiKey").remove("iv").commit())throw new java.io.IOException("移除密钥失败");
    }
    Snapshot snapshot() throws Exception {
        String key=apiKey();
        if(key.isEmpty()||model().isEmpty())throw new java.io.IOException("请先到“模型”填写 API Key 和模型名称；本地材料仍可查看和保存");
        return new Snapshot(normalizeBase(baseUrl())+"/chat/completions",model(),key);
    }
    String normalizeBase(String base) {
        base=base.trim();if(base.isEmpty())base=DEFAULT_BASE;
        base=base.replaceAll("/+$","");
        if(base.endsWith("/chat/completions"))base=base.substring(0,base.length()-"/chat/completions".length());
        URI uri;
        try{uri=URI.create(base);}catch(IllegalArgumentException e){throw new IllegalArgumentException("模型 API 地址格式不正确，请使用供应商提供的 HTTPS 地址");}
        boolean debug=(context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||
            !("https".equals(uri.getScheme())||(debug&&"http".equals(uri.getScheme()))))
            throw new IllegalArgumentException("请输入模型供应商提供的 HTTPS API 地址，例如 https://example.com/v1");
        return base;
    }
    private void validateModel(String model) {
        if(model.isEmpty()||model.length()>200||!model.matches("\\S+"))
            throw new IllegalArgumentException("请填写供应商提供的模型名称，不能包含空格");
    }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return generator.generateKey();
        }
        return (SecretKey)store.getKey(alias,null);
    }
    static final class Snapshot {
        final String endpoint,model,apiKey;
        Snapshot(String endpoint,String model,String apiKey){this.endpoint=endpoint;this.model=model;this.apiKey=apiKey;}
    }
}
