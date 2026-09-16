package com.agentcart.recommendation.service;

import com.agentcart.recommendation.dto.AgentRecommendation;
import com.agentcart.recommendation.dto.RecommendationAgentActionCode;
import com.agentcart.recommendation.dto.RecommendationAgentOutcome;
import com.agentcart.recommendation.dto.RecommendationAgentResult;
import com.agentcart.recommendation.dto.RecommendationResult;
import com.agentcart.recommendation.dto.RecommendationServedEvent;
import com.agentcart.recommendation.dto.RecommendationStreamDone;
import com.agentcart.recommendation.dto.RecommendationStreamError;
import com.agentcart.recommendation.dto.RecommendationStreamEvent;
import com.agentcart.recommendation.dto.RecommendationStreamStatus;
import com.agentcart.recommendation.dto.RecommendationStreamStatusPhase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RecommendationStreamService {

    private final RecommendationAgentService agentService;
    private final RecommendationEventProducer eventProducer;

    @Autowired
    public RecommendationStreamService(
            RecommendationAgentService agentService,
            ObjectProvider<RecommendationEventProducer> eventProducerProvider) {
        this(agentService, eventProducerProvider.getIfAvailable());
    }

    public RecommendationStreamService(
            RecommendationAgentService agentService, RecommendationEventProducer eventProducer) {
        this.agentService = agentService;
        this.eventProducer = eventProducer;
    }

    public void stream(String query, Long memberId, RecommendationCancellationToken cancellationToken,
                       RecommendationStreamSink sink) {
        StreamSession session = new StreamSession(cancellationToken, sink);
        RecommendationAgentResult result;
        try {
            result = agentService.recommend(query, memberId, cancellationToken,
                    (attempt, fallback) -> session.status(statusFor(attempt, fallback)));
        } catch (RuntimeException e) {
            session.error(new RecommendationStreamError(null,
                    RecommendationAgentActionCode.PROCESSING_FAILED,
                    "추천 처리에 실패했습니다.", true));
            return;
        }

        if (session.closed()) return;
        if (result.outcome() == RecommendationAgentOutcome.FAILED) {
            session.error(new RecommendationStreamError(result.requestId(), result.actionCode(),
                    result.message(), retryable(result.actionCode())));
            return;
        }

        for (AgentRecommendation recommendation : result.recommendations()) {
            RecommendationResult streamResult = toStreamResult(recommendation);
            if (!session.result(streamResult)) return;
            publish(query, memberId, streamResult);
        }
        session.done(new RecommendationStreamDone(result.requestId(), result.outcome(), result.actionCode(),
                result.message(), result.recommendations().size()));
    }

    private RecommendationStreamStatus statusFor(int attempt, boolean fallback) {
        if (fallback) {
            return new RecommendationStreamStatus(RecommendationStreamStatusPhase.FALLBACK_SEARCHING, attempt,
                    "일반 검색으로 상품을 찾고 있습니다.");
        }
        if (attempt > 1) {
            return new RecommendationStreamStatus(RecommendationStreamStatusPhase.RESEARCHING, attempt,
                    "검색 조건을 조정해 다시 찾고 있습니다.");
        }
        return new RecommendationStreamStatus(RecommendationStreamStatusPhase.SEARCHING, attempt,
                "조건에 맞는 상품을 찾고 있습니다.");
    }

    private RecommendationResult toStreamResult(AgentRecommendation recommendation) {
        return new RecommendationResult(recommendation.productId(), recommendation.productName(),
                recommendation.price(), recommendation.reason(), List.of(), recommendation.score());
    }

    private boolean retryable(RecommendationAgentActionCode code) {
        return code == RecommendationAgentActionCode.DEADLINE_EXCEEDED
                || code == RecommendationAgentActionCode.PROCESSING_FAILED;
    }

    private void publish(String query, Long memberId, RecommendationResult result) {
        if (eventProducer == null) return;
        eventProducer.publish(new RecommendationServedEvent(UUID.randomUUID().toString(), memberId, query,
                result.productId(), result.productName(), result.reason(), result.score()));
    }

    private static final class StreamSession {
        private final RecommendationCancellationToken cancellationToken;
        private final RecommendationStreamSink sink;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean disconnected = new AtomicBoolean();

        private StreamSession(RecommendationCancellationToken cancellationToken, RecommendationStreamSink sink) {
            this.cancellationToken = cancellationToken;
            this.sink = sink;
        }

        private void status(RecommendationStreamStatus status) {
            sendNonTerminal(RecommendationStreamEvent.status(status));
        }

        private boolean result(RecommendationResult result) {
            return sendNonTerminal(RecommendationStreamEvent.result(result));
        }

        private void done(RecommendationStreamDone done) {
            sendTerminal(RecommendationStreamEvent.done(done));
        }

        private void error(RecommendationStreamError error) {
            sendTerminal(RecommendationStreamEvent.error(error));
        }

        private boolean sendNonTerminal(RecommendationStreamEvent<?> event) {
            if (terminal.get() || disconnected.get() || cancellationToken.isCancelled()) return false;
            return send(event);
        }

        private void sendTerminal(RecommendationStreamEvent<?> event) {
            if (disconnected.get() || cancellationToken.isCancelled()
                    || !terminal.compareAndSet(false, true)) return;
            send(event);
        }

        private boolean send(RecommendationStreamEvent<?> event) {
            try {
                sink.send(event);
                return true;
            } catch (IOException | RuntimeException e) {
                disconnected.set(true);
                cancellationToken.cancel();
                return false;
            }
        }

        private boolean closed() {
            return disconnected.get() || cancellationToken.isCancelled();
        }
    }
}
