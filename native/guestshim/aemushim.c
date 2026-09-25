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

/* перезагрузку и выключение гостя обрабатывает хост (кнопка «Выключить») */
EXPORT int reboot(int cmd) { (void)cmd; return 0; }
EXPORT int __reboot(int m1, int m2, int cmd, void *arg) { (void)m1; (void)m2; (void)cmd; (void)arg; return 0; }
EXPORT int android_reboot(int cmd, int flags, char *arg) { (void)cmd; (void)flags; (void)arg; return 0; }

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

EXPORT int open(const char *path, int flags, ...) {
    char b[128];
    int mode = 0;
    if (flags & 0100 /* O_CREAT */) {
        __builtin_va_list ap; __builtin_va_start(ap, flags); mode = __builtin_va_arg(ap, int); __builtin_va_end(ap);
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
