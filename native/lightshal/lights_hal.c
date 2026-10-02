/*
 * lights.default.so — lights HAL of AEmulator for Android 2.3–7.x (hardware/libhardware/include/hardware/lights.h).
 *
 * LightsService drives the notification LED, the battery LED, the screen backlight… through this module. Each call is
 * written as one text line into /dev/aemu_lights (a FIFO the host reads):
 *     <light> <argb color hex> <flash mode> <on ms> <off ms>\n
 * The host shows the LED above the guest screen (Sony Xperia illumination bar, the notification LED of other phones).
 * No reader → the write is dropped (non-blocking), so the guest never waits on the host.
 *
 * The ABI of hw_module_t / light_device_t / light_state_t has not changed between 2.3 and 7.1. Only libc is used.
 */
#include <stdint.h>

extern int open(const char *path, int flags, ...);
extern long write(int fd, const void *buf, unsigned long n);
extern int close(int fd);
extern void *calloc(unsigned long n, unsigned long size);
extern void free(void *p);

#define O_WRONLY 1
#define O_NONBLOCK 04000
#define O_CLOEXEC 02000000
#define EXPORT __attribute__((visibility("default")))

struct hw_module;
struct hw_device;
struct hw_module_methods { int (*open)(const struct hw_module *m, const char *id, struct hw_device **dev); };
struct hw_module {
    uint32_t tag;
    uint16_t module_api_version, hal_api_version;
    const char *id, *name, *author;
    struct hw_module_methods *methods;
    void *dso;
    unsigned long reserved[25]; /* uint32 on 32-bit, uint64 on 64-bit */
};
struct hw_device {
    uint32_t tag, version;
    struct hw_module *module;
    unsigned long reserved[12];
    int (*close)(struct hw_device *dev);
};
struct light_state { uint32_t color; int flash_mode, flash_on_ms, flash_off_ms, brightness_mode; };
struct light_device {
    struct hw_device common;
    int (*set_light)(struct light_device *dev, const struct light_state *state);
    /* ours */
    char name[24];
};

#define HARDWARE_MODULE_TAG 0x484d4f44 /* HMOD */
#define HARDWARE_DEVICE_TAG 0x48574454 /* HWDT */

static int put_hex(char *p, uint32_t v) {
    static const char H[] = "0123456789abcdef";
    for (int i = 0; i < 8; i++) p[i] = H[(v >> (28 - 4 * i)) & 15];
    return 8;
}
static int put_dec(char *p, int v) {
    char t[12]; int n = 0, k = 0;
    if (v < 0) { p[k++] = '-'; v = -v; }
    do { t[n++] = (char)('0' + v % 10); v /= 10; } while (v && n < 11);
    while (n) p[k++] = t[--n];
    return k;
}

static int set_light(struct light_device *dev, const struct light_state *st) {
    char line[96];
    int k = 0;
    for (const char *c = dev->name; *c && k < 23; c++) line[k++] = *c;
    line[k++] = ' ';
    k += put_hex(line + k, st->color);
    line[k++] = ' ';
    k += put_dec(line + k, st->flash_mode);
    line[k++] = ' ';
    k += put_dec(line + k, st->flash_on_ms);
    line[k++] = ' ';
    k += put_dec(line + k, st->flash_off_ms);
    line[k++] = '\n';
    int fd = open("/dev/aemu_lights", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd >= 0) { write(fd, line, (unsigned long)k); close(fd); }
    return 0;
}

static int dev_close(struct hw_device *d) { free(d); return 0; }

static int lights_open(const struct hw_module *m, const char *id, struct hw_device **out) {
    static const char *known[] = { "backlight", "keyboard", "buttons", "battery", "notifications", "attention", "bluetooth", "wifi", 0 };
    int ok = 0;
    for (int i = 0; known[i]; i++) {
        const char *a = known[i], *b = id;
        while (*a && *a == *b) { a++; b++; }
        if (!*a && !*b) { ok = 1; break; }
    }
    if (!ok) return -22; /* -EINVAL: unknown light */
    struct light_device *d = calloc(1, sizeof(*d));
    if (!d) return -12;
    d->common.tag = HARDWARE_DEVICE_TAG;
    d->common.version = 0;
    d->common.module = (struct hw_module *)m;
    d->common.close = dev_close;
    d->set_light = set_light;
    int i = 0;
    while (id[i] && i < 23) { d->name[i] = id[i]; i++; }
    *out = &d->common;
    return 0;
}

static struct hw_module_methods methods = { lights_open };

EXPORT struct hw_module HMI = {
    .tag = HARDWARE_MODULE_TAG,
    .module_api_version = 1,
    .hal_api_version = 0,
    .id = "lights",
    .name = "AEmulator lights",
    .author = "AEmulator",
    .methods = &methods,
};
