(ns thread-load.core
  (:require [clojure.tools.logging :refer [error]])
  (:import [java.util.concurrent BlockingQueue ArrayBlockingQueue Executors ExecutorService TimeUnit]))


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
  "Only calls init if the return of stop is not :terminate of :fail"
  [init stop state]
  (let [new-state (call-f stop state nil true) ;if stop throws an exception we need to terminate
        status (:status new-state)]
    (if (not (or (= status :terminate) (= status :fail)))
      (call-f init new-state)
      new-state)))

(defn exec-on-data 
  "Waits for data and calls exec with the data, returning the state"
  [queue exec state]
  (call-f exec state (get-queue-data! queue)))

(defn worker-runner! 
  "This function only returns if any of the init, exec, stop functions return status :terminate in the state map"
  [queue init exec stop]
   (loop [state (call-f init {})] 
     (condp = (:status state)
       :fail  (recur (call-on-fail init stop state));will only call init if stop does not fail or temrinate
       :terminate state
       (recur (exec-on-data queue exec state)))))
      
(defn create-pool 
  "Entry point to the API and creates a pool from which consumers can be added
   Optional keys are :queue-limit = the default is 100 and is the number items a queue can fill before it blocks,
                     :thread-pool = default is a cached thread pool, and is the thread pool in which consumers will run"
  [& {:keys [queue-limit thread-pool] :or {queue-limit 100 thread-pool (Executors/newCachedThreadPool)}}]
  {:queue (ArrayBlockingQueue. queue-limit)
   :thread-pool thread-pool
   })

(defn add-consumer 
  "Adds a consumer function tri to the thread pool, which will take data of the queue and run exec on the data
   Returns the pool"
  [pool init exec stop]
  (let [^ExecutorService thread-pool (:thread-pool pool)
        ^Runnable r-f (fn []
                        (try
                          (worker-runner! (:queue pool) init exec stop)
                          (catch Throwable t (prn t))))]
    (.submit thread-pool r-f)
    pool))

(defn publish! 
  "Will block if the queue is full otherwise will send data to the queue which will be processed,
   by one of the consumer functions
   Returns the pool"
  [pool data]
   (.put ^BlockingQueue (:queue pool) data)
   pool)

(defn shutdown-pool 
  "Shutdown the ExecutorService used and wait for termination timeout milliseconds, if the threads did not all terminate, the pool is shutdown forcefully"
  [{:keys [^ExecutorService thread-pool]} timeout]
  (.shutdown thread-pool)
  (if (not (.awaitTermination thread-pool (long timeout) TimeUnit/MILLISECONDS))
    (.shutdownNow thread-pool)))

  
