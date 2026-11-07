package com.truesight.relationship;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The job's batches and passes. What the AI's answer holds is {@link HighlightFinderTest}'s job. */
class HighlightJobTest {

    private final RelationshipRepository relationships = mock(RelationshipRepository.class);
    private final HighlightFinder finder = mock(HighlightFinder.class);
    private final HighlightSaver saver = mock(HighlightSaver.class);
    private final HighlightJob job = new HighlightJob(relationships, finder, saver);

    @Test
    void worksThroughEveryUnreadQuoteTwentyFiveAtATime() {
        when(relationships.findByHighlightedAtIsNullOrderByIdAsc()).thenReturn(pending(60));
        List<Integer> sizes = new ArrayList<>();
        when(finder.find(any())).thenAnswer(call -> {
            sizes.add(call.<List<Relationship>>getArgument(0).size());
            return Map.of(1L, List.of());
        });

        job.run();

        assertThat(sizes).containsExactly(25, 25, 10);
        verify(saver, times(3)).save(any());
    }

    @Test
    void withNothingUnreadGeminiIsNotAsked() {
        when(relationships.findByHighlightedAtIsNullOrderByIdAsc()).thenReturn(List.of());

        job.run();

        verify(finder, never()).find(any());
    }

    @Test
    void aBatchWhoseAnswerCouldNotBeReadIsNotSavedAndIsReadAgainOnlyOnTheNextPass() {
        when(relationships.findByHighlightedAtIsNullOrderByIdAsc()).thenReturn(pending(1));
        when(finder.find(any())).thenReturn(Map.of());

        job.run();

        verify(finder, times(1)).find(any());
        verify(saver, never()).save(any());

        job.run();

        verify(finder, times(2)).find(any());
    }

    @Test
    void whenGeminiFailsThePassStopsRatherThanFailingForEveryBatch() {
        when(relationships.findByHighlightedAtIsNullOrderByIdAsc()).thenReturn(pending(60));
        when(finder.find(any())).thenThrow(new GeminiClient.GeminiException("The AI could not be reached.", null));

        job.run();

        verify(finder, times(1)).find(any());
        verify(saver, never()).save(any());
    }

    @Test
    void anotherFailureLosesOnlyItsOwnBatch() {
        when(relationships.findByHighlightedAtIsNullOrderByIdAsc()).thenReturn(pending(30));
        when(finder.find(any())).thenThrow(new IllegalStateException("One batch failed.")).thenReturn(Map.of(1L, List.of()));

        job.run();

        verify(finder, times(2)).find(any());
        verify(saver, times(1)).save(any());
    }

    @Test
    void askedWhileAPassIsRunningItWaitsItsTurnAndThenGoesRoundAgain() throws Exception {
        when(relationships.findByHighlightedAtIsNullOrderByIdAsc()).thenReturn(pending(1));
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(finder.find(any())).thenAnswer(call -> {
            reading.countDown();
            assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
            return Map.of(1L, List.of());
        });
        ExecutorService first = Executors.newSingleThreadExecutor();
        try {
            Future<?> running = first.submit(job::run);
            assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();

            // Returns at once, without a second call to Gemini beside the first.
            job.run();
            job.run();
            verify(finder, times(1)).find(any());

            finish.countDown();
            running.get(5, TimeUnit.SECONDS);

            // The two requests made while it ran become one more pass.
            verify(finder, times(2)).find(any());
        } finally {
            finish.countDown();
            first.shutdownNow();
        }
    }

    private static List<Relationship> pending(int count) {
        List<Relationship> found = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            Relationship relationship = new Relationship(1L, 1L, 2L, RelationshipType.SUPPLIER, null, "Evidence.");
            ReflectionTestUtils.setField(relationship, "id", id);
            found.add(relationship);
        }
        return found;
    }
}
