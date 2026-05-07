package shufflingway.net;

public enum ActionType {

    // ── Handshake ─────────────────────────────────────────────────────────────
    HELLO,          // payload: { "name": "PlayerName" }
    READY,          // payload: {} — both sides ready, game can begin

    // ── Opening hand ──────────────────────────────────────────────────────────
    KEEP_HAND,      // payload: { "order": [cardIdx, ...] }
    MULLIGAN,       // payload: { "bottomOrder": [cardIdx, ...] }

    // ── Turn flow ─────────────────────────────────────────────────────────────
    ADVANCE_PHASE,  // payload: {}

    // ── Card actions ──────────────────────────────────────────────────────────
    PLAY_CARD,      // payload: { "handIdx": n, "discards": [idx, ...], "backups": [slot, ...] }
    ATTACK,         // payload: { "forwardIdx": n }
    RESOLVE_STACK,  // payload: {}

    // ── Utility ───────────────────────────────────────────────────────────────
    PING,           // payload: {} — keep-alive
    CHAT,           // payload: { "message": "..." }
    DISCONNECT      // payload: { "reason": "..." }
}
