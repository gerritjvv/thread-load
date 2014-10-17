package thread_load.streams;

import clojure.lang.*;

/**
 * See the thread-load.streams namespace
 */
public class StreamsProcessor {

    static {
        RT.var("clojure.core", "require").invoke(Symbol.create("thread-load.streams"));
    }


    final Object processor;

    private StreamsProcessor(Object processor){
        this.processor = processor;
    }

    /**
     * <pre>
     * streams-load-f (f) -> initial-state, f [state] -> [state stream]
     * streams-read-f (f) -> initial-state, f [state stream] -> [state data]
     * process-f      (f) -> initial-state, f [state data] -> state
     * </pre>
     *<p/>
     *In the example below 1000 infinte streams of random integers are created (note this could be Http/TCP Streams, File I/O etc).<br/>
     *The  functions do:<br/>
     * streams-load-f gets a stream, if no new streams it blocks infinitely.<br/>
     * streams-read-f reads a single data item from the stream (for batching this function could read multiple values).<br/>
     * process-f processes a single data point (or if you know the streams-read-f returns multiple values you can process them here).<br/>
     */
    public static final StreamsProcessor createProcessor(IFn streams_load_f, IFn streams_read_f, IFn process_f, StreamsConf conf){

        IPersistentVector v =
                PersistentVector.EMPTY.cons(streams_load_f)
                .cons(streams_read_f)
                .cons(process_f);

        ISeq s = conf.getConf().seq();
        if(s != null){
            SeqIterator it = new SeqIterator(s);
            while(it.hasNext())
                v = v.cons(it.next());
        }


        return new StreamsProcessor(
                RT.var("thread-load.streams", "create-stream-processor").applyTo(v.seq())
        );
    }

    public void shutdown(long timeout){
        RT.var("thread-load.streams", "shutdown!").applyTo(PersistentVector.EMPTY.cons(processor).cons(Keyword.intern("timeout-ms")).cons(timeout).seq());
    }
}
