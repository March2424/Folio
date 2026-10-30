package hk.hkucs.dontwannaforget;

import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Read-only UI checks. Preview bubbles are temporary views, never user memories or model requests. */
@RunWith(AndroidJUnit4.class)
public class AgentChatLayoutTest {
    ActivityScenario<AgentActivity> open(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();return ActivityScenario.launch(new Intent(c,AgentActivity.class).putExtra("page",L10n.s(R.string.ui_209)));}
    void idle(ActivityScenario<AgentActivity> scenario) throws Exception {for(int i=0;i<150;i++){AtomicReference<Boolean> running=new AtomicReference<>();scenario.onActivity(a->running.set((Boolean)field(a,"busy")));if(!running.get())return;Thread.sleep(20);}fail("UI read did not finish");}
    static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    @Test public void fourIconNavigationSitsBelowComposerAndDraftSurvivesSwitchAndRecreate() throws Exception {
        try(ActivityScenario<AgentActivity> scenario=open()){
            idle(scenario);scenario.onActivity(a->{LinearLayout nav=(LinearLayout)field(a,"navigation"),composer=(LinearLayout)field(a,"composer");View dock=(View)field(a,"dock");
                assertEquals(4,nav.getChildCount());assertSame(dock,nav.getParent());assertSame(dock,composer.getParent());assertTrue(((LinearLayout)dock).indexOfChild(composer)<((LinearLayout)dock).indexOfChild(nav));assertEquals(L10n.s(R.string.ui_229),nav.getChildAt(2).getContentDescription());
                EditText question=(EditText)field(a,"question");question.setText("想起一段日子");assertTrue(((View)field(a,"send")).isEnabled());nav.getChildAt(1).performClick();
            });idle(scenario);scenario.onActivity(a->{assertEquals(L10n.s(R.string.ui_210),field(a,"page"));assertEquals(View.GONE,((View)field(a,"composer")).getVisibility());((LinearLayout)field(a,"navigation")).getChildAt(0).performClick();});
            idle(scenario);scenario.recreate();idle(scenario);scenario.onActivity(a->{assertEquals("想起一段日子",((EditText)field(a,"question")).getText().toString());((EditText)field(a,"question")).setText(" ");assertFalse(((View)field(a,"send")).isEnabled());});
        }
    }
    static void show(AgentActivity activity,String page){try{Method method=AgentActivity.class.getDeclaredMethod("show",String.class);method.setAccessible(true);method.invoke(activity,page);}catch(Exception e){throw new AssertionError(e);}}
    @Test public void aboutInputAndMergedPagesKeepSeparateDraftsAcrossRecreation() throws Exception {
        try(ActivityScenario<AgentActivity> scenario=open()){
            idle(scenario);scenario.onActivity(a->((LinearLayout)field(a,"navigation")).getChildAt(2).performClick());idle(scenario);
            scenario.onActivity(a->{assertEquals(L10n.s(R.string.ui_217),field(a,"page"));((EditText)field(a,"titleInput")).setText("我的自述");((EditText)field(a,"materialInput")).setText("喜欢安静地读书");show(a,L10n.s(R.string.ui_218));});idle(scenario);
            scenario.onActivity(a->{assertTrue(((LinearLayout)field(a,"navigation")).getChildAt(2).isSelected());show(a,L10n.s(R.string.ui_210));});idle(scenario);
            scenario.onActivity(a->{assertEquals("",((EditText)field(a,"materialInput")).getText().toString());((EditText)field(a,"materialInput")).setText("独立的材料草稿");show(a,L10n.s(R.string.ui_219));});idle(scenario);
            scenario.onActivity(a->{assertTrue(((LinearLayout)field(a,"navigation")).getChildAt(1).isSelected());show(a,L10n.s(R.string.ui_217));});idle(scenario);scenario.recreate();idle(scenario);
            scenario.onActivity(a->{assertEquals("我的自述",((EditText)field(a,"titleInput")).getText().toString());assertEquals("喜欢安静地读书",((EditText)field(a,"materialInput")).getText().toString());show(a,L10n.s(R.string.ui_210));});idle(scenario);
            scenario.onActivity(a->assertEquals("独立的材料草稿",((EditText)field(a,"materialInput")).getText().toString()));
        }
    }
    @Test public void completedImportAfterRecreationFillsAboutEditorWithoutSaving() throws Exception {
        try(ActivityScenario<AgentActivity> scenario=open()){
            idle(scenario);scenario.onActivity(a->show(a,L10n.s(R.string.ui_217)));idle(scenario);scenario.recreate();idle(scenario);
            scenario.onActivity(a->{try{
                org.json.JSONObject data=new org.json.JSONObject().put("fileImport",true).put("page",L10n.s(R.string.ui_217)).put("title","自述.md").put("content","我喜欢读书，也喜欢雨天。");
                ((AgentJobModel)field(a,"jobs")).state.setValue(new AgentJobModel.State("import-test","正在读取文本文件…",L10n.s(R.string.ui_217),false,data,null));
                assertEquals("自述.md",((EditText)field(a,"titleInput")).getText().toString());assertEquals("我喜欢读书，也喜欢雨天。",((EditText)field(a,"materialInput")).getText().toString());
            }catch(Exception e){throw new AssertionError(e);}});idle(scenario);
        }
    }
    @Test public void textFileImportWaitsForPageLoadAndReachesAboutInput() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();File directory=new File(context.getCacheDir(),"camera");directory.mkdirs();File file=File.createTempFile("agent-import-",".md",directory);
        try {
            try(OutputStream out=new FileOutputStream(file)){out.write("\uFEFF我喜欢读书，也喜欢雨天。".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(context,context.getPackageName()+".fileprovider",file);
            try(ActivityScenario<AgentActivity> scenario=open()){
                idle(scenario);scenario.onActivity(a->{try{
                    show(a,L10n.s(R.string.ui_217)); // Its asynchronous profile read is deliberately still running.
                    Field destination=AgentActivity.class.getDeclaredField("importPage");destination.setAccessible(true);destination.set(a,L10n.s(R.string.ui_217));
                    Method read=AgentActivity.class.getDeclaredMethod("readFile",android.net.Uri.class);read.setAccessible(true);read.invoke(a,uri);
                    assertEquals(uri,field(a,"pendingImport"));
                }catch(Exception e){throw new AssertionError(e);}});
                idle(scenario);scenario.onActivity(a->{assertEquals(L10n.s(R.string.ui_217),field(a,"page"));assertEquals(file.getName(),((EditText)field(a,"titleInput")).getText().toString());assertEquals("我喜欢读书，也喜欢雨天。",((EditText)field(a,"materialInput")).getText().toString());});
                scenario.recreate();idle(scenario);scenario.onActivity(a->assertEquals("我喜欢读书，也喜欢雨天。",((EditText)field(a,"materialInput")).getText().toString()));
            }
            try(OutputStream out=new FileOutputStream(file)){out.write(new byte[]{(byte)0xc3,0x28});}
            try{AgentActivity.readTextFile(context,uri,L10n.s(R.string.ui_217));fail("Invalid UTF-8 must be rejected");}catch(IOException expected){assertTrue(expected.getMessage().contains("UTF-8"));}
        }finally{file.delete();}
    }
    @Test public void failedSendRestoresItsTextAndCompletedReplyKeepsNextDraft() throws Exception {
        try(ActivityScenario<AgentActivity> scenario=open()){
            idle(scenario);scenario.onActivity(a->{try{
                Field pending=AgentActivity.class.getDeclaredField("pendingMessage");pending.setAccessible(true);pending.set(a,"上一条发送的文字");
                ((EditText)field(a,"question")).setText("");
                ((AgentJobModel)field(a,"jobs")).state.setValue(new AgentJobModel.State("failed-test",L10n.s(R.string.ui_250),L10n.s(R.string.ui_209),false,null,"测试连接失败"));
                assertEquals("上一条发送的文字",((EditText)field(a,"question")).getText().toString());
            }catch(Exception e){throw new AssertionError(e);}});
            idle(scenario);scenario.onActivity(a->{try{
                ((EditText)field(a,"question")).setText("等待回复时写的下一条");
                org.json.JSONObject reply=new org.json.JSONObject().put("sessionId",field(a,"session")).put("answer","模拟完成状态");
                ((AgentJobModel)field(a,"jobs")).state.setValue(new AgentJobModel.State("completed-test",L10n.s(R.string.ui_250),L10n.s(R.string.ui_209),false,reply,null));
                assertEquals("等待回复时写的下一条",((EditText)field(a,"question")).getText().toString());
            }catch(Exception e){throw new AssertionError(e);}});
            idle(scenario);
        }
    }
    @Test public void bubblesAlignToDifferentSidesAndTextOnlyPreviewRenders() throws Exception {
        try(ActivityScenario<AgentActivity> scenario=open()){
            idle(scenario);scenario.onActivity(a->{try{
                LinearLayout turns=(LinearLayout)field(a,"turns");turns.removeAllViews();
                Method bubble=AgentActivity.class.getDeclaredMethod("chatBubble",LinearLayout.class,boolean.class,String.class);bubble.setAccessible(true);
                LinearLayout mine=(LinearLayout)bubble.invoke(a,turns,true,"为什么我会想重看《花样年华》？");
                LinearLayout reply=(LinearLayout)bubble.invoke(a,turns,false,"你在记录里写过，喜欢它克制的表达和慢下来的节奏。也许你想再感受一次那种氛围；更具体的原因，还没有留下记录。[1]");
                assertEquals(Gravity.END,mine.getGravity()&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);assertEquals(Gravity.START,reply.getGravity()&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);
                assertEquals("我",((TextView)mine.getChildAt(1)).getText().toString());assertEquals(L10n.s(R.string.ui_254),((TextView)reply.getChildAt(0)).getText().toString());
                assertEquals(3,((LinearLayout)field(a,"composer")).getChildCount());
                ((TextView)field(a,"subtitle")).setText("文字对话 · 界面预览");
            }catch(Exception e){throw new AssertionError(e);}});
            java.util.concurrent.CountDownLatch rendered=new java.util.concurrent.CountDownLatch(1);
            scenario.onActivity(a->((View)field(a,"root")).postOnAnimation(()->((View)field(a,"root")).postOnAnimation(rendered::countDown)));
            assertTrue(rendered.await(3,java.util.concurrent.TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{View dock=(View)field(a,"dock"),root=(View)field(a,"root");
                float fraction=dock.getHeight()/(float)root.getHeight();
                assertTrue("Bottom controls should occupy roughly 1/8 to 1/7 of the screen: "+fraction,fraction>=.11f&&fraction<=.16f);
            });
            Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screenshot);
            File output=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"chat-layout-preview.png");try(OutputStream out=new FileOutputStream(output)){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
        }
    }
}
