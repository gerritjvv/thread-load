(ns thread-load.api-test
  (:require [thread-load.api :as api])
  (:use clojure.test))



(defn helper-test-factory-load [t]
  "Test that the send factory works as expected"
  (let [v (promise)
        f (api/thread-load-factory t [(fn [_ msg] (deliver v msg))] {})]
    (f :a)
    (let [v2 (deref v 10000 nil)]
      (is v2 :a))
    (f)))

(defn helper-test-factory-load-triplets-state [t]
  "Test that the factory with triplets works as expected and state is kept"
  (let [v (promise)
        f (api/thread-load-factory t [
                                      [(fn [& args]  (prn "call my state ") :mystate) ;init function
                                       (fn [state _] (prn "call f state " state) (deliver v state)) ;call function
                                       (fn [& args])        ;stop function
                                       ]
                                      ] {})]
    (f :a)
    (let [v2 (deref v 10000 nil)]
      (is v2 :mystate))))

(defn test-all-properties [t]
  (helper-test-factory-load t)
  (helper-test-factory-load-triplets-state t))

(deftest test-load-factory
  (test-all-properties :load))


(deftest test-disruptor-factory
  (test-all-properties :disruptor))