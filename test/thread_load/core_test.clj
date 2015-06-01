(ns thread-load.core-test
  (:require 
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [clojure.test.check.clojure-test :refer [defspec]]
    [thread-load.core :refer :all])
  
  (:import
    [org.jctools.queues SpmcArrayQueue]
    [java.util.concurrent ArrayBlockingQueue]
    [java.util.concurrent.atomic AtomicInteger]))

(defn create-queue [len]
  (let [^SpmcArrayQueue queue (SpmcArrayQueue. len)]
    (dotimes [i len]
      (.offer queue i))
    queue))

(defspec call-f-should-return-fail-on-exception
  10
  (prop/for-all [a gen/nat]
    (let [state (call-f (fn [& _] (throw (RuntimeException.))) {} nil)]
      (= (:status state) :fail))))


(defspec worker-runner-should-call-init-and-terminate
  10
  (prop/for-all [a gen/nat]
                               ;exec-delegate queue init exec stop n
    (let [state (worker-runner! exec-on-data (create-queue 10) (fn [& _] {:called true :status :terminate}) (fn [&_]) (fn [&_]) 1)]
      (:called state))))



(defspec worker-runner-should-call-init-stop-and-terminate
  10
  (prop/for-all [a gen/nat]
    (let [state (worker-runner! exec-on-data (create-queue 10)
                  (fn [& _] {:called [:init] :status :fail}) (fn [&_]) 
                  (fn [{:keys [called status]} data]  
                    {:called (conj called :stop) :status :terminate})
                  1
                  )]
        (= [:init :stop] (:called state)))))
                                             

(comment

  (defspec test-bulk-operations
           10
           (prop/for-all [a gen/nat]
                         (let [queue (SpmcArrayQueue. 10)]
                           (bulk-single-producer-publish! {:queue queue :limit 10} [1 2 3 4 5])

                           (= (.size queue) 5)

                           (future (bulk-single-producer-publish! {:queue queue :limit 10} (range 6 25)))
                           (while (< (.size queue) 10) (Thread/sleep 1000))

                           (let [arr (bulk-get! {:queue queue} 10)]
                             (= (count arr) 10))

                           (.clear queue)
                           (let [f (future (count (bulk-get! {:queue queue} 10)))]
                             (future (do
                                       (Thread/sleep 1000)
                                       (bulk-single-producer-publish! {:queue queue :limit 10} (range 5))))

                             (let [n (deref f 5000 -1)]
                               (> n 0))))))
  )
(defspec worker-runner-should-call-init-exec-stop-and-terminate
  10
  (prop/for-all [a gen/nat]
    (let [queue (doto (SpmcArrayQueue. 10) (.add :a))
          state (worker-runner! exec-on-data queue
                  (fn [& _] {:called [:init] }) 
                  (fn [{:keys [called status]} data]
                    {:called (conj called :exec) :data data :status :fail})
                  (fn [{:keys [called status] :as state} data]  
                    (assoc state :called (conj called :stop) :status :terminate))
                  1)]
        (and 
          (= [:init :exec :stop] (:called state))
          (= :a (:data state))))))




(defspec consume-from-pool 
  10
  (prop/for-all [a gen/nat]
    (let [init-count (AtomicInteger.)
          exec-count (AtomicInteger.)
          stop-count (AtomicInteger.)
          
          pool (->
                 (create-pool) 
                 (add-consumer 
                   (fn [& _] (.incrementAndGet init-count) {}) 
                   (fn [& _] (.incrementAndGet exec-count) {:status :fail})
                   (fn [& _] (.incrementAndGet stop-count) {:status :terminate}))
                 (publish! :a)
                 (shutdown-pool 10000))
          ]
      (prn init-count " " exec-count " " stop-count)
      (and 
        (= (.get init-count) 1)
        (= (.get exec-count) 1)
        (= (.get stop-count) 1)))))
        
                   
