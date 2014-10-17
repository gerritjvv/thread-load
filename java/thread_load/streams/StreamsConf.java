package thread_load.streams;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;

/**
 * stream-read-threads 2
 data-threads 4
 streams-limit 5000 data-limit 100
 */
public class StreamsConf {

    private static final Keyword KW_STREAM_READ_THREADS = Keyword.intern("stream-read-threads");
    private static final Keyword KW_DATA_THREADS = Keyword.intern("data-threads");
    private static final Keyword KW_STREAMS_LIMIT = Keyword.intern("streams-limit");
    private static final Keyword KW_DATA_LIMIT = Keyword.intern("data-limit");

    IPersistentMap conf = PersistentArrayMap.EMPTY;

    public void setStreamReadThreads(int n){
        conf = conf.assoc(KW_STREAM_READ_THREADS, n);
    }

    public void setDataThreads(int n){
        conf = conf.assoc(KW_DATA_THREADS, n);
    }

    public void setStreamsLimit(int n){
        conf = conf.assoc(KW_STREAMS_LIMIT, n);
    }

    public void setDataLimit(int n){
        conf = conf.assoc(KW_DATA_LIMIT, n);
    }

    public IPersistentMap getConf(){return conf;}
}
