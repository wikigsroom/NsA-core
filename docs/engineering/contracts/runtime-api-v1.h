/* Normative interface specification, not a linked runtime implementation.
 * Target: Android arm64-v8a, bootstrap ABI 1. See ../03-runtime-integration.md.
 * Append fields only with struct_size negotiation; never reorder ABI 1 fields.
 * Strings are UTF-8, NUL-terminated; paths are trusted host-derived absolute
 * paths within private roots, not strings supplied by catalogue metadata.
 */
#ifndef NSA_RUNTIME_API_V1_H
#define NSA_RUNTIME_API_V1_H

#include <stddef.h>
#include <stdint.h>

struct ANativeActivity;
struct ANativeActivityCallbacks;

#if defined(__GNUC__)
#define NSA_RUNTIME_EXPORT __attribute__((visibility("default")))
#else
#define NSA_RUNTIME_EXPORT
#endif

#ifdef __cplusplus
extern "C" {
#endif

enum { NSA_RUNTIME_API_VERSION_1 = 1 };

typedef enum NsaRuntimeResultV1 {
    NSA_RUNTIME_OK = 0,
    NSA_RUNTIME_INVALID_ARGUMENT = -1,
    NSA_RUNTIME_API_MISMATCH = -2,
    NSA_RUNTIME_IDENTITY_MISMATCH = -3,
    NSA_RUNTIME_ALREADY_STARTED = -4,
    NSA_RUNTIME_NOT_STARTED = -5,
    NSA_RUNTIME_INTERNAL_ERROR = -6
} NsaRuntimeResultV1;

typedef enum NsaRuntimeCommandV1 {
    NSA_RUNTIME_PAUSE = 1,
    NSA_RUNTIME_RESUME = 2,
    NSA_RUNTIME_REQUEST_NORMAL_EXIT = 3,
    NSA_RUNTIME_CANCEL_LAUNCH = 4
} NsaRuntimeCommandV1;

typedef struct NsaRuntimeInfoV1 {
    uint32_t struct_size;
    uint32_t runtime_api_version;
    const char *runtime_slot_id;
    const char *core_revision;
    const char *profile_sha256;
    const char *module_build_ids_json;
    const char *shader_chain_id;
    uint32_t settings_schema_version;
    uint32_t reserved_zero;
} NsaRuntimeInfoV1;

typedef struct NsaRuntimeHostV1 {
    uint32_t struct_size;
    uint32_t runtime_api_version;
    void *host_user;
    /* Any runtime thread may emit. Host copies before returning, bounds at
     * 16 KiB, validates runtime-event.schema.json and posts without blocking
     * on UI/Binder. Events carry session/generation/strictly increasing seq;
     * producers serialize sequence allocation and enqueue order together.
     * No input/frame flood, raw save bytes, credentials or private paths. */
    int32_t (*emit_event_json)(void *host_user, const char *json, size_t bytes);
    /* Thread-safe cooperative cancellation, including launch cancellation. */
    int32_t (*is_cancel_requested)(void *host_user);
    /* Bootstrap owns activity->callbacks throughout. The runtime supplies
     * a table here exactly once; host copies the table, serializes dispatch
     * on the activity thread, and replays current Surface/InputQueue state
     * once. Runtime must never replace activity->callbacks directly.
     * Module and callback code remain loaded until the :game process exits. */
    int32_t (*install_activity_callbacks)(
        void *host_user, const struct ANativeActivityCallbacks *callbacks);
    /* Diagnostic data only; bounded/filtered by host, no user secrets. */
    void (*log_message)(void *host_user, int32_t level, const char *message);
} NsaRuntimeHostV1;

typedef struct NsaRuntimeContextV1 {
    uint32_t struct_size;
    uint32_t runtime_api_version;
    const char *session_id;
    uint32_t generation;
    uint32_t reserved_zero;
    const char *game_id;
    const char *release_id;
    const char *local_player_id;
    const char *runtime_slot_id;
    const char *manifest_sha256;
    /* Android owns Activity lifetime. Bootstrap coordinates destroy; runtime
     * must stop using this pointer by the end of onDestroy, even if the process
     * is still alive. It may not retain Java local references. */
    struct ANativeActivity *activity;
    const char *package_root;    /* Immutable, read-only by contract. */
    const char *romfs_root;      /* Immutable, read-only by contract. */
    const char *shader_root;     /* Immutable, read-only by contract. */
    const char *save_root;       /* game/player + committed/work separation. */
    const char *settings_root;
    const char *cache_root;
    const char *temp_root;       /* Session-owned staging, never save root. */
    const char *log_root;
    const char *settings_json;   /* Validated immutable settings snapshot. */
    const NsaRuntimeHostV1 *host;
} NsaRuntimeContextV1;

/* Caller initializes info->struct_size. Runtime fills only supported bytes.
 * Identity strings are immutable static module storage. No startup side effect. */
NSA_RUNTIME_EXPORT int32_t nsa_runtime_get_info_v1(NsaRuntimeInfoV1 *info);

/* Called once per fresh :game process, on the activity thread. Copies all
 * needed context strings/settings/host table before returning. Starts work
 * asynchronously: OK means accepted, never "interactive" or "save committed".
 * Surface/queue may already exist and are delivered by bootstrap above.
 * Must not block the activity thread on graphics preparation or game loop. */
NSA_RUNTIME_EXPORT int32_t nsa_runtime_start_v1(
    const NsaRuntimeContextV1 *context);

/* Thread-safe, nonblocking request; final state reported by correlated events.
 * No "force kill" command: force exit requires user choice and host process
 * management. Normal exit requests finish a valid save transaction if possible,
 * release resources and emit exit_complete; no fabricated save success.
 * No dlclose or in-process second-game restart is allowed in ABI 1. */
NSA_RUNTIME_EXPORT int32_t nsa_runtime_request_v1(
    const char *session_id, uint32_t generation, NsaRuntimeCommandV1 command);

#ifdef __cplusplus
}
#endif
#endif /* NSA_RUNTIME_API_V1_H */
