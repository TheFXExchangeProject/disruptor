package fx.infra.plumbing;

/**
 * Exception to be thrown whenever a disruptor is started without a writer having been assigned.
 *
 * Created by stephen on 05/03/15.
 */
class NoWriterAssignedException extends Throwable {
    public NoWriterAssignedException(String s) {
        super(s);
    }
}
