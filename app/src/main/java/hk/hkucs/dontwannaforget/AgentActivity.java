package hk.hkucs.dontwannaforget;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.core.graphics.Insets;
import androidx.lifecycle.ViewModelProvider;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** On-device Java agent: local memories and profile, direct calls to the user's LLM provider. */
public class AgentActivity extends AppCompatActivity {
    private static final int INK=0xFF333333, MUTED=0xFF858585, PAPER=0xFFFCFCFC;
    private LinearLayout root, content, navigation, dock, composer, turns;
    private ScrollView scroll;
    private int headerHeight;
    private TextView heading,subtitle;
    private ImageView send;
    private View pendingTurn,thinkingTurn;
    private String pendingMessage="";
    private TextView status;
    private ModelSettings config;
    private AgentJobModel jobs;
    private final Map<String,Done<Object>> callbacks=new HashMap<>();
    private int memoryOffset,turnOffset;
    private ActivityResultLauncher<String> backupWriter;
    private ActivityResultLauncher<String[]> backupReader;
    private boolean busy;
    private String page=L10n.s(R.string.ui_209),session=UUID.randomUUID().toString(),draft="",materialTitle="",materialText="",materialDate="";
    private String aboutTitle="",aboutText="",aboutDate="",importPage=L10n.s(R.string.ui_210);
    private EditText question, titleInput, materialInput,dateInput;
    private ActivityResultLauncher<String[]> picker;
    private Uri pendingImport;
    private interface Work<T> {T run(LocalMemoryAgent client) throws Exception;}
    private interface Done<T> {void accept(T result) throws Exception;}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        config=new ModelSettings(this);
        session=getSharedPreferences("agent_ui",MODE_PRIVATE).getString("session",session);
        jobs=new ViewModelProvider(this).get(AgentJobModel.class);
        // Consume a completed result before starting another page read after recreation.
        AgentJobModel.State pending=jobs.state.getValue();busy=pending!=null;
        backupWriter=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"),uri->{
            if(uri!=null){android.content.Context app=getApplicationContext();run(L10n.s(R.string.ui_211),client->{
                try(OutputStream out=app.getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException(L10n.s(R.string.ui_212));new AgentBackup(app,AgentStore.get(app),AppDb.get(app)).exportTo(out);}return true;
            },v->toast(L10n.s(R.string.ui_213)));}
        });
        backupReader=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
            if(uri!=null){android.content.Context app=getApplicationContext();run(L10n.s(R.string.ui_214),client->{
                try(InputStream in=app.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException(L10n.s(R.string.ui_215));return new AgentBackup(app,AgentStore.get(app),AppDb.get(app)).prepare(in);}
            },this::reviewBackup);}
        });
        if(saved==null){
            if(!config.hasKey()||config.model().isEmpty())page=L10n.s(R.string.ui_216);
            String initial=getIntent().getStringExtra("page");if(Arrays.asList(L10n.s(R.string.ui_209),L10n.s(R.string.ui_210),L10n.s(R.string.ui_217),L10n.s(R.string.ui_218),L10n.s(R.string.ui_219),L10n.s(R.string.ui_216)).contains(initial))page=initial;
        }
        if(saved!=null) {
            session=saved.getString("session",session);draft=saved.getString("draft","");pendingMessage=saved.getString("pendingMessage","");
            materialTitle=saved.getString("materialTitle","");materialText=saved.getString("materialText","");materialDate=saved.getString("materialDate","");
            String waiting=saved.getString("pendingImport");if(waiting!=null)pendingImport=Uri.parse(waiting);
            aboutTitle=saved.getString("aboutTitle","");aboutText=saved.getString("aboutText","");aboutDate=saved.getString("aboutDate","");importPage=saved.getString("importPage",L10n.s(R.string.ui_210));
            page=saved.getString("page",L10n.s(R.string.ui_209));turnOffset=saved.getInt("turnOffset",0);
        }
        picker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null)readFile(uri);});
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(PAPER);
        root.setPadding(0,0,0,0);root.setFocusableInTouchMode(true);setContentView(root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        WindowCompat.getInsetsController(getWindow(),root).setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        FrostedNavigation header=new FrostedNavigation(this,null);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(4),0,dp(4),0);header.setCornerRadiusDp(28);header.setGlassAppearance(0x30FFFFFF,0x70FFFFFF,220);
        ImageView back=icon("back",L10n.s(R.string.ui_220),INK);back.setOnClickListener(v->finish());HomeNavigation.bind(back);header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        header.addView(avatar(false),new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout identity=new LinearLayout(this);identity.setOrientation(LinearLayout.VERTICAL);identity.setPadding(dp(8),0,0,0);header.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        heading=text(L10n.s(R.string.ui_221),16,INK);heading.setTypeface(AppFonts.regular(),Typeface.BOLD);compactHeaderText(heading);identity.addView(heading);
        subtitle=text(L10n.s(R.string.ui_222),10,MUTED);compactHeaderText(subtitle);LinearLayout.LayoutParams subtitleParams=new LinearLayout.LayoutParams(-1,-2);subtitleParams.topMargin=dp(2);identity.addView(subtitle,subtitleParams);
        TextView menu=text("···",26,INK);menu.setGravity(Gravity.CENTER);menu.setContentDescription(L10n.s(R.string.ui_223));menu.setOnClickListener(v->chatMenu(menu));header.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(48)));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setBackground(new ChatWallpaper(getResources().getDisplayMetrics().density));FrameLayout readingLayer=new FrameLayout(this);root.addView(readingLayer,new LinearLayout.LayoutParams(-1,0,1));readingLayer.addView(scroll,new FrameLayout.LayoutParams(-1,-1));headerHeight=dp(64);
        FrameLayout.LayoutParams headerParams=new FrameLayout.LayoutParams(-1,headerHeight-dp(12),Gravity.TOP);
        headerParams.setMargins(dp(12),dp(6),dp(12),0);readingLayer.addView(header,headerParams);
        header.setBackdrop(scroll,null);FloatingHeader.style(header);header.setAlpha(1f);
        scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,oldX,oldY)->header.invalidate());
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(root);
            Insets bars=insets==null?Insets.NONE:insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Base the header on the full page, so opening the keyboard does not resize it.
            int next=Math.max(dp(56),Math.min(dp(80),Math.round((b-t-bars.top-bars.bottom)/9f)));
            if(headerHeight!=next){
                headerHeight=next;header.getLayoutParams().height=next-dp(12);header.requestLayout();
                content.setPadding(dp(16),next+dp(4),dp(16),dp(20));
            }
        });
        dock=new LinearLayout(this);dock.setOrientation(LinearLayout.VERTICAL);dock.setPadding(dp(12),dp(2),dp(12),0);dock.setBackgroundColor(0xFFFFFFFF);root.addView(dock);
        status=text("",11,MUTED);status.setPadding(dp(12),0,dp(12),0);status.setVisibility(View.GONE);dock.addView(status);
        composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(2),0,dp(2),0);
        GradientDrawable inputShell=card(0xFFE4E4E4);inputShell.setCornerRadius(dp(22));inputShell.setStroke(dp(1),0xFFFFFFFF);composer.setBackground(inputShell);dock.addView(composer,new LinearLayout.LayoutParams(-1,-2));
        navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);navigation.setPadding(dp(4),0,dp(4),0);
        GradientDrawable navShell=card(0xFFF1F1F1);navShell.setCornerRadius(dp(20));navShell.setStroke(dp(1),0xFFFFFFFF);navigation.setBackground(navShell);navigation.setElevation(0);
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,dp(32));navParams.setMargins(dp(4),dp(2),dp(4),0);dock.addView(navigation,navParams);
        show(page);
        jobs.state.observe(this,state->{
            if(state==null)return;busy=state.running;setStatus(state.running?state.label:"");updateSend();
            if(state.running)return;
            Done<Object> done=callbacks.remove(state.id);jobs.acknowledge();
            if(state.error!=null){if(done==null)show(state.page);removePending();setStatus(state.error);drainImport();return;}
            try{if(done!=null)done.accept(state.result);else if(state.result instanceof AgentBackup.Prepared)reviewBackup((AgentBackup.Prepared)state.result);else {
                if(state.result instanceof JSONObject){JSONObject result=(JSONObject)state.result;if(result.optBoolean("fileImport")){applyImportedText(result);return;}if(result.has("sessionId")&&result.has("answer")){session=result.optString("sessionId",session);saveSession();pendingMessage="";}}
                show(state.page);
            }}
            catch(Exception e){setStatus(e.getMessage()==null?L10n.s(R.string.ui_224):e.getMessage());}
            drainImport();
        });
        drainImport();
    }
    private void rememberDrafts() {
        if(question!=null)draft=question.getText().toString();
        if(L10n.s(R.string.ui_217).equals(page)){
            if(titleInput!=null)aboutTitle=titleInput.getText().toString();
            if(materialInput!=null)aboutText=materialInput.getText().toString();
            if(dateInput!=null)aboutDate=dateInput.getText().toString();
        }else{
            if(titleInput!=null)materialTitle=titleInput.getText().toString();
            if(materialInput!=null)materialText=materialInput.getText().toString();
            if(dateInput!=null)materialDate=dateInput.getText().toString();
        }
    }
    private String navigationGroup(){return L10n.s(R.string.ui_219).equals(page)?L10n.s(R.string.ui_210):L10n.s(R.string.ui_218).equals(page)?L10n.s(R.string.ui_217):page;}
    private void sectionTabs(String first,String second){
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(4),dp(4),dp(4),dp(4));GradientDrawable bg=card(0xFFF0F0F0);bg.setCornerRadius(dp(25));tabs.setBackground(bg);content.addView(tabs);
        for(String label:new String[]{first,second}){
            TextView tab=text(label,14,label.equals(page)?INK:MUTED);tab.setGravity(Gravity.CENTER);tab.setMinHeight(dp(44));tab.setSelected(label.equals(page));
            if(label.equals(page)){GradientDrawable selected=card(0xFFFFFFFF);selected.setCornerRadius(dp(22));tab.setBackground(selected);}
            tab.setContentDescription(L10n.s(R.string.ui_225)+label);tab.setOnClickListener(v->{if(!busy)show(label);else toast(L10n.s(R.string.ui_226));});tabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
        }
    }
    private ImageView icon(String kind,String label,int color){
        ImageView icon=new ImageView(this);icon.setImageDrawable(new AgentLineIcon(kind,color));icon.setPadding(dp(9),dp(9),dp(9),dp(9));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setContentDescription(label);icon.setFocusable(true);
        androidx.appcompat.widget.TooltipCompat.setTooltipText(icon,label);return icon;
    }

    private void show(String destination) {
        boolean typing=question!=null&&question.hasFocus();
        if(!L10n.s(R.string.ui_209).equals(destination)){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);keyboard.hideSoftInputFromWindow(root.getWindowToken(),0);}
        rememberDrafts();page=destination;question=null;titleInput=null;materialInput=null;dateInput=null;send=null;content.removeAllViews();navigation.removeAllViews();composer.removeAllViews();pendingTurn=null;thinkingTurn=null;
        heading.setText(L10n.s(R.string.ui_209).equals(page)?L10n.s(R.string.ui_221):page);subtitle.setText(L10n.s(R.string.ui_209).equals(page)?L10n.s(R.string.ui_222):L10n.s(R.string.ui_227)+page);
        content.setPadding(dp(16),headerHeight+dp(4),dp(16),dp(20));composer.setVisibility(L10n.s(R.string.ui_209).equals(page)?View.VISIBLE:View.GONE);
        String[] names={L10n.s(R.string.ui_209),L10n.s(R.string.ui_210),L10n.s(R.string.ui_217),L10n.s(R.string.ui_216)},labels={L10n.s(R.string.ui_209),L10n.s(R.string.ui_228),L10n.s(R.string.ui_229),L10n.s(R.string.ui_230)};
        for(int i=0;i<names.length;i++) {
            String name=names[i];boolean selected=name.equals(navigationGroup());ImageView tab=icon(name,labels[i],selected?0xFF444444:0xFF888888);tab.setSelected(selected);tab.setPadding(dp(7),dp(7),dp(7),dp(7));
            if(selected){GradientDrawable highlight=card(0xFFD5D5D5);highlight.setCornerRadius(dp(20));tab.setBackground(highlight);}
            tab.setOnClickListener(v->{if(!busy)show(name);else toast(L10n.s(R.string.ui_226));});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(32),1);lp.setMargins(dp(4),0,dp(4),0);navigation.addView(tab,lp);
        }
        if(L10n.s(R.string.ui_210).equals(navigationGroup()))sectionTabs(L10n.s(R.string.ui_210),L10n.s(R.string.ui_219));
        if(L10n.s(R.string.ui_217).equals(navigationGroup()))sectionTabs(L10n.s(R.string.ui_217),L10n.s(R.string.ui_218));
        if(page.equals(L10n.s(R.string.ui_210)))materials();
        else if(page.equals(L10n.s(R.string.ui_217)))profile();
        else if(page.equals(L10n.s(R.string.ui_218)))reviews();
        else if(page.equals(L10n.s(R.string.ui_219)))management();
        else if(page.equals(L10n.s(R.string.ui_216)))modelSettings();
        else chat();
        if(L10n.s(R.string.ui_209).equals(page)&&typing&&question!=null)question.requestFocus();else root.requestFocus();
        if(!L10n.s(R.string.ui_209).equals(page))scroll.post(()->scroll.scrollTo(0,0));
    }
    private void chat() {
        String current=session;turns=new LinearLayout(this);turns.setOrientation(LinearLayout.VERTICAL);content.addView(turns);
        ImageView add=icon(L10n.s(R.string.ui_231),L10n.s(R.string.ui_232),0xFF666666);composer.addView(add,new LinearLayout.LayoutParams(dp(36),dp(36)));add.setOnClickListener(v->{
            if(busy){toast(L10n.s(R.string.ui_226));return;}
            new AlertDialog.Builder(this).setTitle(L10n.s(R.string.ui_233)).setItems(new String[]{L10n.s(R.string.ui_234),L10n.s(R.string.ui_235)},(d,which)->{show(which==0?L10n.s(R.string.ui_210):L10n.s(R.string.ui_217));materialInput.requestFocus();scroll.post(()->scroll.smoothScrollTo(0,materialInput.getTop()));}).show();
        });
        question=new EditText(this);question.setHint(L10n.s(R.string.ui_236));question.setTextSize(13);question.setIncludeFontPadding(false);question.setTextColor(INK);question.setHintTextColor(0xFF888888);question.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        question.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);question.setMinLines(1);question.setMaxLines(3);question.setMinHeight(dp(36));question.setMinimumHeight(dp(36));question.setPadding(dp(10),dp(6),dp(8),dp(6));question.setGravity(Gravity.CENTER_VERTICAL);question.setContentDescription(L10n.s(R.string.ui_237));question.setText(draft);
        composer.addView(question,new LinearLayout.LayoutParams(0,-2,1));
        send=icon(L10n.s(R.string.ui_238),L10n.s(R.string.ui_239),0xFFFFFFFF);GradientDrawable sendCircle=card(0xFF5C5C5C);sendCircle.setCornerRadius(dp(20));send.setBackground(sendCircle);LinearLayout.LayoutParams sendParams=new LinearLayout.LayoutParams(dp(36),dp(36));sendParams.setMargins(dp(4),0,dp(2),0);composer.addView(send,sendParams);send.setOnClickListener(v->sendMessage());
        question.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){updateSend();}public void afterTextChanged(android.text.Editable s){}});updateSend();
        run(L10n.s(R.string.ui_240),client->new JSONObject().put("inventory",client.inventory()).put("turns",client.transcriptPage(current,turnOffset)),data->{
            JSONObject inventory=data.getJSONObject("inventory");subtitle.setText(inventory.optInt("total")+L10n.s(R.string.ui_241));
            JSONObject history=data.getJSONObject("turns");JSONArray messages=history.getJSONArray("items");
            if(history.optBoolean("sourcesChanged"))note(L10n.s(R.string.ui_242));
            if(history.optBoolean("hasOlder"))smallAction(turns,L10n.s(R.string.ui_243),()->{turnOffset+=40;show(L10n.s(R.string.ui_209));});
            if(turnOffset>0)smallAction(turns,L10n.s(R.string.ui_244),()->{turnOffset=Math.max(0,turnOffset-40);show(L10n.s(R.string.ui_209));});
            if(messages.length()==0)emptyChat();
            for(int i=0;i<messages.length();i++){
                JSONObject message=messages.getJSONObject(i);boolean user="user".equals(message.optString("role"));
                LinearLayout row=chatBubble(turns,user,message.optString("content"));
                if(user){long id=message.getLong("id");row.setOnLongClickListener(v->{if(!busy)new AlertDialog.Builder(this).setItems(new String[]{L10n.s(R.string.ui_245),L10n.s(R.string.ui_246)},(d,w)->{
                    if(w==0){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText(L10n.s(R.string.ui_247),message.optString("content")));}
                    else run(L10n.s(R.string.ui_248),client->client.proposeMemory(current,id),this::reviewProposal);
                }).show();return true;});}
                JSONArray sources=message.optJSONArray("sources");if(!user&&sources!=null&&sources.length()>0){
                    TextView citation=text("↗ "+sources.length()+L10n.s(R.string.ui_249),11,MUTED);citation.setPadding(dp(46),0,0,dp(12));citation.setMinHeight(dp(40));citation.setGravity(Gravity.CENTER_VERTICAL);turns.addView(citation);citation.setOnClickListener(v->showSources(sources));
                }
            }
            if(turnOffset==0)scrollToLatest();
        });
        if(busy&&!pendingMessage.isEmpty()&&jobs.state.getValue()!=null&&L10n.s(R.string.ui_250).equals(jobs.state.getValue().label)){pendingTurn=chatBubble(turns,true,pendingMessage);thinkingTurn=chatBubble(turns,false,L10n.s(R.string.ui_251));scrollToLatest();}
    }
    private void sendMessage(){
        if(busy||question==null)return;String message=question.getText().toString().trim();if(message.isEmpty())return;
        if(message.length()>2000){toast(L10n.s(R.string.ui_252));return;}String currentSession=session;
        View empty=turns.findViewWithTag("empty-chat");if(empty!=null)turns.removeView(empty);
        pendingMessage=message;draft="";question.setText("");pendingTurn=chatBubble(turns,true,message);thinkingTurn=chatBubble(turns,false,L10n.s(R.string.ui_251));scrollToLatest();
        run(L10n.s(R.string.ui_250),client->client.chat(currentSession,message),result->{session=result.getString("sessionId");turnOffset=0;saveSession();pendingMessage="";show(L10n.s(R.string.ui_209));});
    }
    private void removePending(){if(question!=null&&question.getText().toString().isEmpty()&&!pendingMessage.isEmpty())question.setText(pendingMessage);pendingMessage="";if(turns!=null){if(pendingTurn!=null)turns.removeView(pendingTurn);if(thinkingTurn!=null)turns.removeView(thinkingTurn);}pendingTurn=null;thinkingTurn=null;}
    private void updateSend(){if(send!=null&&question!=null){boolean ready=!busy&&!question.getText().toString().trim().isEmpty();send.setEnabled(ready);send.setAlpha(ready?1f:.4f);}}
    private void setStatus(String message){status.setText(message);status.setVisibility(message==null||message.isEmpty()?View.GONE:View.VISIBLE);}
    private void scrollToLatest(){scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    private TextView avatar(boolean user){TextView avatar=text(user?L10n.s(R.string.ui_253):L10n.s(R.string.ui_254),14,user?0xFF666666:0xFF707070);avatar.setGravity(Gravity.CENTER);GradientDrawable bg=card(user?0xFFDEDEDE:0xFFE8E8E8);bg.setCornerRadius(dp(12));avatar.setBackground(bg);avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return avatar;}
    private LinearLayout chatBubble(LinearLayout parent,boolean user,String body){
        LinearLayout row=new LinearLayout(this);row.setGravity((user?Gravity.END:Gravity.START)|Gravity.TOP);row.setPadding(0,dp(5),0,dp(7));parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        TextView icon=avatar(user);LinearLayout.LayoutParams av=new LinearLayout.LayoutParams(dp(32),dp(32));av.setMargins(user?dp(8):0,dp(2),user?0:dp(8),0);
        TextView text=text(body,15,INK);text.setTextIsSelectable(true);text.setMaxWidth(Math.max(dp(180),getResources().getDisplayMetrics().widthPixels-dp(112)));text.setPadding(dp(15),dp(12),dp(15),dp(12));
        GradientDrawable bubble=card(user?0xFFDCDCDC:0xFFF0F0F0);float r=dp(21),tail=dp(5);bubble.setCornerRadii(user?new float[]{r,r,tail,tail,r,r,r,r}:new float[]{tail,tail,r,r,r,r,r,r});text.setBackground(bubble);
        if(user)text.setOnLongClickListener(v->row.performLongClick());
        if(!user)row.addView(icon,av);row.addView(text,new LinearLayout.LayoutParams(-2,-2));if(user)row.addView(icon,av);return row;
    }
    private void emptyChat(){
        LinearLayout welcome=new LinearLayout(this);welcome.setOrientation(LinearLayout.VERTICAL);welcome.setGravity(Gravity.CENTER);welcome.setPadding(dp(20),dp(60),dp(20),dp(28));welcome.setTag("empty-chat");turns.addView(welcome,new LinearLayout.LayoutParams(-1,-2));
        TextView star=text("✧",52,0xFFB8B8B8);welcome.addView(star);TextView title=text(L10n.s(R.string.ui_255),20,INK);title.setPadding(0,dp(18),0,dp(8));welcome.addView(title);
        TextView hint=text(L10n.s(R.string.ui_256),13,MUTED);hint.setGravity(Gravity.CENTER);welcome.addView(hint);
        smallAction(welcome,L10n.s(R.string.ui_257),()->{question.setText(L10n.s(R.string.ui_257));question.requestFocus();});
        smallAction(welcome,L10n.s(R.string.ui_258),()->{question.setText(L10n.s(R.string.ui_258));question.requestFocus();});
    }
    private void smallAction(LinearLayout parent,String label,Runnable click){TextView item=text(label,12,MUTED);item.setGravity(Gravity.CENTER);item.setPadding(dp(12),dp(12),dp(12),dp(12));item.setMinHeight(dp(44));item.setOnClickListener(v->{if(!busy)click.run();});parent.addView(item,new LinearLayout.LayoutParams(-1,-2));}
    private void showSources(JSONArray sources){
        String[] labels=new String[sources.length()];for(int i=0;i<sources.length();i++){JSONObject source=sources.optJSONObject(i);labels[i]=(i+1)+" · "+source.optString("title")+(source.optBoolean("testData")?L10n.s(R.string.ui_259):"");}
        new AlertDialog.Builder(this).setTitle(L10n.s(R.string.ui_260)).setItems(labels,(d,i)->viewMemory(sources.optJSONObject(i).optString("id"))).setNegativeButton(L10n.s(R.string.ui_261),null).show();
    }
    private void chatMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add(L10n.s(R.string.ui_262));menu.getMenu().add(L10n.s(R.string.ui_263));menu.getMenu().add(L10n.s(R.string.ui_264));
        menu.setOnMenuItemClickListener(item->{if(busy){toast(L10n.s(R.string.ui_226));return true;}String name=item.getTitle().toString();
            if(L10n.s(R.string.ui_262).equals(name)){clearChat();draft="";if(question!=null)question.setText("");show(L10n.s(R.string.ui_209));}
            else if(L10n.s(R.string.ui_263).equals(name))chooseSession();else run(L10n.s(R.string.ui_265),LocalMemoryAgent::inventory,stats->new AlertDialog.Builder(this).setTitle(L10n.s(R.string.ui_264)).setMessage(coverage(stats)).setPositiveButton(L10n.s(R.string.ui_261),null).show());return true;});menu.show();
    }
    private void materials() {
        note(L10n.s(R.string.ui_266));
        materialEditor(false);
        button(L10n.s(R.string.ui_267),this::chooseRecords);
        note(L10n.s(R.string.ui_268));
        button(L10n.s(R.string.ui_269),this::loadMemories);
        loadMemories();
    }
    private void materialEditor(boolean about){
        titleInput=input(about?L10n.s(R.string.ui_270):L10n.s(R.string.ui_271),false);titleInput.setText(about?aboutTitle:materialTitle);
        dateInput=input(L10n.s(R.string.ui_272),false);dateInput.setText(about?aboutDate:materialDate);
        materialInput=input(about?L10n.s(R.string.ui_273):L10n.s(R.string.ui_274),true);materialInput.setMinLines(5);materialInput.setText(about?aboutText:materialText);forwardMouseWheel(materialInput,scroll);
        button(L10n.s(R.string.ui_275),()->{importPage=page;picker.launch(new String[]{"text/*","application/octet-stream"});});
        button(about?L10n.s(R.string.ui_276):L10n.s(R.string.ui_277),()->{
            String title=titleInput.getText().toString().trim(),body=materialInput.getText().toString().trim();
            if(title.isEmpty()||title.length()>160||body.isEmpty()||body.length()>24000){toast(L10n.s(R.string.ui_278));return;}
            // Stable id for this attempt: a failed model call leaves a single retryable source.
            String id=UUID.randomUUID().toString(),date=dateInput.getText().toString().trim();
            run(L10n.s(R.string.ui_279),client->{
                JSONObject memory=client.save(new JSONObject().put("id",id).put("title",title).put("kind","MATERIAL").put("content",body).put("occurredOn",date.isEmpty()?JSONObject.NULL:date));
                try {return client.analyze(memory.getString("id"));}
                catch(Exception e){throw new IOException(L10n.s(R.string.ui_280)+e.getMessage());}
            },result->{finishMaterialSave(about);toast(about?L10n.s(R.string.ui_281):L10n.s(R.string.ui_282));});
        });
        button(L10n.s(R.string.ui_283),()->{
            String title=titleInput.getText().toString().trim(),body=materialInput.getText().toString(),date=dateInput.getText().toString().trim();
            run(L10n.s(R.string.ui_284),client->client.save(new JSONObject().put("title",title).put("kind","MATERIAL").put("content",body).put("occurredOn",date.isEmpty()?JSONObject.NULL:date)),result->{
                finishMaterialSave(about);toast(L10n.s(R.string.ui_285));
            });
        });
    }
    private void finishMaterialSave(boolean about){
        titleInput.setText("");materialInput.setText("");dateInput.setText("");
        if(about){aboutTitle="";aboutText="";aboutDate="";}else{materialTitle="";materialText="";materialDate="";}
        clearChat();show(about?L10n.s(R.string.ui_217):L10n.s(R.string.ui_210));
    }
    private void loadMemories() {
        run(L10n.s(R.string.ui_286),client->new JSONObject().put("items",client.memoryPage(memoryOffset)).put("inventory",client.inventory()),result->{
            JSONObject pageData=result.getJSONObject("items");JSONArray items=pageData.getJSONArray("items");
            TextView coverage=content.findViewWithTag("coverage");
            if(coverage==null){coverage=text("",12,MUTED);coverage.setTag("coverage");content.addView(coverage);}
            coverage.setText(coverage(result.getJSONObject("inventory")));
            // Rebuild just the list so refresh never duplicates cards.
            View previous=content.findViewWithTag("memory-list");if(previous!=null)content.removeView(previous);
            LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setTag("memory-list");content.addView(list);
            for(int i=0;i<items.length();i++) {
                JSONObject m=items.getJSONObject(i);String id=m.getString("id");
                boolean fromArchive="ARCHIVE".equals(m.optString("origin"));
                LinearLayout box=box(list);box.addView(text(m.getString("title")+(m.optBoolean("testData")?L10n.s(R.string.ui_259):"")+" · "+stateLabel(m.optString("memoryState"))+(m.optBoolean("pinned")?L10n.s(R.string.ui_287):""),17,INK));
                TextView summary=text(m.optString("summary","").isEmpty()?(fromArchive?L10n.s(R.string.ui_288):L10n.s(R.string.ui_289)):m.optString("summary"),13,MUTED);
                summary.setMaxLines(4);box.addView(summary);
                action(box,L10n.s(R.string.ui_290),()->viewMemory(id));
                if(!"READY".equals(m.optString("status")))action(box,L10n.s(R.string.ui_291),()->run(L10n.s(R.string.ui_292),client->client.analyze(id),v->{clearChat();show(L10n.s(R.string.ui_210));}));
                action(box,m.optBoolean("pinned")?L10n.s(R.string.ui_293):L10n.s(R.string.ui_294),()->run(L10n.s(R.string.ui_295),client->{client.pin(id,!m.optBoolean("pinned"));return true;},v->show(L10n.s(R.string.ui_210))));
                action(box,"DORMANT".equals(m.optString("memoryState"))?L10n.s(R.string.ui_296):L10n.s(R.string.ui_297),()->run(L10n.s(R.string.ui_298),client->{client.sleep(id,!"DORMANT".equals(m.optString("memoryState")));return true;},v->show(L10n.s(R.string.ui_210))));
                if(fromArchive)action(box,L10n.s(R.string.ui_299),()->openArchive(m));
                else action(box,L10n.s(R.string.ui_300),()->new AlertDialog.Builder(this).setTitle(L10n.s(R.string.ui_301))
                    .setMessage(L10n.s(R.string.ui_302))
                    .setNegativeButton(L10n.s(R.string.ui_303),null).setPositiveButton(L10n.s(R.string.ui_025),(d,w)->run(L10n.s(R.string.ui_304),client->{client.delete(id);return true;},v->{clearChat();show(L10n.s(R.string.ui_210));})).show());
            }
            if(memoryOffset>0)action(list,L10n.s(R.string.ui_305),()->{memoryOffset=Math.max(0,memoryOffset-20);loadMemories();});
            if(pageData.optBoolean("hasMore"))action(list,L10n.s(R.string.ui_306)+pageData.optInt("total")+L10n.s(R.string.ui_307),()->{memoryOffset+=20;loadMemories();});
        });
    }
    private void profile() {
        note(L10n.s(R.string.ui_308));
        materialEditor(true);
        note(L10n.s(R.string.ui_309));
        note(L10n.s(R.string.ui_310));
        button(L10n.s(R.string.ui_311),()->run(L10n.s(R.string.ui_312),LocalMemoryAgent::groupProfile,v->show(L10n.s(R.string.ui_217))));
        button(L10n.s(R.string.ui_313),()->show(L10n.s(R.string.ui_217)));
        run(L10n.s(R.string.ui_314),client->new JSONObject().put("traits",client.profile()).put("groups",client.profileGroups()),data->{
            JSONArray groups=data.getJSONArray("groups"),items=data.getJSONArray("traits");
            for(int i=0;i<groups.length();i++){
                JSONObject group=groups.getJSONObject(i);LinearLayout card=box(content);
                card.addView(text(group.optString("topic")+(group.optBoolean("testData")?L10n.s(R.string.ui_259):""),17,INK));
                card.addView(text(group.optString("description")+"\n"+relationLabel(group.optString("relation")),14,MUTED));
                JSONArray members=group.optJSONArray("members");if(members!=null)for(int j=0;j<members.length();j++){
                    JSONObject member=members.getJSONObject(j);String traitId=member.getString("id"),groupId=group.getString("id");
                    card.addView(text((member.isNull("date")?L10n.s(R.string.ui_315):member.optString("date"))+" · "+member.optString("description"),13,MUTED));
                    action(card,traitId.equals(group.optString("preferredTraitId"))?L10n.s(R.string.ui_316):L10n.s(R.string.ui_317),()->run(L10n.s(R.string.ui_318),client->{client.preferTrait(groupId,traitId);return true;},v->show(L10n.s(R.string.ui_217))));
                }
            }
            if(items.length()==0)note(L10n.s(R.string.ui_319));
            for(int i=0;i<items.length();i++) {
                JSONObject t=items.getJSONObject(i);String id=t.getString("id"),source=t.getString("memoryId"),state=t.getString("status");
                if("REJECTED".equals(state))continue;
                LinearLayout box=box(content);box.addView(text(t.getString("description"),16,INK));
                box.addView(text(("USER_CORRECTION".equals(t.getString("basis"))?L10n.s(R.string.ui_320):"EXPLICIT".equals(t.getString("basis"))?L10n.s(R.string.ui_321):L10n.s(R.string.ui_322))+(t.optBoolean("testData")?L10n.s(R.string.ui_259):"")+" · "+("CONFIRMED".equals(state)?L10n.s(R.string.ui_323):L10n.s(R.string.ui_324)),12,MUTED));
                box.addView(text("“"+t.getString("evidence")+"”",14,MUTED));
                action(box,L10n.s(R.string.ui_325),()->viewMemory(source));
                action(box,L10n.s(R.string.ui_326),()->{
                    EditText edit=new EditText(this);edit.setText(t.optString("description"));
                    new AlertDialog.Builder(this).setTitle(L10n.s(R.string.ui_327)).setView(edit).setNegativeButton(L10n.s(R.string.ui_131),null)
                        .setPositiveButton(L10n.s(R.string.ui_328),(d,w)->{String value=edit.getText().toString();run(L10n.s(R.string.ui_329),client->{client.correctTrait(id,value);return true;},v->show(L10n.s(R.string.ui_217)));}).show();
                });
                if(!"CONFIRMED".equals(state))action(box,L10n.s(R.string.ui_330),()->setTrait(id,"CONFIRMED"));
                action(box,"CONFIRMED".equals(state)?L10n.s(R.string.ui_331):L10n.s(R.string.ui_332),()->setTrait(id,"REJECTED"));
            }
        });
    }
    private void setTrait(String id,String state) {
        run(L10n.s(R.string.ui_333),client->{client.setTrait(id,state);return true;},v->{clearChat();show(L10n.s(R.string.ui_217));});
    }
    private void modelSettings() {
        note(L10n.s(R.string.ui_334));
        EditText model=input(L10n.s(R.string.ui_335),false);model.setText(config.model());model.setSingleLine(true);
        EditText apiKey=input(L10n.s(R.string.ui_336),false);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);apiKey.setSingleLine(true);
        apiKey.setSaveEnabled(false);
        if(android.os.Build.VERSION.SDK_INT>=26){
            apiKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            apiKey.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        try{apiKey.setText(config.apiKey());}catch(Exception e){note(L10n.s(R.string.ui_337));}
        EditText address=input(L10n.s(R.string.ui_338),false);
        address.setText(config.baseUrl());address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setVisibility(ModelSettings.DEFAULT_BASE.equals(config.baseUrl())?View.GONE:View.VISIBLE);
        button(L10n.s(R.string.ui_339),()->address.setVisibility(address.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        button(L10n.s(R.string.ui_340),()->{
            try{
                config.save(address.getText().toString(),model.getText().toString(),apiKey.getText().toString());clearChat();
                run(L10n.s(R.string.ui_295),client->{return true;},v->setStatus(L10n.s(R.string.ui_341)));
            }catch(Exception e){setStatus(e.getMessage()==null?L10n.s(R.string.ui_342):e.getMessage());}
        });
        button(L10n.s(R.string.ui_343),()->{
            try{
                config.save(address.getText().toString(),model.getText().toString(),apiKey.getText().toString());clearChat();
                run(L10n.s(R.string.ui_344),client->{client.testConnection();return true;},v->setStatus(L10n.s(R.string.ui_345)));
            }catch(Exception e){setStatus(e.getMessage()==null?L10n.s(R.string.ui_346):e.getMessage());}
        });
        note(L10n.s(R.string.ui_347));
        note(L10n.s(R.string.ui_348)+ModelSettings.DEFAULT_BASE+L10n.s(R.string.ui_349));
        button(L10n.s(R.string.ui_350),()->{
            try{config.clearKey();apiKey.setText("");clearChat();
                run(L10n.s(R.string.ui_333),client->{return true;},v->setStatus(getString(R.string.agent_api_key_removed)));
            }catch(Exception e){setStatus(L10n.s(R.string.ui_351));}
        });
    }
    private void chooseRecords() {
        // Read on Repo's IO executor, using a one-shot query rather than retaining a UI observer.
        new Repo(this).getActiveOnce(records->runOnUiThread(()->{
            if(isFinishing()||isDestroyed())return;
            List<Record> real=new ArrayList<>(records);
            if(real.isEmpty()){toast(L10n.s(R.string.ui_352));return;}
            String[] labels=new String[real.size()];boolean[] selected=new boolean[real.size()];
            for(int i=0;i<real.size();i++)labels[i]=real.get(i).date+" · "+real.get(i).title+(real.get(i).id.startsWith(Record.SAMPLE_PREFIX)?L10n.s(R.string.ui_259):"");
            new AlertDialog.Builder(this).setTitle(L10n.s(R.string.ui_353))
                .setMultiChoiceItems(labels,selected,(d,which,value)->selected[which]=value)
                .setNegativeButton(L10n.s(R.string.ui_131),null).setPositiveButton(L10n.s(R.string.ui_354),(d,w)->{
                    List<Record> chosen=new ArrayList<>();for(int i=0;i<real.size();i++)if(selected[i])chosen.add(real.get(i));
                    if(chosen.isEmpty())return;if(chosen.size()>10){toast(L10n.s(R.string.ui_355));return;}
                    run(L10n.s(R.string.ui_356),client->{
                        int completed=0;
                        for(Record r:chosen) {
                            try{client.analyze(r.id);}
                            catch(Exception e){throw new IOException(L10n.s(R.string.ui_357)+completed+L10n.s(R.string.ui_358)+r.title+L10n.s(R.string.ui_359)+e.getMessage());}
                            completed++;
                        }
                        return completed;
                    },count->{clearChat();show(L10n.s(R.string.ui_210));toast(L10n.s(R.string.ui_360)+count+L10n.s(R.string.ui_361));});
                }).show();
        }));
    }
    private String coverage(JSONObject stats) throws JSONException {
        JSONObject counts=stats.getJSONObject("byKind");
        String summary=L10n.s(R.string.ui_362)+stats.getInt("archiveCount")+L10n.s(R.string.ui_363)+stats.getInt("materialCount")+L10n.s(R.string.ui_364)+
            L10n.s(R.string.ui_365)+counts.optInt("MOVIE")+L10n.s(R.string.ui_366)+counts.optInt("BOOK")+L10n.s(R.string.ui_367)+counts.optInt("MUSIC")+
            L10n.s(R.string.ui_368)+counts.optInt("VOICE")+L10n.s(R.string.ui_369)+counts.optInt("FOOTPRINT")+L10n.s(R.string.ui_370)+counts.optInt("TEXT");
        if(stats.optInt("exampleCount")>0)summary+=L10n.s(R.string.ui_371)+stats.optInt("exampleCount")+L10n.s(R.string.ui_372);
        return summary;
    }
    private void openArchive(JSONObject memory) {
        String id=memory.optString("id");int type=memory.optInt("recordType",-1);
        Intent intent=new Intent(this,type==Record.LETTER?LetterActivity.class:DetailActivity.class);
        intent.putExtra("id",id);startActivity(intent);
    }
    private void readFile(Uri uri) {
        if(busy){pendingImport=uri;return;}
        String destination=importPage;android.content.Context app=getApplicationContext();
        run(L10n.s(R.string.ui_373),client->readTextFile(app,uri,destination),this::applyImportedText);
    }
    private void drainImport(){if(!busy&&pendingImport!=null){Uri uri=pendingImport;pendingImport=null;readFile(uri);}}
    static JSONObject readTextFile(android.content.Context context,Uri uri,String destination) throws Exception {
        String filename=L10n.s(R.string.ui_374);
        try(android.database.Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null&&c.moveToFirst())filename=c.getString(0);
        }
        if(filename==null||!filename.toLowerCase(Locale.ROOT).matches(".*\\.(txt|md)$"))throw new IOException(L10n.s(R.string.ui_375));
        byte[] bytes;
        try(InputStream in=context.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null)throw new IOException(L10n.s(R.string.ui_376));byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1){if(out.size()+n>200000)throw new IOException(L10n.s(R.string.ui_377));out.write(buf,0,n);}bytes=out.toByteArray();
        }
        String body;
        try{body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().replaceFirst("^\\uFEFF","");}
        catch(CharacterCodingException e){throw new IOException(L10n.s(R.string.ui_378));}
        if(body.length()>24000||body.indexOf('\0')>=0)throw new IOException(L10n.s(R.string.ui_379));
        if(body.trim().isEmpty())throw new IOException(L10n.s(R.string.ui_380));
        return new JSONObject().put("fileImport",true).put("page",destination).put("title",filename).put("content",body);
    }
    private void applyImportedText(JSONObject data){
        rememberDrafts();String destination=data.optString("page",L10n.s(R.string.ui_210));String name=data.optString("title"),body=data.optString("content");
        if(L10n.s(R.string.ui_217).equals(destination)){aboutTitle=name;aboutText=body;}else{materialTitle=name;materialText=body;destination=L10n.s(R.string.ui_210);}
        // Clear old view references before show() remembers drafts, so the imported text wins.
        titleInput=null;materialInput=null;dateInput=null;show(destination);toast(L10n.s(R.string.ui_381));
    }
    private void viewMemory(String id) {
        run(L10n.s(R.string.ui_382),client->client.memory(id),memory->{
            ScrollView scroll=new ScrollView(this);TextView body=text(memory.optString("summary")+L10n.s(R.string.ui_383)+memory.getString("content"),15,INK);
            body.setPadding(dp(22),dp(16),dp(22),dp(16));body.setTextIsSelectable(true);forwardMouseWheel(body,scroll);scroll.addView(body);
            new AlertDialog.Builder(this).setTitle(memory.getString("title")).setView(scroll).setPositiveButton(L10n.s(R.string.ui_261),null).show();
        });
    }
    /** Full-height material text scrolls with its page, including while selectable/editable. */
    private void forwardMouseWheel(View text,ScrollView container){
        text.setOnGenericMotionListener((v,event)->{
            if(event.getActionMasked()!=MotionEvent.ACTION_SCROLL
                    || !event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)
                    || (event.getMetaState()&KeyEvent.META_SHIFT_ON)!=0 || event.getAxisValue(MotionEvent.AXIS_VSCROLL)==0)return false;
            container.onGenericMotionEvent(event);
            // Consume at both ends too: do not fall back to TextView's internal scrolling.
            return true;
        });
    }
    @SuppressWarnings("unchecked")
    private <T> void run(String label,Work<T> task,Done<T> done) {
        if(busy)return;
        busy=true;setStatus(label);updateSend();String id=UUID.randomUUID().toString();
        callbacks.put(id,value->done.accept((T)value));
        jobs.submit(id,getApplicationContext(),config,label,page,task::run);
    }
    private void saveSession(){getSharedPreferences("agent_ui",MODE_PRIVATE).edit().putString("session",session).apply();}
    private void clearChat(){turnOffset=0;session=UUID.randomUUID().toString();saveSession();}
    private String stateLabel(String state){return "DORMANT".equals(state)?L10n.s(R.string.ui_384):"FADING".equals(state)?L10n.s(R.string.ui_385):L10n.s(R.string.ui_386);}
    private String relationLabel(String relation){return "CONFLICT".equals(relation)?L10n.s(R.string.ui_387):"CHANGED".equals(relation)?L10n.s(R.string.ui_388):L10n.s(R.string.ui_389);}
    private void chooseSession(){run(L10n.s(R.string.ui_390),LocalMemoryAgent::sessions,items->{
        if(items.length()==0){toast(L10n.s(R.string.ui_391));return;}
        String[] labels=new String[items.length()];for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);labels[i]=item.optString("title")+("SOURCES_CHANGED".equals(item.optString("status"))?L10n.s(R.string.ui_392):"");}
        new AlertDialog.Builder(this).setTitle(L10n.s(R.string.ui_263)).setItems(labels,(d,index)->{session=items.optJSONObject(index).optString("id");turnOffset=0;saveSession();show(L10n.s(R.string.ui_209));}).setNegativeButton(L10n.s(R.string.ui_261),null).show();
    });}
    private void reviewProposal(JSONObject proposal) throws Exception {
        EditText edit=new EditText(this);edit.setText(proposal.optString("content"));edit.setMinLines(3);
        String id=proposal.getString("id");new AlertDialog.Builder(this).setTitle(L10n.s(R.string.ui_393)).setView(edit)
            .setMessage(L10n.s(R.string.ui_394))
            .setNegativeButton(L10n.s(R.string.ui_395),(d,w)->run(L10n.s(R.string.ui_333),client->{client.rejectProposal(id);return true;},v->{}))
            .setPositiveButton(L10n.s(R.string.ui_396),(d,w)->{String value=edit.getText().toString();run(L10n.s(R.string.ui_397),client->client.acceptProposal(id,value),v->{toast(L10n.s(R.string.ui_398));show(L10n.s(R.string.ui_210));});}).show();
    }
    private void reviews(){
        note(L10n.s(R.string.ui_399));
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);Calendar cal=Calendar.getInstance();String end=format.format(cal.getTime());cal.set(Calendar.DAY_OF_MONTH,1);String start=format.format(cal.getTime());
        EditText from=input(L10n.s(R.string.ui_400),false);from.setText(start);
        EditText to=input(L10n.s(R.string.ui_401),false);to.setText(end);
        EditText title=input(L10n.s(R.string.ui_402),false);title.setText(L10n.s(R.string.ui_403));
        button(L10n.s(R.string.ui_404),()->{String f=from.getText().toString().trim(),t=to.getText().toString().trim(),name=title.getText().toString().trim();run(L10n.s(R.string.ui_405),client->client.summarizePeriod(f,t,name),v->show(L10n.s(R.string.ui_218)));});
        run(L10n.s(R.string.ui_406),LocalMemoryAgent::periods,items->{for(int i=0;i<items.length();i++){
            JSONObject period=items.getJSONObject(i);LinearLayout card=box(content);card.addView(text(period.optString("title"),18,INK));
            card.addView(text(period.optString("from")+L10n.s(R.string.ui_407)+period.optString("to")+("STALE".equals(period.optString("status"))?L10n.s(R.string.ui_408):""),12,MUTED));
            TextView summary=text(period.optString("summary"),15,INK);summary.setTextIsSelectable(true);card.addView(summary);
            if("STALE".equals(period.optString("status")))action(card,L10n.s(R.string.ui_409),()->run(L10n.s(R.string.ui_410),client->client.summarizePeriod(period.optString("from"),period.optString("to"),period.optString("title")),v->show(L10n.s(R.string.ui_218))));
            JSONObject sources=period.optJSONObject("sourceRevisions");if(sources!=null){Iterator<String> keys=sources.keys();while(keys.hasNext()){String id=keys.next();action(card,L10n.s(R.string.ui_411)+(period.optJSONObject("sourceTitles")==null?L10n.s(R.string.ui_412):period.optJSONObject("sourceTitles").optString(id,L10n.s(R.string.ui_412))),()->viewMemory(id));}}
        }});
    }
    private String taskLabel(JSONObject task){
        switch(task.optString("type")){case "ANALYZE":return L10n.s(R.string.ui_413);case "PERIOD":return L10n.s(R.string.ui_414);case "PROFILE":return L10n.s(R.string.ui_415);case "CHAT":return L10n.s(R.string.ui_416);default:return L10n.s(R.string.ui_417);}
    }
    private void management(){
        note(L10n.s(R.string.ui_418));
        button(L10n.s(R.string.ui_419),()->backupWriter.launch(L10n.s(R.string.ui_420)+new java.text.SimpleDateFormat("yyyyMMdd",Locale.ROOT).format(new Date())+".zip"));
        button(L10n.s(R.string.ui_421),()->backupReader.launch(new String[]{"application/zip","application/octet-stream"}));
        note(L10n.s(R.string.ui_422));
        run(L10n.s(R.string.ui_423),client->new JSONObject().put("tasks",client.pendingTasks()).put("proposals",client.proposals()),data->{
            JSONArray tasks=data.getJSONArray("tasks");if(tasks.length()>0)note(L10n.s(R.string.ui_424));
            for(int i=0;i<tasks.length();i++){JSONObject task=tasks.getJSONObject(i);String id=task.getString("id");LinearLayout card=box(content);
                card.addView(text(taskLabel(task)+" · "+task.optInt("completed")+" / "+task.optInt("total"),14,INK));card.addView(text(task.optString("error",L10n.s(R.string.ui_425)),12,MUTED));
                action(card,L10n.s(R.string.ui_426),()->run(L10n.s(R.string.ui_427),client->client.resumeTask(id),v->{toast(L10n.s(R.string.ui_428));show(L10n.s(R.string.ui_219));}));
                action(card,L10n.s(R.string.ui_429),()->run(L10n.s(R.string.ui_333),client->{client.dismissTask(id);return true;},v->show(L10n.s(R.string.ui_219))));
            }
            JSONArray proposals=data.getJSONArray("proposals");for(int i=0;i<proposals.length();i++){JSONObject proposal=proposals.getJSONObject(i);if(!"PENDING".equals(proposal.optString("status")))continue;LinearLayout card=box(content);card.addView(text(proposal.optString("content"),14,INK));action(card,L10n.s(R.string.ui_430),()->{try{reviewProposal(proposal);}catch(Exception e){toast(L10n.s(R.string.ui_431));}});}
        });
    }
    private void reviewBackup(AgentBackup.Prepared prepared) throws Exception {
        JSONObject preview=prepared.preview();new AlertDialog.Builder(this).setTitle(L10n.s(R.string.ui_432))
            .setMessage(preview.optInt("records")+L10n.s(R.string.ui_363)+preview.optInt("memories")+L10n.s(R.string.ui_433)+preview.optInt("media")+L10n.s(R.string.ui_434))
            .setNegativeButton(L10n.s(R.string.ui_131),(d,w)->prepared.close()).setOnCancelListener(d->prepared.close())
            .setPositiveButton(L10n.s(R.string.ui_435),(d,w)->{android.content.Context app=getApplicationContext();run(L10n.s(R.string.ui_436),client->new AgentBackup(app,AgentStore.get(app),AppDb.get(app)).restore(prepared),v->{toast(L10n.s(R.string.ui_437));show(L10n.s(R.string.ui_210));});}).show();
    }
    private EditText input(String hint,boolean multiline) {
        EditText input=new EditText(this);input.setHint(hint);input.setTextSize(15);input.setTextColor(INK);input.setHintTextColor(MUTED);
        input.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));input.setGravity(Gravity.TOP);
        input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setBackground(card(0xFFF4F4F4));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));content.addView(input,lp);return input;
    }
    private LinearLayout box(LinearLayout parent) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(16),dp(16),dp(12));box.setBackground(card(0xFFF4F4F4));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));parent.addView(box,lp);return box;
    }
    private void block(String body){LinearLayout box=box(content);TextView text=text(body,15,INK);text.setTextIsSelectable(true);box.addView(text);}
    private void note(String body){TextView text=text(body,13,MUTED);text.setPadding(0,dp(8),0,dp(12));content.addView(text);}
    private void button(String label,Runnable task){action(content,label,task);}
    private void action(LinearLayout parent,String label,Runnable task){
        TextView b=text(label,14,INK);b.setPadding(dp(12),dp(14),dp(12),dp(14));b.setMinHeight(dp(48));b.setBackground(card(0xFFDEDEDE));
        b.setGravity(Gravity.CENTER);b.setOnClickListener(v->{if(!busy)task.run();else toast(L10n.s(R.string.ui_226));});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));parent.addView(b,lp);
    }
    private void compactHeaderText(TextView text){
        text.setIncludeFontPadding(false);text.setLineSpacing(0,1);text.setPadding(0,0,0,0);
        text.setSingleLine(true);text.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }
    private TextView text(String s,int size,int color){TextView t=Cards.text(this,s,size,color,AppFonts.regular());t.setLineSpacing(dp(3),1);return t;}
    private GradientDrawable card(int color){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(10));return bg;}
    private int dp(float value){return Cards.dp(this,value);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){
        super.onResume();
        if(content!=null&&!busy&&!L10n.s(R.string.ui_216).equals(page))show(page);
    }
    @Override protected void onSaveInstanceState(Bundle out){rememberDrafts();out.putInt("turnOffset",turnOffset);out.putString("session",session);out.putString("page",page);out.putString("draft",draft);out.putString("pendingMessage",pendingMessage);
        if(pendingImport!=null)out.putString("pendingImport",pendingImport.toString());
        out.putString("aboutTitle",aboutTitle);out.putString("aboutText",aboutText);out.putString("aboutDate",aboutDate);out.putString("importPage",importPage);
        out.putString("materialDate",materialDate);out.putString("materialTitle",materialTitle);out.putString("materialText",materialText);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){callbacks.clear();super.onDestroy();}
}
