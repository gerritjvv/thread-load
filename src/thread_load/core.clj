(ns thread-load.core
  (:require
    [fun-utils.queue :as queue]
    [clojure.tools.logging :refer [error debug info]])
  (:import
    [java.util ArrayList]
    [java.util.concurrent Executors ExecutorService TimeUnit ThreadPoolExecutor ArrayBlockingQueue]
    (thread_load.blocking BlockingExecutor)))

(declare exec-on-bulk-data)

(defn
  ^ThreadPoolExecutor
  bounded-executor
  "Create a Executor Pool that blocks when the queue is full"
  [& {:keys [pool-size queue-size] :or {pool-size 10 queue-size 10}}]
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
  [queue]
  (queue/poll! queue Long/MAX_VALUE))

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
     (case (:status state)
       :fail  (recur (call-on-fail init stop state));will only call init if stop does not fail or temrinate
       :terminate state
       (recur (exec-delegate queue exec state n)))))
      
(defn create-pool 
  "Entry point to the API and creates a pool from which consumers can be added
   Optional keys are :queue-limit = the default is 100 and is the number items a queue can fill before it blocks,
                     :thread-pool = default is a cached thread pool, and is the thread pool in which consumers will run
                     :queue-type = :array-queue :spmc-array-queue :mpmc-array-queue
  "
  [& {:keys [queue-limit queue-type thread-pool] :or {queue-limit 100 queue-type :array-queue thread-pool (Executors/newCachedThreadPool)}}]
  {:queue (queue/queue-factory queue-type queue-limit)
   :thread-pool thread-pool
   :limit queue-limit
   :active-threads (atom #{})
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
                        ;report current thread
                        (swap! (:active-threads pool) conj (Thread/currentThread))
                        (try
                          (f)
                          (catch InterruptedException e nil);ignore interrupted exceptions and exit
                          (catch Throwable t
                            (do
                              (error t t)
                              (.printStackTrace t)))))]
    (.submit thread-pool r-f)
    pool))

(defn- reset-consumer-threads [{:keys [active-threads]}]
  (doseq [th @active-threads]
    (info "Interrupting thread " th)
    ;;(.interrupt ^Thread th)
    ))

(defn publish!
  "Will block if the queue is full otherwise will send data to the queue which will be processed,
   by one of the consumer functions
   Returns the pool"
  [pool data]
  (queue/offer! (:queue pool) data)
   pool)

(defn bulk-get!
  "Returns a collection of at least n,
   Note the return value is of type Collection."
  [pool n]
  (let [v (queue/-drain! (:queue pool) n)]
    (if (empty? v)
      (queue/poll! (:queue pool) Long/MAX_VALUE)
      v)))


(defn exec-on-bulk-data
  "Waits for data and calls exec with the data, returning the state"
  [queue exec state n]
  (call-f exec state (bulk-get! {:queue queue} n)))

(defn bulk-single-producer-publish!
  "currently does single publish"
  [{:keys [queue limit] :as state} data-coll]
  (doseq [data data-coll]
    (publish! state data)))

(defn shutdown-pool 
  "Shutdown the ExecutorService used and wait for termination timeout milliseconds, if the threads did not all terminate, the pool is shutdown forcefully"
  [{:keys [^ExecutorService thread-pool]} timeout]
  (.shutdown thread-pool)
  (if (not (.awaitTermination thread-pool (long timeout) TimeUnit/MILLISECONDS))
    (.shutdownNow thread-pool))
  :true)

  
