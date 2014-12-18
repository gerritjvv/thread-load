package thread_load.blocking;


import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Shamelessly taken from: http://fahdshariff.blogspot.com.es/2013/11/throttling-task-submission-with.html
 * An executor which blocks and prevents further tasks from
 * being submitted to the pool when the queue is full.
 * <p>
 * Based on the BoundedExecutor example in:
 * Brian Goetz, 2006. Java Concurrency in Practice. (Listing 8.4)
 */
public class BlockingExecutor extends ThreadPoolExecutor {

    private final Semaphore semaphore;

    /**
     * Creates a BlockingExecutor which will block and prevent further
     * submission to the pool when the specified queue size has been reached.
     *
     * @param poolSize the number of the threads in the pool
     * @param queueSize the size of the queue
     */
    public BlockingExecutor(final int poolSize, final int queueSize) {
        super(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());

        // the semaphore is bounding both the number of tasks currently executing
        // and those queued up
        semaphore = new Semaphore(poolSize + queueSize);
    }

    /**
     * Executes the given task.
     * This method will block when the semaphore has no permits
     * i.e. when the queue has reached its capacity.
     */
    @Override
    public void execute(final Runnable task) {
        boolean acquired = false;
        do {
            try {
                semaphore.acquire();
                acquired = true;
            } catch (final InterruptedException e) {
                ;
            }
        } while (!acquired);

        try {
            super.execute(task);
        } catch (final RejectedExecutionException e) {
            semaphore.release();
            throw e;
        }
    }

    /**
     * Method invoked upon completion of execution of the given Runnable,
     * by the thread that executed the task.
     * Releases a semaphore permit.
     */
    @Override
    protected void afterExecute(final Runnable r, final Throwable t) {
        super.afterExecute(r, t);
        semaphore.release();
    }
}