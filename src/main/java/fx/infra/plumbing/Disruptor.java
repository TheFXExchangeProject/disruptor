package fx.infra.plumbing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core component offered lock-free synchronization of methods.
 *
 * Will allow one writer and several readers.
 *
 * The readers can be in any order at any time but must not overtake the writer.
 *
 * Likewise the writer cannot overtake the readers.
 *
 * Created by stephen on 03/03/15.
 */
public class Disruptor<T> {
    private static final Logger logger = LoggerFactory.getLogger(Disruptor.class);
    private final ObjectWrapper<T>[] ringBuffer;
    private final int BUFFER_SIZE;
    private final AtomicInteger numReaders = new AtomicInteger(0);
    private volatile long writerIndex = 1;
    private volatile long lastReader = 1;
    private final AtomicBoolean writerAssigned = new AtomicBoolean(false);
    private final AtomicBoolean alreadyStarted = new AtomicBoolean(false);


    public Disruptor() {
        BUFFER_SIZE = 1024;
        ringBuffer = new ObjectWrapper[BUFFER_SIZE];
    }

    public Disruptor(int bufferSize) {
        BUFFER_SIZE = getPowerOfTwo(bufferSize);
        logger.info("Creating Disruptor with BUFFER_SIZE as a power of two. " +
                "Requested: {}; Actual: {}", bufferSize, BUFFER_SIZE);
        ringBuffer = new ObjectWrapper[BUFFER_SIZE];
    }

    /**
     * Returns a reader for the ring buffer. Only works before the buffer has been started.
     *
     * @return reader for the disruptor
     * @throws DisruptorAlreadyStartedException when the disruptor has already been started
     */
    public DisruptorReader getReader() throws DisruptorAlreadyStartedException {
        if (alreadyStarted.get()) {
            throw new DisruptorAlreadyStartedException("Cannot get a reader after the disruptor has already started.");
        }
        numReaders.incrementAndGet();
        logger.info("Disruptor reader #{} has been requested.", numReaders.get());
        return new DisruptorReader();
    }

    /**
     * Returns the writer for the ring buffer. Only works before the buffer has been started.
     *
     * @return reader for the disruptor
     * @throws DisruptorAlreadyStartedException when the disruptor has already been started
     */
    public DisruptorWriter getWriter() throws DisruptorAlreadyStartedException, WriterAlreadyAssignedException {
        if (alreadyStarted.get()) {
            throw new DisruptorAlreadyStartedException("Cannot get a writer after the disruptor has already started.");
        }
        if (writerAssigned.getAndSet(true)) {
            throw new WriterAlreadyAssignedException("A writer has already been assigned for this disruptor.");
        }

        logger.info("DisruptorWriter has been requested.");
        return new DisruptorWriter();
    }

    public void initialise() throws DisruptorAlreadyStartedException, NoWriterAssignedException {
        if (!alreadyStarted.getAndSet((true))) {
            if (!writerAssigned.get()) {
                throw new NoWriterAssignedException("Need to assign a writer before initialising the disruptor.");
            }
            logger.info("Initialising the Disruptor...");
            for (int i = 0; i < BUFFER_SIZE; i++) {
                ringBuffer[i] = new ObjectWrapper<>(0);
            }
            logger.info("Disruptor has been successfully initialised.");
        } else {
            throw new DisruptorAlreadyStartedException("Cannot initialise the disruptor after it has been started.");
        }
    }


    /**
     * Rounds a number down to the nearest power of two.
     *
     * @param bufferSize integer to round down
     * @return nearest integer that is a power of two which is less than or equal to bufferSize
     */
    protected int getPowerOfTwo(int bufferSize) {
        return Integer.highestOneBit(bufferSize);
    }

    private void writeToBuffer(T t) {
        while (writerIndex - lastReader >= BUFFER_SIZE) {
            // Spin in place
        }
        ringBuffer[(int) writerIndex % BUFFER_SIZE].setContent(t, numReaders.get());
        writerIndex++;
    }

    private T readValue(long idx) {
        incrementReaderCount(idx - 1);
        while (idx >= writerIndex) {
            // Spin in place
        }

        return ringBuffer[(int) idx % BUFFER_SIZE].getContent();
    }

    private int readCollection(long idx, Collection<T> batchCollection) {
        while (idx >= writerIndex) {
            // Spin in place
        }
        long currentWriterIndex = writerIndex;

        for (long i = idx; i < currentWriterIndex; i++) {
            incrementReaderCount(idx - 1);
            batchCollection.add(ringBuffer[(int) i % BUFFER_SIZE].getContent());
        }
        return (int)(currentWriterIndex - idx);
        // We know this will be small enough to be an int, since it cannot be bigger than BUFFER_SIZE
    }

    /**
     * This method increments the readerCount once the last reader has passed through.
     *
     * The lastReader does not need to be locked because it should only be modified by the last reader to
     * call this method for that particular index. Since that reader cannot be used concurrently, this field is safe.
     * @param idx index to update
     */
    private void incrementReaderCount(long idx) {
        if (ringBuffer[(int) idx % BUFFER_SIZE].beenRead()) {
            lastReader = idx + 1;
        }
    }


    private class DisruptorReader implements FXReader<T> {
        private volatile long currentCount = 1;

        /**
         * Reads only the next value on the disruptor's ring buffer.
         * @return the content of the buffer node
         */
        @Override
        public T readNext() {
            return readValue(currentCount++);
        }

        /**
         * Reads as many entries as is possible along the ringBuffer, into the collection passed
         * as a parameter.
         * @param runningCollection
         */
        @Override
        public void readAll(Collection<T> runningCollection) {
            currentCount += readCollection(currentCount, runningCollection);
        }
    }

    private class DisruptorWriter implements FXWriter<T> {
        /**
         * Writes to the buffer at the next appropriate point.
         * @param t the value to write as the content of the buffer node.
         */
        @Override
        public void writeNext(T t) {
            writeToBuffer(t);
        }
    }

}