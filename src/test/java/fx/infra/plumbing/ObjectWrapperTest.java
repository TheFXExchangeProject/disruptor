package fx.infra.plumbing;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the basic functionality of the ObjectWrapper.
 *
 * Created by stephen on 04/03/15.
 */
public class ObjectWrapperTest {

    /**
     * Tests that the objectWrapper correctly registers that it has been read.
     *
     * @throws InterruptedException
     */
    @Test
    public void testBeenRead() throws InterruptedException {
        final int TO_READ = 1000;
        final ObjectWrapper<Object> objectWrapper = new ObjectWrapper<>(TO_READ);
        List<Thread> threadList = new ArrayList<>(TO_READ);
        final CountDownLatch countdownLatch = new CountDownLatch(TO_READ);

        for (int i = 0; i < TO_READ; i++) {
            threadList.add(new Thread(new Runnable() {
                @Override
                public void run() {
                    objectWrapper.getContent();
                    countdownLatch.countDown();
                }
            }));
        }

        for (Thread thread : threadList) {
            assertFalse(objectWrapper.beenRead());
            thread.start();
        }

        countdownLatch.await();
        assertTrue(objectWrapper.beenRead());
    }
}
