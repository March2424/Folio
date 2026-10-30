package hk.hkucs.dontwannaforget;

import android.content.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Uses read-only management UI; the simulated long-running work never opens data or calls a model. */
@RunWith(AndroidJUnit4.class)
public class AgentActivityLifecycleTest {
    @Test public void recreationRetainsRunningJobInsteadOfCancellingOrDuplicatingIt() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();AtomicInteger calls=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        AtomicReference<AgentJobModel> before=new AtomicReference<>();
        try(ActivityScenario<AgentActivity> scenario=ActivityScenario.launch(new Intent(context,AgentActivity.class).putExtra("page","管理"))){
            // Drain the initial read-only management refresh.
            for(int i=0;i<100;i++){AtomicBoolean busy=new AtomicBoolean();scenario.onActivity(activity->busy.set(job(activity).state.getValue()!=null));if(!busy.get())break;Thread.sleep(20);}
            scenario.onActivity(activity->{AgentJobModel model=job(activity);before.set(model);model.submit("lifecycle",context,new ModelSettings(context),"测试任务","管理",agent->{calls.incrementAndGet();entered.countDown();release.await(5,TimeUnit.SECONDS);finished.countDown();return true;});});
            assertTrue(entered.await(5,TimeUnit.SECONDS));scenario.recreate();scenario.onActivity(activity->{assertSame(before.get(),job(activity));assertTrue(job(activity).state.getValue().running);});
            release.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));assertEquals(1,calls.get());
        }finally{release.countDown();}
    }
    private static AgentJobModel job(AgentActivity activity){try{Field field=AgentActivity.class.getDeclaredField("jobs");field.setAccessible(true);return (AgentJobModel)field.get(activity);}catch(Exception e){throw new AssertionError(e);}}
}
