/* NsA 2048, an original MIT-licensed native game. Not Switch compatibility evidence. */
#include <android/input.h>
#include <android/looper.h>
#include <android/native_activity.h>
#include <android/native_window.h>
#include <aaudio/AAudio.h>
#include <fcntl.h>
#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include "../../../docs/engineering/contracts/runtime-api-v1.h"
#include "hall_identity.h"

typedef struct { uint32_t magic, version, cells[16], previous[16], score, previous_score, best, moves, random, checksum; } State;
static State state;
static NsaRuntimeContextV1 context;
static NsaRuntimeHostV1 host;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t event_mutex = PTHREAD_MUTEX_INITIALIZER;
static ANativeWindow *window;
static AInputQueue *queue, *attached;
static _Atomic int stopped, paused, tone_frames;
static int started, first_frame, interactive, dirty = 1, undo_available;
static uint32_t sequence, commit_sequence;
static uint64_t began;
static int board_x, board_y, board_size, cell_size;
static float touch_x, touch_y;
static AAudioStream *audio;

static uint64_t millis(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return (uint64_t)t.tv_sec * 1000 + t.tv_nsec / 1000000; }
static void event(const char *type, uint32_t save) {
    pthread_mutex_lock(&event_mutex);
    char json[1024], extra[100] = "";
    if (save) snprintf(extra, sizeof extra, ",\"save_commit_seq\":%u", save);
    snprintf(json, sizeof json, "{\"event_schema\":1,\"session_id\":\"%s\",\"generation\":%u,\"seq\":%u,\"event_type\":\"%s\",\"monotonic_elapsed_ms\":\"%llu\"%s}", context.session_id, context.generation, ++sequence, type, (unsigned long long)(millis() - began), extra);
    host.emit_event_json(host.host_user, json, strlen(json));
    pthread_mutex_unlock(&event_mutex);
}
static uint32_t random_next(void) { uint32_t x = state.random; x ^= x << 13; x ^= x >> 17; x ^= x << 5; return state.random = x ? x : 0x13579bdf; }
static uint32_t checksum(State *s) { const unsigned char *p = (const unsigned char *)s; uint32_t h = 2166136261u; for (size_t i = 0; i < sizeof *s - 4; i++) h = (h ^ p[i]) * 16777619u; return h; }
static int save(void) {
    char current[4096], next[4096]; snprintf(current, sizeof current, "%s/state.dat", context.save_root); snprintf(next, sizeof next, "%s/state.new", context.save_root);
    state.magic = 0x4e534132; state.version = 1; state.checksum = checksum(&state);
    int file = open(next, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (file < 0) return -1;
    ssize_t count = write(file, &state, sizeof state); int error = fsync(file); close(file);
    if (count != sizeof state || error || rename(next, current)) return -1;
    int directory = open(context.save_root, O_RDONLY | O_DIRECTORY); if (directory < 0) return -1;
    error = fsync(directory); close(directory); if (error) return -1;
    event("save_committed", ++commit_sequence); return 0;
}
static void spawn(void) { int available[16], n = 0; for (int i = 0; i < 16; i++) if (!state.cells[i]) available[n++] = i; if (n) state.cells[available[random_next() % n]] = random_next() % 10 ? 2 : 4; }
static void new_game(void) { uint32_t best = state.best; memset(&state, 0, sizeof state); state.best = best; state.random = (uint32_t)millis() | 1; spawn(); spawn(); undo_available = 0; dirty = 1; save(); }
static int can_move(void) { for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) { int i = y * 4 + x; if (!state.cells[i] || (x < 3 && state.cells[i] == state.cells[i + 1]) || (y < 3 && state.cells[i] == state.cells[i + 4])) return 1; } return 0; }
static void move(int direction) {
    uint32_t old[16]; memcpy(old, state.cells, sizeof old); uint32_t score = state.score;
    for (int row = 0; row < 4; row++) {
        int locations[4]; uint32_t values[4] = {0}, output[4] = {0}; int n = 0, out = 0;
        for (int col = 0; col < 4; col++) { int i = direction == 0 ? row * 4 + col : direction == 1 ? row * 4 + 3 - col : direction == 2 ? col * 4 + row : (3 - col) * 4 + row; locations[col] = i; if (state.cells[i]) values[n++] = state.cells[i]; }
        for (int col = 0; col < n; col++) { uint32_t value = values[col]; if (col + 1 < n && values[col + 1] == value) { value *= 2; state.score += value; col++; } output[out++] = value; }
        for (int col = 0; col < 4; col++) state.cells[locations[col]] = output[col];
    }
    if (!memcmp(old, state.cells, sizeof old)) return;
    memcpy(state.previous, old, sizeof old); state.previous_score = score; undo_available = 1;
    spawn(); state.moves++; if (state.score > state.best) state.best = state.score; dirty = 1;
    atomic_store(&tone_frames, 3500);
    if (!interactive) { interactive = 1; event("interactive", 0); }
    if (save()) host.log_message(host.host_user, 3, "2048 save commit failed; no success event was emitted");
}
static void undo(void) { if (!undo_available) return; memcpy(state.cells, state.previous, sizeof state.cells); state.score = state.previous_score; undo_available = 0; dirty = 1; save(); }
static aaudio_data_callback_result_t sound(AAudioStream *stream, void *user, void *data, int32_t frames) {
    (void)stream; (void)user; float *out = data; static float phase;
    int remaining = atomic_load(&tone_frames);
    for (int i = 0; i < frames; i++) { float value = (!atomic_load(&paused) && remaining > 0) ? sinf(phase) * .07f * fminf(remaining / 1000.f, 1.f) : 0; phase += .0575959f; if (phase > 6.2831853f) phase -= 6.2831853f; out[i * 2] = out[i * 2 + 1] = value; if (remaining > 0) remaining--; }
    atomic_store(&tone_frames, remaining); return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
static void rectangle(ANativeWindow_Buffer *buffer, int x, int y, int width, int height, uint32_t color) {
    int left = x < 0 ? 0 : x, top = y < 0 ? 0 : y, right = x + width > buffer->width ? buffer->width : x + width, bottom = y + height > buffer->height ? buffer->height : y + height;
    for (int row = top; row < bottom; row++) { uint32_t *pixels = (uint32_t *)buffer->bits + row * buffer->stride; for (int col = left; col < right; col++) pixels[col] = color; }
}
static const unsigned char digits[10][7] = {{14,17,19,21,25,17,14},{4,12,4,4,4,4,14},{14,17,1,2,4,8,31},{30,1,1,14,1,1,30},{2,6,10,18,31,2,2},{31,16,16,30,1,1,30},{14,16,16,30,17,17,14},{31,1,2,4,8,8,8},{14,17,17,14,17,17,14},{14,17,17,15,1,1,14}};
static void number(ANativeWindow_Buffer *buffer, uint32_t value, int center_x, int center_y, int scale, uint32_t color) {
    char text[24]; snprintf(text, sizeof text, "%u", value); int length = strlen(text); int x = center_x - length * 6 * scale / 2;
    for (int i = 0; i < length; i++) { int d = text[i] - '0'; for (int y = 0; y < 7; y++) for (int col = 0; col < 5; col++) if (digits[d][y] & (1 << (4 - col))) rectangle(buffer, x + (i * 6 + col) * scale, center_y + (y - 3) * scale, scale, scale, color); }
}
static void render(void) {
    if (!window || !dirty) return;
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, NULL)) return;
    rectangle(&buffer, 0, 0, buffer.width, buffer.height, 0xff171310);
    board_size = (buffer.height - 90 < buffer.width * .6f ? buffer.height - 90 : buffer.width * .6f); board_size = board_size / 4 * 4;
    board_x = (buffer.width - board_size) / 2; board_y = (buffer.height - board_size) / 2; cell_size = board_size / 4;
    const uint32_t colors[] = {0xff28211b,0xffb9d5d7,0xff9fcdd2,0xff70beca,0xff58a1c1,0xff527cae,0xff5364a1,0xff684da1,0xff8641a0,0xffa23991,0xffad4474,0xffb65f52};
    int gap = cell_size / 20; if (gap < 3) gap = 3;
    for (int i = 0; i < 16; i++) { unsigned value = state.cells[i], power = 0; for (unsigned v = value; v > 1; v >>= 1) power++; int x = board_x + (i % 4) * cell_size, y = board_y + (i / 4) * cell_size; rectangle(&buffer, x + gap, y + gap, cell_size - gap * 2, cell_size - gap * 2, colors[power < 12 ? power : 11]); if (value) number(&buffer, value, x + cell_size / 2, y + cell_size / 2, value < 100 ? cell_size / 20 : value < 1000 ? cell_size / 26 : cell_size / 34, 0xff252210); }
    int side = board_x / 2, scale = buffer.height / 150; if (scale < 2) scale = 2;
    number(&buffer, 2048, side, buffer.height / 3, scale * 2, 0xffdfd881); number(&buffer, state.score, side, buffer.height / 2, scale, 0xfff7f4f0); number(&buffer, state.best, side, buffer.height * 2 / 3, scale, 0xffb5aa9e);
    int right = board_x + board_size + (buffer.width - board_x - board_size) / 2;
    rectangle(&buffer, right - side / 2, buffer.height / 3 - 20, side, 48, 0xff35302a); number(&buffer, 1, right, buffer.height / 3, scale, 0xffdfd881);
    rectangle(&buffer, right - side / 2, buffer.height * 2 / 3 - 20, side, 48, 0xff35302a); number(&buffer, 0, right, buffer.height * 2 / 3, scale, 0xffdfd881);
    if (!can_move()) rectangle(&buffer, board_x, board_y - 12, board_size, 6, 0xffa6a6f0);
    int presented = ANativeWindow_unlockAndPost(window) == 0; dirty = 0;
    if (presented && !first_frame) { first_frame = 1; event("first_frame", 0); }
}
static int input(AInputEvent *event) {
    if (atomic_load(&paused)) return 0;
    if (AInputEvent_getType(event) == AINPUT_EVENT_TYPE_KEY && AKeyEvent_getAction(event) == AKEY_EVENT_ACTION_DOWN) {
        switch (AKeyEvent_getKeyCode(event)) { case AKEYCODE_DPAD_LEFT: move(0); return 1; case AKEYCODE_DPAD_RIGHT: move(1); return 1; case AKEYCODE_DPAD_UP: move(2); return 1; case AKEYCODE_DPAD_DOWN: move(3); return 1; case AKEYCODE_BUTTON_X: undo(); return 1; default: return 0; }
    }
    if (AInputEvent_getType(event) != AINPUT_EVENT_TYPE_MOTION) return 0;
    int action = AMotionEvent_getAction(event) & AMOTION_EVENT_ACTION_MASK;
    float x = AMotionEvent_getX(event, 0), y = AMotionEvent_getY(event, 0);
    if (action == AMOTION_EVENT_ACTION_DOWN) { touch_x = x; touch_y = y; return 1; }
    if (action == AMOTION_EVENT_ACTION_UP) {
        float dx = x - touch_x, dy = y - touch_y;
        if (fabsf(dx) + fabsf(dy) > 32) move(fabsf(dx) > fabsf(dy) ? (dx > 0 ? 1 : 0) : (dy > 0 ? 3 : 2));
        else if (x > board_x + board_size) { if (y < board_y + board_size / 2) undo(); else new_game(); }
        return 1;
    }
    return 1;
}
static void *loop(void *user) {
    (void)user; ALooper *looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    while (!atomic_load(&stopped) && !host.is_cancel_requested(host.host_user)) {
        pthread_mutex_lock(&mutex);
        if (queue != attached) { if (attached) AInputQueue_detachLooper(attached); attached = queue; if (attached) AInputQueue_attachLooper(attached, looper, 1, NULL, NULL); }
        AInputEvent *event;
        while (attached && AInputQueue_getEvent(attached, &event) >= 0) { int handled = input(event); AInputQueue_finishEvent(attached, event, handled); }
        if (!atomic_load(&paused)) render();
        pthread_mutex_unlock(&mutex);
        usleep(16000);
    }
    if (audio) { AAudioStream_requestStop(audio); AAudioStream_close(audio); audio = NULL; }
    event("exit_complete", 0); ANativeActivity_finish(context.activity); return NULL;
}
static void created_window(ANativeActivity *a, ANativeWindow *w) { (void)a; pthread_mutex_lock(&mutex); window = w; ANativeWindow_setBuffersGeometry(w, 0, 0, WINDOW_FORMAT_RGBA_8888); dirty = 1; pthread_mutex_unlock(&mutex); }
static void destroyed_window(ANativeActivity *a, ANativeWindow *w) { (void)a; (void)w; pthread_mutex_lock(&mutex); window = NULL; pthread_mutex_unlock(&mutex); }
static void created_queue(ANativeActivity *a, AInputQueue *q) { (void)a; pthread_mutex_lock(&mutex); queue = q; pthread_mutex_unlock(&mutex); }
static void destroyed_queue(ANativeActivity *a, AInputQueue *q) { (void)a; pthread_mutex_lock(&mutex); if (attached == q) { AInputQueue_detachLooper(attached); attached = NULL; } if (queue == q) queue = NULL; pthread_mutex_unlock(&mutex); }
static void on_pause(ANativeActivity *a) { (void)a; atomic_store(&paused, 1); }
static void on_resume(ANativeActivity *a) { (void)a; atomic_store(&paused, 0); dirty = 1; }
static void on_destroy(ANativeActivity *a) { (void)a; atomic_store(&stopped, 1); }
NSA_RUNTIME_EXPORT int32_t nsa_runtime_get_info_v1(NsaRuntimeInfoV1 *info) { if (!info || info->struct_size < sizeof *info) return -1; *info = (NsaRuntimeInfoV1){sizeof *info,1,NSA_SLOT_ID,NSA_CORE_REVISION,NSA_PROFILE_SHA256,NSA_MODULE_IDS,NSA_SHADER_CHAIN,1,0}; return 0; }
NSA_RUNTIME_EXPORT int32_t nsa_runtime_start_v1(const NsaRuntimeContextV1 *value) {
    if (!value || value->runtime_api_version != 1 || strcmp(value->runtime_slot_id, NSA_SLOT_ID)) return -1;
    if (started++) return -4;
    context = *value; host = *value->host; began = millis(); event("validating", 0);
    char path[4096]; snprintf(path, sizeof path, "%s/state.dat", context.save_root); FILE *file = fopen(path, "rb");
    if (file) { State loaded; size_t count = fread(&loaded, 1, sizeof loaded, file); fclose(file); if (count == sizeof loaded && loaded.magic == 0x4e534132 && loaded.version == 1 && loaded.checksum == checksum(&loaded)) state = loaded; }
    if (!state.magic) new_game();
    struct ANativeActivityCallbacks callbacks = {0}; callbacks.onNativeWindowCreated = created_window; callbacks.onNativeWindowDestroyed = destroyed_window; callbacks.onInputQueueCreated = created_queue; callbacks.onInputQueueDestroyed = destroyed_queue; callbacks.onPause = on_pause; callbacks.onResume = on_resume; callbacks.onDestroy = on_destroy;
    if (host.install_activity_callbacks(host.host_user, &callbacks)) return -6;
    AAudioStreamBuilder *builder = NULL;
    if (!strstr(value->settings_json, "\"audio\":false") && AAudio_createStreamBuilder(&builder) == AAUDIO_OK) { AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT); AAudioStreamBuilder_setChannelCount(builder, 2); AAudioStreamBuilder_setSampleRate(builder, 48000); AAudioStreamBuilder_setDataCallback(builder, sound, NULL); if (AAudioStreamBuilder_openStream(builder, &audio) == AAUDIO_OK) AAudioStream_requestStart(audio); AAudioStreamBuilder_delete(builder); }
    event("runtime_entered", 0); pthread_t thread; if (pthread_create(&thread, NULL, loop, NULL)) return -6; pthread_detach(thread); return 0;
}
NSA_RUNTIME_EXPORT int32_t nsa_runtime_request_v1(const char *session, uint32_t generation, NsaRuntimeCommandV1 command) {
    if (!session || strcmp(session, context.session_id) || generation != context.generation) return -3;
    if (command == NSA_RUNTIME_PAUSE) atomic_store(&paused, 1);
    else if (command == NSA_RUNTIME_RESUME) atomic_store(&paused, 0);
    else if (command == NSA_RUNTIME_REQUEST_NORMAL_EXIT || command == NSA_RUNTIME_CANCEL_LAUNCH) { event("exit_requested", 0); atomic_store(&stopped, 1); }
    else return -1;
    return 0;
}
