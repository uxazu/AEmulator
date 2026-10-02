// aemu64 — runs the firmware's own arm64 programs natively on an arm64 phone.
//
//   aemu64 <argv0> <guest program> [args…]        AEMU64_ROOT=<image root>
//
// The guest keeps its own linker and bionic. A seccomp filter traps the calls that must see the guest's world
// (paths, ids, ashmem, exec) into the SIGSYS handler below, which redirects them under the image root. Syscalls
// made from this image (linked at IMAGE_BASE) pass the filter, so the handler talks to the kernel directly.
// No libc: the guest owns the thread pointer.

typedef unsigned long u64;
typedef long i64;
typedef unsigned int u32;
typedef unsigned short u16;
typedef unsigned char u8;

#define IMAGE_HI 0x20u            /* IMAGE_BASE >> 32, see build.sh */
#define IMAGE_SPAN 0x00100000u

long sc(long nr, long a, long b, long c, long d, long e, long f);
void enter_guest(void *entry, void *sp) __attribute__((noreturn));
#define S0(n) sc(n, 0, 0, 0, 0, 0, 0)
#define S1(n, a) sc(n, (long)(a), 0, 0, 0, 0, 0)
#define S2(n, a, b) sc(n, (long)(a), (long)(b), 0, 0, 0, 0)
#define S3(n, a, b, c) sc(n, (long)(a), (long)(b), (long)(c), 0, 0, 0)
#define S4(n, a, b, c, d) sc(n, (long)(a), (long)(b), (long)(c), (long)(d), 0, 0)
#define S5(n, a, b, c, d, e) sc(n, (long)(a), (long)(b), (long)(c), (long)(d), (long)(e), 0)
#define S6(n, a, b, c, d, e, f) sc(n, (long)(a), (long)(b), (long)(c), (long)(d), (long)(e), (long)(f))

enum {
    NR_setxattr = 5, NR_lsetxattr = 6, NR_getxattr = 8, NR_lgetxattr = 9, NR_listxattr = 11, NR_llistxattr = 12,
    NR_removexattr = 14, NR_lremovexattr = 15, NR_getcwd = 17, NR_inotify_add_watch = 27, NR_ioctl = 29,
    NR_mknodat = 33, NR_mkdirat = 34, NR_unlinkat = 35, NR_symlinkat = 36, NR_linkat = 37, NR_renameat = 38,
    NR_umount2 = 39, NR_mount = 40, NR_statfs = 43, NR_truncate = 45, NR_ftruncate = 46, NR_faccessat = 48,
    NR_chdir = 49, NR_chroot = 51, NR_fchmodat = 53, NR_fchownat = 54, NR_fchown = 55, NR_openat = 56, NR_close = 57,
    NR_read = 63, NR_write = 64, NR_pread64 = 67, NR_readlinkat = 78, NR_newfstatat = 79, NR_fstat = 80,
    NR_utimensat = 88, NR_timerfd_create = 85, NR_capget = 90, NR_capset = 91, NR_unshare = 97, NR_exit_group = 94, NR_rt_sigaction = 134,
    NR_rt_sigprocmask = 135, NR_reboot = 142, NR_setregid = 143, NR_setgid = 144, NR_setreuid = 145, NR_setuid = 146,
    NR_setresuid = 147, NR_getresuid = 148, NR_setresgid = 149, NR_getresgid = 150, NR_setfsuid = 151,
    NR_setfsgid = 152, NR_getgroups = 158, NR_setgroups = 159, NR_prctl = 167, NR_getuid = 174, NR_geteuid = 175,
    NR_getgid = 176, NR_getegid = 177, NR_socket = 198, NR_bind = 200, NR_connect = 203, NR_setsockopt = 208, NR_munmap = 215, NR_execve = 221,
    NR_mmap = 222, NR_mprotect = 226, NR_renameat2 = 276, NR_memfd_create = 279, NR_statx = 291,
};

#define AT_FDCWD (-100)
#define ENOENT 2
#define ENOEXEC 8
#define ENOMEM 12
#define EFAULT 14
#define EINVAL 22
#define ENAMETOOLONG 36
#define ENOSYS 38
#define PATH_MAX 4096
#define SIGSYS 31
#define SYSBIT (1UL << (SIGSYS - 1))

static u64 slen(const char *s) { u64 n = 0; while (s[n]) n++; return n; }
static int seq(const char *a, const char *b) { while (*a && *a == *b) a++, b++; return *a == *b; }
static int pre(const char *s, const char *p) { while (*p) if (*s++ != *p++) return 0; return 1; }
void *memcpy(void *d, const void *s, u64 n) { u8 *a = d; const u8 *b = s; while (n--) *a++ = *b++; return d; }
void *memset(void *d, int c, u64 n) { u8 *a = d; while (n--) *a++ = (u8)c; return d; }
void *memmove(void *d, const void *s, u64 n) {
    u8 *a = d; const u8 *b = s;
    if (a < b) while (n--) *a++ = *b++; else while (n--) a[n] = b[n];
    return d;
}
static int g_sayfd = -1;          // crash reports also go to <root>/dev/aemu_crash.log
static void say(const char *s) { S3(NR_write, 2, s, slen(s)); if (g_sayfd >= 0) S3(NR_write, g_sayfd, s, slen(s)); }
static void die(const char *a, const char *b) { say("aemu64: "); say(a); say(b); say("\n"); S1(NR_exit_group, 127); for (;;); }
static u64 atou(const char *s) { u64 v = 0; while (*s >= '0' && *s <= '9') v = v * 10 + (u64)(*s++ - '0'); return v; }
static char *utoa(u64 v, char *end) { *--end = 0; do *--end = (char)('0' + v % 10); while (v /= 10); return end; }

// ---------------------------------------------------------------------------------------------------- state

static char g_root[512];        // image root on the host, no trailing slash
static u64 g_rootlen;
static char g_self[512];        // this program
static char g_props[512];       // host file behind /dev/__properties__
static char g_exe[PATH_MAX];    // guest view of the running program
static int g_uid, g_gid;        // the ids the guest believes it has
static u32 g_real_uid;
static char g_qemu[512];        // qemu-arm for the firmware's 32-bit programs (AEMU64_QEMU)
static int g_crash;
static char g_sd[512]; static u64 g_sdlen;   // the shared card folder on the phone (AEMU64_SDCARD)
static int g_is32;                // this process is qemu-arm running a 32-bit program
static void (*g_fatal[32])(int, void *, void *);
static void fatal_relay(int sig, void *info, void *uc);
// qemu-arm's signal handler blocks every signal in the context it returns to (until the guest's handler is
// entered); with SIGSYS blocked the next trapped call would kill the process, so that bit is taken back out.
static void (*g_h32[65])(int, void *, void *);
static void relay32(int sig, void *info, void *uc) {
    void (*h)(int, void *, void *) = g_h32[sig];
    if ((unsigned long)h > 1) h(sig, info, uc);
    *(unsigned long *)((char *)uc + 40) &= ~(1UL << 30);    // uc_sigmask, SIGSYS
}
static int is_fatal(long sig) { return sig == 11 || sig == 7 || sig == 4 || sig == 6 || sig == 8; }            // AEMU64_CRASH: report fatal signals ourselves

// ---------------------------------------------------------------------------------------------------- paths

// Lexically normalises an absolute guest path into out (no "//", ".", ".."), so it cannot climb out of the root.
static int norm(const char *p, char *out, u64 cap) {
    u64 o = 0;
    while (*p) {
        while (*p == '/') p++;
        if (!*p) break;
        const char *e = p; while (*e && *e != '/') e++;
        u64 n = (u64)(e - p);
        if (n == 1 && p[0] == '.') { p = e; continue; }
        if (n == 2 && p[0] == '.' && p[1] == '.') { while (o && out[--o] != '/'); p = e; continue; }
        if (o + n + 2 > cap) return -ENAMETOOLONG;
        out[o++] = '/'; memcpy(out + o, p, n); o += n; p = e;
    }
    if (!o) out[o++] = '/';
    out[o] = 0;
    return 0;
}

static int host_dev(const char *n) {
    static const char *const pass[] = { "null", "zero", "full", "random", "urandom", "tty", "ptmx", 0 };
    for (int i = 0; pass[i]; i++) if (seq(n, pass[i])) return 1;
    return pre(n, "pts/") || seq(n, "pts");
}

// Guest path → host path. Relative paths stay as they are (the working directory is already inside the root).
// Returns the path to use, or 0 with *err set.
static const char *xl(const char *p, char *buf, int *err) {
    if (!p) return p;
    if (p[0] != '/') return p;
    char *n = buf + g_rootlen;              // normalised straight into its place after the root
    if ((*err = norm(p, n, PATH_MAX - g_rootlen))) return 0;
    if ((pre(n, "/proc") && (n[5] == '/' || !n[5])) || (pre(n, "/sys") && (n[4] == '/' || !n[4]))) {
        // the image's own files win (fake sysfs, /proc/mounts); the rest is the phone's. No SELinux for the guest,
        // and none of the phone's suspend controls (reading its wakeup_count blocks the guest's power manager).
        // under qemu-arm the process has the translator's own helper thread; zygote waits before every fork until
        // /proc/self/task lists one thread, so 32-bit programs see a list of one
        if (g_is32 && n[5] == '/') {
            const char *q = n + 6; u64 pid = 0;
            if (pre(q, "self")) q += 4; else { while (*q >= '0' && *q <= '9') pid = pid * 10 + (u64)(*q++ - '0'); if (pid != (u64)S0(172 /* getpid */)) q = ""; }
            if (seq(q, "/task")) memcpy(n, "/proc/.one", 11);
        }
        memcpy(buf, g_root, g_rootlen);
        if (pre(n, "/sys/fs/selinux") || pre(n, "/sys/power") || S4(NR_faccessat, AT_FDCWD, buf, 0, 0) == 0) return buf;
        return p;
    }
    // translated code is slow: the 32-bit zygote starts without its minutes of class preloading
    if (g_is32 && seq(n, "/system/etc/preloaded-classes")) memcpy(n, "/proc/.task1/none", 18);
    if (pre(n, "/dev/") && host_dev(n + 5)) return p;
    if (g_props[0] && seq(n, "/dev/__properties__")) return g_props;
    if (pre(n, g_root) && (n[g_rootlen] == '/' || !n[g_rootlen])) return p;   // already a host path (our own argv)
    if (g_sdlen) {
        // the guest's card: plain directories for it, the shared folder of the phone underneath
        static const char *const sd[] = { "/storage/emulated/0", "/storage/emulated/legacy", "/storage/self/primary", "/sdcard",
                                          "/mnt/sdcard", "/mnt/user/0/primary", "/mnt/shell/emulated/0", "/storage/sdcard0", 0 };
        for (int i = 0; sd[i]; i++) {
            u64 l = slen(sd[i]);
            if (!pre(n, sd[i]) || (n[l] != '/' && n[l])) continue;
            u64 rest = slen(n + l);
            if (g_sdlen + rest + 1 > 3 * PATH_MAX - PATH_MAX) break;
            memmove(buf + g_sdlen, n + l, rest + 1);
            memcpy(buf, g_sd, g_sdlen);
            return buf;
        }
    }
    memcpy(buf, g_root, g_rootlen);
    if (!n[1]) n[0] = 0;
    return buf;
}

// host path → guest path, in place
static void unxl(char *p) {
    if (g_sdlen && pre(p, g_sd) && (p[g_sdlen] == '/' || !p[g_sdlen])) {
        static const char g[] = "/storage/emulated/0";
        u64 rest = slen(p + g_sdlen);
        memmove(p + sizeof g - 1, p + g_sdlen, rest + 1);
        memcpy(p, g, sizeof g - 1);
        return;
    }
    if (!pre(p, g_root)) return;
    if (!p[g_rootlen]) { p[0] = '/'; p[1] = 0; return; }
    if (p[g_rootlen] == '/') memmove(p, p + g_rootlen, slen(p + g_rootlen) + 1);
}

// ---------------------------------------------------------------------------------------------------- stat

struct kstat {
    u64 st_dev, st_ino; u32 st_mode, st_nlink, st_uid, st_gid; u64 st_rdev, __pad1; i64 st_size;
    int st_blksize, __pad2; i64 st_blocks; i64 at, atn, mt, mtn, ct, ctn; u32 __u4, __u5;
};

// files of the image belong to the app's uid; the guest expects its own system to belong to root
static void fake_owner(struct kstat *st) {
    if (st->st_uid == g_real_uid) st->st_uid = 0;
    if (st->st_gid == g_real_uid) st->st_gid = 0;
    if ((st->st_mode & 0170000) != 0120000 && !(st->st_mode & 0077) && (st->st_mode & 0170000) != 0020000) {
        // the importer keeps everything 0700/0600: show the usual world-readable modes
        st->st_mode |= ((st->st_mode & 0500) >> 3) | ((st->st_mode & 0500) >> 6);
    }
}

// ---------------------------------------------------------------------------------------------------- ashmem

#define ASHMEM_MAGIC 0x77

static long ashmem_ioctl(int fd, u32 cmd, u64 arg) {
    switch (cmd & 0xff) {
    case 3: return S2(NR_ftruncate, fd, arg);                       // SET_SIZE
    case 4: { struct kstat st; long r = S2(NR_fstat, fd, &st); return r < 0 ? r : st.st_size; }
    case 2: if (arg) *(char *)arg = 0; return 0;                    // GET_NAME
    case 6: return 7;                                               // GET_PROT_MASK
    default: return 0;                                              // SET_NAME, SET_PROT_MASK, PIN, UNPIN, PURGE…
    }
}

struct sockaddr_un { u16 family; char path[108]; };
#include "bclient.c"

// ---------------------------------------------------------------------------------------------------- framebuffer

// /dev/graphics/fb0 is a plain file of two RGBX pages (the app's screen view maps it); the guest's gralloc asks
// the usual fbdev questions, answered here from <root>/dhd.fbgeom ("width height").
static u32 g_fbw, g_fbh;

static void fb_geom(void) {
    char p[600], t[64];
    memcpy(p, g_root, g_rootlen); memcpy(p + g_rootlen, "/dhd.fbgeom", 12);
    long fd = S4(NR_openat, AT_FDCWD, p, 0, 0);
    g_fbw = 720; g_fbh = 1280;
    if (fd < 0) return;
    long n = S3(NR_read, fd, t, sizeof t - 1);
    S1(NR_close, fd);
    if (n <= 0) return;
    t[n] = 0;
    const char *q = t; u32 w = (u32)atou(q);
    while (*q >= '0' && *q <= '9') q++;
    while (*q == ' ') q++;
    u32 h = (u32)atou(q);
    if (w && h) { g_fbw = w; g_fbh = h; }
}

static long fb_ioctl(u32 cmd, u64 arg) {
    if (!g_fbw) fb_geom();
    switch (cmd) {
    case 0x4600: {                              // FBIOGET_VSCREENINFO
        u32 *v = (u32 *)arg;
        memset(v, 0, 160);
        v[0] = g_fbw; v[1] = g_fbh; v[2] = g_fbw; v[3] = g_fbh; v[6] = 32;   // one page: gralloc copies each frame in on post
        v[8] = 0; v[9] = 8; v[11] = 8; v[12] = 8; v[14] = 16; v[15] = 8; v[17] = 24; v[18] = 8;   // R, G, B, A
        return 0;
    }
    case 0x4602: {                              // FBIOGET_FSCREENINFO
        u8 *f = (u8 *)arg;
        memset(f, 0, 80);
        memcpy(f, "aemu", 5);
        *(u32 *)(f + 24) = g_fbw * g_fbh * 4;       // smem_len
        *(u32 *)(f + 36) = 2;                       // FB_VISUAL_TRUECOLOR
        *(u16 *)(f + 42) = 1;                       // ypanstep
        *(u32 *)(f + 48) = g_fbw * 4;               // line_length
        return 0;
    }
    case 0x4601: case 0x4606: case 0x4611: return 0;    // PUT_VSCREENINFO, PAN_DISPLAY, BLANK
    }
    return -25; /* ENOTTY */
}

// ---------------------------------------------------------------------------------------------------- input

// /dev/input/event0: a stream of struct input_event from the app (AEMU64_INPUT socket); the evdev questions of the
// guest's EventHub are answered here: a direct multi-touch screen with the navigation and volume keys.
static char g_input[108];
static int g_infds[4];                          // descriptors + 1

static int is_input_fd(int fd) { for (int i = 0; i < 4; i++) if (g_infds[i] == fd + 1) return 1; return 0; }

static long input_open(long flags) {
    if (!g_input[0]) return -ENOENT;
    long s = S3(NR_socket, 1, 1 | (flags & 02004000), 0);
    if (s < 0) return s;
    struct sockaddr_un sa; sa.family = 1;
    u64 l = slen(g_input); memcpy(sa.path, g_input, l + 1);
    long r = S3(NR_connect, s, &sa, 2 + l + 1);
    if (r < 0) { S1(NR_close, s); return r; }
    for (int i = 0; i < 4; i++) if (!g_infds[i] || g_infds[i] == (int)s + 1) { g_infds[i] = (int)s + 1; break; }
    return s;
}

static void setbit(u8 *m, u64 len, u32 bit) { if (bit / 8 < len) m[bit / 8] |= (u8)(1 << (bit % 8)); }

static long input_ioctl(u32 cmd, u64 arg) {
    u32 nr = cmd & 0xff, len = (cmd >> 16) & 0x3fff;
    u8 *out = (u8 *)arg;
    if (!g_fbw) fb_geom();
    if (nr == 0x01) { *(int *)arg = 0x010001; return 0; }                        // EVIOCGVERSION
    if (nr == 0x02) { u16 *id = (u16 *)arg; id[0] = 0x18; id[1] = 1; id[2] = 1; id[3] = 1; return 0; }   // EVIOCGID
    if (nr == 0x06) { static const char name[] = "dhd-touch"; u64 n = sizeof name < len ? sizeof name : len; memcpy(out, name, n); return (long)n; }
    if (nr == 0x07 || nr == 0x08) return -ENOENT;                                // phys, uniq
    if (nr == 0x09) { memset(out, 0, len); setbit(out, len, 1); return len; }    // INPUT_PROP_DIRECT
    if (nr == 0x18 || nr == 0x19 || nr == 0x1b) { memset(out, 0, len); return len; }   // key, led, switch state
    if (nr >= 0x20 && nr < 0x40) {                                               // EVIOCGBIT(type)
        memset(out, 0, len);
        switch (nr - 0x20) {
        case 0: setbit(out, len, 0); setbit(out, len, 1); setbit(out, len, 3); break;
        case 1: for (u32 k = 1; k < 0xf0; k++) setbit(out, len, k); setbit(out, len, 580); break;
        case 3: setbit(out, len, 48); setbit(out, len, 50); setbit(out, len, 53); setbit(out, len, 54); setbit(out, len, 57); break;
        }
        return len;
    }
    if (nr >= 0x40 && nr < 0x80) {                                               // EVIOCGABS(axis)
        int *a = (int *)arg; u32 ax = nr - 0x40;
        memset(a, 0, 24);
        if (ax == 53) a[2] = (int)g_fbw - 1; else if (ax == 54) a[2] = (int)g_fbh - 1;
        else if (ax == 48 || ax == 50) a[2] = 255; else if (ax == 57) a[2] = 31; else return -EINVAL;
        return 0;
    }
    if (nr == 0x90 || nr == 0xa0 || nr == 0x03) return 0;                        // grab, clock id, repeat
    return -EINVAL;
}

// ---------------------------------------------------------------------------------------------------- exec

static int env_is_ours(const char *e) { return pre(e, "AEMU64_"); }

static long do_exec(const char *path, char *const argv[], char *const envp[]) {
    static char buf[PATH_MAX]; int err = 0;      /* exec replaces the process: nobody else is left to share it */
    const char *host = xl(path, buf, &err);
    if (!host) return err;
    long r = S4(NR_faccessat, AT_FDCWD, host, 1 /*X_OK*/, 0);
    if (r < 0) return r;
    u64 na = 0, ne = 0;
    if (argv) while (argv[na]) na++;
    if (envp) while (envp[ne]) ne++;
    u64 need = (na + ne + 24) * sizeof(char *);
    char **v = (char **)S6(NR_mmap, 0, need, 3, 0x22, -1, 0);
    if ((u64)v >= (u64)-4095) return -ENOMEM;
    char **a = v, **e;
    *a++ = g_self;
    *a++ = (char *)(na ? argv[0] : path);
    *a++ = (char *)host;
    for (u64 i = 1; i < na; i++) *a++ = argv[i];
    *a++ = 0;
    e = a;
    for (u64 i = 0; i < ne; i++) if (!env_is_ours(envp[i])) *a++ = envp[i];
    static char e_root[540], e_props[540], e_binder[140], e_id[64], e_f[] = "AEMU64_F=1";
    memcpy(e_root, "AEMU64_ROOT=", 12); memcpy(e_root + 12, g_root, g_rootlen + 1);
    *a++ = e_root;
    if (g_props[0]) { memcpy(e_props, "AEMU64_PROPS=", 13); memcpy(e_props + 13, g_props, slen(g_props) + 1); *a++ = e_props; }
    if (g_bsock[0]) { memcpy(e_binder, "AEMU64_BINDER=", 14); memcpy(e_binder + 14, g_bsock, slen(g_bsock) + 1); *a++ = e_binder; }
    char t[24], *s = utoa((u64)g_uid, t + sizeof t); u64 o = 10;
    memcpy(e_id, "AEMU64_ID=", 10); memcpy(e_id + o, s, slen(s)); o += slen(s); e_id[o++] = ',';
    s = utoa((u64)g_gid, t + sizeof t); memcpy(e_id + o, s, slen(s) + 1);
    *a++ = e_id; *a++ = e_f;
    if (g_crash) *a++ = "AEMU64_CRASH=1";
    static char e_input[130];
    if (g_input[0]) { memcpy(e_input, "AEMU64_INPUT=", 13); memcpy(e_input + 13, g_input, slen(g_input) + 1); *a++ = e_input; }
    static char e_qemu[540];
    if (g_qemu[0]) { memcpy(e_qemu, "AEMU64_QEMU=", 12); memcpy(e_qemu + 12, g_qemu, slen(g_qemu) + 1); *a++ = e_qemu; }
    static char e_sd[540];
    if (g_sdlen) { memcpy(e_sd, "AEMU64_SDCARD=", 14); memcpy(e_sd + 14, g_sd, g_sdlen + 1); *a++ = e_sd; }
    *a++ = 0;
    r = S3(NR_execve, g_self, v, e);
    S2(NR_munmap, v, need);
    return r;
}

// ---------------------------------------------------------------------------------------------------- traps

// The trap handler runs on whatever stack the guest thread has, including a 16 KB alternate signal stack with
// the guest's own handler already on it. Path buffers therefore come from this pool, not from the stack.
#define NPB 48
static char g_pb[NPB][3 * PATH_MAX];
static volatile int g_pbbusy[NPB];

static char *pb_get(void) {
    for (;;) {
        for (int i = 0; i < NPB; i++) {
            int z = 0;
            if (!g_pbbusy[i] && __atomic_compare_exchange_n(&g_pbbusy[i], &z, 1, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED)) return g_pb[i];
        }
        S0(NR_sched_yield);
    }
}
static void pb_put(char *b) { __atomic_store_n(&g_pbbusy[(b - g_pb[0]) / (3 * PATH_MAX)], 0, __ATOMIC_RELEASE); }

static long unix_addr(long nr, long fd, const struct sockaddr_un *sa, u64 len, char *pb) {
    if (sa && len > 2 && sa->family == 1 && sa->path[0] == '/') {
        char *p = pb, *buf = pb + PATH_MAX; int err = 0;
        u64 n = len - 2; if (n > 108) n = 108;
        memcpy(p, sa->path, n); p[n] = 0;
        const char *h = xl(p, buf, &err);
        if (!h) return err;
        u64 hl = slen(h);
        if (hl >= 108) return -ENAMETOOLONG;
        struct sockaddr_un t; t.family = 1; memcpy(t.path, h, hl + 1);
        return S3(nr, fd, &t, 2 + hl + 1);
    }
    return S3(nr, fd, sa, len);
}

#define P(i) ((const char *)a[i])
#define X(i) xl(P(i), b##i, &err)
#define NEEDX(i) const char *h##i = X(i); if (!h##i && P(i)) return err

static long trap2(long nr, long *a, void *uc, char **pbp);

static long trap(long nr, long *a, void *uc) {
    char *pb = 0;
    long r = trap2(nr, a, uc, &pb);
    if (pb) pb_put(pb);
    return r;
}

#define PB(i) ((*pbp ? *pbp : (*pbp = pb_get())) + (i) * PATH_MAX)

static long trap2(long nr, long *a, void *uc, char **pbp) {
    int err = 0;
    switch (nr) {
    case NR_openat: {
        char *b1 = PB(0);
        if (P(1) && P(1)[0] == '/' && P(1)[1] == 'd') {
            if (seq(P(1), "/dev/ashmem")) return S2(NR_memfd_create, "aemu-ashmem", (a[2] & 02000000) ? 1 : 0);
            if (seq(P(1), "/dev/binder")) return binder_open(a[2]);
            if (pre(P(1), "/dev/input/event")) return input_open(a[2]);
        }
        NEEDX(1);
        return S4(nr, a[0], h1, a[2], a[3]);
    }
    case NR_newfstatat: {
        char *b1 = PB(0);
        NEEDX(1);
        long r = S4(nr, a[0], h1, a[2], a[3]);
        if (r == 0) fake_owner((struct kstat *)a[2]);
        return r;
    }
    case NR_fstat: {
        long r = S2(nr, a[0], a[1]);
        if (r == 0) fake_owner((struct kstat *)a[1]);
        return r;
    }
    case NR_faccessat: case NR_mkdirat: case NR_unlinkat: case NR_fchmodat: case NR_utimensat: case NR_mknodat:
    case NR_statx: {
        char *b1 = PB(0);
        NEEDX(1);
        return S5(nr, a[0], h1, a[2], a[3], a[4]);
    }
    case NR_fchownat: case NR_fchown: return 0;
    case NR_readlinkat: {
        if (P(1) && (seq(P(1), "/proc/self/exe")) && g_exe[0]) {
            u64 n = slen(g_exe); if (n > (u64)a[3]) n = (u64)a[3];
            memcpy((void *)a[2], g_exe, n); return (long)n;
        }
        char *b1 = PB(0), *t = PB(1);
        NEEDX(1);
        long r = S4(nr, a[0], h1, t, PATH_MAX - 1);
        if (r < 0) return r;
        t[r] = 0; unxl(t);
        u64 n = slen(t); if (n > (u64)a[3]) n = (u64)a[3];
        memcpy((void *)a[2], t, n);
        return (long)n;
    }
    case NR_symlinkat: {             // target, newdirfd, linkpath: an absolute target is re-rooted
        char *b0 = PB(0), *b2 = PB(1);
        NEEDX(0); NEEDX(2);
        return S3(nr, h0, a[1], h2);
    }
    case NR_linkat: case NR_renameat: case NR_renameat2: {
        char *b1 = PB(0), *b3 = PB(1);
        NEEDX(1); NEEDX(3);
        return S5(nr, a[0], h1, a[2], h3, a[4]);
    }
    case NR_statfs: case NR_truncate: case NR_chdir:
    case NR_setxattr: case NR_lsetxattr: case NR_getxattr: case NR_lgetxattr: case NR_listxattr: case NR_llistxattr:
    case NR_removexattr: case NR_lremovexattr: {
        char *b0 = PB(0);
        NEEDX(0);
        long r = S5(nr, h0, a[1], a[2], a[3], a[4]);
        if (nr == NR_statfs && r == 0) {
            // a nearly full phone reads as a nearly full guest disk (no dropbox, low-storage modes): the guest's disk
            // is what is still free plus as much again
            u64 *s = (u64 *)a[1];               /* type, bsize, blocks, bfree, bavail */
            if (s[4] && s[2] > 2 * s[4]) { s[2] = 2 * s[4]; if (s[3] > s[2]) s[3] = s[2]; }
        }
        return r;
    }
    case NR_inotify_add_watch: {
        char *b1 = PB(0);
        NEEDX(1);
        return S3(nr, a[0], h1, a[2]);
    }
    case NR_getcwd: {
        char *t = PB(0);
        long r = S2(nr, t, PATH_MAX);
        if (r < 0) return r;
        unxl(t);
        u64 n = slen(t) + 1;
        if (n > (u64)a[1]) return -34; /* ERANGE */
        memcpy((void *)a[0], t, n);
        return (long)n;
    }
    case NR_bind: case NR_connect:
        if (a[1] && *(u16 *)a[1] == 16) {            // netlink address: fine on a real netlink socket, accepted on the mute one
            long r = S3(nr, a[0], a[1], a[2]);
            return r < 0 && nr == NR_bind ? 0 : r;
        }
        return unix_addr(nr, a[0], (const struct sockaddr_un *)a[1], (u64)a[2], PB(0));
    case NR_execve:
        return do_exec(P(0), (char *const *)a[1], (char *const *)a[2]);
    case NR_ioctl:
        if (((a[1] >> 8) & 0xff) == ASHMEM_MAGIC) return ashmem_ioctl((int)a[0], (u32)a[1], (u64)a[2]);
        if (((a[1] >> 8) & 0xff) == 'E' && is_input_fd((int)a[0])) return input_ioctl((u32)a[1], (u64)a[2]);
        if (((u32)a[1] & 0xffffff00u) == 0x4600) return fb_ioctl((u32)a[1], (u64)a[2]);
        if (((a[1] >> 8) & 0xff) == 'b' && is_binder_fd((int)a[0])) return binder_ioctl((int)a[0], (u32)a[1], (u64)a[2]);
        return S3(nr, a[0], a[1], a[2]);
    case NR_mmap:
        if (a[2] == 1 && is_binder_fd((int)a[4])) return binder_mmap((u64)a[0], (u64)a[1], a[3]);
        return S6(nr, a[0], a[1], a[2], a[3], a[4], a[5]);
    case NR_mount: case NR_umount2: case NR_chroot: case NR_capset: case NR_setgroups: case NR_unshare: return 0;
    case NR_prctl: return 0;                    // only PR_CAPBSET_DROP gets here
    case NR_timerfd_create: {                   // the *_ALARM clocks need CAP_WAKE_ALARM, vendor clocks (power-off alarm)
        long r = S2(nr, a[0], a[1]);            // do not exist here: the plain clocks tick the same
        if (r < 0) r = S2(nr, a[0] == 9 ? 7 : 0, a[1]);
        return r;
    }
    case NR_reboot: S1(NR_exit_group, 0); return 0;
    case NR_capget: {
        struct { u32 e, p, i; } *d = (void *)a[1];
        if (d) { d[0].e = d[0].p = 0xffffffff; d[0].i = 0; d[1].e = d[1].p = 0xffffffff; d[1].i = 0; }
        return 0;
    }
    case NR_getuid: case NR_geteuid: return g_uid;
    case NR_getgid: case NR_getegid: return g_gid;
    case NR_setuid: g_uid = (int)a[0]; return 0;
    case NR_setgid: g_gid = (int)a[0]; return 0;
    case NR_setreuid: case NR_setresuid: if ((int)a[1] != -1) g_uid = (int)a[1]; else if ((int)a[0] != -1) g_uid = (int)a[0]; return 0;
    case NR_setregid: case NR_setresgid: if ((int)a[1] != -1) g_gid = (int)a[1]; else if ((int)a[0] != -1) g_gid = (int)a[0]; return 0;
    case NR_setfsuid: return g_uid;
    case NR_setfsgid: return g_gid;
    case NR_getresuid: for (int i = 0; i < 3; i++) if (a[i]) *(u32 *)a[i] = (u32)g_uid; return 0;
    case NR_getresgid: for (int i = 0; i < 3; i++) if (a[i]) *(u32 *)a[i] = (u32)g_gid; return 0;
    case NR_getgroups: return 0;
    case NR_rt_sigprocmask: {
        // We are inside a signal handler: the mask that counts is the one the kernel restores on return. SIGSYS
        // stays deliverable whatever the guest asks, or the kernel would kill the process on the next trap.
        u64 *mask = (u64 *)((char *)uc + 40), old = *mask;
        if (a[1]) {
            u64 set = *(u64 *)a[1];
            if (a[0] == 0) *mask = old | set; else if (a[0] == 1) *mask = old & ~set; else if (a[0] == 2) *mask = set; else return -EINVAL;
            *mask &= ~(SYSBIT | (1UL << 8) | (1UL << 18));     // and never SIGKILL, SIGSTOP
        }
        if (a[2]) *(u64 *)a[2] = old;
        return 0;
    }
    case NR_socket: {                // netlink (uevents, nflog…) is closed to the app: netd and friends get a mute socket
        long r = S3(nr, a[0], a[1], a[2]);
        if (r < 0 && a[0] == 16) r = S3(nr, 1, 2 /*SOCK_DGRAM*/ | (a[1] & 02004000), 0);
        return r;
    }
    case NR_setsockopt: {
        long r = S5(nr, a[0], a[1], a[2], a[3], a[4]);
        if (r < 0 && a[1] == 1 && (a[2] == 33 || a[2] == 32)) {    // SO_RCVBUFFORCE / SO_SNDBUFFORCE need CAP_NET_ADMIN
            S5(nr, a[0], 1, a[2] == 33 ? 8 : 7, a[3], a[4]);
            return 0;
        }
        return r;
    }
    case NR_rt_sigaction: {
        struct ksa { void *h; u64 flags; void *restorer; u64 mask; } t;
        if (a[0] == SIGSYS) { if (a[2]) memset((void *)a[2], 0, sizeof t); return 0; }
        if (is_fatal(a[0])) {
            struct ksa old; void *prev = (void *)g_fatal[a[0]];
            long r;
            if (a[1]) {
                t = *(struct ksa *)a[1]; t.mask &= ~SYSBIT;
                void *want = t.h;
                if ((u64)want != 1) { t.h = (void *)fatal_relay; t.flags |= 4; if (!want) t.flags &= ~0x80000000UL; }   // not SA_RESETHAND for our own
                r = S4(nr, a[0], &t, &old, a[3]);
                if (r == 0) g_fatal[a[0]] = want;
            } else r = S4(nr, a[0], 0, &old, a[3]);
            if (r == 0 && a[2]) { if (old.h == (void *)fatal_relay) old.h = prev; *(struct ksa *)a[2] = old; }
            return r;
        }
        if (!a[1]) return S4(nr, a[0], 0, a[2], a[3]);
        t = *(struct ksa *)a[1]; t.mask &= ~SYSBIT;
        if (g_is32 && (u64)t.h > 1 && a[0] > 0 && a[0] < 65) {     // qemu's handlers leave through relay32
            void *want = t.h, *prev = (void *)g_h32[a[0]];
            t.h = (void *)relay32; t.flags |= 4;
            struct ksa old;
            long r = S4(nr, a[0], &t, &old, a[3]);
            if (r == 0) g_h32[a[0]] = want;
            if (r == 0 && a[2]) { if (old.h == (void *)relay32) old.h = prev; *(struct ksa *)a[2] = old; }
            return r;
        }
        return S4(nr, a[0], &t, a[2], a[3]);
    }
    }
    return -ENOSYS;
}

struct sigctx { u64 fault; u64 regs[31]; u64 sp, pc, pstate; };

// AEMU64_CRASH=1: fatal signals are reported here (with the mapping of pc and lr) instead of the guest's own handlers
static void hex(u64 v) { char b[19] = "0x"; for (int i = 0; i < 16; i++) b[2 + i] = "0123456789abcdef"[(v >> (60 - 4 * i)) & 15]; b[18] = 0; say(b); }

static void where(u64 addr) {
    static char buf[1 << 16];
    long fd = S4(NR_openat, AT_FDCWD, "/proc/self/maps", 0, 0);
    if (fd < 0) return;
    u64 keep = 0; long n;
    while ((n = S3(NR_read, fd, buf + keep, sizeof buf - 1 - keep)) > 0) {
        u64 len = keep + (u64)n; buf[len] = 0;
        char *l = buf;
        for (;;) {
            char *e = l; while (*e && *e != '\n') e++;
            if (!*e) break;
            *e = 0;
            u64 lo = 0, hi = 0; char *q = l;
            for (; *q && *q != '-'; q++) lo = lo * 16 + (u64)(*q <= '9' ? *q - '0' : *q - 'a' + 10);
            if (*q) for (q++; *q && *q != ' '; q++) hi = hi * 16 + (u64)(*q <= '9' ? *q - '0' : *q - 'a' + 10);
            if (addr >= lo && addr < hi) { say("    "); say(l); say("  +"); hex(addr - lo); say("\n"); S1(NR_close, fd); return; }
            l = e + 1;
        }
        keep = slen(l); memmove(buf, l, keep);
    }
    S1(NR_close, fd);
}

static void on_crash(int sig, void *info, void *uc) {
    struct { u64 fault; u64 regs[31]; u64 sp, pc, pstate; } *c = (void *)((char *)uc + 176);
    char t[24];
    static volatile int busy;                   // one report at a time; the others wait their turn
    while (__atomic_exchange_n(&busy, 1, __ATOMIC_ACQUIRE)) S0(NR_sched_yield);
    {
        static char lp[600];
        memcpy(lp, g_root, g_rootlen); memcpy(lp + g_rootlen, "/dev/aemu_crash.log", 20);
        g_sayfd = (int)S4(NR_openat, AT_FDCWD, lp, 02101 /*O_WRONLY|O_CREAT|O_APPEND*/, 0600);
    }
    say("aemu64: fatal signal "); say(utoa((u64)sig, t + sizeof t)); say(" in "); say(g_exe);
    { char nm[20]; memset(nm, 0, sizeof nm); S5(NR_prctl, 16 /*PR_GET_NAME*/, nm, 0, 0, 0); say(" ["); say(nm); say("]"); }
    say("\n  pc "); hex(c->pc); say("  lr "); hex(c->regs[30]); say("  addr "); hex(*(u64 *)((char *)info + 16)); say("\n");
    where(c->pc); where(c->regs[30]);
    for (int i = 0; i < 8; i++) { say("  x"); say(utoa((u64)i, t + sizeof t)); say(" "); hex(c->regs[i]); if (i % 4 == 3) say("\n"); }
    say("  pid "); say(utoa((u64)S0(172), t + sizeof t)); say("  registers and what they point to:\n");
    for (int i = 0; i < 29; i++) {
        u64 w[4] = { 0, 0, 0, 0 };
        struct { void *b; u64 n; } l = { w, sizeof w }, r = { (void *)c->regs[i], sizeof w };
        say("  x"); say(utoa((u64)i, t + sizeof t)); say(" "); hex(c->regs[i]);
        if (c->regs[i] > 0x10000 && S6(270 /*process_vm_readv*/, S0(172), &l, 1, &r, 1, 0) == (long)sizeof w) {
            say(" ->"); for (int k = 0; k < 4; k++) { say(" "); hex(w[k]); }
        }
        say("\n");
    }
    say("  backtrace:\n");
    u64 fp = c->regs[29];
    for (int i = 0; i < 24 && fp > 0x10000 && !(fp & 7); i++) {
        u64 lr = ((u64 *)fp)[1]; u64 nfp = ((u64 *)fp)[0];
        where(lr);
        if (nfp <= fp) break;
        fp = nfp;
    }
    S1(NR_exit_group, 128 + sig);
}

// Fatal signals: the kernel always calls fatal_relay; the guest's own handler (ART's fault handler, the linker's
// debuggerd hook) runs from it with the flags and mask it asked for. Only when the guest has nothing left
// (SIG_DFL) the crash is reported here, on the program's stderr, with the mappings of pc and the callers.
static void fatal_relay(int sig, void *info, void *uc) {
    void (*h)(int, void *, void *) = g_fatal[sig & 31];
    if ((u64)h > 1) h(sig, info, uc); else on_crash(sig, info, uc);
    if (g_is32) *(u64 *)((char *)uc + 40) &= ~SYSBIT;
}

static void on_sigsys(int sig, void *info, void *uc) {
    struct sigctx *c = (struct sigctx *)((char *)uc + 176);
    (void)sig; (void)info;
    c->regs[0] = (u64)trap((long)c->regs[8], (long *)c->regs, uc);
}

// ---------------------------------------------------------------------------------------------------- filter

struct bpf { u16 code; u8 jt, jf; u32 k; };
#define LD(off) (struct bpf){ 0x20, 0, 0, off }
#define JEQ(k, t, f) (struct bpf){ 0x15, t, f, k }
#define JGE(k, t, f) (struct bpf){ 0x35, t, f, k }
#define RET(k) (struct bpf){ 0x06, 0, 0, k }
#define ALLOW 0x7fff0000u
#define TRAP 0x00030000u

static const u8 trapped[] = {
    NR_setxattr, NR_lsetxattr, NR_getxattr, NR_lgetxattr, NR_listxattr, NR_llistxattr, NR_removexattr, NR_lremovexattr,
    NR_getcwd, NR_inotify_add_watch, NR_ioctl, NR_mknodat, NR_mkdirat, NR_unlinkat, NR_symlinkat, NR_linkat,
    NR_renameat, NR_umount2, NR_mount, NR_statfs, NR_truncate, NR_faccessat, NR_chdir, NR_chroot, NR_fchmodat,
    NR_fchownat, NR_fchown, NR_openat, NR_readlinkat, NR_newfstatat, NR_fstat, NR_utimensat, NR_capget, NR_capset,
    NR_rt_sigaction, NR_reboot, NR_setregid, NR_setgid, NR_setreuid, NR_setuid, NR_setresuid, NR_getresuid,
    NR_setresgid, NR_getresgid, NR_setfsuid, NR_setfsgid, NR_getgroups, NR_setgroups, NR_getuid, NR_geteuid,
    NR_getgid, NR_getegid, NR_bind, NR_connect, NR_execve, NR_setsockopt, NR_unshare, NR_timerfd_create,
};

enum { L_TRAP = 240, L_ALLOW, L_SIG, L_MMAP, L_NR, L_SOCK, L_PRCTL, L_N };

static void install_filter(void) {
    static struct bpf f[128];
    int n = 0, pos[L_N - 240];
#define AT(l) pos[(l) - 240] = n
    f[n++] = LD(4);  f[n++] = JEQ(0xC00000B7u, 0, L_ALLOW);
    f[n++] = LD(12); f[n++] = JEQ(IMAGE_HI, 0, L_NR);          // calls made from this image pass
    f[n++] = LD(8);  f[n++] = JGE(IMAGE_SPAN, L_NR, L_ALLOW);
    AT(L_NR); f[n++] = LD(0);
    for (u64 i = 0; i < sizeof trapped; i++) f[n++] = JEQ(trapped[i], L_TRAP, 0);
    f[n++] = JEQ(NR_renameat2, L_TRAP, 0);
    f[n++] = JEQ(NR_statx, L_TRAP, 0);
    f[n++] = JEQ(NR_rt_sigprocmask, L_SIG, 0);
    f[n++] = JEQ(NR_socket, L_SOCK, 0);
    f[n++] = JEQ(NR_prctl, L_PRCTL, 0);
    f[n++] = JEQ(NR_mmap, L_MMAP, L_ALLOW);
    AT(L_PRCTL);                                                // PR_CAPBSET_DROP only
    f[n++] = LD(16); f[n++] = JEQ(24, L_TRAP, L_ALLOW);
    AT(L_SOCK);                                                 // netlink only
    f[n++] = LD(16); f[n++] = JEQ(16, L_TRAP, L_ALLOW);
    AT(L_SIG);                                                  // only when a set is given and it is not SIG_UNBLOCK
    f[n++] = LD(16); f[n++] = JEQ(1, L_ALLOW, 0);
    f[n++] = LD(24); f[n++] = JEQ(0, 0, L_TRAP);
    f[n++] = LD(28); f[n++] = JEQ(0, L_ALLOW, L_TRAP);
    AT(L_MMAP);                                                 // read-only file mappings: the binder buffer is one
    f[n++] = LD(32); f[n++] = JEQ(1, 0, L_ALLOW);
    f[n++] = LD(48); f[n++] = JEQ(0xffffffffu, L_ALLOW, L_TRAP);
    AT(L_TRAP); f[n++] = RET(TRAP);
    AT(L_ALLOW); f[n++] = RET(ALLOW);
#undef AT
    for (int i = 0; i < n; i++) {
        if ((f[i].code & 7) != 5) continue;                     // jumps only
        if (f[i].jt >= 240) f[i].jt = (u8)(pos[f[i].jt - 240] - i - 1);
        if (f[i].jf >= 240) f[i].jf = (u8)(pos[f[i].jf - 240] - i - 1);
    }
    struct { u16 len; struct bpf *f; } prog = { (u16)n, f };
    S5(NR_prctl, 38 /*PR_SET_NO_NEW_PRIVS*/, 1, 0, 0, 0);
    if (S3(NR_prctl, 22 /*PR_SET_SECCOMP*/, 2, &prog) < 0) die("seccomp filter refused", "");
}

// ---------------------------------------------------------------------------------------------------- loader

struct ehdr { u8 ident[16]; u16 type, machine; u32 version; u64 entry, phoff, shoff; u32 flags; u16 ehsize, phentsize, phnum, shentsize, shnum, shstrndx; };
struct phdr { u32 type, flags; u64 offset, vaddr, paddr, filesz, memsz, align; };
struct image { u64 bias, entry, phdr; u16 phnum; char interp[256]; };

#define PG 4096UL
#define DN(x) ((x) & ~(PG - 1))
#define UP(x) DN((x) + PG - 1)

static int load_elf(const char *host, struct image *im) {
    long fd = S4(NR_openat, AT_FDCWD, host, 0x80000 /*O_CLOEXEC*/, 0);
    if (fd < 0) return (int)fd;
    struct ehdr eh; struct phdr ph[32];
    if (S4(NR_pread64, fd, &eh, sizeof eh, 0) != sizeof eh || eh.ident[0] != 0x7f || eh.ident[1] != 'E' ||
        eh.ident[4] != 2 || eh.machine != 183 || eh.phnum > 32) { S1(NR_close, fd); return -ENOEXEC; }
    if (S4(NR_pread64, fd, ph, eh.phnum * sizeof ph[0], eh.phoff) != (long)(eh.phnum * sizeof ph[0])) { S1(NR_close, fd); return -ENOEXEC; }
    u64 lo = ~0UL, hi = 0;
    im->interp[0] = 0; im->phdr = 0;
    for (int i = 0; i < eh.phnum; i++) {
        if (ph[i].type == 1) { if (ph[i].vaddr < lo) lo = ph[i].vaddr; if (ph[i].vaddr + ph[i].memsz > hi) hi = ph[i].vaddr + ph[i].memsz; }
        if (ph[i].type == 3 && ph[i].filesz < sizeof im->interp) { S4(NR_pread64, fd, im->interp, ph[i].filesz, ph[i].offset); im->interp[ph[i].filesz] = 0; }
    }
    lo = DN(lo); hi = UP(hi);
    u64 base = (u64)S6(NR_mmap, eh.type == 3 ? 0 : lo, hi - lo, 0, 0x22 | (eh.type == 3 ? 0 : 0x10), -1, 0);
    if (base >= (u64)-4095) { S1(NR_close, fd); return -ENOMEM; }
    u64 bias = base - lo;
    for (int i = 0; i < eh.phnum; i++) {
        if (ph[i].type != 1) continue;
        int prot = ((ph[i].flags & 4) ? 1 : 0) | ((ph[i].flags & 2) ? 2 : 0) | ((ph[i].flags & 1) ? 4 : 0);
        u64 s = DN(ph[i].vaddr) + bias, fe = ph[i].vaddr + ph[i].filesz + bias, me = UP(ph[i].vaddr + ph[i].memsz + bias);
        if (ph[i].filesz) {
            u64 r = (u64)S6(NR_mmap, s, UP(fe) - s, prot, 0x12 /*PRIVATE|FIXED*/, fd, DN(ph[i].offset));
            if (r >= (u64)-4095) { S1(NR_close, fd); return (int)r; }
        }
        if (ph[i].memsz > ph[i].filesz) {
            if ((prot & 2) && UP(fe) > fe) memset((void *)fe, 0, UP(fe) - fe);
            if (me > UP(fe)) S6(NR_mmap, UP(fe), me - UP(fe), prot, 0x32, -1, 0);
        }
        if (ph[i].offset <= eh.phoff && eh.phoff < ph[i].offset + ph[i].filesz) im->phdr = ph[i].vaddr + (eh.phoff - ph[i].offset) + bias;
    }
    S1(NR_close, fd);
    im->bias = bias; im->entry = eh.entry + bias; im->phnum = eh.phnum;
    return 0;
}

static const char *env_get(char **envp, const char *k) {
    u64 n = slen(k);
    for (; *envp; envp++) if (pre(*envp, k) && (*envp)[n] == '=') return *envp + n + 1;
    return 0;
}

void aemu_main(u64 *sp) {
    long argc = (long)sp[0];
    char **argv = (char **)(sp + 1), **envp = argv + argc + 1;
    u64 *auxv; { char **e = envp; while (*e) e++; auxv = (u64 *)(e + 1); }
    if (argc < 3) die("usage: aemu64 <argv0> <guest program> [args…]", "");

    const char *r = env_get(envp, "AEMU64_ROOT");
    if (!r || !r[0] || slen(r) >= sizeof g_root) die("AEMU64_ROOT is not set", "");
    memcpy(g_root, r, slen(r) + 1); g_rootlen = slen(g_root);
    if ((r = env_get(envp, "AEMU64_PROPS")) && slen(r) < sizeof g_props) memcpy(g_props, r, slen(r) + 1);
    g_crash = env_get(envp, "AEMU64_CRASH") != 0;
    if ((r = env_get(envp, "AEMU64_INPUT")) && slen(r) < sizeof g_input) memcpy(g_input, r, slen(r) + 1);
    if ((r = env_get(envp, "AEMU64_SDCARD")) && slen(r) < sizeof g_sd) { g_sdlen = slen(r); memcpy(g_sd, r, g_sdlen + 1); }
    if ((r = env_get(envp, "AEMU64_QEMU")) && slen(r) < sizeof g_qemu) memcpy(g_qemu, r, slen(r) + 1);
    if ((r = env_get(envp, "AEMU64_BINDER")) && slen(r) < sizeof g_bsock) memcpy(g_bsock, r, slen(r) + 1);
    if ((r = env_get(envp, "AEMU64_ID"))) { g_uid = (int)atou(r); while (*r && *r != ',') r++; if (*r) g_gid = (int)atou(r + 1); }
    long n = S4(NR_readlinkat, AT_FDCWD, "/proc/self/exe", g_self, sizeof g_self - 1);
    if (n <= 0) die("cannot find myself", "");
    g_self[n] = 0;
    g_real_uid = (u32)S0(NR_getuid);

    // the program: a host path under the root (from our own exec) or a guest path
    char pbuf[PATH_MAX]; int err = 0;
    const char *prog = xl(argv[2], pbuf, &err);
    if (!prog) die("bad path ", argv[2]);

    // "#!interpreter [arg]": run the interpreter on the script
    char head[256];
    long fd = S4(NR_openat, AT_FDCWD, prog, 0x80000, 0);
    if (fd < 0) die("cannot open ", argv[2]);
    long hn = S3(NR_read, fd, head, sizeof head - 1);
    S1(NR_close, fd);
    static char *nargv[4100];
    char **gargv = argv + 2; long gargc = argc - 2;
    const char *argv0 = argv[1];
    if (hn > 2 && head[0] == '#' && head[1] == '!') {
        head[hn] = 0;
        char *p = head + 2, *arg = 0; while (*p == ' ') p++;
        char *interp = p; while (*p && *p != ' ' && *p != '\n') p++;
        if (*p == ' ') { *p++ = 0; while (*p == ' ') p++; if (*p && *p != '\n') { arg = p; while (*p && *p != '\n') p++; } }
        *p = 0;
        static char ibuf[PATH_MAX], script[PATH_MAX];
        memcpy(script, argv[2], slen(argv[2]) + 1); unxl(script);
        long k = 0;
        nargv[k++] = interp; if (arg) nargv[k++] = arg; nargv[k++] = script;
        for (long i = 3; i < argc && k < 4096; i++) nargv[k++] = argv[i];
        nargv[k] = 0; gargv = nargv; gargc = k; argv0 = interp;
        static char ihold[256]; memcpy(ihold, interp, slen(interp) + 1); nargv[0] = ihold;
        if (arg) { static char ahold[256]; memcpy(ahold, arg, slen(arg) + 1); nargv[1] = ahold; }
        argv0 = ihold;
        if (!(prog = xl(ihold, ibuf, &err))) die("bad interpreter ", ihold);
        memcpy(g_exe, ihold, slen(ihold) + 1);
    } else {
        u64 l = slen(prog); if (l >= sizeof g_exe) l = sizeof g_exe - 1;
        memcpy(g_exe, prog, l); g_exe[l] = 0; unxl(g_exe);
    }

    // sockets init would have made for the service: AEMU64_SOCK_<name>=<host path>,<octal mode>
    static char sockenv[12][80]; int nsock = 0;
    for (char **e = envp; *e; e++) {
        if (!pre(*e, "AEMU64_SOCK_") || nsock >= 12) continue;
        const char *name = *e + 12, *eq = name; while (*eq && *eq != '=') eq++;
        if (!*eq) continue;
        struct sockaddr_un sa; sa.family = 1;
        u64 pl = 0; const char *v = eq + 1; while (v[pl] && v[pl] != ',' && pl < 107) { sa.path[pl] = v[pl]; pl++; }
        sa.path[pl] = 0;
        u64 mode = 0; if (v[pl] == ',') for (const char *m = v + pl + 1; *m >= '0' && *m <= '7'; m++) mode = mode * 8 + (u64)(*m - '0');
        long s = S3(NR_socket, 1, 1 /*SOCK_STREAM*/, 0);
        if (s < 0) continue;
        S3(NR_unlinkat, AT_FDCWD, sa.path, 0);
        if (S3(NR_bind, s, &sa, 2 + pl + 1) < 0) { S1(NR_close, s); continue; }
        S4(NR_fchmodat, AT_FDCWD, sa.path, mode ? mode : 0660, 0);
        char *o = sockenv[nsock++]; u64 nl = (u64)(eq - name); if (nl > 40) nl = 40;
        memcpy(o, "ANDROID_SOCKET_", 15); memcpy(o + 15, name, nl); o[15 + nl] = '=';
        char t[24], *d = utoa((u64)s, t + sizeof t); memcpy(o + 16 + nl, d, slen(d) + 1);
    }

    // routing and firewall tools talk netlink to a kernel that will not answer an app: they "succeed" at once
    {
        const char *b = g_exe, *q;
        for (q = g_exe; *q; q++) if (*q == '/') b = q + 1;
        static const char *const mute[] = { "ip", "tc", "iptables", "ip6tables", "iptables-restore", "ip6tables-restore", "ndppd", 0 };
        for (int i = 0; mute[i]; i++) if (seq(b, mute[i])) S1(NR_exit_group, 0);
    }
    struct image exe, ld;
    int is32 = 0;
    if (gargv != nargv) {
        long f2 = S4(NR_openat, AT_FDCWD, prog, 0x80000, 0);
        if (f2 >= 0) { hn = S3(NR_read, f2, head, 8); S1(NR_close, f2); }
    } else {                                    // a script: what matters is its interpreter
        long f2 = S4(NR_openat, AT_FDCWD, prog, 0x80000, 0);
        hn = f2 >= 0 ? S3(NR_read, f2, head, 8) : 0;
        if (f2 >= 0) S1(NR_close, f2);
    }
    if (hn > 5 && head[0] == 0x7f && head[4] == 1) {
        struct kstat st;
        if (S4(NR_newfstatat, AT_FDCWD, prog, &st, 0) == 0 && st.st_size < 1024) S1(NR_exit_group, 0);   // the engine's "true"
        if (!g_qemu[0]) die("32-bit program and no translator: ", prog);
        // a 32-bit program of a 64-bit firmware: qemu-arm translates the code, and its system calls come back
        // through the same filter. Guest addresses are host addresses (-B 0), so binder and ashmem work unchanged.
        static char *qargv[4100]; long k = 0;
        static char progcopy[PATH_MAX];
        memcpy(progcopy, prog, slen(prog) + 1);
        qargv[k++] = g_qemu; qargv[k++] = "-B"; qargv[k++] = "0"; qargv[k++] = "-0"; qargv[k++] = (char *)argv0; qargv[k++] = progcopy;
        for (long i = 1; i < gargc && k < 4096; i++) qargv[k++] = gargv[i];
        qargv[k] = 0; gargv = qargv; gargc = k; argv0 = g_qemu; prog = g_qemu; is32 = 1; g_is32 = 1;
    }
    // each world has its own libraries
    static char ldpath[64];
    memcpy(ldpath, is32 ? "LD_LIBRARY_PATH=/vendor/lib:/system/lib" : "LD_LIBRARY_PATH=/vendor/lib64:/system/lib64", is32 ? 40 : 44);
    for (char **e = envp; *e; e++) if (pre(*e, "LD_LIBRARY_PATH=")) *e = ldpath;
    if ((err = load_elf(prog, &exe))) die("cannot load ", prog);
    u64 entry = exe.entry, base = 0;
    if (exe.interp[0]) {
        char ib[PATH_MAX];
        const char *ih = xl(exe.interp, ib, &err);
        if (!ih || (err = load_elf(ih, &ld))) die("cannot load the linker ", exe.interp);
        entry = ld.entry; base = ld.bias;
    }

    // signal handler first, then the filter (once per process tree: it survives exec)
    struct { void *h; u64 flags; void *restorer; u64 mask; } sa = { (void *)on_sigsys, 4 /*SA_SIGINFO*/ | 0x40000000 /*SA_NODEFER*/, 0, 0 };
    if (S4(NR_rt_sigaction, SIGSYS, &sa, 0, 8) < 0) die("sigaction", "");
    u64 unblock = SYSBIT; S4(NR_rt_sigprocmask, 1, &unblock, 0, 8);
    {
        struct { void *h; u64 flags; void *restorer; u64 mask; } ca = { (void *)fatal_relay, 4, 0, 0 };
        static const int fatal[] = { 11, 7, 4, 6, 8 };
        for (int i = 0; i < 5; i++) S4(NR_rt_sigaction, fatal[i], &ca, 0, 8);
    }
    if (!env_get(envp, "AEMU64_F")) install_filter();

    // the guest's initial stack: argc, argv, envp (without our variables), auxv
    u64 ne = 0; while (envp[ne]) ne++;
    u64 na = 0; while (auxv[na * 2]) na++;
    u64 words = 1 + (u64)gargc + 1 + ne + 12 + 1 + (na + 1) * 2;
    // built on the real stack, well below this frame (bionic and ART find the main stack by its mapping)
    u64 *w = (u64 *)(((u64)&words - (256UL << 10) - words * 8) & ~15UL), *top = w;
    *w++ = (u64)gargc;
    *w++ = (u64)argv0;
    for (long i = 1; i < gargc; i++) *w++ = (u64)gargv[i];
    *w++ = 0;
    for (u64 i = 0; i < ne; i++) if (!env_is_ours(envp[i])) *w++ = (u64)envp[i];
    for (int i = 0; i < nsock; i++) *w++ = (u64)sockenv[i];
    *w++ = 0;
    for (u64 i = 0; i < na; i++) {
        u64 t = auxv[i * 2], v = auxv[i * 2 + 1];
        switch (t) {
        case 3: v = exe.phdr; break;            // AT_PHDR
        case 4: v = sizeof(struct phdr); break; // AT_PHENT
        case 5: v = exe.phnum; break;           // AT_PHNUM
        case 7: v = base; break;                // AT_BASE
        case 9: v = exe.entry; break;           // AT_ENTRY
        case 31: v = (u64)argv0; break;         // AT_EXECFN
        }
        *w++ = t; *w++ = v;
    }
    *w++ = 0; *w++ = 0;
    enter_guest((void *)entry, top);
}
