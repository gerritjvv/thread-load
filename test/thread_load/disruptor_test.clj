(ns thread-load.disruptor-test
  (:require
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [clojure.test.check.clojure-test :refer [defspec]]
    [thread-load.disruptor :refer :all])

  (:import
    [java.util.concurrent.atomic AtomicLong]))


(defspec publish-to-disruptor
         10
         (prop/for-all [a gen/nat]
                       (let [iterations 100000
                             ^AtomicLong i (AtomicLong. 0)
                             d (create-disruptor (fn [n] (.incrementAndGet i)))]

                         (dotimes [i iterations]
                           (publish! d 1))

                         (shutdown-pool d)

                         (= (.get i) iterations))))
