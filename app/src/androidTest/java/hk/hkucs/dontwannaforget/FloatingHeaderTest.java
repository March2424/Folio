package hk.hkucs.dontwannaforget;

import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Checks the actual scroll behavior of the six editors without saving any records. */
@RunWith(AndroidJUnit4.class)
public class FloatingHeaderTest {
    @Test public void editorNavigationStaysOverScrollingContent() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Class<?>[] pages={BookEditActivity.class,TicketEditActivity.class,MusicEditActivity.class,FootprintEditActivity.class,VoiceEditActivity.class,MemoryEditActivity.class};
        for(Class<?> page:pages){
            try(ActivityScenario<?> scenario=ActivityScenario.launch(new Intent(context,page))){
                AtomicReference<View> header=new AtomicReference<>();
                for(int i=0;i<100;i++){
                    scenario.onActivity(a->header.set(a.findViewById(android.R.id.content).findViewWithTag("floating-header")));
                    if(header.get()!=null&&header.get().getHeight()>0)break;
                    Thread.sleep(20);
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                int[] before=new int[2];
                scenario.onActivity(a->{
                    View h=header.get();assertNotNull(page.getSimpleName(),h);assertNull(h.getBackground());
                    assertTrue(h.getAlpha()<1f);h.getLocationOnScreen(before);
                    FrameLayout layer=(FrameLayout)h.getParent();ScrollView scroll=(ScrollView)layer.getChildAt(0);
                    assertTrue(scroll.getHeight()>h.getHeight());scroll.scrollTo(0,240);
                    assertTrue(page.getSimpleName()+" should scroll",scroll.getScrollY()>0);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a->{int[] after=new int[2];header.get().getLocationOnScreen(after);assertEquals(before[1],after[1]);});
                if(page==BookEditActivity.class){
                    Thread.sleep(150);
                    android.graphics.Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                    try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"floating-header-preview.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
                }
            }
        }
    }
}
