/*
 * camera.default.so — camera HAL of AEmulator (camera device API 1.0: Android 4.0–7.x): the phone's cameras.
 *
 * One unix socket connection per opened camera, /dev/socket/aemu_cam. The guest sends text commands
 * ("open <id>", "start", "stop", "shot"), the host answers with frames:
 *     int32 type, int32 width, int32 height, int32 length, then <length> bytes
 *     type 1: preview — RGBA_8888 (w*h*4) followed by NV21 (w*h*3/2): the first goes into the preview window,
 *             the second to the application's preview callback
 *     type 2: JPEG of a picture
 * The host converts and compresses natively; under emulation the HAL only copies.
 *
 * Layouts (camera_device_ops, preview_stream_ops, gralloc lock) are the same on 32-bit ARM from 4.0 to 7.1.
 * Only libc and libdl are used; hw_get_module comes from the firmware's libhardware.so at run time.
 */
#include <stdint.h>
#include <stddef.h>

extern int socket(int domain, int type, int proto);
extern int connect(int fd, const void *addr, unsigned len);
extern long read(int fd, void *buf, unsigned long n);
extern long write(int fd, const void *buf, unsigned long n);
extern int close(int fd);
extern void *malloc(unsigned long n);
extern void *calloc(unsigned long n, unsigned long size);
extern void free(void *p);
extern int usleep(unsigned long us);
extern void *dlopen(const char *name, int flags);
extern void *dlsym(void *h, const char *name);
extern int pthread_create(long *t, const void *attr, void *(*fn)(void *), void *arg);
extern int pthread_join(long t, void **ret);

#define EXPORT __attribute__((visibility("default")))
#define W 640
#define H 480

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
#define HARDWARE_MODULE_TAG 0x484d4f44
#define HARDWARE_DEVICE_TAG 0x48574454

struct camera_info { int facing, orientation; uint32_t device_version; const void *static_characteristics; };
struct camera_module {
    struct hw_module common;
    int (*get_number_of_cameras)(void);
    int (*get_camera_info)(int id, struct camera_info *info);
};

struct camera_memory { void *data; size_t size; void *handle; void (*release)(struct camera_memory *m); };
typedef void (*notify_cb)(int32_t msg, int32_t ext1, int32_t ext2, void *user);
typedef void (*data_cb)(int32_t msg, const struct camera_memory *data, unsigned index, void *metadata, void *user);
typedef void (*data_ts_cb)(int64_t ts, int32_t msg, const struct camera_memory *data, unsigned index, void *user);
typedef struct camera_memory *(*request_memory)(int fd, size_t size, unsigned num, void *user);

struct preview_ops {
    int (*dequeue_buffer)(struct preview_ops *w, const void ***buffer, int *stride);
    int (*enqueue_buffer)(struct preview_ops *w, const void **buffer);
    int (*cancel_buffer)(struct preview_ops *w, const void **buffer);
    int (*set_buffer_count)(struct preview_ops *w, int count);
    int (*set_buffers_geometry)(struct preview_ops *w, int width, int height, int format);
    int (*set_crop)(struct preview_ops *w, int l, int t, int r, int b);
    int (*set_usage)(struct preview_ops *w, int usage);
    int (*set_swap_interval)(struct preview_ops *w, int interval);
    int (*get_min_undequeued_buffer_count)(const struct preview_ops *w, int *count);
    int (*lock_buffer)(struct preview_ops *w, const void **buffer);
    int (*set_timestamp)(struct preview_ops *w, int64_t ts);
};

struct gralloc_module {
    struct hw_module common;
    int (*registerBuffer)(const struct gralloc_module *m, const void *handle);
    int (*unregisterBuffer)(const struct gralloc_module *m, const void *handle);
    int (*lock)(const struct gralloc_module *m, const void *handle, int usage, int l, int t, int w, int h, void **vaddr);
    int (*unlock)(const struct gralloc_module *m, const void *handle);
};

struct camera_device;
struct camera_ops {
    int (*set_preview_window)(struct camera_device *, struct preview_ops *);
    void (*set_callbacks)(struct camera_device *, notify_cb, data_cb, data_ts_cb, request_memory, void *user);
    void (*enable_msg_type)(struct camera_device *, int32_t);
    void (*disable_msg_type)(struct camera_device *, int32_t);
    int (*msg_type_enabled)(struct camera_device *, int32_t);
    int (*start_preview)(struct camera_device *);
    void (*stop_preview)(struct camera_device *);
    int (*preview_enabled)(struct camera_device *);
    int (*store_meta_data_in_buffers)(struct camera_device *, int);
    int (*start_recording)(struct camera_device *);
    void (*stop_recording)(struct camera_device *);
    int (*recording_enabled)(struct camera_device *);
    void (*release_recording_frame)(struct camera_device *, const void *);
    int (*auto_focus)(struct camera_device *);
    int (*cancel_auto_focus)(struct camera_device *);
    int (*take_picture)(struct camera_device *);
    int (*cancel_picture)(struct camera_device *);
    int (*set_parameters)(struct camera_device *, const char *);
    char *(*get_parameters)(struct camera_device *);
    void (*put_parameters)(struct camera_device *, char *);
    int (*send_command)(struct camera_device *, int32_t cmd, int32_t a1, int32_t a2);
    void (*release)(struct camera_device *);
    int (*dump)(struct camera_device *, int fd);
};
struct camera_device {
    struct hw_device common;
    struct camera_ops *ops;
    void *priv;
    /* ours */
    int id, fd;
    volatile int previewing, running, msgs, window_ready;
    long thread;
    struct preview_ops *window;
    notify_cb notify; data_cb data; request_memory get_memory; void *user;
    char *params;
};

#define MSG_SHUTTER 0x002
#define MSG_FOCUS 0x004
#define MSG_PREVIEW_FRAME 0x010
#define MSG_RAW_IMAGE 0x080
#define MSG_COMPRESSED_IMAGE 0x100
#define MSG_RAW_IMAGE_NOTIFY 0x200
#define FORMAT_RGBA_8888 1
#define USAGE_SW_WRITE_OFTEN 0x30

static const char DEFAULTS[] =
    "preview-size=640x480;preview-size-values=640x480;preview-format=yuv420sp;preview-format-values=yuv420sp;"
    "preview-frame-rate=15;preview-frame-rate-values=15;preview-fps-range=15000,15000;preview-fps-range-values=(15000,15000);"
    "picture-size=640x480;picture-size-values=640x480;picture-format=jpeg;picture-format-values=jpeg;jpeg-quality=90;"
    "jpeg-thumbnail-width=0;jpeg-thumbnail-height=0;jpeg-thumbnail-size-values=0x0;jpeg-thumbnail-quality=90;"
    "focus-mode=fixed;focus-mode-values=fixed;focal-length=3.5;horizontal-view-angle=60;vertical-view-angle=45;"
    "focus-distances=0.95,1.9,Infinity;exposure-compensation=0;max-exposure-compensation=0;min-exposure-compensation=0;"
    "exposure-compensation-step=0;whitebalance=auto;whitebalance-values=auto;effect=none;effect-values=none;"
    "antibanding=auto;antibanding-values=auto;scene-mode=auto;scene-mode-values=auto;flash-mode=off;flash-mode-values=off;"
    "zoom-supported=false;smooth-zoom-supported=false;max-zoom=0;zoom=0;zoom-ratios=100;rotation=0;"
    "video-frame-format=yuv420sp;video-size=640x480;video-size-values=640x480;preferred-preview-size-for-video=640x480;"
    "max-num-detected-faces-hw=0;max-num-detected-faces-sw=0;max-num-focus-areas=0;max-num-metering-areas=0;"
    "video-snapshot-supported=false;video-stabilization-supported=false;auto-exposure-lock-supported=false;"
    "auto-whitebalance-lock-supported=false";

static unsigned long slen(const char *s) { unsigned long n = 0; while (s[n]) n++; return n; }
static void copy(void *d, const void *s, unsigned long n) {
    unsigned char *a = d; const unsigned char *b = s;
    while (n >= 4) { *(uint32_t *)a = *(const uint32_t *)b; a += 4; b += 4; n -= 4; }
    while (n--) *a++ = *b++;
}
static char *sdup(const char *s) { unsigned long n = slen(s) + 1; char *r = malloc(n); if (r) copy(r, s, n); return r; }

static const struct gralloc_module *g_gralloc;
static const struct gralloc_module *gralloc(void) {
    if (!g_gralloc) {
        void *h = dlopen("libhardware.so", 0);
        int (*get)(const char *, const struct hw_module **) = h ? dlsym(h, "hw_get_module") : 0;
        const struct hw_module *m = 0;
        if (get && get("gralloc", &m) == 0) g_gralloc = (const struct gralloc_module *)m;
    }
    return g_gralloc;
}

static int host_connect(void) {
    struct { uint16_t family; char path[108]; } a;
    static const char P[] = "/dev/socket/aemu_cam";
    for (unsigned i = 0; i < sizeof(a.path); i++) a.path[i] = i < sizeof(P) ? P[i] : 0;
    a.family = 1; /* AF_UNIX */
    int fd = socket(1, 1 /* SOCK_STREAM */, 0);
    if (fd < 0) return -1;
    if (connect(fd, &a, sizeof(a)) < 0) { close(fd); return -1; }
    return fd;
}
static void say(struct camera_device *d, const char *cmd) { if (d->fd >= 0) write(d->fd, cmd, slen(cmd)); }
static int read_full(int fd, void *buf, unsigned long n) {
    char *p = buf;
    while (n) { long k = read(fd, p, n); if (k <= 0) return -1; p += k; n -= (unsigned long)k; }
    return 0;
}

static void show(struct camera_device *d, const unsigned char *rgba, int w, int h) {
    struct preview_ops *win = d->window;
    const struct gralloc_module *g = gralloc();
    if (!win || !g || !d->window_ready) return;
    const void **buf = 0;
    int stride = 0;
    if (win->dequeue_buffer(win, &buf, &stride) != 0 || !buf) return;
    if (win->lock_buffer) win->lock_buffer(win, buf);
    void *va = 0;
    if (g->lock(g, *buf, USAGE_SW_WRITE_OFTEN, 0, 0, w, h, &va) == 0 && va) {
        if (stride == w) copy(va, rgba, (unsigned long)w * h * 4);
        else for (int y = 0; y < h; y++) copy((char *)va + (unsigned long)y * stride * 4, rgba + (unsigned long)y * w * 4, (unsigned long)w * 4);
        g->unlock(g, *buf);
        win->enqueue_buffer(win, buf);
    } else win->cancel_buffer(win, buf);
}

static void *reader(void *arg) {
    struct camera_device *d = arg;
    unsigned char *frame = malloc(W * H * 4 + W * H * 3 / 2);
    while (d->running && frame) {
        int32_t hdr[4];
        if (read_full(d->fd, hdr, sizeof(hdr)) < 0) break;
        int type = hdr[0], w = hdr[1], h = hdr[2];
        unsigned long len = (unsigned long)hdr[3];
        if (type == 1 && w == W && h == H && len == W * H * 4 + W * H * 3 / 2) {
            if (read_full(d->fd, frame, len) < 0) break;
            if (!d->previewing) continue;
            show(d, frame, w, h);
            if ((d->msgs & MSG_PREVIEW_FRAME) && d->data && d->get_memory) {
                struct camera_memory *m = d->get_memory(-1, W * H * 3 / 2, 1, d->user);
                if (m && m->data) { copy(m->data, frame + W * H * 4, W * H * 3 / 2); d->data(MSG_PREVIEW_FRAME, m, 0, 0, d->user); }
                if (m && m->release) m->release(m);
            }
        } else if (type == 2 && len > 0 && len < (16u << 20)) {
            struct camera_memory *m = d->get_memory ? d->get_memory(-1, len, 1, d->user) : 0;
            if (!m || !m->data) { unsigned char t[512]; while (len) { unsigned long k = len < sizeof(t) ? len : sizeof(t); if (read_full(d->fd, t, k) < 0) break; len -= k; } continue; }
            if (read_full(d->fd, m->data, len) < 0) { if (m->release) m->release(m); break; }
            d->previewing = 0;   /* a picture stops the preview, the application starts it again */
            if ((d->msgs & MSG_SHUTTER) && d->notify) d->notify(MSG_SHUTTER, 0, 0, d->user);
            if ((d->msgs & MSG_RAW_IMAGE_NOTIFY) && d->notify) d->notify(MSG_RAW_IMAGE_NOTIFY, 0, 0, d->user);
            if ((d->msgs & MSG_COMPRESSED_IMAGE) && d->data) d->data(MSG_COMPRESSED_IMAGE, m, 0, 0, d->user);
            if (m->release) m->release(m);
        } else {
            unsigned char t[512];
            int bad = 0;
            while (len) { unsigned long k = len < sizeof(t) ? len : sizeof(t); if (read_full(d->fd, t, k) < 0) { bad = 1; break; } len -= k; }
            if (bad) break;
        }
    }
    free(frame);
    return 0;
}

static int c_set_window(struct camera_device *d, struct preview_ops *w) {
    d->window = w;
    d->window_ready = 0;
    if (!w) return 0;
    int min = 1;
    if (w->get_min_undequeued_buffer_count) w->get_min_undequeued_buffer_count(w, &min);
    w->set_usage(w, USAGE_SW_WRITE_OFTEN);
    w->set_buffer_count(w, min + 2);
    if (w->set_buffers_geometry(w, W, H, FORMAT_RGBA_8888) != 0) return 0;
    d->window_ready = 1;
    return 0;
}
static void c_set_callbacks(struct camera_device *d, notify_cb n, data_cb c, data_ts_cb t, request_memory g, void *user) {
    (void)t; d->notify = n; d->data = c; d->get_memory = g; d->user = user;
}
static void c_enable(struct camera_device *d, int32_t m) { d->msgs |= m; }
static void c_disable(struct camera_device *d, int32_t m) { d->msgs &= ~m; }
static int c_enabled(struct camera_device *d, int32_t m) { return (d->msgs & m) != 0; }
static int c_start_preview(struct camera_device *d) { d->previewing = 1; say(d, "start\n"); return 0; }
static void c_stop_preview(struct camera_device *d) { d->previewing = 0; say(d, "stop\n"); }
static int c_preview_enabled(struct camera_device *d) { return d->previewing; }
static int c_meta(struct camera_device *d, int e) { (void)d; return e ? -38 : 0; }
static int c_start_rec(struct camera_device *d) { (void)d; return -38; }   /* video recording is not supported yet */
static void c_stop_rec(struct camera_device *d) { (void)d; }
static int c_rec_enabled(struct camera_device *d) { (void)d; return 0; }
static void c_release_frame(struct camera_device *d, const void *p) { (void)d; (void)p; }
static int c_focus(struct camera_device *d) { if ((d->msgs & MSG_FOCUS) && d->notify) d->notify(MSG_FOCUS, 1, 0, d->user); return 0; }
static int c_cancel_focus(struct camera_device *d) { (void)d; return 0; }
static int c_take_picture(struct camera_device *d) { say(d, "shot\n"); return 0; }
static int c_cancel_picture(struct camera_device *d) { (void)d; return 0; }
static int c_set_params(struct camera_device *d, const char *p) {
    if (!p) return -22;
    char *n = sdup(p);
    if (!n) return -12;
    free(d->params); d->params = n;
    return 0;
}
static char *c_get_params(struct camera_device *d) { return sdup(d->params ? d->params : DEFAULTS); }
static void c_put_params(struct camera_device *d, char *p) { (void)d; free(p); }
static int c_command(struct camera_device *d, int32_t c, int32_t a, int32_t b) { (void)d; (void)c; (void)a; (void)b; return 0; }
static void c_release(struct camera_device *d) {
    d->previewing = 0;
    if (d->running) {
        d->running = 0;
        say(d, "close\n");
        /* SHUT_RDWR wakes the reader blocked in read() */
        extern int shutdown(int fd, int how);
        shutdown(d->fd, 2);
        pthread_join(d->thread, 0);
    }
    if (d->fd >= 0) { close(d->fd); d->fd = -1; }
}
static int c_dump(struct camera_device *d, int fd) { (void)d; (void)fd; return 0; }

static struct camera_ops ops = {
    c_set_window, c_set_callbacks, c_enable, c_disable, c_enabled, c_start_preview, c_stop_preview, c_preview_enabled,
    c_meta, c_start_rec, c_stop_rec, c_rec_enabled, c_release_frame, c_focus, c_cancel_focus, c_take_picture,
    c_cancel_picture, c_set_params, c_get_params, c_put_params, c_command, c_release, c_dump,
};

static int dev_close(struct hw_device *h) {
    struct camera_device *d = (struct camera_device *)h;
    c_release(d);
    free(d->params);
    free(d);
    return 0;
}

static int cam_open(const struct hw_module *m, const char *id, struct hw_device **out) {
    int n = (id && id[0] >= '0' && id[0] <= '9') ? id[0] - '0' : 0;
    if (n > 1) return -22;
    struct camera_device *d = calloc(1, sizeof(*d));
    if (!d) return -12;
    d->common.tag = HARDWARE_DEVICE_TAG;
    d->common.version = 0;
    d->common.module = (struct hw_module *)m;
    d->common.close = dev_close;
    d->ops = &ops;
    d->id = n;
    d->fd = host_connect();
    if (d->fd < 0) { free(d); return -19; /* -ENODEV: the host does not serve a camera */ }
    say(d, n ? "open 1\n" : "open 0\n");
    d->running = 1;
    if (pthread_create(&d->thread, 0, reader, d) != 0) { close(d->fd); free(d); return -12; }
    *out = &d->common;
    return 0;
}

static int cam_count(void) { return 2; }
static int cam_info(int id, struct camera_info *info) {
    if (id < 0 || id > 1) return -22;
    info->facing = id;                      /* 0 back, 1 front */
    info->orientation = id ? 270 : 90;      /* the phone's sensors are mounted sideways, frames come unrotated */
    return 0;
}

static struct hw_module_methods methods = { cam_open };

EXPORT struct camera_module HMI = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = 0x0100,   /* CAMERA_MODULE_API_VERSION_1_0 */
        .hal_api_version = 0,
        .id = "camera",
        .name = "AEmulator camera",
        .author = "AEmulator",
        .methods = &methods,
    },
    .get_number_of_cameras = cam_count,
    .get_camera_info = cam_info,
};
