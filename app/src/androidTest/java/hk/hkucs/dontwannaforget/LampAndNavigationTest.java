package hk.hkucs.dontwannaforget;

import android.content.*;
import android.graphics.*;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LampAndNavigationTest {
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Bitmap render(View view){Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));return image;}
    private static double luminance(Bitmap image){double sum=0;int count=0;for(int y=image.getHeight()/3;y<image.getHeight()*2/3;y+=4)for(int x=image.getWidth()/4;x<image.getWidth()*3/4;x+=4){int c=image.getPixel(x,y);sum+=(Color.red(c)+Color.green(c)+Color.blue(c))/3.0;count++;}return sum/count;}
    private static void save(Bitmap image,String filename)throws Exception{Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),filename))){image.compress(Bitmap.CompressFormat.PNG,100,out);}}
    @Test public void lampOffWeakAndStrongHaveVisibleBrightnessDifferences()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ActivityScenario<TypewriterActivity> scenario=ActivityScenario.launch(new Intent(context,TypewriterActivity.class))){
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(300);
            AtomicReference<Bitmap> preview=new AtomicReference<>();
            scenario.onActivity(a->{View scene=(View)field(a,"sceneView");assertTrue(scene.getHeight()>0);
                set(a,"lampOn",false);Bitmap off=render(scene);
                set(a,"lampOn",true);set(a,"lampIntensity",.15f);Bitmap weak=render(scene);
                set(a,"lampIntensity",1f);Bitmap strong=render(scene);
                assertTrue("Lamp must brighten the paper",luminance(weak)-luminance(off)>8);
                assertTrue("Intensity must be visible",luminance(strong)-luminance(weak)>15);
                off.recycle();weak.recycle();strong.recycle();set(a,"lampIntensity",.65f);preview.set(render(scene));
            });save(preview.get(),"typewriter-light-preview.png");preview.get().recycle();
        }
    }
    @Test public void moduleListExtendsBehindThreeFloatingActions()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context,MainActivity.class))){
            scenario.onActivity(a->{try{Method m=MainActivity.class.getDeclaredMethod("openModule",int.class);m.setAccessible(true);m.invoke(a,Record.BOOK);}catch(Exception e){throw new AssertionError(e);}});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(300);
            scenario.onActivity(a->{FrostedNavigation nav=a.findViewById(R.id.bottom_navigation);View list=a.findViewById(R.id.list);
                assertEquals(3,nav.getChildCount());assertEquals(View.VISIBLE,nav.getVisibility());
                int[] navPosition=new int[2],listPosition=new int[2];nav.getLocationOnScreen(navPosition);list.getLocationOnScreen(listPosition);
                assertTrue(listPosition[1]+list.getHeight()>navPosition[1]+nav.getHeight());
                Bitmap image=render(a.findViewById(R.id.main));image.recycle(); // Checks sibling backdrop capture while drawing the page.
                list.scrollBy(0,120);int[] afterScroll=new int[2];nav.getLocationOnScreen(afterScroll);assertEquals(navPosition[1],afterScroll[1]);
                Bitmap navigation=render(nav);
                try{save(navigation,"frosted-navigation-preview.png");}catch(Exception e){throw new AssertionError(e);}finally{navigation.recycle();}
                a.findViewById(R.id.nav_home).performClick();assertEquals(View.GONE,nav.getVisibility());
            });
        }
    }
}
