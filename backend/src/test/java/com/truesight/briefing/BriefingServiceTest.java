package com.truesight.briefing;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BriefingServiceTest {

    @Test
    void theTimeZoneTheWebsiteSendsIsTheOneTheBriefingUses() {
        assertThat(BriefingService.zoneOf("Asia/Singapore")).isEqualTo(ZoneId.of("Asia/Singapore"));
        assertThat(BriefingService.zoneOf(" America/New_York ")).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    void noTimeZoneOrOneThatIsNotRealMeansUtc() {
        assertThat(BriefingService.zoneOf(null)).isEqualTo(ZoneOffset.UTC);
        assertThat(BriefingService.zoneOf("  ")).isEqualTo(ZoneOffset.UTC);
        assertThat(BriefingService.zoneOf("Not/AZone")).isEqualTo(ZoneOffset.UTC);
        assertThat(BriefingService.zoneOf("<script>")).isEqualTo(ZoneOffset.UTC);
    }
}
