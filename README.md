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

## Usage

*Clojure*


```clojure

;; Thread load

(def f (api/thread-load-factory :load [(fn [state msg] (prn "state " state " msg " msg) 1)] {:threads 2}))
      (f :a)
      ;; state nil  msg  :a
      ;; state  1  msg  :a
      (api/size f)
      ;; 0
      (api/stats f)
      ;; -- stats map of the thread-load instance
      (f) ;shutdown
      
;; Disruptor
(def f (api/thread-load-factory :disruptor [(fn [state msg] (prn "state " state " msg " msg) 1)] {}))
      (f :a)
      ;; state nil  msg  :a
      ;; state  1  msg  :a
      (f) ;shutdown"  
```

## Factory pattern

Factory methods to create different thread load implementations  

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
```clojure
               (fn  
                  ([msg]  
                   ;always return msg
                   msg)
                  ([k msg & args]  ;the k is used to support keyed messages
                   ;always return msg
                   msg))
                  ([] doclose-here and return a none nil value if successfull))
                  
;;Or use a Proxy if the thread-load instance implements the ILoadMonitorable interface

                (proxy
                      [AFn ILoadMonitorable] []
                      (invoke
                        ([] (disruptor/shutdown-pool dis) :true)
                        ([msg] (disruptor/publish! dis msg) msg)
                        ([k msg & args] (disruptor/publish! dis msg) msg))
                      (size [] 1)
                      (stats [] dis))

```


##Thread Load 

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

## Bounded Executor Service

All ExecutorServices created by the java class Executors have unbounded queues which can create problems if the tasks take
more than the expected time.

In general all unbounded queues is a very bad idea.

This library implements a quick utilty function and java class to create a bounded blocking Executor Service.

*Clojure*

```clojure

(use 'thread-load.core)

(def p (bounded-executor :queue-size 1 :pool-size 1))

(executor-submit p #(Thread/sleep 5000))

```

*Java*:

```java
import java.util.concurrent.ExecutorService;
import thread_load.blocking.BlockingExecutor;

ExecutorService exec = new BlockingExecutor(1, 1);
```


## Disruptor

The disruptor pattern is a quick way to communicate between threads or do async handoff.

Its important to choose the correct wait strategy for each disruptor, :yield and :spin will  
use CPU even when you are not using the disruptor, but with higher throughput and lower latency,   
:block will only use CPU if when messages are published and consumed but has higher latency.  

The default strategy chosen is :block.

The producer type is by default :single but if your planning on publishing from multiple threads  
this needs to be set the :multi.


*Clojure*

```clojure
(require '[thread-load.disruptor :refer :all])

(def d (create-disruptor (fn [v] (prn "Val: " v)) :wait-strategy :block :producer-type :multi))

(dotimes [i 10] (publish! d i))
(shutdown-pool d)

```

*Java*

```java

import clojure.lang.AFn;
import thread_load.disruptor.Disruptor;

Disruptor disruptor = Disruptor.create(new AFn() {
    @Override
    public Object invoke(Object v) {
       System.out.println("Val: " + v);
       return null;
    }
});

for(int i = 0; i < 10; i++)
   disruptor.publish(i);

disruptor.shutdown();
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
  
*Clojure*

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

*Java*

```java

import thread_load.streams.StreamsProcessor;
import thread_load.streams.StreamsConf;

StreamsProcessor p = StreamsProcessor.createProcessor(loaderFn, readFn, processFn, new StreamsConf());

//to shutdown
p.shutdown(2000);
```



