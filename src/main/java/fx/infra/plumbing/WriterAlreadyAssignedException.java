package fx.infra.plumbing;

/**
 * Thrown when attempting to retrieve a writer after one has already been given.
 *
 * Created by stephen on 04/03/15.
 */
class WriterAlreadyAssignedException extends Throwable {
    public WriterAlreadyAssignedException(String s) {
        super(s);
    }
}
