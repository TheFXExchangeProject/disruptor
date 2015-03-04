package fx.infra.plumbing;

/**
 * May build custom FXReaders though they will need to implement the inner class reader from
 * the disruptor.
 *
 * Created by stephen on 03/03/15.
 */
public interface FXReader<T> {
    T readNext();
}
