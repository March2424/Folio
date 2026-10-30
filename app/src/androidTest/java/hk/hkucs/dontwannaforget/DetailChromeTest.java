package hk.hkucs.dontwannaforget;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Read-only navigation through existing sample records, without saving or editing them. */
@RunWith(AndroidJUnit4.class)
public class DetailChromeTest {
    @Test public void allDetailsHaveDarkSurroundingsAndLeftBackRightActions()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ActivityScenario<MainActivity> ignored=ActivityScenario.launch(new Intent(context,MainActivity.class))){}
        CountDownLatch loaded=new CountDownLatch(1);Map<Integer,Record> examples=new HashMap<>();
        new Repo(context).getActiveOnce(records->{for(Record r:records)if(r.id.startsWith(Record.SAMPLE_PREFIX))examples.putIfAbsent(r.type,r);loaded.countDown();});
        assertTrue(loaded.await(5,TimeUnit.SECONDS));
        for(int type=0;type<=Record.LETTER;type++){
            Record record=examples.get(type);assertNotNull("Sample record for module "+type,record);
            Class<?> page=type==Record.LETTER?LetterActivity.class:DetailActivity.class;
            try(ActivityScenario<?> scenario=ActivityScenario.launch(new Intent(context,page).putExtra("id",record.id))){
                for(int i=0;i<150;i++){
                    java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
                    scenario.onActivity(a->ready.set(a.findViewById(android.R.id.content).findViewWithTag("floating-header")!=null));
                    if(ready.get())break;Thread.sleep(20);
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a->{
                    ViewGroup content=a.findViewById(android.R.id.content);
                    LinearLayout row=content.findViewWithTag("floating-header");assertNotNull(row);assertNull(row.getBackground());
                    assertTrue(row.getChildAt(0) instanceof TextView);
                    assertEquals(L10n.s(R.string.ui_060),((TextView)row.getChildAt(0)).getText().toString());
                    assertTrue(row.getChildAt(0).isClickable());assertTrue(row.getChildCount()>=3);
                    assertTrue(row.getChildAt(row.getChildCount()-1).isClickable());
                    View backdrop=page==LetterActivity.class?content.getChildAt(0):findScroll(content);
                    assertNotNull(backdrop);assertTrue(backdrop.getBackground() instanceof ColorDrawable);
                    assertEquals(FloatingHeader.DETAIL_BACKGROUND,((ColorDrawable)backdrop.getBackground()).getColor());
                    if(record.type==Record.VOICE){
                        assertEquals(L10n.s(R.string.ui_071),((TextView)row.getChildAt(2)).getText().toString());
                        assertEquals(L10n.s(R.string.ui_072),((TextView)row.getChildAt(3)).getText().toString());
                    }
                });
                if(type==Record.FOOTPRINT){
                    Thread.sleep(120);Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                    try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"dark-detail-preview.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
                }
            }
        }
    }
    private static ScrollView findScroll(View view){
        if(view instanceof ScrollView)return (ScrollView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){ScrollView result=findScroll(group.getChildAt(i));if(result!=null)return result;}}
        return null;
    }
}
