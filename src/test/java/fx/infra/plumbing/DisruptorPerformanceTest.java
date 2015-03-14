package fx.infra.plumbing;


import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class DisruptorPerformanceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisruptorPerformanceTest.class);
    private static final int BUFFER_SIZE = 10000;
    private static final int TEST_SIZE = 10000000;

    @Test
    public void testPerformance() throws DisruptorAlreadyStartedException, WriterAlreadyAssignedException, NoWriterAssignedException, InterruptedException {
        LOGGER.info("Running warm up run...");
        performanceTestRun();

        System.gc();

        LOGGER.info("Running actual run...");
        performanceTestRun();
    }

    private void performanceTestRun() throws DisruptorAlreadyStartedException, WriterAlreadyAssignedException, NoWriterAssignedException, InterruptedException {
        Disruptor<TestTracker> disruptor = new Disruptor<>(BUFFER_SIZE);

        final List<TestTracker> store = new ArrayList<>(TEST_SIZE);
        final FXReader<TestTracker> secondReader = disruptor.getReader();
        final FXReader<TestTracker> firstReader = disruptor.getReader();
        final FXWriter<TestTracker> fxWriter = disruptor.getWriter();

        disruptor.initialise();

        final CountDownLatch countDownLatch = new CountDownLatch(2);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (long i = 0; i < TEST_SIZE; i++) {
                    TestTracker testTracker = firstReader.readNext();
                    testTracker.setFirstRead(System.currentTimeMillis());
                    store.add(testTracker);
                }

                countDownLatch.countDown();
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (long i = 0; i < TEST_SIZE; i++) {
                    TestTracker testTracker = secondReader.readNext();
                    testTracker.setSecondRead(System.currentTimeMillis());
                }

                countDownLatch.countDown();
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (long i = 0; i < TEST_SIZE; i++) {
                    fxWriter.writeNext(new TestTracker(System.currentTimeMillis()));
                }
            }
        }).start();

        long testStart = System.currentTimeMillis();

        countDownLatch.await();
        long totalTestTime = System.currentTimeMillis() - testStart;
        LOGGER.info("Finished testing. Generating results...");

        Collections.sort(store);

        LOGGER.info("Max latency was: {}ms", store.get(store.size() - 1).getTimeOnBuffer());
        LOGGER.info("99.9 percentile latency was: {}ms", store.get((int) (store.size() * 0.999)).getTimeOnBuffer());
        LOGGER.info("99 percentile latency was: {}ms", store.get((int) (store.size() * 0.99)).getTimeOnBuffer());
        LOGGER.info("95 percentile latency was: {}ms", store.get((int) (store.size() * 0.95)).getTimeOnBuffer());
        LOGGER.info("90 percentile latency was: {}ms", store.get((int) (store.size() * 0.90)).getTimeOnBuffer());
        LOGGER.info("50 percentile latency was: {}ms", store.get((int) (store.size() * 0.50)).getTimeOnBuffer());
        LOGGER.info("Min latency was: {}ms", store.get(0).getTimeOnBuffer());
        LOGGER.info("Total time: {}ms", totalTestTime);
        LOGGER.info("Mean: {}us", ((double)totalTestTime)/(TEST_SIZE/1000));
        LOGGER.info("Average throughput: {} ops/s", (long)1000*TEST_SIZE/totalTestTime);
    }

    private class TestTracker implements Comparable<TestTracker> {
        private volatile long entryTime;
        private volatile long firstRead;
        private volatile long secondRead;

        public TestTracker(long entryTime) {
            this.entryTime = entryTime;
        }

        public void setFirstRead(long firstRead) {
            this.firstRead = firstRead;
        }


        public void setSecondRead(long secondRead) {
            this.secondRead = secondRead;
        }

        @Override
        public int compareTo(TestTracker testTracker) {
            return (int) ((firstRead - entryTime) - (testTracker.firstRead - testTracker.entryTime));
        }

        public long getTimeOnBuffer() {
            return secondRead > firstRead
                    ? secondRead - entryTime
                    : firstRead - entryTime;
        }
    }

}
