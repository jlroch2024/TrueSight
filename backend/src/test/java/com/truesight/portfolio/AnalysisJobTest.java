package com.truesight.portfolio;

import com.truesight.relationship.SecondCheckPassService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisJobTest {

    @Test
    void analyzesManyHoldingsConcurrentlyUpToTheExecutorLimit() throws Exception {
        AnalysisService analysis = mock(AnalysisService.class);
        when(analysis.tickerFor(anyLong())).thenAnswer(call -> "TICKER-" + call.getArgument(0));
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch firstFourStarted = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            int active = running.incrementAndGet();
            maximum.accumulateAndGet(active, Math::max);
            firstFourStarted.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for release");
            } finally {
                running.decrementAndGet();
            }
            return null;
        }).when(analysis).analyze(anyLong());

        ExecutorService workers = Executors.newFixedThreadPool(4);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            AnalysisJob job = new AnalysisJob(analysis, mock(SecondCheckPassService.class), workers);
            Future<?> finished = caller.submit(() -> job.run(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)));

            assertThat(firstFourStarted.await(3, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            finished.get(5, TimeUnit.SECONDS);

            assertThat(maximum.get()).isEqualTo(4);
            org.mockito.Mockito.verify(analysis, org.mockito.Mockito.times(10)).analyze(anyLong());
        } finally {
            release.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
        }
    }

    @Test
    void serializesAnalysisOfTheSameCompanyAcrossPortfolioJobs() throws Exception {
        AnalysisService analysis = mock(AnalysisService.class);
        CountDownLatch tickersLookedUp = new CountDownLatch(2);
        when(analysis.tickerFor(anyLong())).thenAnswer(call -> {
            tickersLookedUp.countDown();
            return "SHARED";
        });
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch firstAnalysisStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            int active = running.incrementAndGet();
            maximum.accumulateAndGet(active, Math::max);
            firstAnalysisStarted.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for release");
            } finally {
                running.decrementAndGet();
            }
            return null;
        }).when(analysis).analyze(anyLong());

        ExecutorService workers = Executors.newFixedThreadPool(4);
        ExecutorService portfolios = Executors.newFixedThreadPool(2);
        try {
            AnalysisJob job = new AnalysisJob(analysis, mock(SecondCheckPassService.class), workers);
            Future<?> firstPortfolio = portfolios.submit(() -> job.run(List.of(1L)));
            Future<?> secondPortfolio = portfolios.submit(() -> job.run(List.of(2L)));

            assertThat(tickersLookedUp.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(firstAnalysisStarted.await(3, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(maximum.get()).isEqualTo(1);
            release.countDown();
            firstPortfolio.get(5, TimeUnit.SECONDS);
            secondPortfolio.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            portfolios.shutdownNow();
            workers.shutdownNow();
        }
    }

    @Test
    void aSingleWorkerKeepsAnalysisSequential() throws Exception {
        AnalysisService analysis = mock(AnalysisService.class);
        when(analysis.tickerFor(anyLong())).thenAnswer(call -> "TICKER-" + call.getArgument(0));
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        doAnswer(call -> {
            maximum.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(30);
            } finally {
                running.decrementAndGet();
            }
            return null;
        }).when(analysis).analyze(anyLong());

        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            new AnalysisJob(analysis, mock(SecondCheckPassService.class), workers).run(List.of(1L, 2L, 3L));
            assertThat(maximum.get()).isEqualTo(1);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void theSecondCheckRunsAfterTheHoldingIsAnalysedAndAFailureOfItIsIgnored() throws Exception {
        AnalysisService analysis = mock(AnalysisService.class);
        SecondCheckPassService secondChecks = mock(SecondCheckPassService.class);
        when(analysis.tickerFor(anyLong())).thenAnswer(call -> "TICKER-" + call.getArgument(0));
        when(analysis.reportIdOfDoneHolding(1L)).thenReturn(11L);
        when(analysis.reportIdOfDoneHolding(2L)).thenReturn(22L);
        when(analysis.reportIdOfDoneHolding(3L)).thenReturn(null); // not Done: nothing to check
        doAnswer(call -> {
            throw new IllegalStateException("The second check broke.");
        }).when(secondChecks).check(11L);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            new AnalysisJob(analysis, secondChecks, workers).run(List.of(1L, 2L, 3L));

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(analysis, secondChecks);
            order.verify(analysis).analyze(1L);
            order.verify(secondChecks).check(11L);
            org.mockito.Mockito.verify(secondChecks).check(22L);
            org.mockito.Mockito.verifyNoMoreInteractions(secondChecks);
        } finally {
            workers.shutdownNow();
        }
    }
}
