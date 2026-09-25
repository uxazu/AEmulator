/*
 * audio.primary.default.so для прошивок Android 4.2–4.4 (AOSP и близкие к нему).
 *
 * Звук стенда устроен просто: гость пишет PCM 16 бит стерео 48 кГц в /dev/eac (FIFO), приложение читает
 * его и играет через AudioTrack. HAL автора делает то же, но его структуры разложены под AudioFlinger HTC
 * (лишние слоты в audio_hw_device и audio_stream_out), и AudioFlinger AOSP вызывает не те функции.
 * Здесь раскладка — ровно как в hardware/libhardware/include/hardware/audio.h KitKat; для 4.2/4.3 она
 * совместима (хвостовые поля 4.4 просто не читаются).
 *
 * Без заголовков AOSP: структуры описаны вручную, зависимостей — только libc.
 */
#include <stdint.h>
#include <stddef.h>

extern int open(const char *path, int flags, ...);
extern long write(int fd, const void *buf, unsigned long n);
extern int close(int fd);
extern void *calloc(unsigned long n, unsigned long size);
extern void free(void *p);
extern int *__errno(void);
extern int usleep(unsigned long us);

#define O_RDWR 2
#define EAGAIN 11
#define O_NONBLOCK 04000
#define EINTR 4
#define ENOSYS 38
#define EINVAL 22

#define RATE 48000
#define CHANNELS_STEREO 3        /* AUDIO_CHANNEL_OUT_STEREO */
#define FORMAT_PCM16 1           /* AUDIO_FORMAT_PCM_16_BIT */
#define DEVICE_OUT_SPEAKER 0x2
#define DEVICE_OUT_DEFAULT 0x40000000
#define BUFFER_BYTES 4096
#define LATENCY_MS 100

typedef void *fnp;

/* ---- hw_module_t / hw_device_t ---- */
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

/* ---- audio_config (4.4: + audio_offload_info_t, который мы не трогаем) ---- */
struct audio_config { uint32_t sample_rate, channel_mask; int format; };

/* ---- audio_stream / audio_stream_out (KitKat) ---- */
struct audio_stream {
    uint32_t (*get_sample_rate)(const struct audio_stream *s);
    int (*set_sample_rate)(struct audio_stream *s, uint32_t rate);
    size_t (*get_buffer_size)(const struct audio_stream *s);
    uint32_t (*get_channels)(const struct audio_stream *s);
    int (*get_format)(const struct audio_stream *s);
    int (*set_format)(struct audio_stream *s, int format);
    int (*standby)(struct audio_stream *s);
    int (*dump)(const struct audio_stream *s, int fd);
    uint32_t (*get_device)(const struct audio_stream *s);
    int (*set_device)(struct audio_stream *s, uint32_t device);
    int (*set_parameters)(struct audio_stream *s, const char *kv);
    char *(*get_parameters)(const struct audio_stream *s, const char *keys);
    int (*add_audio_effect)(const struct audio_stream *s, void *effect);
    int (*remove_audio_effect)(const struct audio_stream *s, void *effect);
};
struct audio_stream_out {
    struct audio_stream common;
    uint32_t (*get_latency)(const struct audio_stream_out *s);
    int (*set_volume)(struct audio_stream_out *s, float left, float right);
    long (*write)(struct audio_stream_out *s, const void *buf, size_t bytes);
    int (*get_render_position)(const struct audio_stream_out *s, uint32_t *frames);
    int (*get_next_write_timestamp)(const struct audio_stream_out *s, int64_t *ts);
    /* 4.4: офлоад и точное положение — не поддерживаем (NULL, AudioFlinger это проверяет) */
    fnp set_callback, pause, resume, drain, flush, get_presentation_position;
};

/* ---- audio_hw_device (KitKat) ---- */
struct audio_hw_device {
    struct hw_device common;
    uint32_t (*get_supported_devices)(const struct audio_hw_device *d);
    int (*init_check)(const struct audio_hw_device *d);
    int (*set_voice_volume)(struct audio_hw_device *d, float v);
    int (*set_master_volume)(struct audio_hw_device *d, float v);
    int (*get_master_volume)(struct audio_hw_device *d, float *v);
    int (*set_mode)(struct audio_hw_device *d, int mode);
    int (*set_mic_mute)(struct audio_hw_device *d, int state);
    int (*get_mic_mute)(const struct audio_hw_device *d, int *state);
    int (*set_parameters)(struct audio_hw_device *d, const char *kv);
    char *(*get_parameters)(const struct audio_hw_device *d, const char *keys);
    size_t (*get_input_buffer_size)(const struct audio_hw_device *d, const struct audio_config *c);
    int (*open_output_stream)(struct audio_hw_device *d, int handle, uint32_t devices, int flags,
                              struct audio_config *c, struct audio_stream_out **out);
    void (*close_output_stream)(struct audio_hw_device *d, struct audio_stream_out *s);
    int (*open_input_stream)(struct audio_hw_device *d, int handle, uint32_t devices,
                             struct audio_config *c, void **in);
    void (*close_input_stream)(struct audio_hw_device *d, void *in);
    int (*dump)(const struct audio_hw_device *d, int fd);
    int (*set_master_mute)(struct audio_hw_device *d, int mute);
    int (*get_master_mute)(struct audio_hw_device *d, int *mute);
};

struct out {
    struct audio_stream_out s;   /* первым полем: указатель на out == указатель на поток */
    int fd;
    uint32_t device;
    uint64_t frames;
};

/* ---------------------------------------------------------------- поток вывода */

static uint32_t o_rate(const struct audio_stream *s) { (void)s; return RATE; }
static int o_set_rate(struct audio_stream *s, uint32_t r) { (void)s; return r == RATE ? 0 : -EINVAL; }
static size_t o_bufsize(const struct audio_stream *s) { (void)s; return BUFFER_BYTES; }
static uint32_t o_channels(const struct audio_stream *s) { (void)s; return CHANNELS_STEREO; }
static int o_format(const struct audio_stream *s) { (void)s; return FORMAT_PCM16; }
static int o_set_format(struct audio_stream *s, int f) { (void)s; return f == FORMAT_PCM16 ? 0 : -EINVAL; }
static int o_dump(const struct audio_stream *s, int fd) { (void)s; (void)fd; return 0; }
static uint32_t o_device(const struct audio_stream *s) { return ((const struct out *)s)->device; }
static int o_set_device(struct audio_stream *s, uint32_t d) { ((struct out *)s)->device = d; return 0; }
static int o_set_params(struct audio_stream *s, const char *kv) { (void)s; (void)kv; return 0; }
static char *o_get_params(const struct audio_stream *s, const char *k) {
    (void)s; (void)k;
    char *r = (char *)calloc(1, 1); /* AudioFlinger освобождает строку сам */
    return r;
}
static int o_effect(const struct audio_stream *s, void *e) { (void)s; (void)e; return 0; }
static uint32_t o_latency(const struct audio_stream_out *s) { (void)s; return LATENCY_MS; }
static int o_volume(struct audio_stream_out *s, float l, float r) { (void)s; (void)l; (void)r; return -ENOSYS; }

static int o_standby(struct audio_stream *st) {
    struct out *o = (struct out *)st;
    if (o->fd >= 0) { close(o->fd); o->fd = -1; }
    return 0;
}

static long o_write(struct audio_stream_out *st, const void *buf, size_t bytes) {
    struct out *o = (struct out *)st;
    /* O_RDWR: открытие FIFO не ждёт читателя (O_WRONLY ждал бы — и микшер AudioFlinger вставал бы,
       а за ним system_server в AudioSystem.setParameters); O_NONBLOCK: полный канал не вешает микшер */
    if (o->fd < 0) o->fd = open("/dev/eac", O_RDWR | O_NONBLOCK);
    unsigned long frame_us = (unsigned long)(bytes / 4) * 1000000UL / RATE;
    int played = 0;
    if (o->fd >= 0) {
        const char *p = (const char *)buf;
        size_t left = bytes;
        unsigned long waited = 0;
        while (left > 0) {
            long n = write(o->fd, p, left);
            if (n > 0) { p += n; left -= (size_t)n; continue; }
            int e = n < 0 ? *__errno() : 0;
            if (e == EINTR) continue;
            if (e == EAGAIN) {
                /* приложение ещё не выбрало звук — подождём не дольше длительности куска, потом выбросим */
                if (waited >= frame_us + 20000) break;
                usleep(5000); waited += 5000;
                continue;
            }
            close(o->fd); o->fd = -1;
            break;
        }
        played = left < bytes;
    }
    /* без читателя канал не тормозит запись — держим темп сами, иначе микшер крутится на 100% процессора */
    if (!played) usleep(frame_us);
    o->frames += bytes / 4;
    return (long)bytes;
}

static int o_render_pos(const struct audio_stream_out *st, uint32_t *frames) {
    if (!frames) return -EINVAL;
    *frames = (uint32_t)((const struct out *)st)->frames;
    return 0;
}
static int o_next_ts(const struct audio_stream_out *s, int64_t *ts) { (void)s; (void)ts; return -EINVAL; }

/* ---------------------------------------------------------------- устройство */

static uint32_t d_devices(const struct audio_hw_device *d) { (void)d; return DEVICE_OUT_SPEAKER | DEVICE_OUT_DEFAULT; }
static int d_zero(void) { return 0; }
static int d_float_nosys(struct audio_hw_device *d, float v) { (void)d; (void)v; return -ENOSYS; }
static int d_get_master_volume(struct audio_hw_device *d, float *v) { (void)d; (void)v; return -ENOSYS; }
static int d_set_mode(struct audio_hw_device *d, int m) { (void)d; (void)m; return 0; }
static int d_set_mic_mute(struct audio_hw_device *d, int s) { (void)d; (void)s; return 0; }
static int d_get_mic_mute(const struct audio_hw_device *d, int *s) { (void)d; if (s) *s = 0; return 0; }
static int d_set_params(struct audio_hw_device *d, const char *kv) { (void)d; (void)kv; return 0; }
static char *d_get_params(const struct audio_hw_device *d, const char *k) { (void)d; (void)k; return (char *)calloc(1, 1); }
static size_t d_in_bufsize(const struct audio_hw_device *d, const struct audio_config *c) { (void)d; (void)c; return 0; }
static int d_dump(const struct audio_hw_device *d, int fd) { (void)d; (void)fd; return 0; }
static int d_set_master_mute(struct audio_hw_device *d, int m) { (void)d; (void)m; return -ENOSYS; }
static int d_get_master_mute(struct audio_hw_device *d, int *m) { (void)d; (void)m; return -ENOSYS; }

static int d_open_out(struct audio_hw_device *d, int handle, uint32_t devices, int flags,
                      struct audio_config *c, struct audio_stream_out **out) {
    (void)d; (void)handle; (void)flags;
    /* поддерживаем ровно один формат; AudioFlinger сам приводит микшер к нему */
    if (c) { c->sample_rate = RATE; c->channel_mask = CHANNELS_STEREO; c->format = FORMAT_PCM16; }
    struct out *o = (struct out *)calloc(1, sizeof(struct out));
    if (!o) return -12;
    o->fd = -1;
    o->device = devices;
    o->s.common.get_sample_rate = o_rate;
    o->s.common.set_sample_rate = o_set_rate;
    o->s.common.get_buffer_size = o_bufsize;
    o->s.common.get_channels = o_channels;
    o->s.common.get_format = o_format;
    o->s.common.set_format = o_set_format;
    o->s.common.standby = o_standby;
    o->s.common.dump = o_dump;
    o->s.common.get_device = o_device;
    o->s.common.set_device = o_set_device;
    o->s.common.set_parameters = o_set_params;
    o->s.common.get_parameters = o_get_params;
    o->s.common.add_audio_effect = o_effect;
    o->s.common.remove_audio_effect = o_effect;
    o->s.get_latency = o_latency;
    o->s.set_volume = o_volume;
    o->s.write = o_write;
    o->s.get_render_position = o_render_pos;
    o->s.get_next_write_timestamp = o_next_ts;
    *out = &o->s;
    return 0;
}

static void d_close_out(struct audio_hw_device *d, struct audio_stream_out *s) {
    (void)d;
    if (!s) return;
    o_standby(&s->common);
    free(s);
}

static int d_open_in(struct audio_hw_device *d, int h, uint32_t dev, struct audio_config *c, void **in) {
    (void)d; (void)h; (void)dev; (void)c;
    if (in) *in = 0;
    return -ENOSYS;   /* микрофона у эмулятора нет */
}
static void d_close_in(struct audio_hw_device *d, void *in) { (void)d; (void)in; }

static int d_close(struct hw_device *dev) { free(dev); return 0; }

static int hal_open(const struct hw_module *m, const char *id, struct hw_device **dev) {
    const char *want = "audio_hw_if";
    for (int i = 0; ; i++) {
        if (id[i] != want[i]) return -EINVAL;
        if (!id[i]) break;
    }
    struct audio_hw_device *d = (struct audio_hw_device *)calloc(1, sizeof(struct audio_hw_device));
    if (!d) return -12;
    d->common.tag = 0x48574454;          /* HARDWARE_DEVICE_TAG */
    d->common.version = 0x0200;          /* AUDIO_DEVICE_API_VERSION_2_0 */
    d->common.module = (struct hw_module *)m;
    d->common.close = d_close;
    d->get_supported_devices = d_devices;
    d->init_check = (int (*)(const struct audio_hw_device *))d_zero;
    d->set_voice_volume = d_float_nosys;
    d->set_master_volume = d_float_nosys;
    d->get_master_volume = d_get_master_volume;
    d->set_mode = d_set_mode;
    d->set_mic_mute = d_set_mic_mute;
    d->get_mic_mute = d_get_mic_mute;
    d->set_parameters = d_set_params;
    d->get_parameters = d_get_params;
    d->get_input_buffer_size = d_in_bufsize;
    d->open_output_stream = d_open_out;
    d->close_output_stream = d_close_out;
    d->open_input_stream = d_open_in;
    d->close_input_stream = d_close_in;
    d->dump = d_dump;
    d->set_master_mute = d_set_master_mute;
    d->get_master_mute = d_get_master_mute;
    *dev = &d->common;
    return 0;
}

static struct hw_module_methods g_methods = { hal_open };

__attribute__((visibility("default"))) struct hw_module HMI = {
    .tag = 0x48574d54,                    /* HARDWARE_MODULE_TAG */
    .module_api_version = 0x0001,         /* AUDIO_MODULE_API_VERSION_0_1 */
    .hal_api_version = 0,
    .id = "audio",
    .name = "AEmulator audio HAL (/dev/eac)",
    .author = "AEmulator",
    .methods = &g_methods,
};
