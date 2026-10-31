package com.truesight.news;

import com.truesight.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Started every day by Google Cloud Scheduler. It takes the scheduler token instead of a login. */
@RestController
@RequestMapping("/api/news-checks")
@Tag(name = "News")
public class DailyNewsController {

    private final NewsCheckJob job;
    private final String schedulerToken;
    private final boolean dailyEnabled;

    public DailyNewsController(NewsCheckJob job,
                               @Value("${truesight.news.scheduler-token:}") String schedulerToken,
                               @Value("${truesight.news.daily-enabled:true}") boolean dailyEnabled) {
        this.job = job;
        this.schedulerToken = schedulerToken;
        this.dailyEnabled = dailyEnabled;
    }

    @PostMapping("/daily")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Check the news for every portfolio with an analysed holding (Cloud Scheduler)")
    public void daily(@RequestHeader(value = "X-Scheduler-Token", required = false) String token) {
        if (!tokenMatches(token)) {
            throw ApiException.unauthorized("The scheduler token is missing or wrong.");
        }
        if (dailyEnabled) {
            job.runDaily();
        }
    }

    /** Compared in constant time, so the answer's speed does not hint at how much of a guess was right. */
    private boolean tokenMatches(String token) {
        if (schedulerToken.isBlank() || token == null) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                schedulerToken.getBytes(StandardCharsets.UTF_8));
    }
}
