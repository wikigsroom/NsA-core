/* Bootstrap owns NativeActivity callbacks and replays early Surface/InputQueue state. */
#include <android/native_activity.h>
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <vulkan/vulkan.h>
#include "../../../../../docs/engineering/contracts/runtime-api-v1.h"

static ANativeActivity *activity;
static struct ANativeActivityCallbacks runtime_callbacks;
static ANativeWindow *window;
static AInputQueue *queue;
static int resumed, focused, installed;
static _Atomic int cancelled;
static pthread_mutex_t event_lock = PTHREAD_MUTEX_INITIALIZER;
static JavaVM *vm;
static jobject receiver;
static jmethodID event_method;
static NsaRuntimeContextV1 context;
static NsaRuntimeHostV1 host;
static int32_t (*runtime_request)(const char *, uint32_t, NsaRuntimeCommandV1);
static char *owned[32];
static unsigned owned_count;

static char *field(const char *name) {
    JNIEnv *env = activity->env;
    jclass cls = (*env)->GetObjectClass(env, activity->clazz);
    jmethodID get = (*env)->GetMethodID(env, cls, "getRuntimeField", "(Ljava/lang/String;)Ljava/lang/String;");
    jstring key = (*env)->NewStringUTF(env, name);
    jstring value = get ? (*env)->CallObjectMethod(env, activity->clazz, get, key) : NULL;
    const char *raw = value ? (*env)->GetStringUTFChars(env, value, NULL) : NULL;
    char *copy = strdup(raw ? raw : "");
    if (raw) (*env)->ReleaseStringUTFChars(env, value, raw);
    if (value) (*env)->DeleteLocalRef(env, value);
    (*env)->DeleteLocalRef(env, key); (*env)->DeleteLocalRef(env, cls);
    if (owned_count < 32) owned[owned_count++] = copy;
    return copy;
}
static int32_t emit(void *user, const char *json, size_t size) {
    (void)user;
    if (!json || !size || size > 16384 || !receiver) return -1;
    JNIEnv *env = NULL; int attached = 0;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*vm)->AttachCurrentThread(vm, &env, NULL)) return -1;
        attached = 1;
    }
    pthread_mutex_lock(&event_lock);
    char copy[16385]; memcpy(copy, json, size); copy[size] = 0;
    jstring text = (*env)->NewStringUTF(env, copy);
    if (text) { (*env)->CallVoidMethod(env, receiver, event_method, text); (*env)->DeleteLocalRef(env, text); }
    int failed = (*env)->ExceptionCheck(env);
    if (failed) (*env)->ExceptionClear(env);
    pthread_mutex_unlock(&event_lock);
    if (attached) (*vm)->DetachCurrentThread(vm);
    return failed ? -1 : 0;
}
static int32_t is_cancelled(void *user) { (void)user; return atomic_load(&cancelled); }
static void log_message(void *user, int32_t level, const char *text) { (void)user; __android_log_print(level >= 3 ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO, "NsARuntime", "%.2048s", text ? text : ""); }
static int32_t callbacks(void *user, const struct ANativeActivityCallbacks *table) {
    (void)user;
    if (!table || installed++) return NSA_RUNTIME_ALREADY_STARTED;
    runtime_callbacks = *table;
    if (window && table->onNativeWindowCreated) table->onNativeWindowCreated(activity, window);
    if (queue && table->onInputQueueCreated) table->onInputQueueCreated(activity, queue);
    if (resumed && table->onResume) table->onResume(activity);
    if (table->onWindowFocusChanged) table->onWindowFocusChanged(activity, focused);
    return NSA_RUNTIME_OK;
}
static void created_window(ANativeActivity *a, ANativeWindow *w) { window = w; if (installed && runtime_callbacks.onNativeWindowCreated) runtime_callbacks.onNativeWindowCreated(a, w); }
static void destroyed_window(ANativeActivity *a, ANativeWindow *w) { if (installed && runtime_callbacks.onNativeWindowDestroyed) runtime_callbacks.onNativeWindowDestroyed(a, w); window = NULL; }
static void resized_window(ANativeActivity *a, ANativeWindow *w) { if (installed && runtime_callbacks.onNativeWindowResized) runtime_callbacks.onNativeWindowResized(a, w); }
static void redraw_window(ANativeActivity *a, ANativeWindow *w) { if (installed && runtime_callbacks.onNativeWindowRedrawNeeded) runtime_callbacks.onNativeWindowRedrawNeeded(a, w); }
static void created_queue(ANativeActivity *a, AInputQueue *q) { queue = q; if (installed && runtime_callbacks.onInputQueueCreated) runtime_callbacks.onInputQueueCreated(a, q); }
static void destroyed_queue(ANativeActivity *a, AInputQueue *q) { if (installed && runtime_callbacks.onInputQueueDestroyed) runtime_callbacks.onInputQueueDestroyed(a, q); queue = NULL; }
static void resume(ANativeActivity *a) { resumed = 1; if (installed && runtime_callbacks.onResume) runtime_callbacks.onResume(a); }
static void pause_game(ANativeActivity *a) { resumed = 0; if (installed && runtime_callbacks.onPause) runtime_callbacks.onPause(a); }
static void focus(ANativeActivity *a, int value) { focused = value; if (installed && runtime_callbacks.onWindowFocusChanged) runtime_callbacks.onWindowFocusChanged(a, value); }
static void configuration(ANativeActivity *a) { if (installed && runtime_callbacks.onConfigurationChanged) runtime_callbacks.onConfigurationChanged(a); }
static void memory(ANativeActivity *a) { if (installed && runtime_callbacks.onLowMemory) runtime_callbacks.onLowMemory(a); }
static void destroy(ANativeActivity *a) { atomic_store(&cancelled, 1); if (installed && runtime_callbacks.onDestroy) runtime_callbacks.onDestroy(a); installed = 0; runtime_request = NULL; /* Process lifetime owns module and JNI globals. */ }

JNIEXPORT jint JNICALL Java_org_nsa_hall_runtime_GameActivity_nativeRequest(JNIEnv *env, jobject object, jint command) {
    (void)env; (void)object;
    if (command == NSA_RUNTIME_CANCEL_LAUNCH) atomic_store(&cancelled, 1);
    return runtime_request ? runtime_request(context.session_id, context.generation, command) : NSA_RUNTIME_NOT_STARTED;
}
JNIEXPORT void JNICALL Java_org_nsa_hall_runtime_GameActivity_nativeDestroy(JNIEnv *env, jobject object) { (void)env; (void)object; atomic_store(&cancelled, 1); }

/* Query the system driver without loading a game slot or creating a GPU device. */
JNIEXPORT jstring JNICALL Java_org_nsa_hall_data_DeviceCapabilities_nativeProbe(JNIEnv *env, jobject object) {
    (void)object;
    char result[4096] = "{}";
    void *library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) return (*env)->NewStringUTF(env, result);
    PFN_vkGetInstanceProcAddr get = (PFN_vkGetInstanceProcAddr)dlsym(library, "vkGetInstanceProcAddr");
    PFN_vkCreateInstance create = get ? (PFN_vkCreateInstance)get(VK_NULL_HANDLE, "vkCreateInstance") : NULL;
    VkApplicationInfo app = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .pApplicationName = "NsA capability probe", .apiVersion = VK_API_VERSION_1_0};
    VkInstanceCreateInfo settings = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app};
    VkInstance instance = VK_NULL_HANDLE;
    if (create && create(&settings, NULL, &instance) == VK_SUCCESS) {
        PFN_vkEnumeratePhysicalDevices enumerate = (PFN_vkEnumeratePhysicalDevices)get(instance, "vkEnumeratePhysicalDevices");
        PFN_vkGetPhysicalDeviceProperties properties = (PFN_vkGetPhysicalDeviceProperties)get(instance, "vkGetPhysicalDeviceProperties");
        PFN_vkGetPhysicalDeviceFeatures features = (PFN_vkGetPhysicalDeviceFeatures)get(instance, "vkGetPhysicalDeviceFeatures");
        PFN_vkDestroyInstance destroy_instance = (PFN_vkDestroyInstance)get(instance, "vkDestroyInstance");
        uint32_t count = 1; VkPhysicalDevice gpu = VK_NULL_HANDLE;
        VkResult status = enumerate ? enumerate(instance, &count, &gpu) : VK_ERROR_INITIALIZATION_FAILED;
        if ((status == VK_SUCCESS || status == VK_INCOMPLETE) && count && properties && features) {
            VkPhysicalDeviceProperties p; VkPhysicalDeviceFeatures f;
            properties(gpu, &p); features(gpu, &f);
            char name[sizeof p.deviceName * 6 + 1], *cursor = name;
            for (unsigned i = 0; i < sizeof p.deviceName && p.deviceName[i]; i++) {
                unsigned char c = (unsigned char)p.deviceName[i];
                if (c < 32 || c >= 127 || c == '"' || c == '\\') { snprintf(cursor, 7, "\\u%04x", c); cursor += 6; }
                else *cursor++ = (char)c;
            }
            *cursor = 0;
            char uuid[VK_UUID_SIZE * 2 + 1];
            for (unsigned i = 0; i < VK_UUID_SIZE; i++) snprintf(uuid + i * 2, 3, "%02x", p.pipelineCacheUUID[i]);
            char available[512] = ""; size_t used = 0;
#define CAPABILITY_FEATURE(key) do { if (f.key) used += (size_t)snprintf(available + used, sizeof available - used, "%s\"%s\"", used ? "," : "", #key); } while (0)
            CAPABILITY_FEATURE(shaderInt64); CAPABILITY_FEATURE(shaderFloat64); CAPABILITY_FEATURE(geometryShader);
            CAPABILITY_FEATURE(imageCubeArray); CAPABILITY_FEATURE(multiViewport); CAPABILITY_FEATURE(shaderCullDistance);
            CAPABILITY_FEATURE(shaderClipDistance); CAPABILITY_FEATURE(textureCompressionASTC_LDR); CAPABILITY_FEATURE(samplerAnisotropy);
#undef CAPABILITY_FEATURE
            snprintf(result, sizeof result, "{\"gpu_family\":\"%s\",\"vulkan_api\":\"%u.%u.%u\",\"driver_marker\":\"%x:%x:%u:%s\",\"features\":[%s]}", name,
                VK_VERSION_MAJOR(p.apiVersion), VK_VERSION_MINOR(p.apiVersion), VK_VERSION_PATCH(p.apiVersion),
                p.vendorID, p.deviceID, p.driverVersion, uuid, available);
        }
        if (destroy_instance) destroy_instance(instance, NULL);
    }
    dlclose(library);
    return (*env)->NewStringUTF(env, result);
}

__attribute__((visibility("default"))) void ANativeActivity_onCreate(ANativeActivity *a, void *saved, size_t size) {
    (void)saved; (void)size;
    activity = a; vm = a->vm;
    JNIEnv *env = a->env;
    receiver = (*env)->NewGlobalRef(env, a->clazz);
    jclass cls = (*env)->GetObjectClass(env, a->clazz);
    event_method = (*env)->GetMethodID(env, cls, "onRuntimeEvent", "(Ljava/lang/String;)V");
    (*env)->DeleteLocalRef(env, cls);
    a->callbacks->onNativeWindowCreated = created_window; a->callbacks->onNativeWindowDestroyed = destroyed_window;
    a->callbacks->onNativeWindowResized = resized_window; a->callbacks->onNativeWindowRedrawNeeded = redraw_window;
    a->callbacks->onInputQueueCreated = created_queue; a->callbacks->onInputQueueDestroyed = destroyed_queue;
    a->callbacks->onResume = resume; a->callbacks->onPause = pause_game; a->callbacks->onWindowFocusChanged = focus;
    a->callbacks->onConfigurationChanged = configuration; a->callbacks->onLowMemory = memory; a->callbacks->onDestroy = destroy;
    host = (NsaRuntimeHostV1){sizeof host, 1, NULL, emit, is_cancelled, callbacks, log_message};
    context = (NsaRuntimeContextV1){.struct_size = sizeof context, .runtime_api_version = 1, .activity = a, .host = &host};
    context.session_id = field("session_id"); context.generation = (uint32_t)strtoul(field("generation"), NULL, 10);
    context.game_id = field("game_id"); context.release_id = field("release_id"); context.local_player_id = field("local_player_id");
    context.runtime_slot_id = field("runtime_slot_id"); context.manifest_sha256 = field("manifest_sha256");
    context.package_root = field("package_root"); context.romfs_root = field("romfs_root"); context.shader_root = field("shader_root"); context.save_root = field("save_root");
    context.settings_root = field("settings_root"); context.cache_root = field("cache_root"); context.temp_root = field("temp_root"); context.log_root = field("log_root"); context.settings_json = field("settings_json");
    const char *library = field("library_path");
    void *module = dlopen(library, RTLD_NOW | RTLD_LOCAL);
    int32_t (*get_info)(NsaRuntimeInfoV1 *) = module ? dlsym(module, "nsa_runtime_get_info_v1") : NULL;
    int32_t (*start)(const NsaRuntimeContextV1 *) = module ? dlsym(module, "nsa_runtime_start_v1") : NULL;
    runtime_request = module ? dlsym(module, "nsa_runtime_request_v1") : NULL;
    NsaRuntimeInfoV1 info = {.struct_size = sizeof info};
    int valid = get_info && start && runtime_request && get_info(&info) == 0 && info.runtime_api_version == 1 && info.runtime_slot_id && !strcmp(info.runtime_slot_id, context.runtime_slot_id);
    if (valid) valid = !strcmp(info.core_revision, field("core_revision")) && !strcmp(info.profile_sha256, field("profile_digest")) && !strcmp(info.shader_chain_id, field("shader_chain_id")) && !strcmp(info.module_build_ids_json, field("module_build_ids"));
    if (!valid || start(&context) != 0) {
        char error[1024]; snprintf(error, sizeof error, "{\"event_schema\":1,\"session_id\":\"%s\",\"generation\":%u,\"seq\":1,\"event_type\":\"fatal_error\",\"monotonic_elapsed_ms\":\"0\",\"error_code\":\"MODULE_MISMATCH\"}", context.session_id, context.generation);
        emit(NULL, error, strlen(error)); ANativeActivity_finish(a);
    }
}
