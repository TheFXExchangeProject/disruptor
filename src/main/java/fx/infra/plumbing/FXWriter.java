package fx.infra.plumbing;

/**
 * May build custom FXWriters though they will need to implement the inner class writer from
 * the disruptor.
 *
 * FXWriter makes not commitments to any thread safety. Each reader/writer should be handled in one thread.
 *
 * Created by stephen on 03/03/15.
 */
public interface FXWriter<T> {
    void writeNext(T t);
}
