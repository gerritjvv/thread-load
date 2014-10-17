#thread-load

This project aims to solve problems in the domain of running functions on data units from multiple threads continuously.
 

[![Clojars Project](http://clojars.org/thread-load/latest-version.svg)](http://clojars.org/thread-load)

See: https://clojars.org/thread-load

## Overview

This library contains several patterns that help with reading data from sources and distribute them over multiple processes.



## Task Loading

You have a continuous stream of data and want them to be processed continuously from multiple threads.
Each worker processor has an init, exec and stop function.

On startup the init function is called to return an initial state.
On each data unit received by thread N exec is called as ```(let [new-state (exec state data)  ... )```, 
if the new-state contains ```:terminate``` the thread will exit, if any exception or if the new-state contains ```:fail``` stop will be called,
otherwise the new-state is then passed to the exec function on the next data unit.

On failure i.e either the state contains ```:fail``` or an exception is thrown by exec, the stop function will be called as ```(let [new-state (stop state data)] ...)```,
if the new-state contains ```:terminate``` the thread will exit, otherwise the thread will loop and call init again passing it the new-state that stop passed.
This allows the init function to track how many failures have occurred.


##Usage

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

## Loading from N Streams

Scale the reading of N streams by splitting the logic of reading from the streams from the logic of processing the data.  
N_a threads should read from a stream-queue in which a data piece is read from the stream and pushed on the data queue.  
  
  
###Usage

The functions used are:

```streams-load-f (f) -> initial-state, f [state] -> [state stream]```
```streams-read-f (f) -> initial-state, f [state stream] -> [state data]```
```process-f      (f) -> initial-state, f [state data] -> state```
                        
In the example below 1000 infinte streams of random integers are created (note this could be Http/TCP Streams, File I/O etc).  
The  functions do:
  * streams-load-f gets a stream, if no new streams it blocks infinitely.   
  * streams-read-f reads a single data item from the stream (for batching this function could read multiple values).
  * process-f processes a single data point (or if you know the streams-read-f returns multiple values you can process them here). 
  

```clojure

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
                         (> @counter 1))
                         
```

For java integration

```java

import thread_load.streams.StreamsProcessor;
import thread_load.streams.StreamsConf;

StreamsProcessor p = StreamsProcessor.createProcessor(loaderFn, readFn, processFn, new StreamsConf());

//to shutdown
p.shutdown(2000);


