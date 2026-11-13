package dev.muuko.honorfreeclip2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class TransportTaskRunnerSelfTest {
    static void check(boolean ok,String label) {
        if(!ok)throw new AssertionError(label);
        System.out.println("PASS "+label);
    }
    public static void main(String[] args) throws Exception {
        TransportTaskRunner runner=new TransportTaskRunner(500,1);
        try {
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),failed=new CountDownLatch(3);
            AtomicInteger failures=new AtomicInteger(),abort=new AtomicInteger(),staleRuns=new AtomicInteger();
            Runnable fail=()->{failures.incrementAndGet();failed.countDown();};
            runner.execute(()->{
                entered.countDown();
                try { release.await(3,TimeUnit.SECONDS); } catch(InterruptedException ignored) {}
            },fail,abort::incrementAndGet);
            check(entered.await(1,TimeUnit.SECONDS),"serial task started");
            runner.execute(staleRuns::incrementAndGet,fail,abort::incrementAndGet);
            runner.execute(staleRuns::incrementAndGet,fail,abort::incrementAndGet);
            check(failed.await(2,TimeUnit.SECONDS),"blocked, queued and rejected tasks report failure");
            check(failures.get()==3 && staleRuns.get()==0,"queued mutations expired without execution");
            release.countDown();
            CountDownLatch recovered=new CountDownLatch(1);
            runner.execute(recovered::countDown,()->{throw new AssertionError("recovery failed");},()->{});
            check(recovered.await(1,TimeUnit.SECONDS),"worker recovered after blocked I/O was released");
            CountDownLatch exceptional=new CountDownLatch(1);
            runner.execute(()->{throw new IllegalStateException();},()->{
                exceptional.countDown();throw new IllegalStateException("consumer failure");
            },()->{});
            check(exceptional.await(1,TimeUnit.SECONDS),"task and callback exceptions isolated");
            check(abort.get()>=1,"deadline requests socket abort");
        } finally { runner.shutdown(); }
    }
}
