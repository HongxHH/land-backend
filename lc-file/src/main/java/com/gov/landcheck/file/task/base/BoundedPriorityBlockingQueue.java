package com.gov.landcheck.file.task.base;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 有界优先级阻塞队列。
 * <p>
 * PriorityBlockingQueue 默认是无界队列，这里通过覆写入队方法实现容量限制。
 * <p>
 * {@link #offer(Object, long, TimeUnit)} 在队列满时会等待直至超时或出队释放空位；等待被中断时返回 false
 * 并恢复中断标记。
 */
public class BoundedPriorityBlockingQueue<E> extends PriorityBlockingQueue<E> {

    private final int capacity;

    public BoundedPriorityBlockingQueue(int capacity, Comparator<? super E> comparator) {
        super(Math.max(1, capacity), comparator);
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than 0");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized boolean offer(E e) {
        if (size() >= capacity) {
            return false;
        }
        return super.offer(e);
    }

    @Override
    public void put(E e) {
        if (!offer(e)) {
            throw new IllegalStateException("Queue full");
        }
    }

    @Override
    public synchronized boolean offer(E e, long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (size() >= capacity) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            long waitMs = TimeUnit.NANOSECONDS.toMillis(remaining);
            try {
                wait(Math.max(1L, waitMs));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return super.offer(e);
    }

    @Override
    public E poll() {
        E el = super.poll();
        if (el != null) {
            wakeWaiters();
        }
        return el;
    }

    @Override
    public E take() throws InterruptedException {
        E el = super.take();
        wakeWaiters();
        return el;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E el = super.poll(timeout, unit);
        if (el != null) {
            wakeWaiters();
        }
        return el;
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        int n = super.drainTo(c);
        if (n > 0) {
            wakeWaiters();
        }
        return n;
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        int n = super.drainTo(c, maxElements);
        if (n > 0) {
            wakeWaiters();
        }
        return n;
    }

    private void wakeWaiters() {
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public synchronized int remainingCapacity() {
        return Math.max(0, capacity - size());
    }

    @Override
    public synchronized boolean add(E e) {
        if (!offer(e)) {
            throw new IllegalStateException("Queue full");
        }
        return true;
    }

    @Override
    public synchronized boolean addAll(Collection<? extends E> c) {
        if (c == null) {
            throw new NullPointerException("collection");
        }
        if (size() + c.size() > capacity) {
            throw new IllegalStateException("Queue full");
        }
        return super.addAll(c);
    }
}
