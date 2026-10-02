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
extern long read(int fd, void *buf, unsigned long n);
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

#ifndef AEMU_PCM_DEV
#define AEMU_PCM_DEV "/dev/eac"
#endif
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
#ifndef AEMU_ICS
    int (*get_next_write_timestamp)(const struct audio_stream_out *s, int64_t *ts);
    /* 4.4: офлоад и точное положение — не поддерживаем (NULL, AudioFlinger это проверяет) */
    fnp set_callback, pause, resume, drain, flush, get_presentation_position;
#endif
};

/* ---- audio_hw_device (KitKat) ---- */
struct audio_hw_device {
    struct hw_device common;
    uint32_t (*get_supported_devices)(const struct audio_hw_device *d);
    int (*init_check)(const struct audio_hw_device *d);
    int (*set_voice_volume)(struct audio_hw_device *d, float v);
    int (*set_master_volume)(struct audio_hw_device *d, float v);
#ifdef AEMU_QCOM
    int (*set_fm_volume)(struct audio_hw_device *d, float v);   /* Qualcomm CAF (libaudioflinger has setFmVolume) */
#endif
#ifndef AEMU_ICS
    int (*get_master_volume)(struct audio_hw_device *d, float *v);
#endif
    int (*set_mode)(struct audio_hw_device *d, int mode);
    int (*set_mic_mute)(struct audio_hw_device *d, int state);
    int (*get_mic_mute)(const struct audio_hw_device *d, unsigned char *state);
    int (*set_parameters)(struct audio_hw_device *d, const char *kv);
    char *(*get_parameters)(const struct audio_hw_device *d, const char *keys);
#ifdef AEMU_ICS
    /* Android 4.0: format/channels/rate passed as separate in-out pointers, no stream handle */
    size_t (*get_input_buffer_size)(const struct audio_hw_device *d, uint32_t rate, int format, int channels);
    int (*open_output_stream)(struct audio_hw_device *d, uint32_t devices, int *format, uint32_t *channels,
                              uint32_t *rate, struct audio_stream_out **out);
#else
    size_t (*get_input_buffer_size)(const struct audio_hw_device *d, const struct audio_config *c);
    int (*open_output_stream)(struct audio_hw_device *d, int handle, uint32_t devices, int flags,
                              struct audio_config *c, struct audio_stream_out **out);
#endif
#ifdef AEMU_QCOM
    int (*open_output_session)(struct audio_hw_device *d, uint32_t devices, int *format, int session,
                               struct audio_stream_out **out);  /* CAF LPA/tunnel output */
#endif
    void (*close_output_stream)(struct audio_hw_device *d, struct audio_stream_out *s);
#ifdef AEMU_ICS
    int (*open_input_stream)(struct audio_hw_device *d, uint32_t devices, int *format, uint32_t *channels,
                             uint32_t *rate, int acoustics, void **in);
#else
    int (*open_input_stream)(struct audio_hw_device *d, int handle, uint32_t devices,
                             struct audio_config *c, void **in);
#endif
    void (*close_input_stream)(struct audio_hw_device *d, void *in);
    int (*dump)(const struct audio_hw_device *d, int fd);
#ifndef AEMU_ICS
    int (*set_master_mute)(struct audio_hw_device *d, int mute);
    int (*get_master_mute)(struct audio_hw_device *d, int *mute);
#endif
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
    if (o->fd < 0) o->fd = open(AEMU_PCM_DEV, O_RDWR | O_NONBLOCK);
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
/* bool *state in audio.h: a 4-byte store clobbers the caller's stack (MediaTek AudioFlinger keeps its hardware-lock
   Autolock right after the flag and then never unlocks it; the next setParameters hangs system_server) */
static int d_get_mic_mute(const struct audio_hw_device *d, unsigned char *s) { (void)d; if (s) *s = 0; return 0; }
static int d_set_params(struct audio_hw_device *d, const char *kv) { (void)d; (void)kv; return 0; }
static char *d_get_params(const struct audio_hw_device *d, const char *k) { (void)d; (void)k; return (char *)calloc(1, 1); }
/* ---------------------------------------------------------------- микрофон
 * The host records the phone's microphone at exactly the rate and channel count the guest asked for: the stream
 * announces them on /dev/aemu_mic_ctl ("start <rate> <channels>", "stop") and reads PCM 16-bit from the FIFO
 * /dev/aemu_mic. Without a host (or without the permission) the guest gets silence at real-time pace.
 */
#define CH_IN_MONO 0x10
#define CH_IN_STEREO 0x0c
struct audio_stream_in {
    struct audio_stream common;
    int (*set_gain)(struct audio_stream_in *s, float gain);
    long (*read)(struct audio_stream_in *s, void *buf, size_t bytes);
    uint32_t (*get_input_frames_lost)(struct audio_stream_in *s);
    fnp get_capture_position, reserved[6];   /* 5.0+: NULL = not supported */
};
struct in {
    struct audio_stream_in s;
    int fd;
    uint32_t rate, mask, device;
    int channels, started;
};
/* no libgcc: division by a variable done by shifts */
static unsigned long udiv(unsigned long n, unsigned long d) {
    unsigned long q = 0, r = 0;
    for (int b = 31; b >= 0; b--) { r = (r << 1) | ((n >> b) & 1); if (r >= d) { r -= d; q |= 1UL << b; } }
    return q;
}
static int rate_ok(uint32_t r) { return r == 8000 || r == 11025 || r == 16000 || r == 22050 || r == 32000 || r == 44100 || r == 48000; }
static size_t in_bytes(uint32_t rate, int ch) { size_t b = (size_t)rate / 50 * (size_t)(ch == 2 ? 4 : 2); return (b + 63) & ~(size_t)63; }  /* 20 ms */

static void mic_ctl(const char *what, uint32_t rate, int ch) {
    char line[48];
    int k = 0;
    for (const char *c = what; *c; c++) line[k++] = *c;
    if (rate) {
        char t[12]; int n = 0; uint32_t v = rate;
        line[k++] = ' ';
        do { t[n++] = (char)('0' + v % 10); v /= 10; } while (v);
        while (n) line[k++] = t[--n];
        line[k++] = ' '; line[k++] = (char)('0' + ch);
    }
    line[k++] = 10;   /* '\n' */
    int fd = open("/dev/aemu_mic_ctl", O_RDWR | O_NONBLOCK);
    if (fd >= 0) { write(fd, line, (unsigned long)k); close(fd); }
}

static uint32_t i_rate(const struct audio_stream *s) { return ((const struct in *)s)->rate; }
static int i_set_rate(struct audio_stream *s, uint32_t r) { (void)s; (void)r; return -ENOSYS; }
static size_t i_bufsize(const struct audio_stream *s) { const struct in *i = (const struct in *)s; return in_bytes(i->rate, i->channels); }
static uint32_t i_channels(const struct audio_stream *s) { return ((const struct in *)s)->mask; }
static uint32_t i_device(const struct audio_stream *s) { return ((const struct in *)s)->device; }
static int i_set_device(struct audio_stream *s, uint32_t d) { ((struct in *)s)->device = d; return 0; }
static int i_gain(struct audio_stream_in *s, float g) { (void)s; (void)g; return 0; }
static uint32_t i_lost(struct audio_stream_in *s) { (void)s; return 0; }
static int i_standby(struct audio_stream *st) {
    struct in *i = (struct in *)st;
    if (i->started) { mic_ctl("stop", 0, 0); i->started = 0; }
    return 0;
}
static long i_read(struct audio_stream_in *st, void *buf, size_t bytes) {
    struct in *i = (struct in *)st;
    if (!i->started) { mic_ctl("start", i->rate, i->channels); i->started = 1; }
    if (i->fd < 0) i->fd = open("/dev/aemu_mic", O_RDWR | O_NONBLOCK);
    unsigned long frames = (unsigned long)bytes / (i->channels == 2 ? 4UL : 2UL);
    unsigned long dur_us = udiv(frames * 1000UL, i->rate) * 1000UL;
    char *p = (char *)buf;
    size_t got = 0;
    unsigned long waited = 0;
    while (got < bytes && i->fd >= 0) {
        long n = read(i->fd, p + got, bytes - got);
        if (n > 0) { got += (size_t)n; continue; }
        if (n < 0 && *__errno() == EINTR) continue;
        if (waited >= dur_us + dur_us / 2) break;   /* host late or absent: the rest is silence */
        usleep(2000); waited += 2000;
    }
    for (size_t k = got; k < bytes; k++) p[k] = 0;
    if (i->fd < 0) usleep(dur_us);
    return (long)bytes;
}

#ifdef AEMU_ICS
static size_t d_in_bufsize(const struct audio_hw_device *d, uint32_t r, int f, int ch) { (void)d; (void)f; return in_bytes(rate_ok(r) ? r : 48000, ch == 1 || ch == 2 ? ch : 1); }
#else
static size_t d_in_bufsize(const struct audio_hw_device *d, const struct audio_config *c) {
    (void)d;
    return in_bytes(rate_ok(c->sample_rate) ? c->sample_rate : 48000, c->channel_mask == CH_IN_STEREO ? 2 : 1);
}
#endif
#ifdef AEMU_QCOM
static int d_open_session(struct audio_hw_device *d, uint32_t dev, int *f, int ses, struct audio_stream_out **out) {
    (void)d; (void)dev; (void)f; (void)ses;
    if (out) *out = 0;
    return -ENOSYS;   /* no hardware decoder: AudioFlinger falls back to the software mixer */
}
#endif
static int d_dump(const struct audio_hw_device *d, int fd) { (void)d; (void)fd; return 0; }
static int d_set_master_mute(struct audio_hw_device *d, int m) { (void)d; (void)m; return -ENOSYS; }
static int d_get_master_mute(struct audio_hw_device *d, int *m) { (void)d; (void)m; return -ENOSYS; }

#ifdef AEMU_ICS
static int d_open_out(struct audio_hw_device *d, uint32_t devices, int *format, uint32_t *channels,
                      uint32_t *rate, struct audio_stream_out **out) {
    (void)d;
    if (format) *format = FORMAT_PCM16;
    if (channels) *channels = CHANNELS_STEREO;
    if (rate) *rate = RATE;
#else
static int d_open_out(struct audio_hw_device *d, int handle, uint32_t devices, int flags,
                      struct audio_config *c, struct audio_stream_out **out) {
    (void)d; (void)handle; (void)flags;
    /* поддерживаем ровно один формат; AudioFlinger сам приводит микшер к нему */
    if (c) { c->sample_rate = RATE; c->channel_mask = CHANNELS_STEREO; c->format = FORMAT_PCM16; }
#endif
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
#ifndef AEMU_ICS
    o->s.get_next_write_timestamp = o_next_ts;
#endif
    *out = &o->s;
    return 0;
}

static void d_close_out(struct audio_hw_device *d, struct audio_stream_out *s) {
    (void)d;
    if (!s) return;
    o_standby(&s->common);
    free(s);
}

#ifdef AEMU_ICS
static int d_open_in(struct audio_hw_device *d, uint32_t dev, int *f, uint32_t *ch, uint32_t *r, int ac, void **in) {
    (void)d; (void)dev; (void)f; (void)ch; (void)r; (void)ac;
#else
static int d_open_in(struct audio_hw_device *d, int h, uint32_t dev, struct audio_config *c, void **in) {
    (void)d; (void)h; (void)dev; (void)c;
#endif
    if (in) *in = 0;
#ifdef AEMU_ICS
    uint32_t want_rate = r ? *r : 0, want_mask = ch ? *ch : 0;
    int want_fmt = f ? *f : FORMAT_PCM16;
#else
    uint32_t want_rate = c ? c->sample_rate : 0, want_mask = c ? c->channel_mask : 0;
    int want_fmt = c ? c->format : FORMAT_PCM16;
#endif
    uint32_t rate = rate_ok(want_rate) ? want_rate : 48000;
    uint32_t mask = want_mask == CH_IN_STEREO ? CH_IN_STEREO : CH_IN_MONO;
    if (rate != want_rate || mask != want_mask || want_fmt != FORMAT_PCM16) {
        /* suggest what we can do; AudioFlinger opens again with it and resamples */
#ifdef AEMU_ICS
        if (r) *r = rate; if (ch) *ch = mask; if (f) *f = FORMAT_PCM16;
#else
        if (c) { c->sample_rate = rate; c->channel_mask = mask; c->format = FORMAT_PCM16; }
#endif
        return -EINVAL;
    }
    struct in *i = calloc(1, sizeof(*i));
    if (!i) return -12;
    i->fd = -1; i->rate = rate; i->mask = mask; i->channels = mask == CH_IN_STEREO ? 2 : 1; i->device = dev;
    struct audio_stream *a = &i->s.common;
    a->get_sample_rate = i_rate; a->set_sample_rate = i_set_rate; a->get_buffer_size = i_bufsize;
    a->get_channels = i_channels; a->get_format = o_format; a->set_format = o_set_format;
    a->standby = i_standby; a->dump = o_dump; a->get_device = i_device; a->set_device = i_set_device;
    a->set_parameters = o_set_params; a->get_parameters = o_get_params;
    a->add_audio_effect = o_effect; a->remove_audio_effect = o_effect;
    i->s.set_gain = i_gain; i->s.read = i_read; i->s.get_input_frames_lost = i_lost;
    *in = i;
    return 0;
}
static void d_close_in(struct audio_hw_device *d, void *p) {
    (void)d;
    struct in *i = (struct in *)p;
    if (!i) return;
    i_standby(&i->s.common);
    if (i->fd >= 0) close(i->fd);
    free(i);
}

#ifdef AEMU_MTK
/* MediaTek extends audio_hw_device with vendor calls (SetAudioCommand, VoiceUnlock...) after the AOSP slots;
   AudioFlinger calls them under its hardware lock. Give it a zeroed tail of harmless stubs. */
#define MTK_EXTRA 48
static long d_mtk_stub(void) { return 0; }
#endif
static int d_close(struct hw_device *dev) { free(dev); return 0; }

static int hal_open(const struct hw_module *m, const char *id, struct hw_device **dev) {
    const char *want = "audio_hw_if";
    for (int i = 0; ; i++) {
        if (id[i] != want[i]) return -EINVAL;
        if (!id[i]) break;
    }
    #ifdef AEMU_MTK
    struct audio_hw_device *d = (struct audio_hw_device *)calloc(1, sizeof(struct audio_hw_device) + MTK_EXTRA * sizeof(void *));
    if (d) { void **x = (void **)(d + 1); for (int i = 0; i < MTK_EXTRA; i++) x[i] = (void *)d_mtk_stub; }
#else
    struct audio_hw_device *d = (struct audio_hw_device *)calloc(1, sizeof(struct audio_hw_device));
#endif
    if (!d) return -12;
    d->common.tag = 0x48574454;          /* HARDWARE_DEVICE_TAG */
#ifdef AEMU_ICS
    d->common.version = 0;               /* 4.0: no device API versioning */
#else
    d->common.version = 0x0200;          /* AUDIO_DEVICE_API_VERSION_2_0 */
#endif
    d->common.module = (struct hw_module *)m;
    d->common.close = d_close;
    d->get_supported_devices = d_devices;
    d->init_check = (int (*)(const struct audio_hw_device *))d_zero;
    d->set_voice_volume = d_float_nosys;
    d->set_master_volume = d_float_nosys;
#ifdef AEMU_QCOM
    d->set_fm_volume = d_float_nosys;
    d->open_output_session = d_open_session;
#endif
#ifndef AEMU_ICS
    d->get_master_volume = d_get_master_volume;
#endif
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
#ifndef AEMU_ICS
    d->set_master_mute = d_set_master_mute;
    d->get_master_mute = d_get_master_mute;
#endif
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

#ifdef AEMU_MTK
/*
 * MediaTek: libaudioflinger takes the primary HAL straight from /system/lib/libaudio.primary.default.so
 * (its HMI) and also links the DcRemove filter from it. This build replaces that library: HMI is our HAL,
 * DcRemove calls go to the vendor original, renamed to libaudio.mtk.so by the emulator.
 */
extern void *dlopen(const char *name, int flags);
extern void *dlsym(void *h, const char *name);
static const char *const k_dc[] = {
    "_ZN7android8DcRemoveC1Ev", "_ZN7android8DcRemoveD1Ev", "_ZN7android8DcRemove4initEjjj",
    "_ZN7android8DcRemove7processEPKvjPv", "_ZN7android8DcRemove5closeEv",
};
static void *g_dc[5];
static void dc_trap(void) { __builtin_trap(); }
__attribute__((used)) static void *dc_resolve(unsigned i) {
    if (!g_dc[i]) {
        static void *h;
        if (!h) h = dlopen("/system/lib/libaudio.mtk.so", 0);
        g_dc[i] = h ? dlsym(h, k_dc[i]) : 0;
        if (!g_dc[i]) g_dc[i] = (void *)dc_trap;
    }
    return g_dc[i];
}
#define DC_STUB(sym, idx) \
    __asm__(".text\n.global " sym "\n.type " sym ",%function\n.thumb_func\n" sym ":\n" \
            "push {r0-r3, lr}\nmovs r0, #" #idx "\nbl dc_resolve\nmov r12, r0\npop {r0-r3, lr}\nbx r12\n");
DC_STUB("_ZN7android8DcRemoveC1Ev", 0)
DC_STUB("_ZN7android8DcRemoveD1Ev", 1)
DC_STUB("_ZN7android8DcRemove4initEjjj", 2)
DC_STUB("_ZN7android8DcRemove7processEPKvjPv", 3)
DC_STUB("_ZN7android8DcRemove5closeEv", 4)
#endif
