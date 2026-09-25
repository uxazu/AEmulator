/*
 * audio_policy.default.so для прошивок, чей AudioPolicyService знает больше потоков, чем AOSP.
 *
 * Samsung 4.x пропускает в политику потоки 0..14 (AOSP 4.3 — 0..9). Политика AOSP хранит состояние
 * потоков в массиве на AUDIO_STREAM_CNT элементов и при номере 10+ пишет за его конец — куча портится,
 * mediaserver падает в случайных местах. Этот модуль грузит политику AOSP (libaemu_apaosp.so),
 * подменяет create_audio_policy и отдаёт наружу свою таблицу функций: лишние потоки сводятся к MUSIC
 * или отбрасываются, остальные вызовы уходят в политику как есть.
 */
#include <stdint.h>

extern void *dlopen(const char *name, int flags);
extern void *dlsym(void *handle, const char *name);
extern const char *dlerror(void);
extern void *malloc(unsigned int n);
extern void free(void *p);
extern int __android_log_print(int prio, const char *tag, const char *fmt, ...);
#define LOGI(...) __android_log_print(4, "aemu_ap", __VA_ARGS__)

#define NSLOTS 31          /* struct audio_policy в AOSP 4.3: 31 указатель */
#define NPUB 40            /* наружу — с запасом: Samsung добавляет свои слоты в конец (31 — get_param_from_policy) */
#define AOSP_STREAM_CNT 10
#define STREAM_MUSIC 3

typedef void *fn_t;
struct wrap { fn_t slot[NPUB]; void *real; }; /* real — по смещению 160, его читают трамплины */

struct hw_module_methods { int (*open)(const void *module, const char *id, void **device); };
struct hw_module {
    uint32_t tag; uint16_t module_api_version, hal_api_version;
    const char *id, *name, *author;
    struct hw_module_methods *methods;
    void *dso; uint32_t reserved[25];
};
/* audio_policy_device: hw_device_t (0x40 байт), затем create/destroy */
struct ap_device {
    uint32_t common[16];
    int (*create_audio_policy)(const struct ap_device *dev, void *ops, void *service, void **ap);
    int (*destroy_audio_policy)(const struct ap_device *dev, void *ap);
};

static const struct hw_module *g_real;
/* 1 — родная политика прошивки (libaemu_approm.so): все слоты напрямую, только пул binder заранее;
   0 — политика AOSP 4.3 (libaemu_apaosp.so): 31 слот, лишние потоки производителя сводим к MUSIC */
static int g_rom_mode;
static int (*g_create)(const struct ap_device *, void *, void *, void **);
static int (*g_destroy)(const struct ap_device *, void *);

extern char aemu_ap_tramp_table[]; /* 31 трамплин по 16 байт (tramp.S) */

#define REAL(p) (((struct wrap *)(p))->real)
#define SLOT(p, i) (((fn_t *)REAL(p))[i])
static int big(int s) { return s < 0 || s >= AOSP_STREAM_CNT; }
static int map(int s) { return big(s) ? STREAM_MUSIC : s; }

static int w_get_output(void *p, int stream, uint32_t sr, int fmt, uint32_t mask, int flags) {
    return ((int (*)(void *, int, uint32_t, int, uint32_t, int))SLOT(p, 8))(REAL(p), map(stream), sr, fmt, mask, flags);
}
static int w_start_output(void *p, int out, int stream, int session) {
    return ((int (*)(void *, int, int, int))SLOT(p, 9))(REAL(p), out, map(stream), session);
}
static int w_stop_output(void *p, int out, int stream, int session) {
    return ((int (*)(void *, int, int, int))SLOT(p, 10))(REAL(p), out, map(stream), session);
}
static void w_init_stream_volume(void *p, int stream, int mn, int mx) {
    if (!big(stream)) ((void (*)(void *, int, int, int))SLOT(p, 16))(REAL(p), stream, mn, mx);
}
static int w_set_index(void *p, int stream, int index) {
    return big(stream) ? 0 : ((int (*)(void *, int, int))SLOT(p, 17))(REAL(p), stream, index);
}
static int w_get_index(void *p, int stream, int *index) {
    if (big(stream)) { if (index) *index = 5; return 0; }
    return ((int (*)(void *, int, int *))SLOT(p, 18))(REAL(p), stream, index);
}
static int w_set_index_dev(void *p, int stream, int index, uint32_t dev) {
    return big(stream) ? 0 : ((int (*)(void *, int, int, uint32_t))SLOT(p, 19))(REAL(p), stream, index, dev);
}
static int w_get_index_dev(void *p, int stream, int *index, uint32_t dev) {
    if (big(stream)) { if (index) *index = 5; return 0; }
    return ((int (*)(void *, int, int *, uint32_t))SLOT(p, 20))(REAL(p), stream, index, dev);
}
static uint32_t w_strategy(void *p, int stream) {
    return ((uint32_t (*)(void *, int))SLOT(p, 21))(REAL(p), map(stream));
}
static uint32_t w_devices(void *p, int stream) {
    return ((uint32_t (*)(void *, int))SLOT(p, 22))(REAL(p), map(stream));
}
static int w_active(void *p, int stream, uint32_t past) {
    return big(stream) ? 0 : ((int (*)(void *, int, uint32_t))SLOT(p, 27))(REAL(p), stream, past);
}
static int w_active_remote(void *p, int stream, uint32_t past) {
    return big(stream) ? 0 : ((int (*)(void *, int, uint32_t))SLOT(p, 28))(REAL(p), stream, past);
}

/* Samsung: char *get_param_from_policy(pol, keys) — вызывающий освобождает строку через free() */
static char *w_get_param(void *p, const char *keys) {
    (void)p; (void)keys;
    char *s = (char *)malloc(1);
    if (s) s[0] = 0;
    return s;
}
static int w_zero(void) { return 0; }

/* audio_policy_service_ops: 18 указателей; слот 16 — open_output_on_module */
#define NOPS 24
static fn_t g_ops[NOPS];
static fn_t g_real_open_on_module;
typedef int (*open_on_module_t)(void *, int, uint32_t *, uint32_t *, int *, uint32_t *, uint32_t *, int);
static int w_open_output_on_module(void *service, int module, uint32_t *devices, uint32_t *rate, int *fmt,
                                   uint32_t *mask, uint32_t *latency, int flags) {
    LOGI("open_output_on_module: модуль %d, устройства %x, частота %u, флаги %x", module,
         devices ? *devices : 0xdead, rate ? *rate : 0, flags);
    int r = ((open_on_module_t)g_real_open_on_module)(service, module, devices, rate, fmt, mask, latency, flags);
    LOGI("open_output_on_module → %d", r);
    return r;
}

/*
 * binderd стенда отдаёт процессу его же службу прокси-объектом, поэтому вызов политики в AudioFlinger
 * из того же mediaserver идёт транзакцией самому себе. Политика открывает выходы ещё в конструкторе,
 * до ProcessState::startThreadPool() в main() — отвечать некому, выход не открывается, звука нет.
 * Запускаем пул потоков binder заранее (повторный вызов из main() ничего не делает).
 */
static void start_binder_pool(void) {
    void *h = dlopen("libbinder.so", 0);
    if (!h) return;
    void (*self)(void **) = (void (*)(void **))dlsym(h, "_ZN7android12ProcessState4selfEv");
    void (*start)(void *) = (void (*)(void *))dlsym(h, "_ZN7android12ProcessState15startThreadPoolEv");
    if (!self || !start) return;
    void *sp[2] = { 0, 0 }; /* sp<ProcessState>: ссылку сознательно не отпускаем — объект живёт до конца процесса */
    self(sp);
    if (sp[0]) start(sp[0]);
}

static int w_create(const struct ap_device *dev, void *ops, void *service, void **ap) {
    void *real = 0;
    start_binder_pool();
    fn_t *o = (fn_t *)ops;
    for (int i = 0; i < 18; i++) g_ops[i] = o[i];
    g_real_open_on_module = g_ops[16];
    g_ops[16] = (fn_t)w_open_output_on_module;
    ops = g_ops;
    int r = g_create(dev, ops, service, &real);
    if (r != 0 || !real) return r;
    struct wrap *w = (struct wrap *)malloc(sizeof(struct wrap));
    if (!w) { *ap = real; return 0; }
    if (g_rom_mode) {
        /* родная политика знает свои потоки и свои слоты — пропускаем всё как есть */
        for (int i = 0; i < NPUB; i++) w->slot[i] = (fn_t)(aemu_ap_tramp_table + i * 16 + 1);
        w->real = real;
        *ap = w;
        return 0;
    }
    for (int i = 0; i < NSLOTS; i++) w->slot[i] = (fn_t)(aemu_ap_tramp_table + i * 16 + 1); /* Thumb */
    for (int i = NSLOTS; i < NPUB; i++) w->slot[i] = (fn_t)w_zero;
    w->slot[31] = (fn_t)w_get_param;
    w->real = real;
    w->slot[8] = (fn_t)w_get_output;
    w->slot[9] = (fn_t)w_start_output;
    w->slot[10] = (fn_t)w_stop_output;
    w->slot[16] = (fn_t)w_init_stream_volume;
    w->slot[17] = (fn_t)w_set_index;
    w->slot[18] = (fn_t)w_get_index;
    w->slot[19] = (fn_t)w_set_index_dev;
    w->slot[20] = (fn_t)w_get_index_dev;
    w->slot[21] = (fn_t)w_strategy;
    w->slot[22] = (fn_t)w_devices;
    w->slot[27] = (fn_t)w_active;
    w->slot[28] = (fn_t)w_active_remote;
    *ap = w;
    return 0;
}

static int w_destroy(const struct ap_device *dev, void *ap) {
    if (!ap) return g_destroy(dev, ap);
    void *real = REAL(ap);
    free(ap);
    return g_destroy(dev, real);
}

static int w_open(const void *module, const char *id, void **device) {
    (void)module;
    if (!g_real) {
        void *h = dlopen("/system/lib/libaemu_approm.so", 0);
        if (h) { g_real = (const struct hw_module *)dlsym(h, "HMI"); g_rom_mode = g_real != 0; }
        if (!g_real) {
            h = dlopen("/system/lib/libaemu_apaosp.so", 0);
            if (h) g_real = (const struct hw_module *)dlsym(h, "HMI");
            else LOGI("политика AOSP не загрузилась: %s", dlerror());
        }
        if (!g_real) return -22;
    }
    struct ap_device *dev = 0;
    int r = g_real->methods->open(g_real, id, (void **)&dev);
    if (r != 0 || !dev) { LOGI("open политики → %d", r); return r; }
    g_create = dev->create_audio_policy;
    g_destroy = dev->destroy_audio_policy;
    dev->create_audio_policy = w_create;
    dev->destroy_audio_policy = w_destroy;
    *device = dev;
    return 0;
}

static struct hw_module_methods g_methods = { w_open };

__attribute__((visibility("default"))) struct hw_module HMI = {
    .tag = 0x48574d54, /* HARDWARE_MODULE_TAG */
    .module_api_version = 0x0100,
    .hal_api_version = 0,
    .id = "audio_policy",
    .name = "AEmulator audio policy (AOSP + streams clamp)",
    .author = "AEmulator",
    .methods = &g_methods,
};
