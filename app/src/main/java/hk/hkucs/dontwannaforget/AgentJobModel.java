package hk.hkucs.dontwannaforget;

import android.content.Context;
import androidx.lifecycle.*;
import java.util.concurrent.*;

/** Owns work across Activity recreation. Process-death recovery uses AgentTasks in SQLite. */
public final class AgentJobModel extends ViewModel {
    interface Work {Object run(LocalMemoryAgent agent) throws Exception;}
    static final class State {
        final String id,label,page;final boolean running;final Object result;final String error;
        State(String id,String label,String page,boolean running,Object result,String error){this.id=id;this.label=label;this.page=page;this.running=running;this.result=result;this.error=error;}
    }
    final MutableLiveData<State> state=new MutableLiveData<>();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile LocalMemoryAgent active;private volatile boolean closed;
    public AgentJobModel(){}
    void submit(String id,Context context,ModelSettings settings,String label,String page,Work work){
        state.setValue(new State(id,label,page,true,null,null));Context app=context.getApplicationContext();
        executor.execute(()->{
            LocalMemoryAgent agent=new LocalMemoryAgent(app,settings);active=agent;if(closed){agent.cancel();return;}
            try{Object result=work.run(agent);state.postValue(new State(id,label,page,false,result,null));}
            catch(Exception e){state.postValue(new State(id,label,page,false,null,e.getMessage()==null?"操作未完成，可到管理页继续任务":e.getMessage()));}
            finally{active=null;}
        });
    }
    void acknowledge(){state.setValue(null);}
    @Override protected void onCleared(){closed=true;if(active!=null)active.cancel();executor.shutdownNow();}
}
