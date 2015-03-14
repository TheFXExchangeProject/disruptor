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

    @Test
    public void testPerformance() throws DisruptorAlreadyStartedException, WriterAlreadyAssignedException, NoWriterAssignedException, InterruptedException {
        LOGGER.info("Running warm up run...");
        performanceTestRun(10000, 10000000);


        LOGGER.info("Running actual runs...");
        performanceTestRun(1024, 10000000);
        performanceTestRun(2048, 10000000);
        performanceTestRun(4096, 10000000);
        performanceTestRun(8192, 10000000);
        performanceTestRun(16384, 10000000);
    }

    private void performanceTestRun(final int bufferSize, final int testSize) throws DisruptorAlreadyStartedException, WriterAlreadyAssignedException, NoWriterAssignedException, InterruptedException {
        Disruptor<TestTracker> disruptor = new Disruptor<>(bufferSize);

        final List<TestTracker> store = new ArrayList<>(testSize);
        final FXReader<TestTracker> secondReader = disruptor.getReader();
        final FXReader<TestTracker> firstReader = disruptor.getReader();
        final FXWriter<TestTracker> fxWriter = disruptor.getWriter();

        disruptor.initialise();

        final CountDownLatch countDownLatch = new CountDownLatch(2);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (long i = 0; i < testSize; i++) {
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
                for (long i = 0; i < testSize; i++) {
                    TestTracker testTracker = secondReader.readNext();
                    testTracker.setSecondRead(System.currentTimeMillis());
                }

                countDownLatch.countDown();
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (long i = 0; i < testSize; i++) {
                    fxWriter.writeNext(new TestTracker(System.currentTimeMillis()));
                }
            }
        }).start();

        long testStart = System.currentTimeMillis();

        countDownLatch.await();
        long totalTestTime = System.currentTimeMillis() - testStart;
        LOGGER.info("Finished testing. Generating results...");

        Collections.sort(store);

        LOGGER.info("ResultSet for [testSize: {}], [bufferSizeUsed: {}]", testSize, disruptor.getPowerOfTwo(bufferSize));
        LOGGER.info("Max latency was: {}ms", store.get(store.size() - 1).getTimeOnBuffer());
        LOGGER.info("99.9 percentile latency was: {}ms", store.get((int) (store.size() * 0.999)).getTimeOnBuffer());
        LOGGER.info("99 percentile latency was: {}ms", store.get((int) (store.size() * 0.99)).getTimeOnBuffer());
        LOGGER.info("95 percentile latency was: {}ms", store.get((int) (store.size() * 0.95)).getTimeOnBuffer());
        LOGGER.info("90 percentile latency was: {}ms", store.get((int) (store.size() * 0.90)).getTimeOnBuffer());
        LOGGER.info("50 percentile latency was: {}ms", store.get((int) (store.size() * 0.50)).getTimeOnBuffer());
        LOGGER.info("Min latency was: {}ms", store.get(0).getTimeOnBuffer());
        LOGGER.info("Total time: {}ms", totalTestTime);
        LOGGER.info("Mean: {}us", ((double)totalTestTime)/(testSize/1000));
        LOGGER.info("Average throughput: {} ops/s", (long)1000*testSize/totalTestTime);
        LOGGER.info("Running cleanup GC");
        System.gc();
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
