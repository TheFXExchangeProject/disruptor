package fx.infra.plumbing;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An object wrapper to make up the "Segments" of the ring-buffer held within the disruptor.
 *
 * Remains mutable to avoid any unnecessary garbage creation.
 *
 * Created by stephen on 03/03/15.
 */
class ObjectWrapper<T> {
    private T content;
    private final AtomicInteger readBy = new AtomicInteger();

    public ObjectWrapper(int readBy) {
        this.readBy.set(readBy);
    }

    public void setContent(T t, int numReaders) {
        content = t;
        readBy.set(numReaders);
    }

    public T getContent() {
        readBy.decrementAndGet();
        return content;
    }

    public boolean beenRead() {
        return readBy.intValue() < 1;
    }
}
