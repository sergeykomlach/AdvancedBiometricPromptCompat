#include <jni.h>
#include <link.h>
#include <string.h>

static int matches_library(const char *name, const char *expected) {
    const size_t length = strlen(expected);
    return strncmp(name, expected, length) == 0 &&
           (name[length] == '\0' || strcmp(name + length, " (deleted)") == 0);
}

static int find_instrumentation(struct dl_phdr_info *info, size_t size, void *data) {
    (void)size;
    (void)data;
    const char *name = info->dlpi_name;
    if (name == NULL) return 0;

    const char *slash = strrchr(name, '/');
    if (slash != NULL) name = slash + 1;

    if (strncmp(name, "memfd:", 6) == 0) name += 6;
    if (strncmp(name, "lib", 3) == 0) name += 3;
    return matches_library(name, "frida-gadget.so") ||
           matches_library(name, "frida-agent.so") ||
           matches_library(name, "frida-agent-32.so") ||
           matches_library(name, "frida-agent-64.so") ||
           matches_library(name, "frida-agent-arm.so") ||
           matches_library(name, "frida-agent-arm64.so");
}

JNIEXPORT jboolean JNICALL
Java_dev_skomlach_common_protection_NativeHookProbe_probeNative(JNIEnv *env, jobject instance) {
    (void)env;
    (void)instance;
    return dl_iterate_phdr(find_instrumentation, NULL) == 1 ? JNI_TRUE : JNI_FALSE;
}
