/*
 * sensors.default.so — sensors HAL of AEmulator for Android 2.3–7.x: an accelerometer fed by the phone.
 *
 * The host writes fixed 16-byte records into the FIFO /dev/aemu_sensors: int32 type, float x, y, z (the phone's own
 * accelerometer, m/s²). poll() turns them into sensors_event_t while the guest has the sensor activated.
 *
 * On 32-bit ARM the layouts used here are identical from 2.3 to 7.1: sensor_t is 68 bytes (the 4.4/5.0 fields
 * fifo*, stringType, requiredPermission, maxDelay, flags took the place of the old reserved words) and
 * sensors_event_t is 104 bytes. The device API version is chosen from ro.build.version.sdk: 0.1 up to 4.3,
 * 1.1 on 4.4, 1.3 from 5.0 (SensorService of 7.x refuses 1.0 devices).
 */
#include <stdint.h>

extern int open(const char *path, int flags, ...);
extern long read(int fd, void *buf, unsigned long n);
extern int close(int fd);
extern void *calloc(unsigned long n, unsigned long size);
extern void free(void *p);
extern int __system_property_get(const char *name, char *value);
struct timespec_ { long sec; long nsec; };
extern int clock_gettime(int clk, struct timespec_ *ts);
extern int nanosleep(const struct timespec_ *a, struct timespec_ *b);

#define O_RDWR 2
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
    uint32_t reserved[25];
};
struct hw_device {
    uint32_t tag, version;
    struct hw_module *module;
    uint32_t reserved[12];
    int (*close)(struct hw_device *dev);
};

struct sensor_t {
    const char *name, *vendor;
    int version, handle, type;
    float maxRange, resolution, power;
    int32_t minDelay;
    uint32_t fifoReservedEventCount, fifoMaxEventCount;
    const char *stringType, *requiredPermission;
    int32_t maxDelay;
    uint32_t flags;
    void *reserved[2];
};
struct sensors_event_t {
    int32_t version, sensor, type, reserved0;
    int64_t timestamp;
    float data[16];
    uint32_t flags, reserved1[3];
};
struct sensors_module {
    struct hw_module common;
    int (*get_sensors_list)(struct sensors_module *m, const struct sensor_t **list);
    void (*set_operation_mode)(unsigned mode);  /* 5.0+, unused here */
};
struct poll_device {
    struct hw_device common;
    int (*activate)(struct poll_device *d, int handle, int enabled);
    int (*setDelay)(struct poll_device *d, int handle, int64_t ns);
    int (*poll)(struct poll_device *d, struct sensors_event_t *data, int count);
    int (*batch)(struct poll_device *d, int handle, int flags, int64_t period_ns, int64_t timeout);
    int (*flush)(struct poll_device *d, int handle);
    int (*inject_sensor_data)(struct poll_device *d, const struct sensors_event_t *data);
    void *reserved[8];
    /* ours */
    int fd;
    int active;
    int flush_pending;
};

#define HARDWARE_MODULE_TAG 0x484d4f44
#define HARDWARE_DEVICE_TAG 0x48574454
#define SENSOR_TYPE_ACCELEROMETER 1
#define SENSOR_TYPE_META_DATA 0
#define META_DATA_FLUSH_COMPLETE 1
#define HANDLE_ACCEL 1
#define GRAVITY 9.80665f

static const struct sensor_t sensors[] = { {
    .name = "AEmulator 3-axis Accelerometer", .vendor = "AEmulator", .version = 1, .handle = HANDLE_ACCEL,
    .type = SENSOR_TYPE_ACCELEROMETER, .maxRange = 4.0f * GRAVITY, .resolution = 1.0f / 4096.0f, .power = 0.2f,
    .minDelay = 10000, .stringType = "android.sensor.accelerometer", .maxDelay = 1000000, .flags = 0,
} };

static int get_list(struct sensors_module *m, const struct sensor_t **list) { (void)m; *list = sensors; return 1; }

static int64_t now_ns(void) {
    struct timespec_ t;
    clock_gettime(1 /* CLOCK_MONOTONIC */, &t);
    return (int64_t)t.sec * 1000000000LL + t.nsec;
}

static int dev_activate(struct poll_device *d, int handle, int en) { if (handle == HANDLE_ACCEL) d->active = en; return 0; }
static int dev_set_delay(struct poll_device *d, int handle, int64_t ns) { (void)d; (void)handle; (void)ns; return 0; }
static int dev_batch(struct poll_device *d, int handle, int flags, int64_t p, int64_t t) { (void)d; (void)handle; (void)flags; (void)p; (void)t; return 0; }
static int dev_flush(struct poll_device *d, int handle) { if (handle != HANDLE_ACCEL) return -22; d->flush_pending++; return 0; }
static int dev_inject(struct poll_device *d, const struct sensors_event_t *e) { (void)d; (void)e; return -38; }

static int dev_poll(struct poll_device *d, struct sensors_event_t *out, int count) {
    if (count <= 0) return 0;
    for (;;) {
        if (d->flush_pending > 0) {
            d->flush_pending--;
            struct sensors_event_t *e = out;
            for (unsigned i = 0; i < sizeof(*e) / 4; i++) ((uint32_t *)e)[i] = 0;
            e->version = 3; /* META_DATA_VERSION */
            e->type = SENSOR_TYPE_META_DATA;
            ((int32_t *)e->data)[0] = META_DATA_FLUSH_COMPLETE; /* meta_data.what */
            ((int32_t *)e->data)[1] = HANDLE_ACCEL;             /* meta_data.sensor */
            return 1;
        }
        int32_t rec[4];
        long n = d->fd >= 0 ? read(d->fd, rec, sizeof(rec)) : -1;
        if (n != (long)sizeof(rec)) {
            /* no host: never busy-loop, sleep like an idle sensor */
            struct timespec_ ts = { 0, 200000000 };
            nanosleep(&ts, 0);
            continue;
        }
        if (!d->active || rec[0] != SENSOR_TYPE_ACCELEROMETER) continue;
        struct sensors_event_t *e = out;
        for (unsigned i = 0; i < sizeof(*e) / 4; i++) ((uint32_t *)e)[i] = 0;
        e->version = (int32_t)sizeof(*e);
        e->sensor = HANDLE_ACCEL;
        e->type = SENSOR_TYPE_ACCELEROMETER;
        e->timestamp = now_ns();
        e->data[0] = *(float *)&rec[1];
        e->data[1] = *(float *)&rec[2];
        e->data[2] = *(float *)&rec[3];
        ((int8_t *)&e->data[3])[0] = 3; /* SENSOR_STATUS_ACCURACY_HIGH */
        return 1;
    }
}

static int dev_close(struct hw_device *h) {
    struct poll_device *d = (struct poll_device *)h;
    if (d->fd >= 0) close(d->fd);
    free(d);
    return 0;
}

static int sensors_open(const struct hw_module *m, const char *id, struct hw_device **out) {
    (void)id;
    struct poll_device *d = calloc(1, sizeof(*d));
    if (!d) return -12;
    char sdk[92] = { 0 };
    __system_property_get("ro.build.version.sdk", sdk);
    int api = 0;
    for (int i = 0; sdk[i] >= '0' && sdk[i] <= '9'; i++) api = api * 10 + (sdk[i] - '0');
    d->common.tag = HARDWARE_DEVICE_TAG;
    /* HARDWARE_DEVICE_API_VERSION(major, minor) = (major << 24) | (minor << 16) | header version */
    d->common.version = api >= 21 ? ((1u << 24) | (3u << 16)) : api >= 19 ? ((1u << 24) | (1u << 16)) : 1;
    d->common.module = (struct hw_module *)m;
    d->common.close = dev_close;
    d->activate = dev_activate;
    d->setDelay = dev_set_delay;
    d->poll = dev_poll;
    d->batch = dev_batch;
    d->flush = dev_flush;
    d->inject_sensor_data = dev_inject;
    /* O_RDWR: no EOF when the host has not started writing yet */
    d->fd = open("/dev/aemu_sensors", O_RDWR | O_CLOEXEC);
    *out = &d->common;
    return 0;
}

static struct hw_module_methods methods = { sensors_open };

EXPORT struct sensors_module HMI = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = 1,
        .hal_api_version = 0,
        .id = "sensors",
        .name = "AEmulator sensors",
        .author = "AEmulator",
        .methods = &methods,
    },
    .get_sensors_list = get_list,
};
