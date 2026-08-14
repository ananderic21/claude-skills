package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.ImportJobStatus;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable state of one asynchronous import, updated from the import executor
 * thread and read by status polling — hence the atomics and volatiles.
 */
public class ImportJob {

    public enum State {
        PENDING, RUNNING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED
    }

    private final String id = UUID.randomUUID().toString();
    private final String username;
    private final Instant submittedAt = Instant.now();
    private final AtomicLong totalRecords = new AtomicLong();
    private final AtomicLong imported = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
    private volatile String error;
    private volatile Instant finishedAt;

    public ImportJob(String username) {
        this.username = username;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void markRunning() {
        state.set(State.RUNNING);
    }

    public void recordImported(long count) {
        imported.addAndGet(count);
        totalRecords.addAndGet(count);
    }

    public void recordFailed(long count) {
        failed.addAndGet(count);
        totalRecords.addAndGet(count);
    }

    public void markFinished() {
        state.set(failed.get() > 0 ? State.COMPLETED_WITH_ERRORS : State.COMPLETED);
        finishedAt = Instant.now();
    }

    public void markFailed(String reason) {
        state.set(State.FAILED);
        error = reason;
        finishedAt = Instant.now();
    }

    public boolean isFinished() {
        State current = state.get();
        return current != State.PENDING && current != State.RUNNING;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public ImportJobStatus toStatus() {
        return new ImportJobStatus(id, state.get().name(), totalRecords.get(),
                imported.get(), failed.get(), error, submittedAt, finishedAt);
    }

    @Override
    public String toString() {
        return "ImportJob[id=" + id + ", state=" + state.get() + "]";
    }
}
