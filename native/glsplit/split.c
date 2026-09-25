/*
 * libGLES_split — переходник перед GL-мостом (libGLES_bridge.so, гостевая часть моста автора).
 *
 * 1. Раздельные имена. Часть загрузчиков EGL (Samsung 4.3, AOSP 4.4+ в раздельном режиме) требуют
 *    libEGL_<тег>.so / libGLESv1_CM_<тег>.so / libGLESv2_<тег>.so. Этот файл кладётся под всеми
 *    именами, а вызовы уходят в единственный экземпляр моста — состояние EGL/GL общее.
 *
 * 2. Загрузка текстур. Мост передаёт на хост w*h*bpp байт, не учитывая GL_UNPACK_ALIGNMENT и
 *    GL_UNPACK_ROW_LENGTH, а драйвер телефона читает с выравниванием строк — при строках некратной
 *    длины (RGB, альфа-текстуры нечётной ширины) он лезет за конец буфера и роняет приложение.
 *    Здесь такие загрузки переупаковываются в плотный буфер с выравниванием 1.
 *
 * Зависит только от libc/libdl, одинаково грузится в bionic Android 2.3–6.0.
 */
#include "names.h"

typedef unsigned int GLenum;
typedef int GLint;
typedef int GLsizei;
typedef void GLvoid;

extern void *dlopen(const char *name, int flags);
extern void *dlsym(void *handle, const char *name);
extern void *malloc(unsigned int n);
extern void free(void *p);

#define N (sizeof(kNames) / sizeof(kNames[0]))
static void *g_lib;
static void *g_fn[N];

static void trap(void) { __builtin_trap(); }

static int es1_current(void);
static int zero_fn(void) { return 0; }
static volatile int g_any_es1;   /* в процессе есть контекст ES1 — только тогда проверяем поток */

__attribute__((visibility("hidden"))) void *aemu_split_resolve(unsigned idx) {
    /* функции ES2/ES3 в контексте ES1: у телефона-хозяина их нет в таблице ES1 — был бы вызов по нулю */
    if (g_any_es1 && kEs2Only[idx] && es1_current()) return (void *)zero_fn;
    void *f = g_fn[idx];
    if (f) return f;
    if (!g_lib) g_lib = dlopen("/system/lib/egl/libGLES_bridge.so", 0);
    if (g_lib) f = dlsym(g_lib, kNames[idx]);
    if (!f) f = (void *)trap;
    g_fn[idx] = f;
    return f;
}

#define GL_UNPACK_ALIGNMENT 0x0CF5
#define GL_UNPACK_ROW_LENGTH 0x0CF2
#define GL_UNPACK_SKIP_ROWS 0x0CF3
#define GL_UNPACK_SKIP_PIXELS 0x0CF4
#define GL_PIXEL_UNPACK_BUFFER 0x88EC

/* состояние распаковки текущего потока (гость обычно рисует из одного потока на контекст) */
static int t_align = 4, t_rowlen, t_skiprows, t_skippix, t_pbo;

typedef void (*PixelStoreFn)(GLenum, GLint);
typedef void (*BindBufferFn)(GLenum, unsigned);
typedef void (*TexImageFn)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const GLvoid *);
typedef void (*TexSubImageFn)(GLenum, GLint, GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, const GLvoid *);

__attribute__((visibility("default"))) void glPixelStorei(GLenum pname, GLint param) {
    switch (pname) {
        case GL_UNPACK_ALIGNMENT: t_align = param; break;
        case GL_UNPACK_ROW_LENGTH: t_rowlen = param; break;
        case GL_UNPACK_SKIP_ROWS: t_skiprows = param; break;
        case GL_UNPACK_SKIP_PIXELS: t_skippix = param; break;
    }
    ((PixelStoreFn)aemu_split_resolve(IDX_glPixelStorei))(pname, param);
}

__attribute__((visibility("default"))) void glBindBuffer(GLenum target, unsigned buffer) {
    if (target == GL_PIXEL_UNPACK_BUFFER) t_pbo = buffer != 0;
    ((BindBufferFn)aemu_split_resolve(IDX_glBindBuffer))(target, buffer);
}

/* байт на пиксель; 0 — формат, который не переупаковываем (сжатый, неизвестный) */
static int bytes_per_pixel(GLenum format, GLenum type) {
    int comps;
    switch (format) {
        case 0x1906: /* ALPHA */ case 0x1909: /* LUMINANCE */ case 0x1903: /* RED */
        case 0x1902: /* DEPTH_COMPONENT */ case 0x8D94: /* RED_INTEGER */ comps = 1; break;
        case 0x190A: /* LUMINANCE_ALPHA */ case 0x8227: /* RG */ case 0x8228: /* RG_INTEGER */ comps = 2; break;
        case 0x1907: /* RGB */ case 0x8D98: /* RGB_INTEGER */ comps = 3; break;
        case 0x1908: /* RGBA */ case 0x80E1: /* BGRA */ case 0x8D99: /* RGBA_INTEGER */ comps = 4; break;
        default: return 0;
    }
    switch (type) {
        case 0x1401: /* UNSIGNED_BYTE */ case 0x1400: /* BYTE */ return comps;
        case 0x1403: /* UNSIGNED_SHORT */ case 0x1402: /* SHORT */
        case 0x140B: /* HALF_FLOAT */ case 0x8D61: /* HALF_FLOAT_OES */ return comps * 2;
        case 0x1405: /* UNSIGNED_INT */ case 0x1404: /* INT */ case 0x1406: /* FLOAT */ return comps * 4;
        case 0x8363: /* 5_6_5 */ case 0x8033: /* 4_4_4_4 */ case 0x8034: /* 5_5_5_1 */ return 2;
        case 0x8368: /* 2_10_10_10_REV */ case 0x84FA: /* 24_8 */ return 4;
        default: return 0;
    }
}

/* без memcpy: в bionic 2.x нет __aeabi_memcpy, а зависеть от libgcc не хотим */
static void copy_bytes(unsigned char *d, const unsigned char *s, unsigned long n) {
    if ((((unsigned long)d | (unsigned long)s | n) & 3) == 0) {
        unsigned int *dw = (unsigned int *)d; const unsigned int *sw = (const unsigned int *)s;
        for (unsigned long i = 0; i < n / 4; i++) dw[i] = sw[i];
        return;
    }
    for (unsigned long i = 0; i < n; i++) d[i] = s[i];
}

/*
 * Если данные гостя лежат не плотно (выравнивание строк, длина строки, пропуски) — возвращает
 * плотную копию (освободить free) и выставляет *need = 1; иначе возвращает исходный указатель.
 */
static const void *tighten(GLsizei w, GLsizei h, GLenum format, GLenum type, const void *pixels, int *need) {
    *need = 0;
    if (!pixels || t_pbo || w <= 0 || h <= 0) return pixels;
    int bpp = bytes_per_pixel(format, type);
    if (!bpp) return pixels;
    int align = (t_align == 1 || t_align == 2 || t_align == 4 || t_align == 8) ? t_align : 4;
    unsigned long row = (unsigned long)w * bpp;
    unsigned long srcRowPixels = t_rowlen > 0 ? (unsigned long)t_rowlen : (unsigned long)w;
    unsigned long stride = (srcRowPixels * bpp + align - 1) & ~(unsigned long)(align - 1); /* align — степень двойки */
    if (stride == row && t_skiprows == 0 && t_skippix == 0) {
        /* плотно, но драйвер хоста всё равно выровняет последнюю строку — это безопасно,
           только если выравнивание 1 или строка кратна ему */
        if (align == 1 || (row & (unsigned long)(align - 1)) == 0) return pixels;
    }
    const unsigned char *src = (const unsigned char *)pixels + (unsigned long)t_skiprows * stride + (unsigned long)t_skippix * bpp;
    unsigned char *dst = (unsigned char *)malloc(row * h);
    if (!dst) return pixels;
    for (GLsizei y = 0; y < h; y++) copy_bytes(dst + (unsigned long)y * row, src + (unsigned long)y * stride, row);
    *need = 1;
    return dst;
}

static void unpack_tight(int on) {
    PixelStoreFn ps = (PixelStoreFn)aemu_split_resolve(IDX_glPixelStorei);
    if (on) {
        ps(GL_UNPACK_ALIGNMENT, 1);
        if (t_rowlen) ps(GL_UNPACK_ROW_LENGTH, 0);
        if (t_skiprows) ps(GL_UNPACK_SKIP_ROWS, 0);
        if (t_skippix) ps(GL_UNPACK_SKIP_PIXELS, 0);
    } else {
        ps(GL_UNPACK_ALIGNMENT, t_align);
        if (t_rowlen) ps(GL_UNPACK_ROW_LENGTH, t_rowlen);
        if (t_skiprows) ps(GL_UNPACK_SKIP_ROWS, t_skiprows);
        if (t_skippix) ps(GL_UNPACK_SKIP_PIXELS, t_skippix);
    }
}

__attribute__((visibility("default"))) void glTexImage2D(GLenum target, GLint level, GLint ifmt, GLsizei w, GLsizei h,
                                                          GLint border, GLenum format, GLenum type, const GLvoid *pixels) {
    TexImageFn f = (TexImageFn)aemu_split_resolve(IDX_glTexImage2D);
    int need;
    const void *p = tighten(w, h, format, type, pixels, &need);
    if (need) unpack_tight(1);
    f(target, level, ifmt, w, h, border, format, type, p);
    if (need) { unpack_tight(0); free((void *)p); }
}

__attribute__((visibility("default"))) void glTexSubImage2D(GLenum target, GLint level, GLint x, GLint y, GLsizei w, GLsizei h,
                                                             GLenum format, GLenum type, const GLvoid *pixels) {
    TexSubImageFn f = (TexSubImageFn)aemu_split_resolve(IDX_glTexSubImage2D);
    int need;
    const void *p = tighten(w, h, format, type, pixels, &need);
    if (need) unpack_tight(1);
    f(target, level, x, y, w, h, format, type, p);
    if (need) { unpack_tight(0); free((void *)p); }
}

/*
 * Контекст ES2 для SurfaceFlinger 4.4. Его RenderEngine создаёт контекст без EGL_CONTEXT_CLIENT_VERSION
 * (то есть ES1) и рисует фиксированным конвейером, а сборки MIUI/MTK при этом ещё и зовут шейдерные
 * функции ES2 (размытие, DRM-значок). На настоящем железе такой вызов в контексте ES1 — ошибка в журнале,
 * а драйвер телефона-хозяина роняет мост нулевым указателем. Если движок выставил AEMU_GL_ES2=1
 * (только для surfaceflinger), просим ES2 — тогда SurfaceFlinger сам выбирает GLES20RenderEngine.
 */
extern char *getenv(const char *name);
typedef void *(*CreateContextFn)(void *dpy, void *config, void *share, const GLint *attribs);
#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#define EGL_NONE 0x3038

static void ctx_remember(void *ctx, int ver);
extern long syscall(long n, ...);

static int is_surfaceflinger(void) {
    static int v = -1;
    if (v < 0) {
        char buf[128];
        long fd = syscall(5 /* open */, "/proc/self/cmdline", 0, 0);
        long n = fd >= 0 ? syscall(3 /* read */, fd, buf, sizeof(buf) - 1) : 0;
        if (fd >= 0) syscall(6 /* close */, fd);
        v = 0;
        if (n > 0) {
            buf[n] = 0;
            const char *want = "surfaceflinger";
            for (long i = 0; i + 14 <= n && !v; i++) {
                int k = 0;
                while (k < 14 && buf[i + k] == want[k]) k++;
                if (k == 14) v = 1;
            }
        }
    }
    return v;
}

__attribute__((visibility("default"))) void *eglCreateContext(void *dpy, void *config, void *share, const GLint *attribs) {
    CreateContextFn f = (CreateContextFn)aemu_split_resolve(IDX_eglCreateContext);
    static int force = -1;
    if (force < 0) { const char *v = getenv("AEMU_GL_ES2"); force = v && v[0] == '1'; }
    int ver = 1;
    if (attribs) for (int i = 0; attribs[i] != EGL_NONE && i < 64; i += 2)
        if (attribs[i] == EGL_CONTEXT_CLIENT_VERSION) ver = attribs[i + 1];
    void *ctx;
    if (force && ver < 2) {
        GLint list[64];
        int n = 0;
        if (attribs) for (int i = 0; attribs[i] != EGL_NONE && n < 60; i += 2) {
            if (attribs[i] == EGL_CONTEXT_CLIENT_VERSION) continue;
            list[n++] = attribs[i]; list[n++] = attribs[i + 1];
        }
        list[n++] = EGL_CONTEXT_CLIENT_VERSION; list[n++] = 2;
        list[n] = EGL_NONE;
        ver = 2;
        ctx = f(dpy, config, share, list);
    } else ctx = f(dpy, config, share, attribs);
    /* гостевая часть моста всем контекстам SurfaceFlinger даёт ES1 (и сообщает «ES-CM 1.1»): учитываем это */
    if (is_surfaceflinger()) ver = 1;
    ctx_remember(ctx, ver);
    return ctx;
}

/* ------------------------------------------------------------ версии контекстов по потокам */

extern long syscall(long n, ...);
#define MAXCTX 256
#define MAXTHR 256
static void *g_ctx[MAXCTX];
static unsigned char g_ctxver[MAXCTX];
static int g_tid[MAXTHR];
static unsigned char g_thrver[MAXTHR];

static void ctx_remember(void *ctx, int ver) {
    if (!ctx) return;
    if (ver < 2) g_any_es1 = 1;
    for (int i = 0; i < MAXCTX; i++) if (g_ctx[i] == ctx || g_ctx[i] == 0) { g_ctx[i] = ctx; g_ctxver[i] = (unsigned char)ver; return; }
}
static int ctx_version(void *ctx) {
    for (int i = 0; i < MAXCTX; i++) if (g_ctx[i] == ctx) return g_ctxver[i];
    return 2;
}
static int es1_current(void) {
    int tid = (int)syscall(224 /* gettid */);
    for (int i = 0; i < MAXTHR; i++) if (g_tid[i] == tid) return g_thrver[i] < 2;
    return 0;
}
static void thread_set(int ver) {
    int tid = (int)syscall(224);
    int free = -1;
    for (int i = 0; i < MAXTHR; i++) {
        if (g_tid[i] == tid) { g_thrver[i] = (unsigned char)ver; return; }
        if (g_tid[i] == 0 && free < 0) free = i;
    }
    if (free >= 0) { g_thrver[free] = (unsigned char)ver; g_tid[free] = tid; }
}

typedef unsigned (*MakeCurrentFn)(void *dpy, void *draw, void *read, void *ctx);
__attribute__((visibility("default"))) unsigned eglMakeCurrent(void *dpy, void *draw, void *read, void *ctx) {
    unsigned r = ((MakeCurrentFn)aemu_split_resolve(IDX_eglMakeCurrent))(dpy, draw, read, ctx);
    if (r) thread_set(ctx ? ctx_version(ctx) : 2);
    return r;
}
