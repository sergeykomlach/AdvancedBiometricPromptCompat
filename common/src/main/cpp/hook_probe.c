#include <jni.h>
#include <link.h>
#include <stdint.h>
#include <string.h>

// FNV-1a fingerprints of normalized ASCII basenames. This keeps marker literals out
// of the ELF and avoids decoding names into memory. Keep in sync with HookDetection.kt.
static const struct {
    size_t length;
    uint64_t fingerprint;
} loader_markers[] = {
    {14, UINT64_C(0xf361f7b83177dcd9)},
    {15, UINT64_C(0x23ffd26eb03642ae)},
    {17, UINT64_C(0x274e5fe4c829aff1)},
    {17, UINT64_C(0xe517093fa14c877c)},
    {18, UINT64_C(0xd3fa34aef3623a10)},
    {20, UINT64_C(0xcc4a252c9ee8d802)},
};

static int matches_loader_marker(const char *name) {
    static const char deleted_suffix[] = " (deleted)";
    const size_t suffix_length = sizeof(deleted_suffix) - 1;
    size_t length = strlen(name);
    if (length >= suffix_length &&
        memcmp(name + length - suffix_length, deleted_suffix, suffix_length) == 0) {
        length -= suffix_length;
    }
    if (length < 14 || length > 20) return 0;

    uint64_t fingerprint = UINT64_C(14695981039346656037);
    for (size_t i = 0; i < length; ++i) {
        const unsigned char byte = (unsigned char)name[i];
        if (byte > 0x7f) return 0;
        fingerprint = (fingerprint ^ byte) * UINT64_C(1099511628211);
    }
    for (size_t i = 0; i < sizeof(loader_markers) / sizeof(loader_markers[0]); ++i) {
        if (loader_markers[i].length == length &&
            loader_markers[i].fingerprint == fingerprint) return 1;
    }
    return 0;
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
    if (!matches_loader_marker(name) || info->dlpi_phdr == NULL) return 0;
    for (size_t i = 0; i < info->dlpi_phnum; ++i) {
        const ElfW(Phdr) *segment = &info->dlpi_phdr[i];
        if (segment->p_type == PT_LOAD && (segment->p_flags & PF_X) != 0 &&
            segment->p_memsz != 0) return 1;
    }
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_dev_skomlach_common_protection_NativeHookProbe_probeNative(JNIEnv *env, jobject instance) {
    (void)env;
    (void)instance;
    return dl_iterate_phdr(find_instrumentation, NULL) == 1 ? JNI_TRUE : JNI_FALSE;
}
