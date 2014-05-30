(ns thread-load.core
  
  (:import [java.util.concurrent BlockingQueue ArrayBlockingQueue Executors ExecutorService]))


(defn call-f 
  "Calls f as (f state data) if an exception is thrown a :status :fail :throwble t:Throwable is added to the state."
  ([f state]
    (call-f f state nil))
  ([f state data]
  (try
    (f state data)
    (catch Throwable t (assoc state :status :fail :throwable t)))))

(defn get-queue-data! 
  "Blocks till data is available on the queue and returns the data, queue must be BlockingQueue"
  [^BlockingQueue queue]
  (.take queue))

(defn call-on-fail 
  "Only calls init if the return of stop is not :terminate of :fail"
  [init stop state]
  (let [new-state (call-f stop state)
        status (:status new-state)]
    (if (not (or (= status :terminate) (= status :fail)))
      (call-f init new-state)
      new-state)))

(defn exec-on-data 
  "Waits for data and calls exec with the data, returning the state"
  [queue exec state]
  (call-f exec state (get-queue-data! queue)))

(defn woker-runner! 
  "This function only returns if any of the init, exec, stop functions return status :terminate in the state map"
  [queue init exec stop]
   (loop [state (call-f init {})] 
     (condp = (:status state)
       :fail  (recur (call-on-fail init stop state));will only call init if stop does not fail or temrinate
       :terminate state
       (recur (exec-on-data queue exec state)))))
      
(defn create-pool [& {:keys [queue-limit] :or {queue-limit 100}}]
  {:queue (ArrayBlockingQueue. queue-limit)
   :thread-pool  (Executors/newCachedThreadPool)
   })

(defn add-consumer 
  "Adds a consumer function tri to the thread pool, which will take data of the queue and run exec on the data"
  [pool init exec stop]
  (let [^ExecutorService thread-pool (:thread-pool pool)
        ^Runnable r-f woker-runner!]
    (.submit thread-pool r-f)))

(defn publish! 
  "Will block if the queue is full otherwise will send data to the queue which will be processed,
   by one of the consumer functions"
  [pool data]
   (.put ^BlockingQueue (:queue pool) data))
