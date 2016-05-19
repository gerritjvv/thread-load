(ns thread-load.streams-test
  (:require
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [clojure.test.check.clojure-test :refer [defspec]]
    [clojure.tools.logging :refer [info]]
    [thread-load.streams :refer :all]))


(defspec test-streams-process
         1
         (prop/for-all [a gen/nat]
                       (info "Start test")
                       (let [counter (atom 0)
                             streams (repeatedly 1000 (fn [] (repeatedly #(rand-int 100))))
                             streams-load-f (fn
                                              ([] streams)
                                              ([streams]
                                               (if-let [stream (first streams)]
                                                 [(rest streams) stream]
                                                 (Thread/sleep Integer/MAX_VALUE))))
                             streams-read-f (fn
                                              ([] nil)
                                              ([_ stream]
                                               [nil (nth stream (rand-int 100))]))
                             process-f (fn
                                         ([] nil)
                                         ([_ data]
                                          (swap! counter inc)
                                          nil))

                             processor (create-stream-processor streams-load-f streams-read-f process-f :streams-limit 5000)]

                         (Thread/sleep 2000)
                         (shutdown! processor)
                         (> @counter 1))))

