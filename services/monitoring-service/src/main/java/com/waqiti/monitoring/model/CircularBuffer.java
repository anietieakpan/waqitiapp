package com.waqiti.monitoring.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe circular buffer for efficient metric history storage
 * Automatically overwrites oldest values when capacity is reached
 */
@Getter
public class CircularBuffer {
    private final double[] buffer;
    private final int capacity;
    private int head;
    private int tail;
    private int size;
    private final ReentrantLock lock;
    
    public CircularBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.buffer = new double[capacity];
        this.head = 0;
        this.tail = 0;
        this.size = 0;
        this.lock = new ReentrantLock();
    }
    
    /**
     * Add a value to the buffer
     */
    public void add(double value) {
        lock.lock();
        try {
            buffer[tail] = value;
            tail = (tail + 1) % capacity;
            
            if (size == capacity) {
                // Buffer is full, move head forward
                head = (head + 1) % capacity;
            } else {
                size++;
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get the most recent value
     */
    public double getLast() {
        lock.lock();
        try {
            if (size == 0) {
                throw new IllegalStateException("Buffer is empty");
            }
            int lastIndex = (tail - 1 + capacity) % capacity;
            return buffer[lastIndex];
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get the oldest value
     */
    public double getFirst() {
        lock.lock();
        try {
            if (size == 0) {
                throw new IllegalStateException("Buffer is empty");
            }
            return buffer[head];
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get all values in chronological order
     */
    public List<Double> getValues() {
        lock.lock();
        try {
            List<Double> values = new ArrayList<>(size);
            int index = head;
            for (int i = 0; i < size; i++) {
                values.add(buffer[index]);
                index = (index + 1) % capacity;
            }
            return values;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get the most recent N values
     */
    public List<Double> getRecentValues(int count) {
        lock.lock();
        try {
            count = Math.min(count, size);
            List<Double> values = new ArrayList<>(count);
            
            int startIndex = (tail - count + capacity) % capacity;
            for (int i = 0; i < count; i++) {
                values.add(buffer[(startIndex + i) % capacity]);
            }
            return values;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Calculate average of all values
     */
    public double getAverage() {
        lock.lock();
        try {
            if (size == 0) {
                return 0;
            }
            
            double sum = 0;
            int index = head;
            for (int i = 0; i < size; i++) {
                sum += buffer[index];
                index = (index + 1) % capacity;
            }
            return sum / size;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Calculate moving average of last N values
     */
    public double getMovingAverage(int window) {
        lock.lock();
        try {
            if (size == 0) {
                return 0;
            }
            
            window = Math.min(window, size);
            double sum = 0;
            int startIndex = (tail - window + capacity) % capacity;
            
            for (int i = 0; i < window; i++) {
                sum += buffer[(startIndex + i) % capacity];
            }
            return sum / window;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get minimum value
     */
    public double getMin() {
        lock.lock();
        try {
            if (size == 0) {
                throw new IllegalStateException("Buffer is empty");
            }
            
            double min = Double.MAX_VALUE;
            int index = head;
            for (int i = 0; i < size; i++) {
                min = Math.min(min, buffer[index]);
                index = (index + 1) % capacity;
            }
            return min;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get maximum value
     */
    public double getMax() {
        lock.lock();
        try {
            if (size == 0) {
                throw new IllegalStateException("Buffer is empty");
            }
            
            double max = Double.MIN_VALUE;
            int index = head;
            for (int i = 0; i < size; i++) {
                max = Math.max(max, buffer[index]);
                index = (index + 1) % capacity;
            }
            return max;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get standard deviation
     */
    public double getStandardDeviation() {
        lock.lock();
        try {
            if (size == 0) {
                return 0;
            }
            
            double mean = getAverage();
            double sumSquaredDiff = 0;
            int index = head;
            
            for (int i = 0; i < size; i++) {
                double diff = buffer[index] - mean;
                sumSquaredDiff += diff * diff;
                index = (index + 1) % capacity;
            }
            
            return Math.sqrt(sumSquaredDiff / size);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Check if buffer is empty
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return size == 0;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Check if buffer is full
     */
    public boolean isFull() {
        lock.lock();
        try {
            return size == capacity;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Clear the buffer
     */
    public void clear() {
        lock.lock();
        try {
            head = 0;
            tail = 0;
            size = 0;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get buffer statistics
     */
    public BufferStatistics getStatistics() {
        lock.lock();
        try {
            if (size == 0) {
                return new BufferStatistics(0, 0, 0, 0, 0, 0);
            }
            
            return new BufferStatistics(
                size,
                getMin(),
                getMax(),
                getAverage(),
                getStandardDeviation(),
                size >= 2 ? getMovingAverage(Math.min(10, size)) : getAverage()
            );
        } finally {
            lock.unlock();
        }
    }
    
    @Getter
    public static class BufferStatistics {
        private final int count;
        private final double min;
        private final double max;
        private final double average;
        private final double standardDeviation;
        private final double movingAverage10;
        
        public BufferStatistics(int count, double min, double max, 
                              double average, double standardDeviation, 
                              double movingAverage10) {
            this.count = count;
            this.min = min;
            this.max = max;
            this.average = average;
            this.standardDeviation = standardDeviation;
            this.movingAverage10 = movingAverage10;
        }
    }
}