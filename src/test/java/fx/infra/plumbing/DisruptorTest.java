package fx.infra.plumbing;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DisruptorTest {
    @Test
    public void testAddToAndRetrieveFromDisruptor() throws DisruptorAlreadyStartedException {
        final Disruptor<String> disruptor = new Disruptor<>(1024);

        final FXReader<String> fxReader = disruptor.getReader();
        final FXWriter<String> fxWriter = disruptor.getWriter();

        disruptor.initialise();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 1024; i++) {
                    fxWriter.writeNext("Example " + i);
                }
            }
        }).start();


        for (int i = 0; i < 1024; i++) {
            assertEquals("Example " + i, fxReader.readNext());
        }
    }


    @Test
    public void testDoesntOverwriteUnreadData() throws InterruptedException, DisruptorAlreadyStartedException {
        final Disruptor<String> disruptor = new Disruptor<>(1024);

        final FXReader<String> fxReader = disruptor.getReader();
        final FXWriter<String> fxWriter = disruptor.getWriter();

        disruptor.initialise();

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final CountDownLatch testFinished = new CountDownLatch(2);
        final int TEST_SIZE = 100000000;


        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < TEST_SIZE; i++) {
                    if (i == 31) {
                        countDownLatch.countDown();
                    }
                    fxWriter.writeNext("Example " + i);
                }

                testFinished.countDown();
            }
        }).start();


        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            // Do nothing if interrupted
        }
        for (int i = 0; i < TEST_SIZE; i++) {
            assertEquals("Example " + i, fxReader.readNext());
        }

        testFinished.countDown();


        testFinished.await();
    }

    @Test
    public void testMultipleReaders() throws DisruptorAlreadyStartedException, InterruptedException {
        Disruptor<String> disruptor = new Disruptor<>(1024);

        final FXReader<String> firstReader = disruptor.getReader();
        final FXReader<String> secondReader = disruptor.getReader();
        final FXWriter<String> writer = disruptor.getWriter();

        final AtomicBoolean firstPassed = new AtomicBoolean(true);
        final AtomicBoolean secondPassed = new AtomicBoolean(true);

        disruptor.initialise();

        final int TEST_SIZE = 2048;

        final CountDownLatch testFinished = new CountDownLatch(3);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < TEST_SIZE; i++) {
                    String val = firstReader.readNext();
                    if (!val.equals("TEST " + i)) {
                        firstPassed.set(false);
                    }
                }
                testFinished.countDown();
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < TEST_SIZE; i++) {
                    String val = secondReader.readNext();
                    if (!val.equals("TEST " + i)) {
                        secondPassed.set(false);
                    }
                }
                testFinished.countDown();
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < TEST_SIZE; i++) {
                    writer.writeNext("TEST " + i);
                }

                testFinished.countDown();
            }
        }).start();


        testFinished.await();

        assertTrue(firstPassed.get());
        assertTrue(secondPassed.get());
    }

    @Test
    public void testPowerofTwo() {
        Disruptor<String> disruptor = new Disruptor<>();

        assertEquals(1024, disruptor.getPowerOfTwo(1533));
        assertEquals(0, disruptor.getPowerOfTwo(0));
        assertEquals(2, disruptor.getPowerOfTwo(3));
        assertEquals(2048, disruptor.getPowerOfTwo(2048));
    }
}
