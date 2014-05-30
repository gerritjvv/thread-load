(ns thread-load.core-test
  (:require 
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [clojure.test.check.clojure-test :refer [defspec]]
    [thread-load.core :refer :all])
  
  (:import [java.util.concurrent ArrayBlockingQueue]))


(defspec call-f-should-return-fail-on-exception
  10
  (prop/for-all [a gen/nat]
    (let [state (call-f (fn [& _] (throw (RuntimeException.))) {} nil)]
      (= (:status state) :fail))))


(defspec worker-runner-should-call-init-and-terminate
  10
  (prop/for-all [a gen/nat]
    (let [state (woker-runner! nil (fn [& _] {:called true :status :terminate}) (fn [&_]) (fn [&_]))]
      (:called state))))



(defspec worker-runner-should-call-init-stop-and-terminate
  10
  (prop/for-all [a gen/nat]
    (let [state (woker-runner! nil 
                  (fn [& _] {:called [:init] :status :fail}) (fn [&_]) 
                  (fn [{:keys [called status]} data]  
                    {:called (conj called :stop) :status :terminate}))]
        (= [:init :stop] (:called state)))))\
                                             




(defspec worker-runner-should-call-init-exec-stop-and-terminate
  10
  (prop/for-all [a gen/nat]
    (let [queue (doto (ArrayBlockingQueue. 10) (.add :a))
          state (woker-runner! queue 
                  (fn [& _] {:called [:init] }) 
                  (fn [{:keys [called status]} data]
                    {:called (conj called :exec) :data data :status :fail})
                  (fn [{:keys [called status] :as state} data]  
                    (assoc state :called (conj called :stop) :status :terminate)))]
        (and 
          (= [:init :exec :stop] (:called state))
          (= :a (:data state))))))

