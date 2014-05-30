#thread-load

This project aims to solve problems in the domain of running functions on data units from multiple threads continuously.
 

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



