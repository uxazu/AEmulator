/*
 * libaemushim.so — гостевая прослойка AEmulator (32-битный ARM, подгружается через LD_PRELOAD
 * в каждый процесс гостя вместе с libashmemshim.so).
 *
 * Прошивка работает внутри приложения Android, где seccomp запрещает часть системных вызовов:
 * mount, umount, swapon, reboot, settimeofday… Системные службы старых прошивок (например,
 * system_server Samsung монтирует /efs сам) получают за такой вызов SIGSYS и падают.
 * Здесь эти функции libc подменены безопасными заглушками: «успех» без обращения к ядру.
 *
 * Второе: qemu стенда умеет запускать только ELF — execve() скрипта (#!/system/bin/sh: am, pm, input,
 * monkey, svc…) заканчивается «Exec format error» уже в новом процессе, и гость не узнаёт об ошибке.
 * execve/execv/execvp здесь сами разбирают строку #! и запускают интерпретатор, как это делает ядро.
 *
 * Библиотека собирается с -nostdlib и зовёт ядро напрямую; из libc гостя берёт только environ,
 * поэтому одинаково грузится в bionic Android 2.3–6.0.
 */

#define EXPORT __attribute__((visibility("default")))

typedef unsigned long ulong;
struct timeval_s { long tv_sec; long tv_usec; };
struct timespec_s { long tv_sec; long tv_nsec; };

/* монтирование: раздел «уже смонтирован» */
EXPORT int mount(const char *src, const char *target, const char *type, ulong flags, const void *data) {
    (void)src; (void)target; (void)type; (void)flags; (void)data;
    return 0;
}
EXPORT int umount(const char *target) { (void)target; return 0; }
EXPORT int umount2(const char *target, int flags) { (void)target; (void)flags; return 0; }

/* подкачка, учёт процессов, смена корня */
EXPORT int swapon(const char *path, int flags) { (void)path; (void)flags; return 0; }
EXPORT int swapoff(const char *path) { (void)path; return 0; }
EXPORT int acct(const char *file) { (void)file; return 0; }

/* время ставит телефон: попытки гостя поменять часы игнорируем */
EXPORT int settimeofday(const struct timeval_s *tv, const void *tz) { (void)tv; (void)tz; return 0; }
EXPORT int clock_settime(int clk, const struct timespec_s *tp) { (void)clk; (void)tp; return 0; }
EXPORT int stime(const long *t) { (void)t; return 0; }

/* Reboot / power off: the host restarts or stops the VM. The request goes to /dev/aemu_power
 * ("reboot", "reboot,recovery", "shutdown"), which the host watches. */
static long sys3(long n, long a, long b, long c);
static void power_request(const char *what, const char *arg) {
    char buf[96]; int n = 0;
    while (*what && n < 40) buf[n++] = *what++;
    if (arg && *arg) { buf[n++] = ','; while (*arg && n < 90) buf[n++] = *arg++; }
    long fd = sys3(5 /* open */, (long)"/dev/aemu_power", 0x241 /* O_WRONLY|O_CREAT|O_TRUNC */, 0666);
    if (fd >= 0) { sys3(4 /* write */, fd, (long)buf, n); sys3(6 /* close */, fd, 0, 0); }
}
EXPORT int reboot(int cmd) {
    power_request(cmd == 0x4321FEDC /* POWER_OFF */ || cmd == (int)0xCDEF0123 /* HALT */ ? "shutdown" : "reboot", 0);
    return 0;
}
EXPORT int __reboot(int m1, int m2, int cmd, void *arg) {
    (void)m1; (void)m2;
    if (cmd == (int)0xA1B2C3D4 /* RESTART2 */) power_request("reboot", (const char *)arg);
    else reboot(cmd);
    return 0;
}
EXPORT int android_reboot(int cmd, int flags, char *arg) {
    (void)flags;
    power_request(cmd == (int)0xDEAD0002 ? "shutdown" : "reboot", cmd == (int)0xDEAD0003 ? arg : 0);
    return 0;
}

/* модули ядра */
EXPORT int init_module(void *img, ulong len, const char *params) { (void)img; (void)len; (void)params; return 0; }
EXPORT int delete_module(const char *name, int flags) { (void)name; (void)flags; return 0; }
EXPORT int klogctl(int type, char *buf, int len) { (void)type; (void)buf; (void)len; return 0; }

/* ------------------------------------------------------------------ запуск скриптов */

extern char **environ;

/* системный вызов с тремя аргументами; r7 в Thumb занят под кадр — сохраняем его сами */
__attribute__((naked, noinline)) static long sys3(long n, long a, long b, long c) {
    __asm__ volatile(
        "push {r7}; mov r7, r0; mov r0, r1; mov r1, r2; mov r2, r3; svc #0; pop {r7}; bx lr");
}
#define SYS_read 3
#define SYS_write 4
#define SYS_open 5
#define SYS_close 6
#define SYS_execve 11
#define SYS_access 33
#define ENOENT 2
#define E2BIG 7

extern int *__errno(void);

static int fail(long r) { *__errno() = (int)-r; return -1; }

static long raw_execve(const char *path, char *const argv[], char *const envp[]) {
    return sys3(SYS_execve, (long)path, (long)argv, (long)envp);
}

#define MAXARGS 512

EXPORT int execve(const char *path, char *const argv[], char *const envp[]) {
    char head[256];
    long fd = sys3(SYS_open, (long)path, 0 /* O_RDONLY */, 0);
    if (fd >= 0) {
        long n = sys3(SYS_read, fd, (long)head, sizeof(head) - 1);
        sys3(SYS_close, fd, 0, 0);
        if (n > 2 && head[0] == '#' && head[1] == '!') {
            head[n] = 0;
            /* строка интерпретатора: #!/путь [один аргумент] */
            char *p = head + 2, *interp, *arg = 0;
            while (*p == ' ' || *p == 9) p++;
            interp = p;
            while (*p && *p != ' ' && *p != 9 && *p != 10 && *p != 13) p++;
            if (*p == ' ' || *p == 9) {
                *p++ = 0;
                while (*p == ' ' || *p == 9) p++;
                if (*p && *p != 10 && *p != 13) {
                    arg = p;
                    while (*p && *p != 10 && *p != 13) p++;
                    while (p > arg && (p[-1] == ' ' || p[-1] == 9)) p--;
                }
            }
            *p = 0;
            if (*interp) {
                char *nargv[MAXARGS];
                int k = 0;
                nargv[k++] = interp;
                if (arg) nargv[k++] = arg;
                nargv[k++] = (char *)path;
                if (argv && argv[0]) for (int i = 1; argv[i]; i++) {
                    if (k >= MAXARGS - 1) return fail(-E2BIG);
                    nargv[k++] = argv[i];
                }
                nargv[k] = 0;
                return fail(raw_execve(interp, nargv, envp));
            }
        }
    }
    return fail(raw_execve(path, argv, envp));
}

EXPORT int execv(const char *path, char *const argv[]) { return execve(path, argv, environ); }

EXPORT int execvp(const char *file, char *const argv[]) {
    int slash = 0;
    for (const char *c = file; *c; c++) if (*c == '/') { slash = 1; break; }
    if (slash) return execve(file, argv, environ);
    const char *path = 0;
    for (char **e = environ; e && *e; e++) {
        const char *v = *e;
        if (v[0] == 'P' && v[1] == 'A' && v[2] == 'T' && v[3] == 'H' && v[4] == '=') { path = v + 5; break; }
    }
    if (!path) path = "/system/bin:/system/xbin:/vendor/bin:/sbin";
    char buf[512];
    int last = -ENOENT;
    while (1) {
        const char *end = path;
        while (*end && *end != ':') end++;
        int len = (int)(end - path), flen = 0;
        while (file[flen]) flen++;
        if (len > 0 && len + 1 + flen < (int)sizeof(buf)) {
            for (int i = 0; i < len; i++) buf[i] = path[i];
            buf[len] = '/';
            for (int i = 0; i <= flen; i++) buf[len + 1 + i] = file[i];
            if (sys3(SYS_access, (long)buf, 1 /* X_OK */, 0) == 0) {
                execve(buf, argv, environ);
                last = -*__errno();
            }
        }
        if (!*end) break;
        path = end + 1;
    }
    return fail(last);
}

/* ------------------------------------------------------------------ read-only precompiled /system code */
/*
 * On a phone /system is a read-only mount. Here it is a plain directory owned by the guest's uid, and installd /
 * PackageManager of 5.0+ "clean up" the stock odex/oat files on a fresh /data: the framework and the apps then fail
 * with "No original dex files found" on every following boot. The host takes the write bit away (TreeFixer.protectOat),
 * but an owner can chmod it back. During a normal boot (AEMU_RO_SYSTEM=1) changes to that code fail with EROFS like on
 * the real mount; recovery does not set the variable, so flashing still works.
 */
#define EROFS 30
#define AT_FDCWD (-100)
static int g_ro = -1;
static int ro_enabled(void) {
    if (g_ro < 0) {
        g_ro = 0;
        for (char **e = environ; e && *e; e++) {
            const char *v = *e, *k = "AEMU_RO_SYSTEM=1";
            int i = 0;
            while (k[i] && v[i] == k[i]) i++;
            if (!k[i]) { g_ro = 1; break; }
        }
    }
    return g_ro;
}
static int has(const char *s, const char *sub) {
    for (; *s; s++) {
        int i = 0;
        while (sub[i] && s[i] == sub[i]) i++;
        if (!sub[i]) return 1;
    }
    return 0;
}
static int ends(const char *s, const char *suf) {
    int n = 0, m = 0;
    while (s[n]) n++;
    while (suf[m]) m++;
    if (m > n) return 0;
    for (int i = 0; i < m; i++) if (s[n - m + i] != suf[i]) return 0;
    return 1;
}
static int ro_path(const char *p) {
    if (!p || !ro_enabled()) return 0;
    if (!(p[0] == '/' && p[1] == 's' && p[2] == 'y' && p[3] == 's' && p[4] == 't' && p[5] == 'e' && p[6] == 'm' && p[7] == '/')) return 0;
    return has(p, "/oat/") || has(p, "/framework/arm") || ends(p, ".odex") || ends(p, ".oat") || ends(p, ".art") || ends(p, ".vdex");
}
#define SYS_unlink 10
#define SYS_chmod 15
#define SYS_rename 38
#define SYS_rmdir 40
#define SYS_unlinkat 328
static int ret(long r) { return r < 0 ? fail(r) : (int)r; }
EXPORT int unlink(const char *p) { return ro_path(p) ? fail(-EROFS) : ret(sys3(SYS_unlink, (long)p, 0, 0)); }
EXPORT int rmdir(const char *p) { return ro_path(p) ? fail(-EROFS) : ret(sys3(SYS_rmdir, (long)p, 0, 0)); }
EXPORT int remove(const char *p) {
    if (ro_path(p)) return fail(-EROFS);
    long r = sys3(SYS_unlink, (long)p, 0, 0);
    if (r == -21 /* EISDIR */) r = sys3(SYS_rmdir, (long)p, 0, 0);
    return ret(r);
}
EXPORT int rename(const char *a, const char *b) { return ro_path(a) || ro_path(b) ? fail(-EROFS) : ret(sys3(SYS_rename, (long)a, (long)b, 0)); }
EXPORT int chmod(const char *p, unsigned m) { return ro_path(p) ? fail(-EROFS) : ret(sys3(SYS_chmod, (long)p, (long)m, 0)); }
EXPORT int unlinkat(int fd, const char *p, int fl) {
    return (fd == AT_FDCWD || p[0] == '/') && ro_path(p) ? fail(-EROFS) : ret(sys3(SYS_unlinkat, fd, (long)p, fl));
}
/* ------------------------------------------------------------------ учёт трафика (xt_qtaguid) */
/*
 * NetworkManagementService включает учёт трафика, только если есть /proc/net/xt_qtaguid/ctrl; без него
 * NetworkStatsService бросает «Bandwidth module disabled» и падают менеджеры трафика (MIUI «Безопасность»,
 * «Использование данных»). /proc qemu не подменяет — подменяем здесь: ctrl → /dev/null (метки сокетов
 * пишутся туда), таблицы статистики → пустые таблицы с заголовками в /data/.aemu_qtaguid (их кладёт движок).
 */
static const char QT[] = "/proc/net/xt_qtaguid/";
static const char *qt_redirect(const char *path, char *buf, int n) {
    if (!path) return path;
    /* MTK: ActivityManager пишет отметки загрузки в /proc/bootprof на каждый запуск процесса;
       хост не пускает, и каждый раз в журнал летит исключение со стеком */
    static const char BP[] = "/proc/bootprof";
    int b = 0;
    while (BP[b] && path[b] == BP[b]) b++;
    if (!BP[b] && !path[b]) return "/dev/null";
    int i = 0;
    while (QT[i] && path[i] == QT[i]) i++;
    if (QT[i]) {
        /* сам каталог */
        if (!path[i] && i == (int)sizeof(QT) - 2) return "/data/.aemu_qtaguid";
        return path;
    }
    const char *name = path + i;
    if (name[0] == 'c' && name[1] == 't' && name[2] == 'r' && name[3] == 'l' && !name[4]) return "/dev/null";
    const char *pre = "/data/.aemu_qtaguid/";
    int k = 0;
    while (pre[k] && k < n - 1) { buf[k] = pre[k]; k++; }
    for (int j = 0; name[j] && k < n - 1; j++) buf[k++] = name[j];
    buf[k] = 0;
    return buf;
}

EXPORT int access(const char *path, int mode) {
    char b[128];
    long r = sys3(SYS_access, (long)qt_redirect(path, b, sizeof(b)), mode, 0);
    return r < 0 ? fail(r) : 0;
}

/* ------------------------------------------------------------------ важность процессов (oom_adj) */
/*
 * ActivityManager до 4.3 пишет важность процессов в /proc/<pid>/oom_adj, хост это запрещает, и ядерный
 * lowmemorykiller всё равно не про нас. Отдаём вместо файла FIFO /dev/aemu_oom с заголовком
 * «\n<pid> a » (или « s » для oom_score_adj): следом ActivityManager сам допишет число, а хост (GuestLmk)
 * убивает по этим числам фоновые процессы, когда памяти мало. Нет читателя — пишем в /dev/null.
 */
static int oom_open(const char *path) {
    static const char P[] = "/proc/";
    int i = 0;
    while (P[i] && path[i] == P[i]) i++;
    if (P[i]) return -1;
    const char *pid = path + i;
    int n = 0;
    while (pid[n] >= '0' && pid[n] <= '9') n++;
    if (!n || n > 10 || pid[n] != '/') return -1;
    const char *f = pid + n + 1;
    static const char A[] = "oom_adj", S[] = "oom_score_adj";
    int ka = 0, ks = 0;
    while (A[ka] && f[ka] == A[ka]) ka++;
    while (S[ks] && f[ks] == S[ks]) ks++;
    char kind;
    if (!A[ka] && !f[ka]) kind = 'a';
    else if (!S[ks] && !f[ks]) kind = 's';
    else return -1;
    long fd = sys3(SYS_open, (long)"/dev/aemu_oom", 01 /* O_WRONLY */ | 04000 /* O_NONBLOCK */ | 02000000 /* O_CLOEXEC */, 0);
    if (fd < 0) fd = sys3(SYS_open, (long)"/dev/null", 01 | 02000000, 0);
    if (fd < 0) return -1;
    char h[16];
    int k = 0;
    h[k++] = '\n';
    for (int j = 0; j < n; j++) h[k++] = pid[j];
    h[k++] = ' '; h[k++] = kind; h[k++] = ' ';
    sys3(SYS_write, fd, (long)h, k);
    return (int)fd;
}

/*
 * qemu answers /proc/self/cmdline from the command line the process was started with, but zygote children rename
 * themselves by rewriting their argument block (Process.setArgV0). Google Play services check that name ("must be
 * used only in persistent process") and crash. The block's address is taken at load time (it sits right before
 * environ on the initial stack) and its current text is served through a pipe.
 */
static char *g_argv0;
static unsigned long g_main_sp;
__attribute__((constructor)) static void find_argv(void) {
    g_main_sp = (unsigned long)__builtin_frame_address(0);
    char **e = environ;
    if (!e || e[-1]) return;
    long n = 0;
    char **a = e - 2;
    /* walk back over argv[] to the argc word */
    while (n < 64 && (unsigned long)a[0] > 4096) { a--; n++; }
    if (n > 0 && (long)a[0] == n) g_argv0 = a[1];
}
#define SYS_getpid 20
#define SYS_pipe2 359
/* 1 when path is /proc/self<suffix> or /proc/<own pid><suffix> */
static int proc_is(const char *path, const char *suffix) {
    static const char P[] = "/proc/";
    int i = 0;
    while (P[i] && path[i] == P[i]) i++;
    if (P[i]) return 0;
    const char *who = path + i;
    int n = 0;
    if (who[0] == 's' && who[1] == 'e' && who[2] == 'l' && who[3] == 'f') n = 4;
    else {
        long pid = 0;
        while (who[n] >= '0' && who[n] <= '9' && n < 10) pid = pid * 10 + (who[n++] - '0');
        if (!n || pid != sys3(SYS_getpid, 0, 0, 0)) return 0;
    }
    int k = 0;
    while (suffix[k] && who[n + k] == suffix[k]) k++;
    return !suffix[k] && !who[n + k];
}
static int cmdline_open(const char *path) {
    if (!g_argv0) return -1;
    if (!proc_is(path, "/cmdline")) return -1;
    int fds[2];
    if (sys3(SYS_pipe2, (long)fds, 02000000 /* O_CLOEXEC */, 0) < 0) return -1;
    long len = 0;
    while (len < 1024 && g_argv0[len]) len++;
    sys3(SYS_write, fds[1], (long)g_argv0, len + 1);
    sys3(SYS_close, fds[1], 0, 0);
    return fds[0];
}


/*
 * Bionic derives the main thread stack from the "[stack]" line of /proc/self/maps. On some hosts the emulator's
 * maps file tags a wrong range (or none at all), ART then sees a stack that does not contain sp and aborts with
 * "Check failed: &stack_variable > stack_end" (zygote and patchoat never start). The file is served again with the
 * tag moved onto the range that holds the main thread's sp; when it is already right the real file is used.
 */
#define SYS_lseek 19
#define SYS_memfd_create 385
static char g_maps_in[1 << 20], g_maps_out[(1 << 20) + 4096];
static volatile int g_maps_lock;
static unsigned long hexv(const char **pp) {
    const char *p = *pp; unsigned long v = 0;
    for (;; p++) {
        char c = *p;
        if (c >= '0' && c <= '9') v = v * 16 + (unsigned long)(c - '0');
        else if (c >= 'a' && c <= 'f') v = v * 16 + (unsigned long)(c - 'a' + 10);
        else break;
    }
    *pp = p; return v;
}
static int maps_open(const char *path) {
    if (!g_main_sp || !proc_is(path, "/maps")) return -1;
    if (__sync_lock_test_and_set(&g_maps_lock, 1)) return -1;
    int res = -1;
    long fd = sys3(SYS_open, (long)path, 0400000, 0);
    if (fd < 0) { __sync_lock_release(&g_maps_lock); return -1; }
    long total = 0;
    for (;;) {
        long n = sys3(SYS_read, fd, (long)(g_maps_in + total), (long)sizeof(g_maps_in) - 1 - total);
        if (n < 0) { total = -1; break; }
        if (n == 0) break;
        total += n;
        if (total >= (long)sizeof(g_maps_in) - 1) { total = -1; break; }
    }
    sys3(SYS_close, fd, 0, 0);
    if (total <= 0) goto out;
    g_maps_in[total] = 0;
    int changed = 0, found = 0;
    long o = 0;
    char *p = g_maps_in;
    while (*p) {
        char *line = p, *e = p;
        while (*e && *e != 0x0a) e++;
        p = *e ? e + 1 : e;
        long len = e - line;
        const char *q = line;
        unsigned long s0 = hexv(&q), e0 = 0;
        if (*q == '-') { q++; e0 = hexv(&q); }
        int holds = s0 <= g_main_sp && g_main_sp < e0;
        int tagAt = -1;
        for (long i = 0; i + 7 <= len; i++)
            if (line[i] == '[' && line[i+1] == 's' && line[i+2] == 't' && line[i+3] == 'a' && line[i+4] == 'c' && line[i+5] == 'k' && line[i+6] == ']') { tagAt = (int)i; break; }
        if (holds) found = 1;
        long keep = len;
        int add = 0;
        if (tagAt >= 0 && !holds) { keep = tagAt; while (keep > 0 && line[keep-1] == ' ') keep--; changed = 1; }
        else if (tagAt < 0 && holds) { add = 1; changed = 1; }
        for (long i = 0; i < keep; i++) g_maps_out[o++] = line[i];
        if (add) { const char T[] = "                       [stack]"; for (int i = 0; T[i]; i++) g_maps_out[o++] = T[i]; }
        if (*e) g_maps_out[o++] = 0x0a;
        if (o > (long)sizeof(g_maps_out) - 128) { changed = 0; break; }
    }
    if (!changed || !found) goto out;
    long m = sys3(SYS_memfd_create, (long)"maps", 0, 0);
    if (m < 0) goto out;
    long w = 0;
    while (w < o) { long n = sys3(SYS_write, m, (long)(g_maps_out + w), o - w); if (n <= 0) break; w += n; }
    if (w != o) { sys3(SYS_close, m, 0, 0); goto out; }
    sys3(SYS_lseek, m, 0, 0);
    res = (int)m;
out:
    __sync_lock_release(&g_maps_lock);
    return res;
}

/*
 * qemu passes the phone's own /proc/meminfo through: a 2.3 firmware saw 16 GB and vendors that take the total from a
 * build prop (MIUI "-22766MB of 1GB available") computed negative free memory. The file is served scaled to the
 * guest's RAM budget (AEMU_RAM_KB from the host): every kB value times guest/host total, so free, cached and buffers
 * keep the phone's proportions. -nostdlib on ARMv7 has no division helpers, so the division is done by shifts.
 */
static unsigned long long udiv64(unsigned long long n, unsigned long d) {
    unsigned long long q = 0, r = 0;
    for (int i = 63; i >= 0; i--) {
        r = (r << 1) | ((n >> i) & 1);
        if (r >= d) { r -= d; q |= 1ULL << i; }
    }
    return q;
}
static unsigned long env_ulong(const char *key) {
    for (char **e = environ; e && *e; e++) {
        const char *v = *e;
        int i = 0;
        while (key[i] && v[i] == key[i]) i++;
        if (key[i] || v[i] != '=') continue;
        unsigned long x = 0;
        for (v += i + 1; *v >= '0' && *v <= '9'; v++) x = x * 10 + (unsigned long)(*v - '0');
        return x;
    }
    return 0;
}
static int meminfo_open(const char *path) {
    static const char M[] = "/proc/meminfo";
    int i = 0;
    while (M[i] && path[i] == M[i]) i++;
    if (M[i] || path[i]) return -1;
    unsigned long guest = env_ulong("AEMU_RAM_KB");
    if (!guest) return -1;
    char in[8192], out[8192 + 512];
    long fd = sys3(SYS_open, (long)path, 0400000, 0);
    if (fd < 0) return -1;
    long total = 0;
    for (;;) {
        long n = sys3(SYS_read, fd, (long)(in + total), (long)sizeof(in) - 1 - total);
        if (n <= 0) break;
        total += n;
        if (total >= (long)sizeof(in) - 1) break;
    }
    sys3(SYS_close, fd, 0, 0);
    if (total <= 0) return -1;
    in[total] = 0;
    /* the host total: first number of the file (MemTotal) */
    unsigned long host = 0;
    const char *p = in;
    while (*p && (*p < '0' || *p > '9')) p++;
    while (*p >= '0' && *p <= '9') host = host * 10 + (unsigned long)(*p++ - '0');
    if (!host || guest >= host) return -1;
    long o = 0;
    p = in;
    while (*p && o < (long)sizeof(out) - 64) {
        const char *line = p, *e = p;
        while (*e && *e != 0x0a) e++;
        const char *d = line;
        while (d < e && (*d < '0' || *d > '9')) d++;
        const char *de = d;
        unsigned long long v = 0;
        while (de < e && *de >= '0' && *de <= '9') v = v * 10 + (unsigned long long)(*de++ - '0');
        int kb = de + 3 <= e && de[0] == ' ' && de[1] == 'k' && de[2] == 'B';
        if (d < e && kb) {
            unsigned long s = (unsigned long)udiv64(v * guest, host);
            char num[12]; int k = 0;
            do { num[k++] = (char)('0' + (int)(s % 10)); s /= 10; } while (s && k < 11);
            /* keep the column: the new number ends where the old one did */
            int extra = k - (int)(de - d);
            const char *pe = d;
            while (extra > 0 && pe - line >= 2 && pe[-1] == ' ' && pe[-2] == ' ') { pe--; extra--; }
            for (const char *c = line; c < pe; c++) out[o++] = *c;
            for (int j = (int)(de - d); j > k; j--) out[o++] = ' ';
            while (k) out[o++] = num[--k];
            for (const char *c = de; c < e; c++) out[o++] = *c;
        } else {
            for (const char *c = line; c < e; c++) out[o++] = *c;
        }
        if (*e) out[o++] = 0x0a;
        p = *e ? e + 1 : e;
    }
    long m = sys3(SYS_memfd_create, (long)"meminfo", 0, 0);
    if (m < 0) return -1;
    long w = 0;
    while (w < o) { long n = sys3(SYS_write, m, (long)(out + w), o - w); if (n <= 0) break; w += n; }
    sys3(SYS_lseek, m, 0, 0);
    return (int)m;
}

EXPORT int open(const char *path, int flags, ...) {
    char b[128];
    int mode = 0;
    if (flags & 0100 /* O_CREAT */) {
        __builtin_va_list ap; __builtin_va_start(ap, flags); mode = __builtin_va_arg(ap, int); __builtin_va_end(ap);
    }
    if (path && (flags & 3) && path[0] == '/' && path[1] == 'p') {
        int fd = oom_open(path);
        if (fd >= 0) return fd;
    }
    if (path && !(flags & 3) && path[0] == '/' && path[1] == 'p') {
        int fd = cmdline_open(path);
        if (fd >= 0) return fd;
        fd = meminfo_open(path);
        if (fd >= 0) return fd;
        fd = maps_open(path);
        if (fd >= 0) return fd;
    }
    long r = sys3(SYS_open, (long)qt_redirect(path, b, sizeof(b)), flags | 0400000 /* O_LARGEFILE */, mode);
    return r < 0 ? fail(r) : (int)r;
}

EXPORT int __open_2(const char *path, int flags) { return open(path, flags); }

/*
 * Пустые «запасные» виртуальные методы VectorImpl/SortedVectorImpl из libutils 4.0–4.3.
 * В libutils части прошивок (MediaTek 4.4 и др.) их выбросили, и библиотеки, собранные под AOSP
 * (политика звука движка), не загружаются: «cannot locate symbol reservedVectorImpl1».
 * В AOSP они пустые и никогда не вызываются — даём их всем процессам через LD_PRELOAD.
 */
#define RESERVED(n) EXPORT void _ZN7android10VectorImpl19reservedVectorImpl##n##Ev(void *self) { (void)self; } \
    EXPORT void _ZN7android16SortedVectorImpl25reservedSortedVectorImpl##n##Ev(void *self) { (void)self; }
RESERVED(1) RESERVED(2) RESERVED(3) RESERVED(4) RESERVED(5) RESERVED(6) RESERVED(7) RESERVED(8)

/* ------------------------------------------------------------------ sigsuspend
 *
 * qemu of the stand mis-emulates rt_sigsuspend: the call returns at once without delivering the
 * pending (host-blocked) signal. A shell waiting for its child blocks SIGCHLD, calls sigsuspend
 * and spins at 100% CPU forever — every `sh -c` that runs a program never returns (MIUI firewall,
 * am/pm from the emulator menu). Here the mask is lifted with rt_sigprocmask (qemu delivers
 * pending signals after that syscall), we sleep briefly, restore the mask and report EINTR, which
 * is what a real sigsuspend returns once a handler has run.
 */
__attribute__((naked, noinline)) static long sys4(long n, long a, long b, long c, long d) {
    __asm__ volatile(
        "push {r7}; mov r7, r0; mov r0, r1; mov r1, r2; mov r2, r3; ldr r3, [sp, #4]; svc #0; pop {r7}; bx lr");
}
#define SYS_nanosleep 162
#define SYS_rt_sigprocmask 175
#define SIG_SETMASK 2
#define EINTR 4

EXPORT int sigsuspend(const unsigned long *mask) {
    unsigned long want[2] = { mask ? mask[0] : 0, 0 }, old[2] = { 0, 0 };
    sys4(SYS_rt_sigprocmask, SIG_SETMASK, (long)want, (long)old, 8);
    struct timespec_s ts = { 0, 5 * 1000 * 1000 };
    sys3(SYS_nanosleep, (long)&ts, 0, 0);
    sys4(SYS_rt_sigprocmask, SIG_SETMASK, (long)old, 0, 8);
    *__errno() = EINTR;
    return -1;
}

/*
 * Fault handlers that resume at a Thumb function (ART's implicit null / stack-overflow / suspend checks:
 * the SIGSEGV handler sets uc->arm_pc = art_quick_throw_null_pointer_exception, an address with bit 0 set).
 * A real CPU ignores PC bit 0 on the exception return, qemu keeps it and aborts translating an odd Thumb pc.
 * The fault signals' handlers are wrapped: after the guest's handler returns, an odd pc becomes even with the
 * Thumb bit set in cpsr. Everything else about sigaction stays as the kernel does it.
 */
#define SYS_rt_sigaction 174
#define SA_SIGINFO_ 4
#define SA_RESTORER_ 0x04000000
struct bionic_sigaction { void *handler; unsigned long mask; int flags; void (*restorer)(void); };
struct kernel_sigaction { void *handler; unsigned long flags; void (*restorer)(void); unsigned long mask[2]; };
static struct bionic_sigaction guest_act[32];

static int wrapped(int sig) { return sig == 4 /* ILL */ || sig == 5 /* TRAP */ || sig == 7 /* BUS */ || sig == 8 /* FPE */ || sig == 11 /* SEGV */; }

static void fault_trampoline(int sig, void *info, void *uc) {
    struct bionic_sigaction *a = &guest_act[sig & 31];
    if (a->flags & SA_SIGINFO_) ((void (*)(int, void *, void *))a->handler)(sig, info, uc);
    else ((void (*)(int))a->handler)(sig);
    /* ucontext: uc_flags, uc_link, uc_stack(3) | trap_no, error_code, oldmask, r0..r10, fp, ip, sp, lr, pc, cpsr */
    ulong *pc = (ulong *)((char *)uc + 20 + 4 * 18);
    ulong *cpsr = pc + 1;
    if (*pc & 1) { *pc &= ~1UL; *cpsr |= 0x20; }
}

EXPORT int sigaction(int sig, const struct bionic_sigaction *act, struct bionic_sigaction *old) {
    struct kernel_sigaction k, ko;
    struct kernel_sigaction *kp = 0;
    int wrap = wrapped(sig) && act && act->handler != (void *)0 && act->handler != (void *)1;
    if (act) {
        k.handler = act->handler; k.flags = (unsigned long)act->flags; k.restorer = act->restorer;
        k.mask[0] = act->mask; k.mask[1] = 0;
        if (wrap) { k.handler = (void *)fault_trampoline; k.flags |= SA_SIGINFO_; }
        kp = &k;
    }
    long r = sys4(SYS_rt_sigaction, sig, (long)kp, (long)&ko, 8);
    if (r < 0) return fail(r);
    if (old) {
        if (ko.handler == (void *)fault_trampoline) *old = guest_act[sig & 31];
        else { old->handler = ko.handler; old->mask = ko.mask[0]; old->flags = (int)ko.flags; old->restorer = ko.restorer; }
    }
    if (wrap) guest_act[sig & 31] = *act;
    return 0;
}


/*
 * libbacktrace (ART thread dumps on SIGQUIT, ANR traces) unwinds each thread by sending it signal 33 and
 * waiting on a futex for the handler; under qemu every wait runs into its timeout, a dump of system_server
 * takes minutes and the watchdogs kill the system. That request fails at once instead: dumps keep Java stacks.
 */
#define SYS_tgkill 268
EXPORT int tgkill(int tgid, int tid, int sig) {
    if (sig == 33 /* libbacktrace THREAD_SIGNAL, __SIGRTMIN+1 */) { *__errno() = 22; return -1; }
    long r = sys3(SYS_tgkill, tgid, tid, sig);
    return r < 0 ? fail(r) : 0;
}

/*
 * binder ordering. The stand's binderd hands a oneway transaction to a thread that is itself waiting for the
 * reply of a synchronous call (the kernel driver never does: such a thread only takes work from its own call
 * chain). An app's main thread blocked in attachApplication then receives scheduleCreateService/LaunchActivity
 * while bindApplication, sent earlier, went to a pool thread — whichever posts to the main looper first wins,
 * and a lost race kills the app ("Instrumentation.onException on a null object"). A oneway transaction that
 * arrives nested in a pending call is held back briefly so the earlier one is queued first.
 */
#define BINDER_WRITE_READ_ 0xc0186201
#define BC_TRANSACTION_ 0x40286300
#define BR_TRANSACTION_ 0x80287202
#define BR_REPLY_ 0x80287203
#define TF_ONE_WAY_ 1
#define SYS_ioctl 54
#define SYS_gettid 224
struct bwr { long write_size, write_consumed; ulong write_buffer; long read_size, read_consumed; ulong read_buffer; };
static volatile int waiting_tid[64];

static int wait_slot(int tid, int add) {
    for (int k = 0; k < 64; k++) if (waiting_tid[k] == tid) { if (!add) waiting_tid[k] = 0; return 1; }
    if (add) for (int k = 0; k < 64; k++) if (__sync_bool_compare_and_swap(&waiting_tid[k], 0, tid)) return 1;
    return 0;
}

/* player notifications taken from a thread that waits for a reply; the next pool thread that reads gets them */
static unsigned char g_held[1024];
static volatile int g_held_len, g_held_lock;
static void held_lock(void) { while (!__sync_bool_compare_and_swap(&g_held_lock, 0, 1)) sys3(158 /* sched_yield */, 0, 0, 0); }
static void held_unlock(void) { __sync_lock_release(&g_held_lock); }

/* binder_transaction_data (32-bit) of a oneway call to android.media.IMediaPlayerClient: the parcel starts with the
 * strict-mode word, then the interface name as String16 */
static int is_player_notify(const unsigned char *td) {
    const unsigned char *d = (const unsigned char *)(ulong)*(const unsigned *)(td + 32);
    unsigned size = *(const unsigned *)(td + 24);
    const char *want = "android.media.IMediaPlayerClient";
    if (!d || size < 8 + 64) return 0;
    if (*(const unsigned *)(d + 4) != 32) return 0;
    for (int i = 0; i < 32; i++) if (d[8 + 2 * i] != (unsigned char)want[i] || d[9 + 2 * i]) return 0;
    /* MEDIA_PREPARED and MEDIA_SEEK_COMPLETE stay: prepare()/seekTo() expect them on the calling thread */
    int msg = *(const int *)(d + 76);
    return msg != 1 && msg != 4;
}

static int is_waiting(int tid) { for (int k = 0; k < 64; k++) if (waiting_tid[k] == tid) return 1; return 0; }

EXPORT int ioctl(int fd, int req, void *arg) {
    if ((unsigned)req != BINDER_WRITE_READ_ || !arg) {
        long r = sys3(SYS_ioctl, fd, req, (long)arg);
        return r < 0 ? fail(r) : (int)r;
    }
    struct bwr *b = (struct bwr *)arg;
    int tid = (int)sys3(SYS_gettid, 0, 0, 0);
    // a synchronous BC_TRANSACTION in this write: the thread now waits for its reply
    for (long p = b->write_consumed; p + 4 <= b->write_size;) {
        unsigned cmd = *(unsigned *)(b->write_buffer + p);
        unsigned sz = (cmd >> 16) & 0x3fff;
        if (cmd == BC_TRANSACTION_ && p + 4 + 16 <= b->write_size && !(*(unsigned *)(b->write_buffer + p + 4 + 12) & TF_ONE_WAY_))
            wait_slot(tid, 1);
        p += 4 + sz;
    }
    long start = b->read_consumed;
    long r = sys3(SYS_ioctl, fd, req, (long)arg);
    /* The stand's binder link now and then answers ENOTCONN; libbinder aborts the whole process on that
     * (SurfaceFlinger, system_server, ...). A short retry rides out a hiccup. */
    for (int tries = 0; r == -107 /* ENOTCONN */ && tries < 25; tries++) {
        struct timespec_s ts = { 0, 80 * 1000 * 1000 };
        sys3(162 /* nanosleep */, (long)&ts, 0, 0);
        r = sys3(SYS_ioctl, fd, req, (long)arg);
    }
    if (r < 0) return fail(r);
    int nested_oneway = 0, replied = 0;
    for (long p = start; p + 4 <= b->read_consumed;) {
        unsigned cmd = *(unsigned *)(b->read_buffer + p);
        unsigned sz = (cmd >> 16) & 0x3fff;
        if (cmd == BR_REPLY_ || cmd == 0x7205 /* BR_DEAD_REPLY */ || cmd == 0x7211 /* BR_FAILED_REPLY */) replied = 1;
        else if (cmd == BR_TRANSACTION_ && p + 4 + 16 <= b->read_consumed && (*(unsigned *)(b->read_buffer + p + 4 + 12) & TF_ONE_WAY_))
            nested_oneway = 1;
        p += 4 + sz;
    }
    int waiting = is_waiting(tid);
    if (nested_oneway && !replied && waiting) {
        /* a media player's notify must not run here: MediaPlayer::reset() and friends hold the player lock across
         * the call and notify() takes it (MIUI's SystemUI froze playing the charging sound). It goes to the next
         * pool thread that reads, where the kernel driver would have delivered it. */
        unsigned char *rb = (unsigned char *)b->read_buffer;
        held_lock();
        for (long p = start; p + 4 <= b->read_consumed;) {
            unsigned cmd = *(unsigned *)(rb + p);
            long len = 4 + ((cmd >> 16) & 0x3fff);
            if (cmd == BR_TRANSACTION_ && p + len <= b->read_consumed && (*(unsigned *)(rb + p + 4 + 12) & TF_ONE_WAY_)
                && is_player_notify(rb + p + 4) && g_held_len + len <= (long)sizeof g_held) {
                for (long i = 0; i < len; i++) g_held[g_held_len + i] = rb[p + i];
                g_held_len += (int)len;
                for (long i = p; i + len < b->read_consumed; i++) rb[i] = rb[i + len];
                b->read_consumed -= len;
                continue;
            }
            p += len;
        }
        held_unlock();
    }
    if (nested_oneway && !replied && waiting && sys3(199 /* getuid32 */, 0, 0, 0) >= 10000) {
        struct timespec_s ts = { 0, 30 * 1000 * 1000 };
        sys3(162 /* nanosleep */, (long)&ts, 0, 0);
    }
    if (!waiting && g_held_len) {
        held_lock();
        if (g_held_len && b->read_consumed + g_held_len <= b->read_size) {
            unsigned char *rb = (unsigned char *)b->read_buffer;
            for (int i = 0; i < g_held_len; i++) rb[b->read_consumed + i] = g_held[i];
            b->read_consumed += g_held_len;
            g_held_len = 0;
        }
        held_unlock();
    }
    if (replied) wait_slot(tid, 0);
    return (int)r;
}

/*
 * Host paths leak into the guest: /proc/self/fd/N links and the names of bound unix sockets are the phone's
 * real paths (".../files/images/<id>/root/dev/socket/zygote"). 7.0's zygote checks every open descriptor
 * against a whitelist of guest paths before forking and aborts ("Socket name not whitelisted", "Unable to
 * construct file descriptor table"). The image-root prefix is cut so the guest sees its own paths.
 */
static int strip_root(char *s, int n) {
    // find "/files/images/" then the following "/root", keep what comes after it
    for (int i = 0; i + 14 < n; i++) {
        const char *m = "/files/images/";
        int k = 0;
        while (k < 14 && s[i + k] == m[k]) k++;
        if (k < 14) continue;
        int j = i + 14;
        while (j < n && s[j] != '/') j++;
        if (j + 5 > n || s[j + 1] != 'r' || s[j + 2] != 'o' || s[j + 3] != 'o' || s[j + 4] != 't') return n;
        int from = j + 5, len = n - from;
        if (len <= 0) { s[0] = '/'; return 1; }
        for (int q = 0; q < len; q++) s[q] = s[from + q];
        return len;
    }
    return n;
}

#define SYS_readlink 85
#define SYS_readlinkat 332
#define SYS_getsockname 286
/* qemu's own ownership table stays open in every guest process; zygote (7.0+) only lets through descriptors on
 * whitelisted paths and reopens them by path after fork: it is shown as a framework jar that links to it */
static long guest_fd_name_(char *buf, long n, unsigned long size);
/* readlink does not terminate the name, but the host path it returned is longer than the guest one we leave: clear
 * the tail, or callers that zero the buffer and read it as a string (MediaTek FileSourceProxy) get "...ogg" + "oot/..." */
static long guest_fd_name(char *buf, long n, unsigned long size) {
    long r = guest_fd_name_(buf, n, size);
    for (long i = r; i < n; i++) buf[i] = 0;
    return r;
}
static long guest_fd_name_(char *buf, long n, unsigned long size) {
    n = strip_root(buf, (int)n);
    // stdio of guest processes: pipes to the host's log readers or the log file itself (on Android: /dev/null)
    int logfile = 0;
    for (int i = 0; i + 14 < n && !logfile; i++) {
        const char *m = "/files/images/";
        int k = 0;
        while (k < 14 && buf[i + k] == m[k]) k++;
        if (k == 14) for (int j = i + 14; j + 5 <= n; j++) if (buf[j] == '/' && buf[j + 1] == 'r' && buf[j + 2] == 'u' && buf[j + 3] == 'n' && buf[j + 4] == '/') { logfile = 1; break; }
    }
    // the property area stays open here (on Android it is only mapped): after fork nobody needs the descriptor
    { const char *pp = "/dev/__properties__"; int k = 0; while (k < n && pp[k] && buf[k] == pp[k]) k++; if (!pp[k]) logfile = 1; }
    if (size >= 9 && (logfile || (n > 5 && buf[0] == 'p' && buf[1] == 'i' && buf[2] == 'p' && buf[3] == 'e' && buf[4] == ':'))) {
        const char *dn = "/dev/null";
        for (int m = 0; m < 9; m++) buf[m] = dn[m];
        return 9;
    }
    const char *own = "/dhd.owners", *as = "/system/framework/aemu-owners.jar";
    int k = 0;
    while (k < n && own[k] && buf[k] == own[k]) k++;
    if (k == n && !own[k]) {
        int m = 0;
        while (as[m] && (unsigned long)m < size) { buf[m] = as[m]; m++; }
        n = m;
    }
    return n;
}
EXPORT long readlink(const char *path, char *buf, unsigned long size) {
    long r = sys3(SYS_readlink, (long)path, (long)buf, (long)size);
    if (r < 0) return fail(r);
    return guest_fd_name(buf, r, size);
}
EXPORT long readlinkat(int dirfd, const char *path, char *buf, unsigned long size) {
    long r = sys4(SYS_readlinkat, dirfd, (long)path, (long)buf, (long)size);
    if (r < 0) return fail(r);
    return guest_fd_name(buf, r, size);
}
struct sockaddr_un_ { unsigned short family; char path[108]; };
EXPORT int getsockname(int fd, void *addr, unsigned *len) {
    long r = sys3(SYS_getsockname, fd, (long)addr, (long)len);
    if (r < 0) return fail(r);
    struct sockaddr_un_ *u = (struct sockaddr_un_ *)addr;
    if (u && len && *len > 2 && u->family == 1 /* AF_UNIX */ && u->path[0]) {
        int n = (int)*len - 2;
        while (n > 0 && u->path[n - 1] == 0) n--;
        int m = strip_root(u->path, n);
        if (m != n) { u->path[m] = 0; *len = 2 + m + 1; }
    }
    return 0;
}

/* Extended attributes are unavailable here (ENOSYS): 7.0+ installd gives up on every app dir when restorecon cannot
 * read the SELinux label, and never creates the /data/user_de storage. Label reads give a fixed label, writes succeed.
 * getxattr passes through except for user.default, before this library is relocated. */
static long sys5(long n, long a, long b, long c, long d, long e);
/* 7.0+ installd migrates app data between /data/user_de and /data/data on every boot unless the target dir carries
 * user.default, deleting one copy each time: report that marker as present. Everything else is the plain syscall
 * (mapping other errors broke property reads in 7.x processes). */
EXPORT long getxattr(const char *p, const char *n, void *v, unsigned long s) {
    const char *want = "user.default";
    int k = 0;
    while (n && want[k] && n[k] == want[k]) k++;
    if (n && !want[k] && !n[k]) return 0;
    /* UserManager reads user.serial on the user dirs; any error but ENODATA ("not set yet, write it") makes
     * system_server destroy all data of user 0 */
    const char *ser = "user.serial";
    k = 0;
    while (n && ser[k] && n[k] == ser[k]) k++;
    if (n && !ser[k] && !n[k]) return fail(-61 /* ENODATA */);
    long r = sys4(229, (long)p, (long)n, (long)v, (long)s);
    return r >= 0 ? r : fail(r);
}
EXPORT long lgetxattr(const char *p, const char *n, void *v, unsigned long s) {
    long r = sys4(230, (long)p, (long)n, (long)v, (long)s);
    if (r >= 0 || (r != -38 /* ENOSYS */ && r != -95 /* EOPNOTSUPP */)) return r >= 0 ? r : fail(r);
    /* restorecon needs some current label to compare with; any mismatch is then "fixed" by lsetxattr below */
    const char *sel = "security.selinux", *ctx = "u:object_r:system_data_file:s0";
    for (int k = 0; sel[k]; k++) if (!n || n[k] != sel[k]) return fail(-61 /* ENODATA */);
    unsigned long m = 0;
    while (ctx[m]) m++;
    m++;
    if (!s) return (long)m;
    if (s < m) return fail(-34 /* ERANGE */);
    for (unsigned long k = 0; k < m; k++) ((char *)v)[k] = ctx[k];
    return (long)m;
}
/* installd tags app dirs (user.default, user.inode_cache); libc may reach this through fsetxattr, so no errno
 * is touched on the ENOSYS path */
EXPORT int setxattr(const char *p, const char *n, const void *v, unsigned long s, int fl) {
    long r = sys5(226, (long)p, (long)n, (long)v, (long)s, fl);
    return r >= 0 || r == -38 || r == -95 ? 0 : fail(r);
}
EXPORT int lsetxattr(const char *p, const char *n, const void *v, unsigned long s, int fl) {
    long r = sys5(227, (long)p, (long)n, (long)v, (long)s, fl);
    return r >= 0 || r == -38 || r == -95 ? 0 : fail(r);
}
/* 5.0+ marks every socket with its network id through fwmarkd (netd), which sets SO_MARK. An app has no
 * CAP_NET_ADMIN, the phone's kernel refuses the option, and netd answers every connect() with that error
 * (ENOPROTOOPT): no TCP and no DNS at all. All sockets already leave through the phone's one network, so the mark
 * is accepted and dropped. */
EXPORT int setsockopt(int fd, int level, int name, const void *val, unsigned int len) {
    if (level == 1 /* SOL_SOCKET */ && name == 36 /* SO_MARK */) return 0;
    long r = sys5(294 /* setsockopt */, fd, level, name, (long)val, len);
    return r >= 0 ? (int)r : fail(r);
}
__attribute__((naked, noinline)) static long sys5(long n, long a, long b, long c, long d, long e) {
    __asm__ volatile(
        "push {r4, r7}; mov r7, r0; mov r0, r1; mov r1, r2; mov r2, r3; ldr r3, [sp, #8]; ldr r4, [sp, #12]; svc #0; pop {r4, r7}; bx lr");
}

/* The *_ALARM clocks need CAP_WAKE_ALARM, which an app never has: 7.0+ AlarmManagerService then falls back to a
 * handler that throws on listener alarms and kills system_server. The plain clocks behave the same without wakeups. */
EXPORT int timerfd_create(int clock, int flags) {
    if (clock == 8 /* REALTIME_ALARM */) clock = 0 /* REALTIME */;
    else if (clock == 9 /* BOOTTIME_ALARM */) clock = 7 /* BOOTTIME */;
    long r = sys3(350 /* timerfd_create */, clock, flags, 0);
    return r < 0 ? fail(r) : (int)r;
}
