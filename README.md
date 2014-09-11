#thread-load

This project aims to solve problems in the domain of running functions on data units from multiple threads continuously.
 

[![Clojars Project](http://clojars.org/thread-load/latest-version.svg)](http://clojars.org/thread-load)

See: https://clojars.org/thread-load

## Overview 

You have a continuous stream of data and want them to be processed continuously from multiple threads.
Each worker processor has an init, exec and stop function.

On startup the init function is called to return an initial state.
On each data unit received by thread N exec is called as ```(let [new-state (exec state data)  ... )```, 
if the new-state contains ```:terminate``` the thread will exit, if any exception or if the new-state contains ```:fail``` stop will be called,
otherwise the new-state is then passed to the exec function on the next data unit.

On failure i.e either the state contains ```:fail``` or an exception is thrown by exec, the stop function will be called as ```(let [new-state (stop state data)] ...)```,
if the new-state contains ```:terminate``` the thread will exit, otherwise the thread will loop and call init again passing it the new-state that stop passed.
This allows the init function to track how many failures have occurred.


#Usage

```clojure

(require '[thread-load.core :refer :all])

(def pool (create-pool))
;; create a pool with a blocking queue of 100

(dotimes [i 10] ;add 10 consumers i.e 10 threads consuming from the pool's queue
 (add-consumer 
   pool
   (fn [state _] {}) ; init function
   (fn [state data] (prn data) state) ; exec function
   (fn [state _] {:status :terminate})))


(publish! pool :a) ;sends :a to the queue, which will run the exec function of a consumer;

(shutdown-pool pool 10000)

```

