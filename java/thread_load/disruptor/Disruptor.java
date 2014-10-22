package thread_load.disruptor;

import clojure.lang.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Java support for thread-load.disruptor
 */
public class Disruptor {

    static {
        RT.var("clojure.core", "require").invoke(Symbol.create("thread-load.disruptor"));
    }

    private Var publishVar = RT.var("thread-load.disruptor", "publish!");

    private final Object disruptor;

    private Disruptor(Object disruptor){
        this.disruptor = disruptor;
    }

    public void publish(Object v){
        publishVar.invoke(disruptor, v);
    }

    public void shutdown(){
        RT.var("thread-load.disruptor", "shutdown-pool").invoke(disruptor);
    }

    public static Disruptor create(IFn f){
        return create(new DisruptorConf(), f);
    }

    public static Disruptor create(DisruptorConf conf, IFn f){
        IPersistentVector v =
                PersistentVector.EMPTY.cons(f);

        ISeq s = conf.getConf().seq();
        if(s != null){
            SeqIterator it = new SeqIterator(s);
            while(it.hasNext()) {
                MapEntry entry = (MapEntry) it.next();
                v = v.cons(entry.getKey()).cons(entry.getValue());
            }
        }

       return new Disruptor(RT.var("thread-load.disruptor", "create-disruptor").applyTo(v.seq()));
    }

    public static class DisruptorConf{
        public enum PRODUCER_TYPE {MULTI, SINGLE};
        public enum WAIT_STRATEGY {YIELD, BLOCK, SPIN};

        private static final Keyword KW_BUFFER_SIZE = Keyword.intern("buffer-size");
        private static final Keyword KW_PRODUCER_TYPE = Keyword.intern("producer-type");
        private static final Keyword KW_WAIT_STRATEGY = Keyword.intern("wait-strategy");
        private static final Keyword KW_EXEC_SERVICE = Keyword.intern("executor-service");

        IPersistentMap conf = PersistentArrayMap.EMPTY;

        public void setBufferSize(int bufferSize){
            conf = conf.assoc(KW_BUFFER_SIZE, bufferSize);
        }
        public void setProducerType(PRODUCER_TYPE type){
            if(type == PRODUCER_TYPE.MULTI)
                conf = conf.assoc(KW_PRODUCER_TYPE, Keyword.intern("multi"));
            else
                conf = conf.assoc(KW_PRODUCER_TYPE, Keyword.intern("single"));
        }
        public void setWaitStrategy(WAIT_STRATEGY s){
            if(s == WAIT_STRATEGY.BLOCK)
                conf = conf.assoc(KW_WAIT_STRATEGY, Keyword.intern("block"));
            else if(s == WAIT_STRATEGY.SPIN)
                conf = conf.assoc(KW_WAIT_STRATEGY, Keyword.intern("spin"));
            else
                conf = conf.assoc(KW_WAIT_STRATEGY, Keyword.intern("yield"));

        }

        public void setExecutorService(ExecutorService service){
            conf = conf.assoc(KW_EXEC_SERVICE, service);
        }

        public IPersistentMap getConf(){
            return conf;
        }
    }

    public static void test(){

        DisruptorConf conf = new DisruptorConf();
        conf.setExecutorService(Executors.newCachedThreadPool());
        conf.setProducerType(DisruptorConf.PRODUCER_TYPE.SINGLE);
        conf.setWaitStrategy(DisruptorConf.WAIT_STRATEGY.SPIN);

        Disruptor disruptor = Disruptor.create(conf,
           new AFn() {
            @Override
            public Object invoke(Object v) {
                System.out.println("Val1: " + v);
                return null;
            }
        });

        for(int i = 0; i < 10; i++)
            disruptor.publish(i);

        disruptor.shutdown();
    }
}
