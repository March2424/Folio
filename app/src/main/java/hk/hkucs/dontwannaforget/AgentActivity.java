package hk.hkucs.dontwannaforget;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private static final int INK=0xFF2B2927, MUTED=0xFF7D7972, PAPER=0xFFFAF8F4, CARD_BG=0xFFFFFFFF, FIELD_BG=0xFFF5F2EB, LINE=0xFFE6E0D5, ACCENT=0xFF9E8A4E;
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
        FrostedNavigation header=new FrostedNavigation(this,null);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(4),0,dp(4),0);header.setCornerRadiusDp(23);header.setBackdropFillColor(PAPER);header.setGlassAppearance(0x78FFFFFF,0x22000000,155);header.enableRoundedShadow(8);
        ImageView back=icon("back",L10n.s(R.string.ui_220),INK);back.setOnClickListener(v->finish());HomeNavigation.bind(back);header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        heading=text(L10n.s(R.string.ui_221),16,INK);heading.setTypeface(AppFonts.regular(),Typeface.BOLD);heading.setLetterSpacing(0.03f);heading.setGravity(Gravity.CENTER);compactHeaderText(heading);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        subtitle=text("",10,MUTED);subtitle.setVisibility(View.GONE);
        ImageView menu=icon("more",L10n.s(R.string.ui_223),INK);menu.setOnClickListener(v->chatMenu(menu));header.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);scroll.setBackground(new ChatWallpaper(getResources().getDisplayMetrics().density));FrameLayout readingLayer=new FrameLayout(this);root.addView(readingLayer,new LinearLayout.LayoutParams(-1,0,1));readingLayer.addView(scroll,new FrameLayout.LayoutParams(-1,-1));headerHeight=dp(54);
        FrameLayout.LayoutParams headerParams=new FrameLayout.LayoutParams(-1,headerHeight-dp(8),Gravity.TOP);
        headerParams.setMargins(dp(12),dp(6),dp(12),0);readingLayer.addView(header,headerParams);
        header.setBackdrop(scroll,null);
        scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,oldX,oldY)->header.invalidate());
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(root);
            Insets bars=insets==null?Insets.NONE:insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Base the header on the full page, so opening the keyboard does not resize it.
            int next=Math.max(dp(50),Math.min(dp(64),Math.round((b-t-bars.top-bars.bottom)/12f)));
            if(headerHeight!=next){
                headerHeight=next;header.getLayoutParams().height=next-dp(8);header.requestLayout();
                content.setPadding(dp(16),next+dp(6),dp(16),dp(24));
            }
        });
        dock=new LinearLayout(this);dock.setOrientation(LinearLayout.VERTICAL);dock.setPadding(dp(14),dp(4),dp(14),dp(4));dock.setBackgroundColor(PAPER);root.addView(dock);
        status=text("",11,MUTED);status.setPadding(dp(12),0,dp(12),dp(4));status.setVisibility(View.GONE);dock.addView(status);
        composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(5),dp(4),dp(5),dp(4));composer.setElevation(dp(2));
        GradientDrawable inputShell=card(CARD_BG);inputShell.setCornerRadius(dp(24));inputShell.setStroke(dp(1),LINE);composer.setBackground(inputShell);dock.addView(composer,new LinearLayout.LayoutParams(-1,-2));
        navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);navigation.setPadding(dp(4),dp(3),dp(4),dp(3));
        GradientDrawable navShell=card(0xFFF0ECE3);navShell.setCornerRadius(dp(20));navShell.setStroke(dp(1),0xFFE4DDD0);navigation.setBackground(navShell);navigation.setElevation(0);
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,dp(38));navParams.setMargins(dp(4),dp(6),dp(4),dp(2));dock.addView(navigation,navParams);
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
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(3),dp(3),dp(3),dp(3));
        GradientDrawable bg=card(0xFFEFECE4);bg.setCornerRadius(dp(20));bg.setStroke(dp(1),0xFFE3DDD0);tabs.setBackground(bg);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,dp(38));tlp.bottomMargin=dp(12);content.addView(tabs,tlp);
        for(String label:new String[]{first,second}){
            boolean active=label.equals(page);
            TextView tab=text(label,13,active?INK:MUTED);tab.setGravity(Gravity.CENTER);tab.setIncludeFontPadding(false);tab.setSelected(active);
            if(active){tab.setTypeface(AppFonts.regular(),Typeface.BOLD);GradientDrawable selected=card(CARD_BG);selected.setCornerRadius(dp(17));selected.setStroke(dp(1),0xFFE0D9CC);tab.setBackground(selected);tab.setElevation(dp(1));}
            tab.setContentDescription(L10n.s(R.string.ui_225)+label);tab.setOnClickListener(v->{if(!busy)show(label);else toast(L10n.s(R.string.ui_226));});tabs.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
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
        heading.setText(L10n.s(R.string.ui_209).equals(page)?L10n.s(R.string.ui_221):page);
        content.setPadding(dp(16),headerHeight+dp(6),dp(16),dp(24));composer.setVisibility(L10n.s(R.string.ui_209).equals(page)?View.VISIBLE:View.GONE);
        String[] names={L10n.s(R.string.ui_209),L10n.s(R.string.ui_210),L10n.s(R.string.ui_217),L10n.s(R.string.ui_216)},labels={L10n.s(R.string.ui_209),L10n.s(R.string.ui_228),L10n.s(R.string.ui_229),L10n.s(R.string.ui_230)};
        for(int i=0;i<names.length;i++) {
            String name=names[i];boolean selected=name.equals(navigationGroup());
            LinearLayout tab=new LinearLayout(this);tab.setOrientation(LinearLayout.HORIZONTAL);tab.setGravity(Gravity.CENTER);
            tab.setSelected(selected);tab.setContentDescription(labels[i]);tab.setFocusable(true);
            androidx.appcompat.widget.TooltipCompat.setTooltipText(tab,labels[i]);
            int tint=selected?INK:0xFF8E887E;
            ImageView ic=new ImageView(this);ic.setImageDrawable(new AgentLineIcon(name,tint));ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tab.addView(ic,new LinearLayout.LayoutParams(dp(13),dp(13)));
            TextView lbl=text(name,11f,tint);lbl.setIncludeFontPadding(false);lbl.setSingleLine(true);lbl.setEllipsize(TextUtils.TruncateAt.END);
            if(selected)lbl.setTypeface(AppFonts.regular(),Typeface.BOLD);
            LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-2,-2);llp.leftMargin=dp(3);tab.addView(lbl,llp);
            tab.setPadding(dp(4),0,dp(4),0);
            if(selected){GradientDrawable highlight=card(CARD_BG);highlight.setCornerRadius(dp(16));highlight.setStroke(dp(1),0xFFE0D9CC);tab.setBackground(highlight);tab.setElevation(dp(1));}
            tab.setOnClickListener(v->{if(!busy)show(name);else toast(L10n.s(R.string.ui_226));});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(32),1);lp.setMargins(dp(2),0,dp(2),0);navigation.addView(tab,lp);
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
        ImageView add=icon(L10n.s(R.string.ui_231),L10n.s(R.string.ui_232),0xFF5C5852);
        GradientDrawable addBg=card(FIELD_BG);addBg.setCornerRadius(dp(17));add.setBackground(addBg);add.setPadding(dp(8),dp(8),dp(8),dp(8));
        LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(dp(34),dp(34));addLp.setMargins(dp(2),0,dp(4),0);
        composer.addView(add,addLp);add.setOnClickListener(v->{
            if(busy){toast(L10n.s(R.string.ui_226));return;}
            glassChoiceDialog(L10n.s(R.string.ui_233),new String[]{L10n.s(R.string.ui_234),L10n.s(R.string.ui_235)},which->{
                show(which==0?L10n.s(R.string.ui_210):L10n.s(R.string.ui_217));
                if(materialInput!=null){materialInput.requestFocus();scroll.post(()->scroll.smoothScrollTo(0,materialInput.getTop()));}
            });
        });
        question=new EditText(this);question.setHint(L10n.s(R.string.ui_236));question.setTextSize(13.5f);question.setIncludeFontPadding(false);question.setTextColor(INK);question.setHintTextColor(0xFF9A948A);question.setBackgroundColor(Color.TRANSPARENT);
        question.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);question.setMinLines(1);question.setMaxLines(3);question.setMinHeight(dp(34));question.setMinimumHeight(dp(34));question.setPadding(dp(8),dp(6),dp(8),dp(6));question.setGravity(Gravity.CENTER_VERTICAL);question.setContentDescription(L10n.s(R.string.ui_237));question.setText(draft);
        composer.addView(question,new LinearLayout.LayoutParams(0,-2,1));
        send=icon(L10n.s(R.string.ui_238),L10n.s(R.string.ui_239),0xFFFFFFFF);GradientDrawable sendCircle=card(INK);sendCircle.setCornerRadius(dp(17));send.setBackground(sendCircle);send.setPadding(dp(8),dp(8),dp(8),dp(8));
        LinearLayout.LayoutParams sendParams=new LinearLayout.LayoutParams(dp(34),dp(34));sendParams.setMargins(dp(4),0,dp(2),0);composer.addView(send,sendParams);send.setOnClickListener(v->sendMessage());
        question.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){updateSend();}public void afterTextChanged(android.text.Editable s){}});updateSend();
        run(L10n.s(R.string.ui_240),client->new JSONObject().put("inventory",client.inventory()).put("turns",client.transcriptPage(current,turnOffset)),data->{
            JSONObject history=data.getJSONObject("turns");JSONArray messages=history.getJSONArray("items");
            if(history.optBoolean("sourcesChanged"))note(L10n.s(R.string.ui_242));
            if(history.optBoolean("hasOlder"))smallAction(turns,L10n.s(R.string.ui_243),()->{turnOffset+=40;show(L10n.s(R.string.ui_209));});
            if(turnOffset>0)smallAction(turns,L10n.s(R.string.ui_244),()->{turnOffset=Math.max(0,turnOffset-40);show(L10n.s(R.string.ui_209));});
            if(messages.length()==0)emptyChat();
            for(int i=0;i<messages.length();i++){
                JSONObject message=messages.getJSONObject(i);boolean user="user".equals(message.optString("role"));
                LinearLayout row=chatBubble(turns,user,message.optString("content"));
                if(user){long id=message.getLong("id");row.setOnLongClickListener(v->{if(!busy)glassChoiceDialog(null,new String[]{L10n.s(R.string.ui_245),L10n.s(R.string.ui_246)},w->{
                    if(w==0){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText(L10n.s(R.string.ui_247),message.optString("content")));toast(L10n.s(R.string.ui_245));}
                    else run(L10n.s(R.string.ui_248),client->client.proposeMemory(current,id),this::reviewProposal);
                });return true;});}
                JSONArray sources=message.optJSONArray("sources");if(!user&&sources!=null&&sources.length()>0){
                    TextView citation=text("↗  "+sources.length()+L10n.s(R.string.ui_249),11.5f,0xFF8E7B46);citation.setIncludeFontPadding(false);citation.setPadding(dp(11),dp(5),dp(11),dp(5));
                    GradientDrawable citeBg=card(0xFFF4F0E5);citeBg.setCornerRadius(dp(13));citeBg.setStroke(dp(1),0xFFDFD7C4);citation.setBackground(citeBg);
                    LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-2,-2);clp.setMargins(dp(34),dp(2),0,dp(10));turns.addView(citation,clp);
                    citation.setOnClickListener(v->showSources(sources));
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
    private void updateSend(){if(send!=null&&question!=null){boolean ready=!busy&&!question.getText().toString().trim().isEmpty();send.setEnabled(ready);send.setAlpha(ready?1f:.30f);}}
    private void setStatus(String message){status.setText(message);status.setVisibility(message==null||message.isEmpty()?View.GONE:View.VISIBLE);}
    private void scrollToLatest(){scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    private TextView avatar(boolean user){TextView avatar=text(user?L10n.s(R.string.ui_253):L10n.s(R.string.ui_254),10.5f,user?0xFF5A564E:ACCENT);avatar.setGravity(Gravity.CENTER);avatar.setIncludeFontPadding(false);GradientDrawable bg=card(user?0xFFEDE9DF:CARD_BG);bg.setCornerRadius(dp(13));bg.setStroke(dp(1),user?0xFFDFD8C8:0xFFE3DEC8);avatar.setBackground(bg);avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return avatar;}
    private LinearLayout chatBubble(LinearLayout parent,boolean user,String body){
        LinearLayout row=new LinearLayout(this);row.setGravity((user?Gravity.END:Gravity.START)|Gravity.TOP);row.setPadding(0,dp(5),0,dp(7));parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        TextView icon=avatar(user);LinearLayout.LayoutParams av=new LinearLayout.LayoutParams(dp(26),dp(26));av.setMargins(user?dp(8):0,dp(3),user?0:dp(8),0);
        TextView text=text(body,14.5f,user?0xFFFAF8F4:INK);text.setTextIsSelectable(true);text.setMaxWidth(Math.max(dp(180),getResources().getDisplayMetrics().widthPixels-dp(104)));text.setPadding(dp(15),dp(11),dp(15),dp(11));
        GradientDrawable bubble=card(user?INK:CARD_BG);if(!user)bubble.setStroke(dp(1),LINE);float r=dp(19),tail=dp(6);bubble.setCornerRadii(user?new float[]{r,r,tail,tail,r,r,r,r}:new float[]{tail,tail,r,r,r,r,r,r});text.setBackground(bubble);text.setElevation(dp(1));
        if(user)text.setOnLongClickListener(v->row.performLongClick());
        if(!user)row.addView(icon,av);row.addView(text,new LinearLayout.LayoutParams(-2,-2));if(user)row.addView(icon,av);return row;
    }
    private void emptyChat(){
        LinearLayout welcome=new LinearLayout(this);welcome.setOrientation(LinearLayout.VERTICAL);welcome.setGravity(Gravity.CENTER_HORIZONTAL);welcome.setPadding(dp(20),dp(48),dp(20),dp(24));welcome.setTag("empty-chat");turns.addView(welcome,new LinearLayout.LayoutParams(-1,-2));
        TextView star=Cards.text(this,"✧",26,ACCENT,AppFonts.regular());star.setGravity(Gravity.CENTER);star.setIncludeFontPadding(false);
        GradientDrawable medallion=card(0x18AE9E62);medallion.setCornerRadius(dp(26));medallion.setStroke(dp(1),0x40AE9E62);star.setBackground(medallion);
        LinearLayout.LayoutParams starLp=new LinearLayout.LayoutParams(dp(52),dp(52));starLp.gravity=Gravity.CENTER_HORIZONTAL;welcome.addView(star,starLp);
        TextView title=text(L10n.s(R.string.ui_255),20,INK);title.setTypeface(AppFonts.regular(),Typeface.BOLD);title.setGravity(Gravity.CENTER);title.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);titleLp.topMargin=dp(18);titleLp.bottomMargin=dp(8);welcome.addView(title,titleLp);
        TextView hint=text(L10n.s(R.string.ui_256),13.5f,MUTED);hint.setGravity(Gravity.CENTER);hint.setLineSpacing(dp(4),1);
        LinearLayout.LayoutParams hintLp=new LinearLayout.LayoutParams(-1,-2);hintLp.bottomMargin=dp(22);welcome.addView(hint,hintLp);
        View rule=new View(this);rule.setBackgroundColor(0xFFDCD4BE);
        LinearLayout.LayoutParams ruleLp=new LinearLayout.LayoutParams(dp(32),Math.max(1,dp(1)));ruleLp.bottomMargin=dp(20);ruleLp.gravity=Gravity.CENTER_HORIZONTAL;welcome.addView(rule,ruleLp);
        smallAction(welcome,L10n.s(R.string.ui_257),()->{question.setText(L10n.s(R.string.ui_257));question.requestFocus();});
        smallAction(welcome,L10n.s(R.string.ui_258),()->{question.setText(L10n.s(R.string.ui_258));question.requestFocus();});
    }
    private void smallAction(LinearLayout parent,String label,Runnable click){
        TextView item=text(label,13.5f,0xFF45413C);item.setGravity(Gravity.CENTER);item.setIncludeFontPadding(false);item.setPadding(dp(20),dp(11),dp(20),dp(11));
        GradientDrawable pill=card(CARD_BG);pill.setCornerRadius(dp(22));pill.setStroke(dp(1),0xFFE4DEC8);item.setBackground(pill);item.setElevation(dp(1.5f));
        item.setOnClickListener(v->{if(!busy)click.run();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.gravity=Gravity.CENTER_HORIZONTAL;lp.bottomMargin=dp(10);parent.addView(item,lp);
    }
    private void showSources(JSONArray sources){
        String[] labels=new String[sources.length()];for(int i=0;i<sources.length();i++){JSONObject source=sources.optJSONObject(i);labels[i]=(i+1)+" · "+source.optString("title")+(source.optBoolean("testData")?L10n.s(R.string.ui_259):"");}
        glassChoiceDialog(L10n.s(R.string.ui_260),labels,i->viewMemory(sources.optJSONObject(i).optString("id")));
    }
    private void chatMenu(View anchor){
        FrostedNavigation panel=new FrostedNavigation(this,null);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setCornerRadiusDp(20);
        panel.setBackdropFillColor(PAPER);
        panel.setGlassAppearance(0xBAFBF9F5,0x24000000,175);
        panel.enableRoundedShadow(12);
        panel.setBackdrop(root,null);
        panel.setPadding(dp(6),dp(6),dp(6),dp(6));

        int shadowPad=dp(12),menuWidth=dp(188);
        FrameLayout wrapper=new FrameLayout(this);
        wrapper.setClipChildren(false);wrapper.setClipToPadding(false);
        wrapper.setPadding(shadowPad,dp(4),shadowPad,shadowPad);
        wrapper.addView(panel,new FrameLayout.LayoutParams(menuWidth,-2));

        PopupWindow popup=new PopupWindow(wrapper,menuWidth+shadowPad*2,ViewGroup.LayoutParams.WRAP_CONTENT,true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(0);

        android.util.TypedValue selectable=new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground,selectable,true);
        String[] labels={L10n.s(R.string.ui_262),L10n.s(R.string.ui_263),L10n.s(R.string.ui_264)};
        for(int i=0;i<labels.length;i++){
            final int index=i;
            TextView item=text(labels[i],14,INK);
            item.setPadding(dp(16),dp(10),dp(16),dp(10));
            item.setMinHeight(dp(42));
            item.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
            item.setBackgroundResource(selectable.resourceId);
            item.setOnClickListener(v->{
                popup.dismiss();
                if(busy){toast(L10n.s(R.string.ui_226));return;}
                if(index==0){clearChat();draft="";if(question!=null)question.setText("");show(L10n.s(R.string.ui_209));}
                else if(index==1)chooseSession();
                else run(L10n.s(R.string.ui_265),LocalMemoryAgent::inventory,stats->glassMessageDialog(L10n.s(R.string.ui_264),coverage(stats),L10n.s(R.string.ui_261),null));
            });
            panel.addView(item,new LinearLayout.LayoutParams(-1,-2));
            if(i<labels.length-1){
                View divider=new View(this);
                divider.setBackgroundColor(0x14000000);
                LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(0.5f)));
                dlp.setMargins(dp(12),0,dp(12),0);
                panel.addView(divider,dlp);
            }
        }
        popup.showAsDropDown(anchor,shadowPad+dp(4),dp(4),Gravity.END);
    }
    private void materials() {
        note(L10n.s(R.string.ui_266));
        materialEditor(false);
        LinearLayout syncCard=box(content);
        secondaryButton(syncCard,L10n.s(R.string.ui_267),this::chooseRecords);
        TextView hint=text(L10n.s(R.string.ui_268),12,MUTED);hint.setPadding(dp(2),dp(6),dp(2),dp(2));syncCard.addView(hint);
        LinearLayout listHdr=new LinearLayout(this);listHdr.setOrientation(LinearLayout.HORIZONTAL);listHdr.setGravity(Gravity.CENTER_VERTICAL);
        listHdr.setPadding(dp(4),dp(14),dp(4),dp(4));
        TextView listTitle=text(L10n.s(R.string.ui_210),13,MUTED);listTitle.setTypeface(AppFonts.regular(),Typeface.BOLD);
        listHdr.addView(listTitle,new LinearLayout.LayoutParams(0,-2,1));
        chip(listHdr,L10n.s(R.string.ui_269),false,false,this::loadMemories);
        content.addView(listHdr,new LinearLayout.LayoutParams(-1,-2));
        loadMemories();
    }
    private void materialEditor(boolean about){
        LinearLayout form=box(content);
        titleInput=inputIn(form,about?L10n.s(R.string.ui_270):L10n.s(R.string.ui_271),false);titleInput.setText(about?aboutTitle:materialTitle);
        dateInput=inputIn(form,L10n.s(R.string.ui_272),false);dateInput.setText(about?aboutDate:materialDate);
        materialInput=inputIn(form,about?L10n.s(R.string.ui_273):L10n.s(R.string.ui_274),true);materialInput.setMinLines(4);materialInput.setText(about?aboutText:materialText);forwardMouseWheel(materialInput,scroll);
        ViewGroup tools=chipRow(form);
        chip(tools,L10n.s(R.string.ui_275),false,false,()->{importPage=page;picker.launch(new String[]{"text/*","application/octet-stream"});});
        primaryButton(form,about?L10n.s(R.string.ui_276):L10n.s(R.string.ui_277),()->{
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
        secondaryButton(form,L10n.s(R.string.ui_283),()->{
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
            if(coverage==null){coverage=text("",12,MUTED);coverage.setPadding(dp(4),dp(4),dp(4),dp(6));coverage.setTag("coverage");content.addView(coverage);}
            coverage.setText(coverage(result.getJSONObject("inventory")));
            // Rebuild just the list so refresh never duplicates cards.
            View previous=content.findViewWithTag("memory-list");if(previous!=null)content.removeView(previous);
            LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setTag("memory-list");content.addView(list);
            for(int i=0;i<items.length();i++) {
                JSONObject m=items.getJSONObject(i);String id=m.getString("id");
                boolean fromArchive="ARCHIVE".equals(m.optString("origin"));
                LinearLayout card=box(list);
                LinearLayout topRow=new LinearLayout(this);topRow.setOrientation(LinearLayout.HORIZONTAL);topRow.setGravity(Gravity.CENTER_VERTICAL);
                TextView title=text(m.getString("title")+(m.optBoolean("testData")?L10n.s(R.string.ui_259):""),15.5f,INK);title.setTypeface(AppFonts.regular(),Typeface.BOLD);
                topRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                badge(topRow,stateLabel(m.optString("memoryState"))+(m.optBoolean("pinned")?L10n.s(R.string.ui_287):""),m.optBoolean("pinned"));
                card.addView(topRow,new LinearLayout.LayoutParams(-1,-2));
                TextView summary=text(m.optString("summary","").isEmpty()?(fromArchive?L10n.s(R.string.ui_288):L10n.s(R.string.ui_289)):m.optString("summary"),13,MUTED);
                summary.setMaxLines(4);summary.setPadding(0,dp(6),0,dp(8));card.addView(summary);
                ViewGroup chips=chipRow(card);
                chip(chips,L10n.s(R.string.ui_290),false,false,()->viewMemory(id));
                if(!"READY".equals(m.optString("status")))chip(chips,L10n.s(R.string.ui_291),true,false,()->run(L10n.s(R.string.ui_292),client->client.analyze(id),v->{clearChat();show(L10n.s(R.string.ui_210));}));
                chip(chips,m.optBoolean("pinned")?L10n.s(R.string.ui_293):L10n.s(R.string.ui_294),false,false,()->run(L10n.s(R.string.ui_295),client->{client.pin(id,!m.optBoolean("pinned"));return true;},v->show(L10n.s(R.string.ui_210))));
                chip(chips,"DORMANT".equals(m.optString("memoryState"))?L10n.s(R.string.ui_296):L10n.s(R.string.ui_297),false,false,()->run(L10n.s(R.string.ui_298),client->{client.sleep(id,!"DORMANT".equals(m.optString("memoryState")));return true;},v->show(L10n.s(R.string.ui_210))));
                if(fromArchive)chip(chips,L10n.s(R.string.ui_299),false,false,()->openArchive(m));
                else chip(chips,L10n.s(R.string.ui_300),false,true,()->glassConfirmDialog(L10n.s(R.string.ui_301),L10n.s(R.string.ui_302),L10n.s(R.string.ui_303),L10n.s(R.string.ui_025),true,()->run(L10n.s(R.string.ui_304),client->{client.delete(id);return true;},v->{clearChat();show(L10n.s(R.string.ui_210));})));
            }
            if(memoryOffset>0)secondaryButton(list,L10n.s(R.string.ui_305),()->{memoryOffset=Math.max(0,memoryOffset-20);loadMemories();});
            if(pageData.optBoolean("hasMore"))secondaryButton(list,L10n.s(R.string.ui_306)+pageData.optInt("total")+L10n.s(R.string.ui_307),()->{memoryOffset+=20;loadMemories();});
        });
    }
    private void profile() {
        note(L10n.s(R.string.ui_308));
        materialEditor(true);
        note(L10n.s(R.string.ui_309));
        note(L10n.s(R.string.ui_310));
        ViewGroup actions=chipRow(content);
        chip(actions,L10n.s(R.string.ui_311),true,false,()->run(L10n.s(R.string.ui_312),LocalMemoryAgent::groupProfile,v->show(L10n.s(R.string.ui_217))));
        chip(actions,L10n.s(R.string.ui_313),false,false,()->show(L10n.s(R.string.ui_217)));
        run(L10n.s(R.string.ui_314),client->new JSONObject().put("traits",client.profile()).put("groups",client.profileGroups()),data->{
            JSONArray groups=data.getJSONArray("groups"),items=data.getJSONArray("traits");
            for(int i=0;i<groups.length();i++){
                JSONObject group=groups.getJSONObject(i);LinearLayout card=box(content);
                TextView gt=text(group.optString("topic")+(group.optBoolean("testData")?L10n.s(R.string.ui_259):""),16,INK);gt.setTypeface(AppFonts.regular(),Typeface.BOLD);card.addView(gt);
                TextView gd=text(group.optString("description")+"\n"+relationLabel(group.optString("relation")),13.5f,MUTED);gd.setPadding(0,dp(4),0,dp(6));card.addView(gd);
                JSONArray members=group.optJSONArray("members");if(members!=null)for(int j=0;j<members.length();j++){
                    JSONObject member=members.getJSONObject(j);String traitId=member.getString("id"),groupId=group.getString("id");
                    TextView mt=text((member.isNull("date")?L10n.s(R.string.ui_315):member.optString("date"))+" · "+member.optString("description"),13,MUTED);mt.setPadding(0,dp(4),0,dp(4));card.addView(mt);
                    ViewGroup mr=chipRow(card);
                    boolean pref=traitId.equals(group.optString("preferredTraitId"));
                    chip(mr,pref?L10n.s(R.string.ui_316):L10n.s(R.string.ui_317),pref,false,()->run(L10n.s(R.string.ui_318),client->{client.preferTrait(groupId,traitId);return true;},v->show(L10n.s(R.string.ui_217))));
                }
            }
            if(items.length()==0)note(L10n.s(R.string.ui_319));
            for(int i=0;i<items.length();i++) {
                JSONObject t=items.getJSONObject(i);String id=t.getString("id"),source=t.getString("memoryId"),state=t.getString("status");
                if("REJECTED".equals(state))continue;
                LinearLayout box=box(content);
                TextView td=text(t.getString("description"),15.5f,INK);td.setTypeface(AppFonts.regular(),Typeface.BOLD);box.addView(td);
                TextView meta=text(("USER_CORRECTION".equals(t.getString("basis"))?L10n.s(R.string.ui_320):"EXPLICIT".equals(t.getString("basis"))?L10n.s(R.string.ui_321):L10n.s(R.string.ui_322))+(t.optBoolean("testData")?L10n.s(R.string.ui_259):"")+" · "+("CONFIRMED".equals(state)?L10n.s(R.string.ui_323):L10n.s(R.string.ui_324)),12,MUTED);
                meta.setPadding(0,dp(4),0,dp(4));box.addView(meta);
                TextView ev=text("“"+t.getString("evidence")+"”",13.5f,MUTED);ev.setPadding(0,0,0,dp(8));box.addView(ev);
                ViewGroup row=chipRow(box);
                chip(row,L10n.s(R.string.ui_325),false,false,()->viewMemory(source));
                chip(row,L10n.s(R.string.ui_326),false,false,()->glassInputDialog(L10n.s(R.string.ui_327),null,t.optString("description"),L10n.s(R.string.ui_131),L10n.s(R.string.ui_328),value->run(L10n.s(R.string.ui_329),client->{client.correctTrait(id,value);return true;},v->show(L10n.s(R.string.ui_217)))));
                if(!"CONFIRMED".equals(state))chip(row,L10n.s(R.string.ui_330),true,false,()->setTrait(id,"CONFIRMED"));
                chip(row,"CONFIRMED".equals(state)?L10n.s(R.string.ui_331):L10n.s(R.string.ui_332),false,true,()->setTrait(id,"REJECTED"));
            }
        });
    }
    private void setTrait(String id,String state) {
        run(L10n.s(R.string.ui_333),client->{client.setTrait(id,state);return true;},v->{clearChat();show(L10n.s(R.string.ui_217));});
    }
    private void modelSettings() {
        note(L10n.s(R.string.ui_334));
        LinearLayout form=box(content);
        EditText model=inputIn(form,L10n.s(R.string.ui_335),false);model.setText(config.model());model.setSingleLine(true);
        EditText apiKey=inputIn(form,L10n.s(R.string.ui_336),false);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);apiKey.setSingleLine(true);
        apiKey.setSaveEnabled(false);
        if(android.os.Build.VERSION.SDK_INT>=26){
            apiKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            apiKey.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        try{apiKey.setText(config.apiKey());}catch(Exception e){note(L10n.s(R.string.ui_337));}
        EditText address=inputIn(form,L10n.s(R.string.ui_338),false);
        address.setText(config.baseUrl());address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setVisibility(ModelSettings.DEFAULT_BASE.equals(config.baseUrl())?View.GONE:View.VISIBLE);
        ViewGroup addrToggleRow=chipRow(form);
        chip(addrToggleRow,L10n.s(R.string.ui_339),false,false,()->address.setVisibility(address.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        primaryButton(form,L10n.s(R.string.ui_340),()->{
            try{
                config.save(address.getText().toString(),model.getText().toString(),apiKey.getText().toString());clearChat();
                run(L10n.s(R.string.ui_295),client->{return true;},v->setStatus(L10n.s(R.string.ui_341)));
            }catch(Exception e){setStatus(e.getMessage()==null?L10n.s(R.string.ui_342):e.getMessage());}
        });
        secondaryButton(form,L10n.s(R.string.ui_343),()->{
            try{
                config.save(address.getText().toString(),model.getText().toString(),apiKey.getText().toString());clearChat();
                run(L10n.s(R.string.ui_344),client->{client.testConnection();return true;},v->setStatus(L10n.s(R.string.ui_345)));
            }catch(Exception e){setStatus(e.getMessage()==null?L10n.s(R.string.ui_346):e.getMessage());}
        });
        note(L10n.s(R.string.ui_347));
        note(L10n.s(R.string.ui_348)+ModelSettings.DEFAULT_BASE+L10n.s(R.string.ui_349));
        dangerButton(content,L10n.s(R.string.ui_350),()->{
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
            glassMultiChoiceDialog(L10n.s(R.string.ui_353),labels,selected,L10n.s(R.string.ui_131),L10n.s(R.string.ui_354),()->{
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
            });
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
            ScrollView readerScroll=new ScrollView(this);TextView body=text(memory.optString("summary")+L10n.s(R.string.ui_383)+memory.getString("content"),14.5f,INK);
            body.setPadding(dp(4),dp(6),dp(4),dp(6));body.setTextIsSelectable(true);forwardMouseWheel(body,readerScroll);readerScroll.addView(body);
            glassViewDialog(memory.getString("title"),null,readerScroll,null,L10n.s(R.string.ui_261),null,null,false);
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
        glassChoiceDialog(L10n.s(R.string.ui_263),labels,index->{session=items.optJSONObject(index).optString("id");turnOffset=0;saveSession();show(L10n.s(R.string.ui_209));});
    });}
    private void reviewProposal(JSONObject proposal) throws Exception {
        String id=proposal.getString("id");
        glassInputDialog(L10n.s(R.string.ui_393),L10n.s(R.string.ui_394),proposal.optString("content"),L10n.s(R.string.ui_395),L10n.s(R.string.ui_396),
            value->run(L10n.s(R.string.ui_397),client->client.acceptProposal(id,value),v->{toast(L10n.s(R.string.ui_398));show(L10n.s(R.string.ui_210));}),
            ()->run(L10n.s(R.string.ui_333),client->{client.rejectProposal(id);return true;},v->{}));
    }
    private void reviews(){
        note(L10n.s(R.string.ui_399));
        LinearLayout form=box(content);
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);Calendar cal=Calendar.getInstance();String end=format.format(cal.getTime());cal.set(Calendar.DAY_OF_MONTH,1);String start=format.format(cal.getTime());
        EditText from=inputIn(form,L10n.s(R.string.ui_400),false);from.setText(start);
        EditText to=inputIn(form,L10n.s(R.string.ui_401),false);to.setText(end);
        EditText title=inputIn(form,L10n.s(R.string.ui_402),false);title.setText(L10n.s(R.string.ui_403));
        primaryButton(form,L10n.s(R.string.ui_404),()->{String f=from.getText().toString().trim(),t=to.getText().toString().trim(),name=title.getText().toString().trim();run(L10n.s(R.string.ui_405),client->client.summarizePeriod(f,t,name),v->show(L10n.s(R.string.ui_218)));});
        run(L10n.s(R.string.ui_406),LocalMemoryAgent::periods,items->{for(int i=0;i<items.length();i++){
            JSONObject period=items.getJSONObject(i);LinearLayout card=box(content);
            TextView pt=text(period.optString("title"),17,INK);pt.setTypeface(AppFonts.regular(),Typeface.BOLD);card.addView(pt);
            TextView pd=text(period.optString("from")+L10n.s(R.string.ui_407)+period.optString("to")+("STALE".equals(period.optString("status"))?L10n.s(R.string.ui_408):""),12,MUTED);
            pd.setPadding(0,dp(3),0,dp(6));card.addView(pd);
            TextView summary=text(period.optString("summary"),14.5f,INK);summary.setTextIsSelectable(true);summary.setPadding(0,0,0,dp(8));card.addView(summary);
            ViewGroup row=chipRow(card);
            if("STALE".equals(period.optString("status")))chip(row,L10n.s(R.string.ui_409),true,false,()->run(L10n.s(R.string.ui_410),client->client.summarizePeriod(period.optString("from"),period.optString("to"),period.optString("title")),v->show(L10n.s(R.string.ui_218))));
            JSONObject sources=period.optJSONObject("sourceRevisions");if(sources!=null){Iterator<String> keys=sources.keys();while(keys.hasNext()){String id=keys.next();chip(row,L10n.s(R.string.ui_411)+(period.optJSONObject("sourceTitles")==null?L10n.s(R.string.ui_412):period.optJSONObject("sourceTitles").optString(id,L10n.s(R.string.ui_412))),false,false,()->viewMemory(id));}}
        }});
    }
    private String taskLabel(JSONObject task){
        switch(task.optString("type")){case "ANALYZE":return L10n.s(R.string.ui_413);case "PERIOD":return L10n.s(R.string.ui_414);case "PROFILE":return L10n.s(R.string.ui_415);case "CHAT":return L10n.s(R.string.ui_416);default:return L10n.s(R.string.ui_417);}
    }
    private void management(){
        note(L10n.s(R.string.ui_418));
        LinearLayout backupCard=box(content);
        primaryButton(backupCard,L10n.s(R.string.ui_419),()->backupWriter.launch(L10n.s(R.string.ui_420)+new java.text.SimpleDateFormat("yyyyMMdd",Locale.ROOT).format(new Date())+".zip"));
        secondaryButton(backupCard,L10n.s(R.string.ui_421),()->backupReader.launch(new String[]{"application/zip","application/octet-stream"}));
        note(L10n.s(R.string.ui_422));
        run(L10n.s(R.string.ui_423),client->new JSONObject().put("tasks",client.pendingTasks()).put("proposals",client.proposals()),data->{
            JSONArray tasks=data.getJSONArray("tasks");if(tasks.length()>0)note(L10n.s(R.string.ui_424));
            for(int i=0;i<tasks.length();i++){JSONObject task=tasks.getJSONObject(i);String id=task.getString("id");LinearLayout card=box(content);
                TextView tt=text(taskLabel(task)+" · "+task.optInt("completed")+" / "+task.optInt("total"),14.5f,INK);tt.setTypeface(AppFonts.regular(),Typeface.BOLD);card.addView(tt);
                TextView te=text(task.optString("error",L10n.s(R.string.ui_425)),12,MUTED);te.setPadding(0,dp(3),0,dp(8));card.addView(te);
                ViewGroup tr=chipRow(card);
                chip(tr,L10n.s(R.string.ui_426),true,false,()->run(L10n.s(R.string.ui_427),client->client.resumeTask(id),v->{toast(L10n.s(R.string.ui_428));show(L10n.s(R.string.ui_219));}));
                chip(tr,L10n.s(R.string.ui_429),false,true,()->run(L10n.s(R.string.ui_333),client->{client.dismissTask(id);return true;},v->show(L10n.s(R.string.ui_219))));
            }
            JSONArray proposals=data.getJSONArray("proposals");for(int i=0;i<proposals.length();i++){JSONObject proposal=proposals.getJSONObject(i);if(!"PENDING".equals(proposal.optString("status")))continue;LinearLayout card=box(content);TextView pc=text(proposal.optString("content"),14,INK);pc.setPadding(0,0,0,dp(8));card.addView(pc);ViewGroup pr=chipRow(card);chip(pr,L10n.s(R.string.ui_430),true,false,()->{try{reviewProposal(proposal);}catch(Exception e){toast(L10n.s(R.string.ui_431));}});}
        });
    }
    private void reviewBackup(AgentBackup.Prepared prepared) throws Exception {
        JSONObject preview=prepared.preview();
        String msg=preview.optInt("records")+L10n.s(R.string.ui_363)+preview.optInt("memories")+L10n.s(R.string.ui_433)+preview.optInt("media")+L10n.s(R.string.ui_434);
        Dialog dlg=glassConfirmDialog(L10n.s(R.string.ui_432),msg,L10n.s(R.string.ui_131),L10n.s(R.string.ui_435),false,()->{
            android.content.Context app=getApplicationContext();
            run(L10n.s(R.string.ui_436),client->new AgentBackup(app,AgentStore.get(app),AppDb.get(app)).restore(prepared),v->{toast(L10n.s(R.string.ui_437));show(L10n.s(R.string.ui_210));});
        },prepared::close);
        dlg.setOnCancelListener(d->prepared.close());
    }
    private EditText inputIn(LinearLayout parent,String hint,boolean multiline) {
        EditText input=new EditText(this);input.setHint(hint);input.setTextSize(14);input.setTextColor(INK);input.setHintTextColor(0xFF969086);input.setIncludeFontPadding(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));input.setGravity(multiline?Gravity.TOP:Gravity.CENTER_VERTICAL);
        input.setPadding(dp(13),dp(10),dp(13),dp(10));if(!multiline){input.setSingleLine(true);input.setMinHeight(dp(42));}
        GradientDrawable ibg=card(FIELD_BG);ibg.setCornerRadius(dp(12));ibg.setStroke(dp(1),0xFFE4DDD0);input.setBackground(ibg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(6));parent.addView(input,lp);return input;
    }
    private LinearLayout box(LinearLayout parent) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable bbg=card(CARD_BG);bbg.setCornerRadius(dp(18));bbg.setStroke(dp(1),LINE);box.setBackground(bbg);box.setElevation(dp(1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(8));parent.addView(box,lp);return box;
    }
    private void note(String body){TextView text=text(body,12.5f,MUTED);text.setPadding(dp(4),dp(8),dp(4),dp(8));content.addView(text);}
    private void primaryButton(LinearLayout parent,String label,Runnable task){
        TextView b=text(label,14,PAPER);b.setIncludeFontPadding(false);b.setPadding(dp(16),dp(11),dp(16),dp(11));b.setMinHeight(dp(44));
        GradientDrawable abg=card(INK);abg.setCornerRadius(dp(22));b.setBackground(abg);b.setElevation(dp(1.5f));
        b.setGravity(Gravity.CENTER);b.setOnClickListener(v->{if(!busy)task.run();else toast(L10n.s(R.string.ui_226));});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(4));parent.addView(b,lp);
    }
    private void secondaryButton(LinearLayout parent,String label,Runnable task){
        TextView b=text(label,13.5f,INK);b.setIncludeFontPadding(false);b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setMinHeight(dp(42));
        GradientDrawable abg=card(FIELD_BG);abg.setCornerRadius(dp(21));abg.setStroke(dp(1),0xFFDFD8CA);b.setBackground(abg);
        b.setGravity(Gravity.CENTER);b.setOnClickListener(v->{if(!busy)task.run();else toast(L10n.s(R.string.ui_226));});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(4));parent.addView(b,lp);
    }
    private void dangerButton(LinearLayout parent,String label,Runnable task){
        TextView b=text(label,13.5f,0xFFA6443A);b.setIncludeFontPadding(false);b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setMinHeight(dp(42));
        GradientDrawable abg=card(0xFFFDF4F2);abg.setCornerRadius(dp(21));abg.setStroke(dp(1),0xFFEED3CE);b.setBackground(abg);
        b.setGravity(Gravity.CENTER);b.setOnClickListener(v->{if(!busy)task.run();else toast(L10n.s(R.string.ui_226));});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(10));parent.addView(b,lp);
    }
    private ViewGroup chipRow(ViewGroup parent){
        ChipFlowLayout row=new ChipFlowLayout(this,dp(6),dp(6));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(3),0,dp(3));parent.addView(row,lp);return row;
    }
    private void chip(ViewGroup row,String label,boolean primary,boolean danger,Runnable task){
        TextView b=text(label,12,danger?0xFFA6443A:(primary?PAPER:INK));b.setIncludeFontPadding(false);b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(12),dp(7),dp(12),dp(7));b.setMinHeight(dp(32));b.setGravity(Gravity.CENTER);
        GradientDrawable bg=card(danger?0xFFFDF3F1:(primary?INK:FIELD_BG));bg.setCornerRadius(dp(16));bg.setStroke(dp(1),danger?0xFFEAD0CB:(primary?INK:0xFFE2DBD0));
        b.setBackground(bg);b.setOnClickListener(v->{if(!busy)task.run();else toast(L10n.s(R.string.ui_226));});
        row.addView(b,new ViewGroup.LayoutParams(-2,-2));
    }
    private static final class ChipFlowLayout extends ViewGroup {
        private final int hGap,vGap;
        ChipFlowLayout(android.content.Context c,int hGap,int vGap){super(c);this.hGap=hGap;this.vGap=vGap;}
        @Override protected void onMeasure(int ws,int hs){
            int maxW=MeasureSpec.getMode(ws)==MeasureSpec.UNSPECIFIED?Integer.MAX_VALUE:MeasureSpec.getSize(ws);
            int padH=getPaddingLeft()+getPaddingRight(),avail=Math.max(1,maxW-padH);
            int x=0,y=getPaddingTop(),rowH=0;
            for(int i=0;i<getChildCount();i++){
                View ch=getChildAt(i);if(ch.getVisibility()==GONE)continue;
                measureChild(ch,MeasureSpec.makeMeasureSpec(avail,MeasureSpec.AT_MOST),hs);
                int cw=ch.getMeasuredWidth(),chH=ch.getMeasuredHeight();
                if(x>0&&x+cw>avail){y+=rowH+vGap;x=0;rowH=0;}
                x+=cw+hGap;rowH=Math.max(rowH,chH);
            }
            setMeasuredDimension(resolveSize(maxW,ws),resolveSize(y+rowH+getPaddingBottom(),hs));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            int avail=Math.max(1,(r-l)-getPaddingLeft()-getPaddingRight());
            int x=getPaddingLeft(),y=getPaddingTop(),rowH=0;
            for(int i=0;i<getChildCount();i++){
                View ch=getChildAt(i);if(ch.getVisibility()==GONE)continue;
                int cw=ch.getMeasuredWidth(),chH=ch.getMeasuredHeight();
                if(x>getPaddingLeft()&&(x-getPaddingLeft())+cw>avail){y+=rowH+vGap;x=getPaddingLeft();rowH=0;}
                ch.layout(x,y,x+cw,y+chH);x+=cw+hGap;rowH=Math.max(rowH,chH);
            }
        }
    }
    private void badge(LinearLayout parent,String label,boolean accent){
        TextView b=text(label,10.5f,accent?ACCENT:MUTED);b.setIncludeFontPadding(false);b.setSingleLine(true);
        b.setPadding(dp(9),dp(3),dp(9),dp(3));
        GradientDrawable bg=card(accent?0xFFF5F0E1:FIELD_BG);bg.setCornerRadius(dp(10));bg.setStroke(dp(1),accent?0xFFE4DAC0:0xFFE4DDD0);
        b.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(8);parent.addView(b,lp);
    }
    private FrostedNavigation createGlassDialogPanel(Dialog dialog,String title){
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrostedNavigation panel=new FrostedNavigation(this,null);
        panel.setOrientation(LinearLayout.VERTICAL);panel.setCornerRadiusDp(22);
        panel.setBackdropFillColor(PAPER);panel.setGlassAppearance(0xCCFBF9F5,0x30000000,195);
        panel.enableRoundedShadow(14);panel.setBackdrop(root,null);
        panel.setPadding(dp(18),dp(16),dp(18),dp(14));
        if(title!=null&&!title.isEmpty()){
            TextView tv=text(title,15.5f,INK);tv.setTypeface(AppFonts.regular(),Typeface.BOLD);
            tv.setPadding(dp(2),0,dp(2),dp(10));panel.addView(tv);
            View div=new View(this);div.setBackgroundColor(0x16000000);
            LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(0.5f)));dlp.bottomMargin=dp(10);panel.addView(div,dlp);
        }
        return panel;
    }
    private void showGlassDialog(Dialog dialog,FrostedNavigation panel){
        int sw=getResources().getDisplayMetrics().widthPixels;
        int dw=Math.min(sw-dp(48),dp(340));
        dialog.setContentView(panel,new ViewGroup.LayoutParams(dw,ViewGroup.LayoutParams.WRAP_CONTENT));
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(dw,WindowManager.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.28f);
        }
        dialog.show();
    }
    private void glassChoiceDialog(String title,String[] labels,java.util.function.IntConsumer onPick){
        Dialog dialog=new Dialog(this);
        FrostedNavigation panel=createGlassDialogPanel(dialog,title);
        android.util.TypedValue selectable=new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground,selectable,true);
        ScrollView listScroll=new ScrollView(this);listScroll.setVerticalScrollBarEnabled(false);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);listScroll.addView(list);
        for(int i=0;i<labels.length;i++){
            final int idx=i;
            TextView item=text(labels[i],14.5f,INK);item.setMinHeight(dp(44));item.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
            item.setPadding(dp(8),dp(10),dp(8),dp(10));item.setBackgroundResource(selectable.resourceId);
            item.setOnClickListener(v->{dialog.dismiss();onPick.accept(idx);});
            list.addView(item,new LinearLayout.LayoutParams(-1,-2));
            if(i<labels.length-1){View d=new View(this);d.setBackgroundColor(0x12000000);list.addView(d,new LinearLayout.LayoutParams(-1,Math.max(1,dp(0.5f))));}
        }
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);
        int maxH=(int)(getResources().getDisplayMetrics().heightPixels*0.52f);
        if(labels.length>7)slp.height=maxH;
        panel.addView(listScroll,slp);
        showGlassDialog(dialog,panel);
    }
    private void glassMessageDialog(String title,String message,String okLabel,Runnable onOk){
        Dialog dialog=new Dialog(this);FrostedNavigation panel=createGlassDialogPanel(dialog,title);
        TextView msg=text(message,14,INK);msg.setLineSpacing(dp(4),1);msg.setPadding(dp(2),0,dp(2),dp(14));panel.addView(msg);
        primaryButton(panel,okLabel,()->{dialog.dismiss();if(onOk!=null)onOk.run();});
        showGlassDialog(dialog,panel);
    }
    private Dialog glassConfirmDialog(String title,String message,String cancelLabel,String okLabel,boolean danger,Runnable onOk){
        return glassConfirmDialog(title,message,cancelLabel,okLabel,danger,onOk,null);
    }
    private Dialog glassConfirmDialog(String title,String message,String cancelLabel,String okLabel,boolean danger,Runnable onOk,Runnable onCancel){
        Dialog dialog=new Dialog(this);FrostedNavigation panel=createGlassDialogPanel(dialog,title);
        TextView msg=text(message,14,MUTED);msg.setLineSpacing(dp(4),1);msg.setPadding(dp(2),0,dp(2),dp(14));panel.addView(msg);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);panel.addView(row,new LinearLayout.LayoutParams(-1,-2));
        TextView c=text(cancelLabel,13.5f,INK);c.setGravity(Gravity.CENTER);c.setMinHeight(dp(40));c.setIncludeFontPadding(false);
        GradientDrawable cbg=card(FIELD_BG);cbg.setCornerRadius(dp(20));cbg.setStroke(dp(1),0xFFE0D9CC);c.setBackground(cbg);
        c.setOnClickListener(v->{dialog.dismiss();if(onCancel!=null)onCancel.run();});
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,-2,1);clp.rightMargin=dp(8);row.addView(c,clp);
        TextView ok=text(okLabel,13.5f,PAPER);ok.setGravity(Gravity.CENTER);ok.setMinHeight(dp(40));ok.setIncludeFontPadding(false);
        GradientDrawable obg=card(danger?0xFFA6443A:INK);obg.setCornerRadius(dp(20));ok.setBackground(obg);
        ok.setOnClickListener(v->{dialog.dismiss();onOk.run();});
        row.addView(ok,new LinearLayout.LayoutParams(0,-2,1));
        showGlassDialog(dialog,panel);
        return dialog;
    }
    private void glassInputDialog(String title,String message,String initial,String negLabel,String posLabel,java.util.function.Consumer<String> onConfirm){
        glassInputDialog(title,message,initial,negLabel,posLabel,onConfirm,null);
    }
    private void glassInputDialog(String title,String message,String initial,String negLabel,String posLabel,java.util.function.Consumer<String> onConfirm,Runnable onNeg){
        Dialog dialog=new Dialog(this);FrostedNavigation panel=createGlassDialogPanel(dialog,title);
        if(message!=null&&!message.isEmpty()){TextView m=text(message,13,MUTED);m.setPadding(dp(2),0,dp(2),dp(8));panel.addView(m);}
        EditText edit=inputIn(panel,"",true);edit.setText(initial);edit.setMinLines(3);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);rlp.topMargin=dp(8);panel.addView(row,rlp);
        TextView neg=text(negLabel,13.5f,INK);neg.setGravity(Gravity.CENTER);neg.setMinHeight(dp(40));neg.setIncludeFontPadding(false);
        GradientDrawable nbg=card(FIELD_BG);nbg.setCornerRadius(dp(20));nbg.setStroke(dp(1),0xFFE0D9CC);neg.setBackground(nbg);
        neg.setOnClickListener(v->{dialog.dismiss();if(onNeg!=null)onNeg.run();});
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,-2,1);nlp.rightMargin=dp(8);row.addView(neg,nlp);
        TextView pos=text(posLabel,13.5f,PAPER);pos.setGravity(Gravity.CENTER);pos.setMinHeight(dp(40));pos.setIncludeFontPadding(false);
        GradientDrawable pbg=card(INK);pbg.setCornerRadius(dp(20));pos.setBackground(pbg);
        pos.setOnClickListener(v->{dialog.dismiss();onConfirm.accept(edit.getText().toString());});
        row.addView(pos,new LinearLayout.LayoutParams(0,-2,1));
        showGlassDialog(dialog,panel);
    }
    private void glassMultiChoiceDialog(String title,String[] labels,boolean[] selected,String negLabel,String posLabel,Runnable onConfirm){
        Dialog dialog=new Dialog(this);FrostedNavigation panel=createGlassDialogPanel(dialog,title);
        ScrollView sv=new ScrollView(this);sv.setVerticalScrollBarEnabled(false);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);
        for(int i=0;i<labels.length;i++){
            final int idx=i;
            CheckBox cb=new CheckBox(this);cb.setText(labels[i]);cb.setTextSize(13.5f);cb.setTextColor(INK);cb.setTypeface(AppFonts.regular());
            cb.setChecked(selected[i]);cb.setPadding(dp(4),dp(8),dp(4),dp(8));
            cb.setOnCheckedChangeListener((b,checked)->selected[idx]=checked);
            list.addView(cb,new LinearLayout.LayoutParams(-1,-2));
        }
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,Math.min(dp(320),(int)(getResources().getDisplayMetrics().heightPixels*0.48f)));
        panel.addView(sv,slp);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);rlp.topMargin=dp(10);panel.addView(row,rlp);
        TextView neg=text(negLabel,13.5f,INK);neg.setGravity(Gravity.CENTER);neg.setMinHeight(dp(40));neg.setIncludeFontPadding(false);
        GradientDrawable nbg=card(FIELD_BG);nbg.setCornerRadius(dp(20));nbg.setStroke(dp(1),0xFFE0D9CC);neg.setBackground(nbg);
        neg.setOnClickListener(v->dialog.dismiss());
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,-2,1);nlp.rightMargin=dp(8);row.addView(neg,nlp);
        TextView pos=text(posLabel,13.5f,PAPER);pos.setGravity(Gravity.CENTER);pos.setMinHeight(dp(40));pos.setIncludeFontPadding(false);
        GradientDrawable pbg=card(INK);pbg.setCornerRadius(dp(20));pos.setBackground(pbg);
        pos.setOnClickListener(v->{dialog.dismiss();onConfirm.run();});
        row.addView(pos,new LinearLayout.LayoutParams(0,-2,1));
        showGlassDialog(dialog,panel);
    }
    private void glassViewDialog(String title,String message,View body,String negLabel,String posLabel,Runnable onPos,Runnable onNeg,boolean danger){
        Dialog dialog=new Dialog(this);FrostedNavigation panel=createGlassDialogPanel(dialog,title);
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-1,Math.min(dp(340),(int)(getResources().getDisplayMetrics().heightPixels*0.50f)));
        blp.bottomMargin=dp(12);panel.addView(body,blp);
        primaryButton(panel,posLabel,()->{dialog.dismiss();if(onPos!=null)onPos.run();});
        showGlassDialog(dialog,panel);
    }
    private void compactHeaderText(TextView text){
        text.setIncludeFontPadding(false);text.setLineSpacing(0,1);text.setPadding(0,0,0,0);
        text.setSingleLine(true);text.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }
    private TextView text(String s,int size,int color){return text(s,(float)size,color);}
    private TextView text(String s,float size,int color){TextView t=Cards.text(this,s,size,color,AppFonts.regular());t.setLineSpacing(dp(3),1);return t;}
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
