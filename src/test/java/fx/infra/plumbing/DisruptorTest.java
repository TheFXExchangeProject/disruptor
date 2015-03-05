package fx.infra.plumbing;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DisruptorTest {
    private static final Logger logger = LoggerFactory.getLogger(DisruptorTest.class);

    /**
     * Tests the base reading functionality of the disruptor, with a pause added in to the writer
     * to ensure that the reader does not overtake.
     *
     * @throws DisruptorAlreadyStartedException
     * @throws WriterAlreadyAssignedException
     * @throws NoWriterAssignedException
     */
    @Test
    public void testAddToAndRetrieveFromDisruptor() throws DisruptorAlreadyStartedException, WriterAlreadyAssignedException, NoWriterAssignedException {
        final Disruptor<String> disruptor = new Disruptor<>(1024);

        final FXReader<String> fxReader = disruptor.getReader();
        final FXWriter<String> fxWriter = disruptor.getWriter();

        disruptor.initialise();
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 1024; i++) {
                    fxWriter.writeNext("Example " + i);
                    if (i == 512) {
                        try {
                            countDownLatch.await();
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            logger.error("interrupted while waiting in the writer thread of testAddToAndRetrieveFromDisruptor test");
                        }
                    }

                }
            }
        }).start();


        for (int i = 0; i < 1024; i++) {
            countDownLatch.countDown();
            assertEquals("Example " + i, fxReader.readNext());
        }
    }


    /**
     * test to ensure the basic functionality of the disruptor, which is that data is not overwritten before it is read.
     * @throws InterruptedException
     * @throws DisruptorAlreadyStartedException
     * @throws WriterAlreadyAssignedException
     * @throws NoWriterAssignedException
     */
    @Test
    public void testDoesNotOverwriteUnreadData() throws InterruptedException, DisruptorAlreadyStartedException, WriterAlreadyAssignedException, NoWriterAssignedException {
        final Disruptor<String> disruptor = new Disruptor<>(256);

        final FXReader<String> fxReader = disruptor.getReader();
        final FXWriter<String> fxWriter = disruptor.getWriter();

        disruptor.initialise();

        final CountDownLatch testFinished = new CountDownLatch(2);
        final int TEST_SIZE = 1000000; //Purposefully large in an attempt to flag any overtaking issues.


        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < TEST_SIZE; i++) {
                    fxWriter.writeNext("Example " + i);
                }

                testFinished.countDown();
            }
        }).start();

        for (int i = 0; i < TEST_SIZE; i++) {
            assertEquals("Example " + i, fxReader.readNext());
        }

        testFinished.countDown();

        testFinished.await();
    }


    /**
     * Asserts that the data is uncorrupted and the ringBuffer is not blocked by concurrency issues when multiple readers are used.
     * @throws DisruptorAlreadyStartedException
     * @throws InterruptedException
     * @throws WriterAlreadyAssignedException
     * @throws NoWriterAssignedException
     */
    @Test
    public void testMultipleReaders() throws DisruptorAlreadyStartedException, InterruptedException, WriterAlreadyAssignedException, NoWriterAssignedException {
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
                        System.out.println("Test 2 failed on :" + i);
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

    /**
     * Asserts rounding down to nearest power of two is done correctly.
     */
    @Test
    public void testPowerOfTwo() {
        Disruptor<String> disruptor = new Disruptor<>();

        assertEquals(1024, disruptor.getPowerOfTwo(1533));
        assertEquals(0, disruptor.getPowerOfTwo(0));
        assertEquals(2, disruptor.getPowerOfTwo(3));
        assertEquals(2048, disruptor.getPowerOfTwo(2048));
    }

    /**
     * Tests the read all function which allows the owner of a reader to pass a collection for the reader to fill.
     *
     * @throws DisruptorAlreadyStartedException
     * @throws WriterAlreadyAssignedException
     * @throws InterruptedException
     * @throws NoWriterAssignedException
     */
    @Test
    public void testReadAll() throws DisruptorAlreadyStartedException, WriterAlreadyAssignedException, InterruptedException, NoWriterAssignedException {
        Disruptor<String> disruptor = new Disruptor<>();

        final FXReader<String> fxReader = disruptor.getReader();
        final FXWriter<String> fxWriter = disruptor.getWriter();

        disruptor.initialise();

        final int TEST_SIZE = 20000;
        final Semaphore semaphore = new Semaphore(0);
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final Collection<String> runningCollection = new ArrayList<>(TEST_SIZE);
        final Collection<String> expectedValues = new ArrayList<>(TEST_SIZE);


        new Thread(new Runnable() {
            @Override
            public void run() {
                while (runningCollection.size() < TEST_SIZE) {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        logger.error("Could not await the semaphore.");
                    }
                    fxReader.readAll(runningCollection);
                }
                countDownLatch.countDown();
            }
        }).start();


        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < TEST_SIZE; i++) {
                    if (i % 128 == 0) {
                        semaphore.release();
                    }
                    fxWriter.writeNext("TEST " + i);
                    expectedValues.add("TEST " + i);
                }
                semaphore.release();
                countDownLatch.countDown();
            }
        }).start();


        countDownLatch.await();

        for (String s : expectedValues) {
            assertTrue(runningCollection.contains(s));
        }
    }


    @Test
    public void testOnlyOneWriter() {
        Disruptor<String> disruptor = new Disruptor<>();

        try {
            disruptor.getWriter();
        } catch (DisruptorAlreadyStartedException e) {
            fail("Disruptor should not already have started.");
        } catch (WriterAlreadyAssignedException e) {
            // Do nothing
        }

        boolean passTest = false;
        try {
            disruptor.getWriter();
        } catch (DisruptorAlreadyStartedException e) {
            fail("Disruptor should not already have started.");
        } catch (WriterAlreadyAssignedException e) {
            passTest = true;
        }

        assertTrue(passTest);
    }


    /**
     * Verifies that no writers nor readers may be requested after the disruptor has been started.
     */
    @Test
    public void testNoWritersOrReadersAfterStart() throws NoWriterAssignedException {
        Disruptor<String> disruptor = new Disruptor<>();

        try {
            disruptor.getWriter();
            disruptor.getReader();
        } catch (WriterAlreadyAssignedException e) {
            fail("Writer should not have already been assigned");
        } catch (DisruptorAlreadyStartedException e) {
            fail("Disruptor should not have already started.");
        }

        try {
            disruptor.initialise();
        } catch (DisruptorAlreadyStartedException e) {
            fail("Should not have already started.");
        }

        boolean exceptionOnWriter = false;
        boolean exceptionOnReader = false;
        try {
            disruptor.getWriter();
        } catch (WriterAlreadyAssignedException e) {
            fail("This should be of a lower priority than the disruptor having already started.");
        } catch (DisruptorAlreadyStartedException e) {
            exceptionOnWriter = true;
        }

        try {
            disruptor.getReader();
        } catch (DisruptorAlreadyStartedException e) {
            exceptionOnReader = true;
        }

        assertTrue(exceptionOnReader);
        assertTrue(exceptionOnWriter);
    }

    /**
     * Test to assert that a writer is always assigned by the disruptor, with or without readers.
     *
     * @throws DisruptorAlreadyStartedException when disruptor is started twice without stopping
     */
    @Test
    public void testNeedExactlyOneWriter() throws DisruptorAlreadyStartedException {
        Disruptor<String> disruptor = new Disruptor<>();

        disruptor.getReader();

        boolean exceptionThrown = false;
        try {
            disruptor.initialise();
        } catch (NoWriterAssignedException e) {
            exceptionThrown = true;
        }

        assertTrue(exceptionThrown);

        disruptor = new Disruptor<>();

        exceptionThrown = false;
        try {
            disruptor.initialise();
        } catch (NoWriterAssignedException e) {
            exceptionThrown = true;
        }

        assertTrue(exceptionThrown);

    }
}
