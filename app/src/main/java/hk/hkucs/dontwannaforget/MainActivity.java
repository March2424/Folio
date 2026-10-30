package hk.hkucs.dontwannaforget;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class MainActivity extends AppCompatActivity {
    private final String[] NAMES = {L10n.s(R.string.ui_082), L10n.s(R.string.ui_083), L10n.s(R.string.ui_084), L10n.s(R.string.ui_085), L10n.s(R.string.ui_086), L10n.s(R.string.ui_042), L10n.s(R.string.ui_087)};
    private static final int[] TYPES = {-1, Record.TICKET, Record.VOICE, Record.BOOK, Record.MUSIC, Record.FOOTPRINT, Record.LETTER};

    private MemoryAdapter adapter;
    private RecyclerView list;
    private FrameLayout homeContainer;
    private HomeMemoryCanvasView homeArchiveView;
    private SharedPreferences navigationPreferences;
    private int currentType = -1;
    private int creationType = Record.TICKET;
    private boolean showingHome = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        FloatingHeader.style(findViewById(R.id.title));
        FloatingHeader.style(findViewById(R.id.module_back));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        navigationPreferences = getSharedPreferences("navigation", MODE_PRIVATE);
        creationType = navigationPreferences.getInt("last_module", Record.TICKET);
        if (creationType < Record.TICKET || creationType > Record.LETTER) creationType = Record.TICKET;
        if (savedInstanceState != null) {
            currentType = savedInstanceState.getInt("module", -1);
            showingHome = savedInstanceState.getBoolean("home", true);
        }

        Repo repo = new Repo(this);
        repo.seedSamplesIfDebug();
        adapter = new MemoryAdapter(r -> {
            Intent intent = new Intent(this, r.type == Record.LETTER ? LetterActivity.class : DetailActivity.class);
            intent.putExtra("id", r.id);
            startActivity(intent);
        }, date -> {
            Intent intent = new Intent(this, DaySpreadActivity.class);
            intent.putExtra("date", date);
            startActivity(intent);
        });
        adapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        adapter.setFilter(currentType);
        adapter.setTicketArrange(false);
        adapter.setTicketCover(false);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        FrostedNavigation floatingNav=findViewById(R.id.bottom_navigation);
        floatingNav.setBackdrop(list,findViewById(R.id.module_background));
        list.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrolled(RecyclerView recycler,int dx,int dy){floatingNav.invalidate();}
        });
        list.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->floatingNav.invalidate());
        repo.observeActive().observe(this, data -> {
            adapter.setData(data);
            if (homeArchiveView != null) homeArchiveView.setRecords(data);
        });
        repo.observeMedia().observe(this, media -> {
            MediaIndex.set(media);
            adapter.notifyDataSetChanged();
            if (homeArchiveView != null) homeArchiveView.refreshMedia();
        });
        findViewById(R.id.title).setOnLongClickListener(v -> {
            repo.clearSamples();
            return true;
        });

        homeContainer = findViewById(R.id.home_container);
        homeArchiveView = new HomeMemoryCanvasView(this, new HomeMemoryCanvasView.Host() {
            @Override public void openModule(int type) { MainActivity.this.openModule(type); }
            @Override public void openConversation() { startActivity(new Intent(MainActivity.this, AgentActivity.class)); }
            @Override public void back() { getOnBackPressedDispatcher().onBackPressed(); }
        });
        homeContainer.addView(homeArchiveView, new FrameLayout.LayoutParams(-1, -1));
        findViewById(R.id.nav_create).setOnClickListener(v -> createInCurrentModule());
        findViewById(R.id.nav_home).setOnClickListener(v -> showHomeScreen());
        findViewById(R.id.nav_functions).setOnClickListener(v -> showFunctions());
        updateScreen();
        // A fresh launch plays the original artwork; returning home or rotating skips it.
        if (savedInstanceState == null && showingHome && !getIntent().getBooleanExtra(HomeNavigation.SHOW_HOME, false)) {
            HomeEntranceView entrance = new HomeEntranceView(this, this::updateScreen);
            homeContainer.addView(entrance, new FrameLayout.LayoutParams(-1, -1));
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
        }
        findViewById(R.id.module_back).setOnClickListener(v -> showHomeScreen());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!showingHome) showHomeScreen();
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(HomeNavigation.SHOW_HOME, false)) showHomeScreen();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("module", currentType);
        state.putBoolean("home", showingHome);
        super.onSaveInstanceState(state);
    }

    private void openModule(int type) {
        currentType = type;
        if (type >= Record.TICKET && type <= Record.LETTER) {
            creationType = type;
            navigationPreferences.edit().putInt("last_module", type).apply();
        }
        showingHome = false;
        adapter.setFilter(type);
        adapter.setTicketArrange(false);
        adapter.setTicketCover(false);
        list.scrollToPosition(0);
        updateScreen();
    }

    private void showHomeScreen() {
        showingHome = true;
        updateScreen();
    }

    private void updateScreen() {
        View root = findViewById(R.id.main);
        if (showingHome) root.setBackgroundResource(R.drawable.bg_archive);
        else root.setBackgroundColor(0xFF101112);
        findViewById(R.id.module_background).setVisibility(showingHome ? View.GONE : View.VISIBLE);
        homeContainer.setVisibility(showingHome ? View.VISIBLE : View.GONE);
        findViewById(R.id.bottom_navigation).setVisibility(showingHome ? View.GONE : View.VISIBLE);
        findViewById(R.id.bottom_navigation).invalidate();
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(showingHome);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(showingHome);
        list.setVisibility(showingHome ? View.INVISIBLE : View.VISIBLE);
        TextView title = findViewById(R.id.title);
        title.setVisibility(showingHome ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.module_back).setVisibility(showingHome ? View.GONE : View.VISIBLE);
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i] == currentType) title.setText(currentType == -1 ? L10n.s(R.string.ui_088) : NAMES[i]);
        }
        findViewById(R.id.nav_home).setSelected(showingHome);
        ((TextView) findViewById(R.id.nav_home)).setTextColor(showingHome ? 0xFFF2EFE9 : 0xFFB5B8BD);
        View create = findViewById(R.id.nav_create);
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i] == creationType) create.setContentDescription(L10n.s(R.string.ui_089) + NAMES[i]);
        }
    }

    private void createInCurrentModule() {
        createRecord(creationType);
    }

    private void showHomeCreateMenu() {
        String[] items = {
                L10n.s(R.string.ui_090),
                L10n.s(R.string.ui_091),
                L10n.s(R.string.ui_092),
                L10n.s(R.string.ui_093),
                L10n.s(R.string.ui_094),
                L10n.s(R.string.ui_095)
        };
        int[] types = {Record.TICKET, Record.VOICE, Record.BOOK, Record.MUSIC, Record.FOOTPRINT, Record.LETTER};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(L10n.s(R.string.ui_096))
                .setItems(items, (dialog, index) -> createRecord(types[index]))
                .show();
    }

    private void createRecord(int type) {
        Class<?> editor;
        switch (type) {
            case Record.VOICE: editor = VoiceEditActivity.class; break;
            case Record.BOOK: editor = BookEditActivity.class; break;
            case Record.MUSIC: editor = MusicEditActivity.class; break;
            case Record.FOOTPRINT: editor = FootprintEditActivity.class; break;
            case Record.LETTER: editor = TypewriterActivity.class; break;
            default: editor = TicketEditActivity.class;
        }
        startActivity(new Intent(this, editor));
    }

    private void showFunctions() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.Theme_Dontwannaforget_ModuleSheet);
        View page=findViewById(R.id.main);
        int pageHeight=page.getHeight()-page.getPaddingTop()-page.getPaddingBottom();
        int panelHeight=Math.round(pageHeight*2f/3f);
        FrostedNavigation panel=new FrostedNavigation(this,null);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setCornerRadiusDp(24);
        panel.setGlassAppearance(0x70202224,0x38FFFFFF,235);
        panel.setBackdrop(list,findViewById(R.id.module_background));
        panel.setPadding(Cards.dp(this,12),Cards.dp(this,4),Cards.dp(this,12),Cards.dp(this,8));

        FrameLayout header=new FrameLayout(this);
        TextView heading=Cards.text(this,L10n.s(R.string.ui_097),13,0xFFD8D4CC,AppFonts.regular());
        heading.setIncludeFontPadding(false);heading.setGravity(Gravity.CENTER);
        header.addView(heading,new FrameLayout.LayoutParams(-1,-1));
        android.widget.ImageView close=new android.widget.ImageView(this);
        close.setImageDrawable(new AgentLineIcon("close",0xFFD8D4CC));
        close.setPadding(Cards.dp(this,12),Cards.dp(this,12),Cards.dp(this,12),Cards.dp(this,12));
        close.setContentDescription(L10n.s(R.string.ui_261));close.setFocusable(true);
        close.setOnClickListener(v->sheet.dismiss());
        header.addView(close,new FrameLayout.LayoutParams(Cards.dp(this,40),Cards.dp(this,40),Gravity.START|Gravity.TOP));
        panel.addView(header,new LinearLayout.LayoutParams(-1,Cards.dp(this,40)));

        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Cards.dp(this,8),0,Cards.dp(this,8),0);
        for(int i=0;i<NAMES.length;i++){
            final int type=TYPES[i];boolean selected=!showingHome&&currentType==type;
            TextView item=functionItem(NAMES[i],selected?0xFFF2F3F5:0xFFC5C7CB);
            item.setSelected(selected);
            item.setOnClickListener(v->{sheet.dismiss();openModule(type);});
            content.addView(item,new LinearLayout.LayoutParams(-1,-2));
        }
        TextView memoryAgent=functionItem(L10n.s(R.string.ui_098),0xFFF2F3F5);
        memoryAgent.setOnClickListener(v->{sheet.dismiss();startActivity(new Intent(MainActivity.this,AgentActivity.class));});
        content.addView(memoryAgent,new LinearLayout.LayoutParams(-1,-2));
        TextView language=functionItem("語言 / Language",0xFFC5C7CB);
        language.setOnClickListener(v->{sheet.dismiss();L10n.choose(this);});
        content.addView(language,new LinearLayout.LayoutParams(-1,-2));
        scroll.addView(content);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        sheet.setContentView(panel,new android.view.ViewGroup.LayoutParams(-1,panelHeight));
        sheet.show();
        FrameLayout container=sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if(container!=null){
            container.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            container.getLayoutParams().height=panelHeight;container.requestLayout();
        }
        if(sheet.getWindow()!=null)sheet.getWindow().setDimAmount(.18f);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.getBehavior().setPeekHeight(panelHeight);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback(){
            @Override public void onStateChanged(View view,int state){panel.invalidate();}
            @Override public void onSlide(View view,float offset){panel.invalidate();}
        });
    }
    private TextView functionItem(String label,int color){
        TextView item=Cards.text(this,label,15,color,AppFonts.regular());
        item.setIncludeFontPadding(false);item.setLineSpacing(0,1);
        item.setGravity(Gravity.CENTER_VERTICAL);item.setMinHeight(Cards.dp(this,36));
        item.setPadding(0,Cards.dp(this,4),0,Cards.dp(this,4));
        return item;
    }
}
