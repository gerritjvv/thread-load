(ns thread-load.disruptor
  (:import
    [java.util.concurrent Executors ExecutorService]
    [java.util.concurrent.atomic AtomicReference]
    [com.lmax.disruptor EventFactory EventHandler RingBuffer]
    [com.lmax.disruptor.dsl Disruptor ProducerType ProducerType]))


(defn- ^EventFactory create-event-factory []
  (reify EventFactory
    (newInstance [this]
      (AtomicReference.))))


(defn- ^EventHandler event-handler [f]
  (reify EventHandler
    (onEvent [this evt seq endofbatch]
      (f (.get ^AtomicReference evt)))))


(defn- set-atomic [^AtomicReference r v]
  (.set r v))

(defn publish! [{:keys [ring-buffer]} data]
  (let [seq (.next ^RingBuffer ring-buffer)]
    (try
      (-> ^RingBuffer ring-buffer (.get seq) (set-atomic data))
      (finally
        (.publish ^RingBuffer ring-buffer seq)))))

(defn- get-producer-type [type]
  (cond
    (= type :multi) (ProducerType/MULTI)
    (= type :single) (ProducerType/SINGLE)
    :else
    (throw (RuntimeException. (str "Producer type must be :single or :multi but got " type)))))

(defn create-disruptor [threads event-f & {:keys [buffer-size producer-type] :or {buffer-size 4096 producer-type :multi}}]
  (let [^ExecutorService service (Executors/newFixedThreadPool threads)
        ^EventFactory event-factory (create-event-factory)
        ^Disruptor disruptor (doto
                                 (Disruptor. event-factory buffer-size service)
                                 (.handleEventsWith (into-array [(event-handler event-f)]))
                                 .start)]
    {:exec-service service :ring-buffer (.getRingBuffer disruptor) :disruptor disruptor}))


(defn shutdown-pool [{:keys [disruptor]}]
  (.shutdown disruptor))