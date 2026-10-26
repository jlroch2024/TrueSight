package com.truesight.news;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    @Test
    void withholdingRequiresAnAnalystNote() {
        RecommendationRepository recommendations = mock(RecommendationRepository.class);
        RecommendationService service = new RecommendationService(recommendations,
                mock(NewsArticleRepository.class), mock(NewsAssessmentRepository.class), mock(CurrentUser.class));

        assertThatThrownBy(() -> service.decide(11L,
                new RecommendationDecisionRequest(RecommendationDecision.WITHHELD, null, "  ")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Please say why this recommendation is withheld.");
    }

    @Test
    void approvalSavesEditedTextAndDecision() {
        RecommendationRepository recommendations = mock(RecommendationRepository.class);
        Recommendation recommendation = mock(Recommendation.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(recommendations.findById(11L)).thenReturn(Optional.of(recommendation));
        when(recommendation.getArticleId()).thenReturn(12L);
        when(currentUser.id()).thenReturn(5L);
        RecommendationService service = new RecommendationService(recommendations,
                mock(NewsArticleRepository.class), mock(NewsAssessmentRepository.class), currentUser);

        service.decide(11L, new RecommendationDecisionRequest(RecommendationDecision.APPROVED,
                "Check supplier delivery updates.", null));

        verify(recommendation).setEditedText("Check supplier delivery updates.");
        verify(recommendation).setStatus(RecommendationStatus.APPROVED);
        verify(recommendation).setDecidedBy(5L);
        verify(recommendations).save(recommendation);
    }
}
