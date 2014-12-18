(ns thread-load.core
  (:require [clojure.tools.logging :refer [error]])
  (:import
    [java.util ArrayList]
    [java.util.concurrent BlockingQueue ArrayBlockingQueue Executors ExecutorService TimeUnit ThreadPoolExecutor]
    (thread_load.blocking BlockingExecutor)))

(declare exec-on-bulk-data)


(defn
  ^ThreadPoolExecutor
  bounded-executor
  "Create a Executor Pool that blocks when the queue is full"
  [& {:keys [pool-size queue-size] :or {pool-size 10 queue-size 10}}]
  (prn "pool-size: " pool-size " " queue-size)
  (BlockingExecutor. (int pool-size) (int queue-size)))

(defn executor-submit
  "Submits the function f to the ExceutorService, blocks only if a bounded-executor is used"
  [^ExecutorService exec ^Runnable f]
  (.submit exec f))

(defn call-f 
  "Calls f as (f state data) if an exception is thrown a :status :fail :throwble t:Throwable is added to the state."
  ([f state]
    (call-f f state nil))
  ([f state data]
    (call-f f state data false))
  ([f state data terminate?]
  (try
    (f state data)
    (catch Throwable t (assoc state :status (if terminate? :terminate :fail) :throwable t)))))

(defn get-queue-data! 
  "Blocks till data is available on the queue and returns the data, queue must be BlockingQueue"
  [^BlockingQueue queue]
  (.take queue))

(defn call-on-fail 
  "Only calls init if the return of stop is not :terminate or :fail"
  [init stop state]
  (let [new-state (call-f stop state nil true) ;if stop throws an exception we need to terminate
        status (:status new-state)]
    (if (not (or (= status :terminate) (= status :fail)))
      (call-f init new-state)
      new-state)))

(defn exec-on-data 
  "Waits for data and calls exec with the data, returning the state"
  [queue exec state _]
  (call-f exec state (get-queue-data! queue)))

(defn worker-runner! 
  "This function only returns if any of the init, exec, stop functions return status :terminate in the state map"
  [exec-delegate queue init exec stop n]
   (loop [state (call-f init {})] 
     (condp = (:status state)
       :fail  (recur (call-on-fail init stop state));will only call init if stop does not fail or temrinate
       :terminate state
       (recur (exec-delegate queue exec state n)))))
      
(defn create-pool 
  "Entry point to the API and creates a pool from which consumers can be added
   Optional keys are :queue-limit = the default is 100 and is the number items a queue can fill before it blocks,
                     :thread-pool = default is a cached thread pool, and is the thread pool in which consumers will run"
  [& {:keys [queue-limit thread-pool] :or {queue-limit 100 thread-pool (Executors/newCachedThreadPool)}}]
  {:queue (ArrayBlockingQueue. queue-limit)
   :thread-pool thread-pool
   :limit queue-limit
   })


(defn add-consumer 
  "Adds a consumer function tri to the thread pool, which will take data of the queue and run exec on the data
   Returns the pool
   If :bulk is specified the worker thread will use (get-bulk!) and send a collection to the function"
  [pool init exec stop & {:keys [bulk] :or {bulk 1}}]
  (let [^ExecutorService thread-pool (:thread-pool pool)
        f #(let [state (worker-runner! (if (> bulk 1) exec-on-bulk-data exec-on-data) (:queue pool) init exec stop bulk)]
            (error "Exit work pool consumer: " state))
        ^Runnable r-f (fn r-f []
                        (try
                          (f)
                          (catch InterruptedException e nil);ignore interrupted exceptions and exit
                          (catch Throwable t
                            (do
                              (.printStackTrace t)))))]
    (.submit thread-pool r-f)
    pool))

(defn publish! 
  "Will block if the queue is full otherwise will send data to the queue which will be processed,
   by one of the consumer functions
   Returns the pool"
  [pool data]
   (.put ^BlockingQueue (:queue pool) data)
   pool)

(defn bulk-get!
  "Returns a collection of at least n,
   If no data is available in the queue a blocking operation is performed
   Note the return value is of type Collection."
  [{:keys [^BlockingQueue queue]} n]
  (let [arr (ArrayList. (int n))
        n2 (.drainTo queue arr)]
    (if (zero? n2)
      [(get-queue-data! queue)]
      arr)))


(defn exec-on-bulk-data
  "Waits for data and calls exec with the data, returning the state"
  [queue exec state n]
  (call-f exec state (bulk-get! {:queue queue} n)))

(defn bulk-single-producer-publish!
  "Important: This function is not threadsafe and should only be called from a single producer
   The reason is that the .size of the queue is checked, and if enough space .addAll is called,
   otherwise .put is used"
  [{:keys [^BlockingQueue queue limit]} data-coll]
  (if (> (.size queue) limit)
    (.addAll queue data-coll)
    (doseq [data data-coll]
      (.put queue data))))

(defn shutdown-pool 
  "Shutdown the ExecutorService used and wait for termination timeout milliseconds, if the threads did not all terminate, the pool is shutdown forcefully"
  [{:keys [^ExecutorService thread-pool]} timeout]
  (.shutdown thread-pool)
  (if (not (.awaitTermination thread-pool (long timeout) TimeUnit/MILLISECONDS))
    (.shutdownNow thread-pool)))

  
