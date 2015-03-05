package fx.infra.plumbing;

import java.util.Collection;

/**
 * May build custom FXReaders though they will need to implement the inner class reader from
 * the disruptor.
 *
 * FXReader makes not guarantees about thread-safety. In fact, best practice would be to have
 * a reader/writer used in a single thread
 *
 * Created by stephen on 03/03/15.
 */
public interface FXReader<T> {
    T readNext();

    void readAll(Collection<T> runningCollection);
}
