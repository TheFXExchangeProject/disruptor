package fx.infra.plumbing;

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

        return new DisruptorWriter();
    }

    public void initialise() {
        alreadyStarted.set(true);
        for (int i = 0; i < BUFFER_SIZE; i++) {
            ringBuffer[i] = new ObjectWrapper<>(0);
        }
    }


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

    /**
     * This method increments the readercount once the last reader has passed through.
     *
     * The lastReader does not need to be locked because it should only be modified by the last reader to
     * call this method for that particular index. Since that reader cannot be used oncurrently, this field is safe.
     * @param idx index to update
     */
    private void incrementReaderCount(long idx) {
        if (ringBuffer[(int) idx % BUFFER_SIZE].beenRead()) {
            lastReader = idx + 1;
        }
    }

    private class DisruptorReader implements FXReader<T> {
        private volatile long currentCount = 1;

        @Override
        public T readNext() {
            return readValue(currentCount++);
        }
    }

    private class DisruptorWriter implements FXWriter<T> {
        @Override
        public void writeNext(T t) {
            writeToBuffer(t);
        }
    }

}