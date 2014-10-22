(ns thread-load.disruptor
  (:import
    [thread_load Event]
    [java.util.concurrent Executors ExecutorService TimeUnit]
    [java.util.concurrent.atomic AtomicReference]
    [com.lmax.disruptor EventFactory EventHandler RingBuffer YieldingWaitStrategy BlockingWaitStrategy BusySpinWaitStrategy WaitStrategy]
    [com.lmax.disruptor.dsl Disruptor ProducerType ProducerType]))


(defn- ^EventFactory create-event-factory []
  (reify EventFactory
    (newInstance [this]
      (Event.))))


(defn- ^EventHandler event-handler [f]
  (reify EventHandler
    (onEvent [this evt seq endofbatch]
      (f (.getVal ^Event evt)))))


(defn publish! [{:keys [^RingBuffer ring-buffer]} data]
  (let [seq (.next ring-buffer)
        evt (.get ring-buffer seq)]
    (try
      (.setVal ^Event evt data)
      (finally
        (.publish ring-buffer seq)))))

(defn- get-producer-type [type]
  (cond
    (= type :multi) (ProducerType/MULTI)
    (= type :single) (ProducerType/SINGLE)
    :else
    (throw (RuntimeException. (str "Producer type must be :single or :multi but got " type)))))

(defn- ^WaitStrategy get-wait-strategy [type]
  (cond
    (= type :yield) (YieldingWaitStrategy.)
    (= type :block) (BlockingWaitStrategy.)
    (= type :spin)  (BusySpinWaitStrategy.)
    :else
    (throw (RuntimeException. (str "Wait strategy can only be :yield :block :spin but got " type)))))

(defn add-event-handler [{:keys [disruptor]} f]
  (.handleEventsWith ^Disruptor disruptor (into-array [(event-handler f)])))


(defn create-disruptor [event-f & {:keys [buffer-size producer-type wait-strategy executor-service]
                                   :or {buffer-size 1024 producer-type :multi wait-strategy :block executor-service nil}}]
  (let [^ExecutorService service (if executor-service executor-service (Executors/newCachedThreadPool))
        ^EventFactory event-factory (create-event-factory)
        ^Disruptor disruptor (doto
                                 (Disruptor. event-factory buffer-size
                                             service
                                             (get-producer-type producer-type)
                                             (get-wait-strategy wait-strategy))
                                 (.handleEventsWith (into-array [(event-handler event-f)]))
                                 .start)]
    {:exec-service service :ring-buffer (.getRingBuffer disruptor) :disruptor disruptor :exec-service-type (if executor-service :shared :unique) }))


(defn shutdown-pool [{:keys [disruptor exec-service-type exec-service]}]
  (.shutdown ^Disruptor disruptor)
  (when (= exec-service-type :unique)
     (.shutdown ^ExecutorService exec-service)
     (.awaitTermination ^ExecutorService exec-service 2000 TimeUnit/MILLISECONDS)
     (.shutdownNow ^ExecutorService exec-service)))
