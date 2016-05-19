(ns
  ^{:doc "Factory methods to create different thread load implementations
          The model is simple:
                 The factory returns a function f [msg & args], submit is (f msg) or (apply f msg options), and close is (f)
                 When called (f msg) or (apply f msg args) the return value is always the message sent
                 When called (f) if the shutdown was successfull a non nil value is returned

          The thread-load-factory function takes 3 parameters,
                          t for type :load default star thread consumptions, see thread-load.core,
                          handlers a sequence of handler functions (a single function [f f2 fN] or [[init exec stop] [init exec stop]]),
                          conf a configuration map

                          The exec/handler function should have arity two [state msg] state is what the function returns and if no init function
                          will be nil

           How to write a factory implementation
             return a function of structure
               (fn
                 ([msg]
                   ;always return msg
                   msg)
                  ([k msg & args] ;the k is used to support keyed messages
                   ;always return msg
                   msg))
                  ([] doclose-here and return a none nil value if successfull))

              Or use Proxy, to also implement the ILoadMonitorable
              (proxy
                      [AFn ILoadMonitorable] []
                      (invoke
                        ([] (disruptor/shutdown-pool dis) :true)
                        ([msg] (disruptor/publish! dis msg) msg)
                        ([k msg & args] (disruptor/publish! dis msg) msg))
                      (size [] 1)
                      (stats [] dis))
              To wrap handlers where the factory implementation does not treat state etc
              use the _compose-handlers function, this returns a single function (fn [msg]) that keeps
              the function state in an atom


     usage
      (def f (api/thread-load-factory :load [(fn [state msg] (prn \" state \" state \" msg \" msg) 1)] {}))
      (f :a)
      ;; state nil  msg  :a
      ;; state  1  msg  :a
      (f) ;shutdown"}
  thread-load.api
  (:require [thread-load.core :as core]
            [thread-load.disruptor :as disruptor])
  (:import (clojure.lang AFn)
           (java.util.concurrent ArrayBlockingQueue)))


(definterface ILoadMonitorable
  (size [] "Returns the number of operations waiting in the backpressure queue if any")
  (stats [] "Returns a statistics map for monitoring and internals"))

(defn _firstarg-f
  "Helper function that always return the first argument"
  [arg & args ] arg)

(defn _state-helper
  "Helper function that uses an atom to keep track of the function state"
  ([f]
   (_state-helper _firstarg-f f _firstarg-f))
  ([init f _]
   (let [state (atom (init nil nil))]
     (fn [msg]
       (swap! state #(f % msg))))))

(defn _triplet-composer
  "Helper function that removes the init and stop function if any"
  ([_ exec _]
   exec)
  ([exec] exec))

(defn _wrap-multiple-calls
  "Helper function that wraps the handlers into state functions each with their own state atom
   Returns a function (f [msg]) which will call each state function with the message"
  [handlers]
  (let [state-fs (map (fn [fs] (apply _state-helper fs)) handlers)]
    (fn [msg]
      (doseq [f state-fs]
        (f msg)))))

(defn _compose-handlers
  "Helper function to compose any handlers when only one is expected"
  [handlers]
  (if (> (count handlers) 1)
    (_wrap-multiple-calls handlers)
    (let [f (first handlers)]
      (if (coll? f)
        (apply _state-helper f)
        (_state-helper f)))))

(defmulti thread-load-factory (fn [t handlers conf] t))

(defn add-consumer [pool handler-f]
  (if (coll? handler-f)
    (let [[init exec stop] handler-f]
      (if (and init exec stop)
        (core/add-consumer pool init exec stop)
        (throw (RuntimeException. (str "Handler functions can only be a single function or a triplet of [init exec stop]")))))
    (core/add-consumer pool _firstarg-f handler-f _firstarg-f)))

(defmethod thread-load-factory :load [_ handlers {:keys [threads] :or {threads 4} :as conf}]
  (let [pool (apply core/create-pool conf)
        handler-f (if (= (count handlers) 1) (first handlers) (_compose-handlers handlers))]
    (dotimes [i threads]
      (add-consumer pool handler-f))

    (proxy
      [AFn ILoadMonitorable] []
      (invoke
        ([] (core/shutdown-pool pool 10000) :true)
        ([msg] (core/publish! pool msg) msg)
        ([_ msg & args] (apply core/publish! pool msg args) msg))
      (size [] (.size ^ArrayBlockingQueue (:queue pool)))
      (stats [] pool))))

(defmethod thread-load-factory :disruptor [_ handlers conf]
  (let [dis (apply disruptor/create-disruptor (_compose-handlers handlers) conf)]
    (proxy
      [AFn ILoadMonitorable] []
      (invoke
        ([] (disruptor/shutdown-pool dis) :true)
        ([msg] (disruptor/publish! dis msg) msg)
        ([_ msg & args] (disruptor/publish! dis msg) msg))
      (size [] 1)
      (stats [] dis))))

(defn size
  "If mon is ILoadMonitorable then the size function is called, otherwise return 0"
  [mon]
  (if (instance? ILoadMonitorable mon)
    (.size ^ILoadMonitorable mon)
    0))

(defn stats
  "If mon is ILoadMonitorable then the stats function is called, otherwise return {}"
  [mon]
  (if (instance? ILoadMonitorable mon)
    (.stats ^ILoadMonitorable mon)
    {}))