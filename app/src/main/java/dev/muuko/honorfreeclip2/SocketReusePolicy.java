package dev.muuko.honorfreeclip2;

/** Testable guard for the very short-lived in-process RFCOMM connection cache. */
final class SocketReusePolicy {
    static final long IDLE_TTL_MS = 12_000L;
    private SocketReusePolicy() { }

    static boolean eligible(String requested, String cached, boolean connected,
                            boolean borrowed, long idleMs, boolean closing) {
        return requested != null && requested.equals(cached) && connected && !borrowed
                && !closing && idleMs >= 0 && idleMs < IDLE_TTL_MS;
    }
}
