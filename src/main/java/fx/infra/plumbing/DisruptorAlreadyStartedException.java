package fx.infra.plumbing;

/**
 * Thrown when trying to retrieve a reader or writer from the disruptor after it has been initialised.
 *
 * Created by stephen on 03/03/15.
 */
class DisruptorAlreadyStartedException extends Throwable {
    public DisruptorAlreadyStartedException(String s) {
        super(s);
    }
}
