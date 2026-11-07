/* Linked only for explicitly selected unified-hall builds. Standalone APKs keep their entry. */
#ifdef DM_HALL_BINDING
#include <android/native_activity.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include "hall_identity.h"
#include "../../../docs/engineering/contracts/runtime-api-v1.h"

NSA_RUNTIME_EXPORT const char nsa_runtime_identity_json_v1[] __attribute__((section(".nsa_identity"))) = NSA_RUNTIME_IDENTITY_JSON;

extern void dm_front_hall_on_create(ANativeActivity *, void *, size_t);
extern void dm_front_hall_command(int);
extern uint64_t dm_fs_commit_count(void);
static NsaRuntimeHostV1 host;
static NsaRuntimeContextV1 context;
static ANativeActivity shadow;
static struct ANativeActivityCallbacks callbacks;
static pthread_mutex_t emit_mu = PTHREAD_MUTEX_INITIALIZER;
static unsigned sequence;
static _Atomic int started, presented, stopping;
static uint64_t base_ms;

/* A fresh process owns this pipe. Producers have bounded kernel buffering;
   the writer retains at most four 10 MiB files in the current session. */
typedef struct { int reader, writer; char root[4096]; } HallLog;
static HallLog log_sink;
static void log_path(char *out, size_t size, int index) {
    if (index) snprintf(out, size, "%s/log.%d.txt", log_sink.root, index);
    else snprintf(out, size, "%s/log.txt", log_sink.root);
}
static void *log_drain(void *unused) {
    (void)unused; char buffer[4096], path[4160], older[4160];
    uint64_t written = 0; ssize_t count;
    while ((count = read(log_sink.reader, buffer, sizeof buffer)) != 0) {
        if (count < 0) { if (errno == EINTR) continue; break; }
        if (written + (uint64_t)count > 10u * 1024u * 1024u) {
            close(log_sink.writer);
            log_path(path, sizeof path, 3); unlink(path);
            for (int i = 2; i >= 0; i--) { log_path(path, sizeof path, i); log_path(older, sizeof older, i + 1); rename(path, older); }
            log_path(path, sizeof path, 0);
            log_sink.writer = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600); written = 0;
        }
        size_t offset = 0;
        while (log_sink.writer >= 0 && offset < (size_t)count) {
            ssize_t done = write(log_sink.writer, buffer + offset, (size_t)count - offset);
            if (done < 0 && errno == EINTR) continue;
            if (done <= 0) { close(log_sink.writer); log_sink.writer = -1; break; }
            offset += (size_t)done; written += (uint64_t)done;
        }
    }
    if (log_sink.writer >= 0) close(log_sink.writer);
    close(log_sink.reader); return NULL;
}
int dm_hall_log_open(const char *root) {
    int pipe_fds[2]; char path[4160];
    if (!root || strlen(root) >= sizeof log_sink.root || pipe2(pipe_fds, O_CLOEXEC)) return -1;
    strcpy(log_sink.root, root); log_path(path, sizeof path, 0);
    log_sink.writer = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    log_sink.reader = pipe_fds[0];
    if (log_sink.writer < 0) { close(pipe_fds[0]); close(pipe_fds[1]); return -1; }
    pthread_t thread;
    if (pthread_create(&thread, NULL, log_drain, NULL)) { close(log_sink.writer); close(pipe_fds[0]); close(pipe_fds[1]); return -1; }
    pthread_detach(thread);
    int result = dup2(pipe_fds[1], STDERR_FILENO); close(pipe_fds[1]);
    return result < 0 ? -1 : 0;
}

static uint64_t millis(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return (uint64_t)t.tv_sec * 1000 + t.tv_nsec / 1000000; }
static void event(const char *type, uint64_t commit) {
    pthread_mutex_lock(&emit_mu);
    char message[1200], save[100] = "";
    if (commit) snprintf(save, sizeof save, ",\"save_commit_seq\":%llu", (unsigned long long)commit);
    snprintf(message, sizeof message, "{\"event_schema\":1,\"session_id\":\"%s\",\"generation\":%u,\"seq\":%u,\"event_type\":\"%s\",\"monotonic_elapsed_ms\":\"%llu\"%s}",
             context.session_id, context.generation, ++sequence, type, (unsigned long long)(millis() - base_ms), save);
    host.emit_event_json(host.host_user, message, strlen(message));
    pthread_mutex_unlock(&emit_mu);
}
void dm_hall_frame_presented(void) {
    if (atomic_load(&started) && !atomic_exchange(&presented, 1)) event("first_frame", 0);
}
static void *monitor(void *unused) {
    (void)unused; uint64_t last = 0;
    while (!atomic_load(&stopping) && !host.is_cancel_requested(host.host_user)) {
        uint64_t committed = dm_fs_commit_count();
        if (committed > last) { last = committed; event("save_committed", committed); }
        usleep(100000);
    }
    return NULL;
}
void dm_hall_front_destroy(void) { if (atomic_load(&stopping)) event("exit_complete", 0); }
NSA_RUNTIME_EXPORT int32_t nsa_runtime_get_info_v1(NsaRuntimeInfoV1 *info) {
    if (!info || info->struct_size < sizeof *info) return NSA_RUNTIME_INVALID_ARGUMENT;
    *info = (NsaRuntimeInfoV1){sizeof *info, 1, NSA_SLOT_ID, NSA_CORE_REVISION, NSA_PROFILE_SHA256, NSA_MODULE_IDS, NSA_SHADER_CHAIN, 1, 0};
    return 0;
}
NSA_RUNTIME_EXPORT int32_t nsa_runtime_start_v1(const NsaRuntimeContextV1 *value) {
    if (!value || value->struct_size < sizeof *value || value->runtime_api_version != 1 || !value->activity || !value->host) return NSA_RUNTIME_INVALID_ARGUMENT;
    if (strcmp(value->runtime_slot_id, NSA_SLOT_ID)) return NSA_RUNTIME_IDENTITY_MISMATCH;
    if (atomic_exchange(&started, 1)) return NSA_RUNTIME_ALREADY_STARTED;
    context = *value; host = *value->host; base_ms = millis();
    /* Bootstrap owns immutable context storage for the lifetime of this process. */
    setenv("DM_HALL_ROOT", value->settings_root, 1); setenv("DM_ROOT", value->package_root, 1);
    setenv("DM_ROMFS", value->romfs_root, 1); setenv("DM_SAVE", value->save_root, 1); setenv("DM_TEMP", value->temp_root, 1);
    setenv("DM_HALL_LOG_ROOT", value->log_root, 1);
    setenv("DM_HALL_CACHE_ROOT", value->cache_root, 1);
    setenv("DM_AUDIO_MUTE", strstr(value->settings_json, "\"audio\":false") ? "1" : "0", 1);
    for (unsigned i = 0; i < NSA_ENV_COUNT; i++) {
        const char *setting = NSA_ENV_VALUES[i]; char resolved[4096];
        if (!strncmp(setting, "${PACKAGE_ROOT}/", 16)) { snprintf(resolved, sizeof resolved, "%s/%s", value->package_root, setting + 16); setting = resolved; }
        setenv(NSA_ENV_KEYS[i], setting, 1);
    }
    char shaders[4096]; snprintf(shaders, sizeof shaders, "%s/shaders.dmsv", value->shader_root); setenv("DM_VK_SHADERS", shaders, 1);
    event("validating", 0);
    shadow = *value->activity; shadow.callbacks = &callbacks;
    event("loading_modules", 0);
    dm_front_hall_on_create(&shadow, NULL, 0);
    /* The legacy frontend receives a private Activity copy and callback table;
       it never replaces bootstrap's table on the actual Android Activity. */
    if (host.install_activity_callbacks(host.host_user, &callbacks)) return NSA_RUNTIME_INTERNAL_ERROR;
    event("runtime_entered", 0);
    pthread_t thread; if (!pthread_create(&thread, NULL, monitor, NULL)) pthread_detach(thread);
    return 0;
}
NSA_RUNTIME_EXPORT int32_t nsa_runtime_request_v1(const char *session, uint32_t generation, NsaRuntimeCommandV1 command) {
    if (!atomic_load(&started)) return NSA_RUNTIME_NOT_STARTED;
    if (!session || strcmp(session, context.session_id) || generation != context.generation) return NSA_RUNTIME_IDENTITY_MISMATCH;
    if (command == NSA_RUNTIME_REQUEST_NORMAL_EXIT || command == NSA_RUNTIME_CANCEL_LAUNCH) {
        atomic_store(&stopping, 1); event("exit_requested", 0);
        /* Never synthesize a save. The frontend ends only this game's process. */
        dm_front_hall_command(command);
        return 0;
    }
    if (command != NSA_RUNTIME_PAUSE && command != NSA_RUNTIME_RESUME) return NSA_RUNTIME_INVALID_ARGUMENT;
    dm_front_hall_command(command); return 0;
}
#endif
