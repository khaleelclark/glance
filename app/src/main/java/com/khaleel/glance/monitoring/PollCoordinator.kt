package com.khaleel.glance.monitoring

import com.khaleel.glance.core.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.time.Duration
import kotlin.math.max
import kotlin.random.Random

data class PollingPolicy(
    val requestTimeoutMillis: Long = 30_000L,
    val schedulerJitterMillis: Long = 3_000L,
) {
    init {
        require(requestTimeoutMillis > 0)
        require(schedulerJitterMillis >= 0)
    }
}

enum class PollTrigger { AUTOMATIC, MANUAL }

enum class PollDecision {
    COMPLETED,
    COALESCED,
    DISABLED,
    STALE_GENERATION,
    BLOCKED,
    COOLDOWN,
    TIMED_OUT,
}

/**
 * One monotonic scheduler and one non-queuing mutex for every service instance.
 * Source/session state and provider retry policy remain owned by AppController.
 */
class PollCoordinator(
    private val dependencies: MonitoringDependencies,
    private val scope: CoroutineScope,
    private val policy: PollingPolicy = PollingPolicy(),
) {
    private val pollMutex = Mutex()
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val pollCompletions = Channel<Unit>(Channel.CONFLATED)
    private var schedulerGeneration: Long? = null
    private var schedulerJob: kotlinx.coroutines.Job? = null

    fun start(actionGeneration: Long) {
        if (schedulerGeneration == actionGeneration && schedulerJob?.isActive == true) return
        schedulerJob?.cancel()
        schedulerGeneration = actionGeneration
        schedulerJob = scope.launch { schedulerLoop(actionGeneration) }
    }

    fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    fun stop() {
        schedulerGeneration = null
        schedulerJob?.cancel()
        schedulerJob = null
        while (refreshRequests.tryReceive().isSuccess) Unit
        while (pollCompletions.tryReceive().isSuccess) Unit
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun schedulerLoop(actionGeneration: Long) {
        var nominalDueElapsed = dependencies.clock.elapsedRealtimeMillis()
        while (scope.isActive && schedulerGeneration == actionGeneration) {
            val display = dependencies.controller.display.value
            if (!display.monitoringEnabled) return
            if (display.connection.blocksAutomaticPolling()) {
                refreshRequests.receive()
                continue
            }

            val elapsedNow = dependencies.clock.elapsedRealtimeMillis()
            val interval = dependencies.controller.settings.intervalMinutes
                .coerceIn(1, 5) * 60_000L
            val cooldownDelay = dependencies.controller.settings.nextAllowedFetch
                ?.let { Duration.between(dependencies.clock.now(), it).toMillis().coerceAtLeast(0L) }
                ?: 0L
            val dueDelay = (nominalDueElapsed - elapsedNow).coerceAtLeast(0L)
            val waitMillis = max(dueDelay, cooldownDelay)

            val trigger = if (waitMillis == 0L) {
                PollTrigger.AUTOMATIC
            } else {
                select {
                    refreshRequests.onReceive { PollTrigger.MANUAL }
                    onTimeout(waitMillis) { PollTrigger.AUTOMATIC }
                }
            }

            val decision = pollIfEligible(trigger, actionGeneration)
            if (decision == PollDecision.COALESCED) {
                pollCompletions.receive()
                continue
            }
            val after = dependencies.clock.elapsedRealtimeMillis()
            if (trigger == PollTrigger.AUTOMATIC) {
                do {
                    val jitter = if (policy.schedulerJitterMillis == 0L) 0L else {
                        Random.nextLong(-policy.schedulerJitterMillis, policy.schedulerJitterMillis + 1L)
                    }
                    nominalDueElapsed += (interval + jitter).coerceAtLeast(1L)
                } while (nominalDueElapsed <= after)
            }
        }
    }

    /** Visible for focused tests; concurrent calls coalesce rather than queue. */
    internal suspend fun pollIfEligible(
        trigger: PollTrigger,
        actionGeneration: Long,
    ): PollDecision {
        if (!pollMutex.tryLock()) return PollDecision.COALESCED
        try {
            if (schedulerGeneration != null && schedulerGeneration != actionGeneration) {
                return PollDecision.STALE_GENERATION
            }
            val controller = dependencies.controller
            val display = controller.display.value
            if (!display.monitoringEnabled) return PollDecision.DISABLED
            if (display.connection.blocksAutomaticPolling()) return PollDecision.BLOCKED
            if (controller.settings.nextAllowedFetch?.isAfter(dependencies.clock.now()) == true) {
                return PollDecision.COOLDOWN
            }

            try {
                withTimeout(policy.requestTimeoutMillis) {
                    controller.poll(manual = trigger == PollTrigger.MANUAL)
                }
            } catch (_: TimeoutCancellationException) {
                controller.updateFreshness()
                render(actionGeneration)
                return PollDecision.TIMED_OUT
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                controller.updateFreshness()
            }

            if (schedulerGeneration != null && schedulerGeneration != actionGeneration) {
                return PollDecision.STALE_GENERATION
            }
            controller.updateFreshness()
            render(actionGeneration)
            return PollDecision.COMPLETED
        } finally {
            pollMutex.unlock()
            pollCompletions.trySend(Unit)
        }
    }

    private fun render(actionGeneration: Long) {
        runCatching {
            dependencies.notifications.render(
                MonitoringPresentation(dependencies.controller.display.value, actionGeneration),
            )
        }
    }
}

private fun ConnectionState.blocksAutomaticPolling(): Boolean = when (this) {
    ConnectionState.SIGN_IN_REQUIRED,
    ConnectionState.TERMS_REQUIRED,
    ConnectionState.CONNECTION_REMOVED,
    ConnectionState.PROTOCOL_ERROR,
    -> true
    else -> false
}

