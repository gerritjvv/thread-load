(ns thread-load.core-test
  (:require 
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [clojure.test.check.clojure-test :refer [defspec]]
    [thread-load.core :refer :all]))


(defspec call-f-should-return-fail-on-exception
  10
  (prop/for-all [a gen/nat]
    (let [state (call-f (fn [& _] (throw (RuntimeException.))) {} nil)]
      (not (nil? (:fail state))))))


(defspec worker-runner-should-call-init-and-terminate
  10
  (prop/for-all [a gen/nat]
    (let [state (woker-runner! nil (fn [& _] {:called true :status :terminate}) (fn [&_]) (fn [&_]))]
      (:called state))))