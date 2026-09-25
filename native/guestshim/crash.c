/*
 * Мини-debuggerd для гостя.
 *
 * Настоящий debuggerd работает через ptrace, а под qemu-user его нет — падения native-кода остаются без
 * стека («Unable to open connection to debuggerd»). Здесь на SIGSEGV/SIGBUS/SIGILL/SIGFPE/SIGABRT/SIGSTKFLT
 * ставится свой обработчик: он пишет в журнал гостя (тег aemu_crash, видно в logcat и в журнале эмулятора)
 * регистры, адрес сбоя и все слова стека, похожие на адрес возврата в исполняемый код, в виде
 * «библиотека+смещение», после чего возвращает действие по умолчанию — процесс падает как обычно.
 *
 * Внутри обработчика — только системные вызовы и статические буферы.
 */

typedef unsigned int u32;

extern int sigaction(int sig, const void *act, void *old);

__attribute__((naked, noinline)) static long cr_sys3(long n, long a, long b, long c) {
    __asm__ volatile(
        "push {r7}; mov r7, r0; mov r0, r1; mov r1, r2; mov r2, r3; svc #0; pop {r7}; bx lr");
}
#define SYS_read 3
#define SYS_write 4
#define SYS_open 5
#define SYS_close 6
#define SYS_getpid 20
#define SYS_gettid 224

/* ---- вывод ---- */

static char g_line[512];
static int g_len;

static void put(const char *s) { while (*s && g_len < (int)sizeof(g_line) - 1) g_line[g_len++] = *s++; }
static void puthex(u32 v) {
    char b[11]; b[0] = '0'; b[1] = 'x';
    for (int i = 0; i < 8; i++) { u32 d = (v >> (28 - 4 * i)) & 15; b[2 + i] = (char)(d < 10 ? '0' + d : 'a' + d - 10); }
    b[10] = 0; put(b);
}
static void putdec(u32 v) {
    char b[12]; int i = 11; b[i] = 0;
    do { b[--i] = (char)('0' + v % 10); v /= 10; } while (v && i > 0);
    put(b + i);
}

static int g_logfd = -1;

/* одна запись журнала: [приоритет][тег]\0[сообщение]\0 — так пишет liblog гостя в файл /dev/log/main */
static void flush_line(void) {
    g_line[g_len] = 0;
    if (g_logfd >= 0) {
        static char rec[600];
        int n = 0;
        rec[n++] = 7; /* ANDROID_LOG_FATAL */
        const char *tag = "aemu_crash";
        while (*tag) rec[n++] = *tag++;
        rec[n++] = 0;
        for (int i = 0; i < g_len && n < (int)sizeof(rec) - 2; i++) rec[n++] = g_line[i];
        rec[n++] = 0;
        cr_sys3(SYS_write, g_logfd, (long)rec, n);
    }
    g_line[g_len] = '\n';
    cr_sys3(SYS_write, 2, (long)g_line, g_len + 1);
    g_len = 0;
}

/* ---- карта памяти ---- */

#define MAXMAP 1024
static char g_maps[256 * 1024];
static u32 g_ms[MAXMAP], g_me[MAXMAP], g_mo[MAXMAP];
static const char *g_mp[MAXMAP];
static int g_nmaps;
static u32 g_sp, g_stack_end;   /* граница отображения, где лежит sp (любые права) */

static u32 hexnum(const char **pp) {
    const char *p = *pp; u32 v = 0;
    for (;;) {
        char c = *p;
        if (c >= '0' && c <= '9') v = v * 16 + (u32)(c - '0');
        else if (c >= 'a' && c <= 'f') v = v * 16 + (u32)(c - 'a' + 10);
        else break;
        p++;
    }
    *pp = p; return v;
}

static void load_maps(void) {
    g_nmaps = 0;
    long fd = cr_sys3(SYS_open, (long)"/proc/self/maps", 0, 0);
    if (fd < 0) return;
    long total = 0;
    for (;;) {
        long n = cr_sys3(SYS_read, fd, (long)(g_maps + total), (long)(sizeof(g_maps) - 1 - total));
        if (n <= 0) break;
        total += n;
        if (total >= (long)sizeof(g_maps) - 1) break;
    }
    cr_sys3(SYS_close, fd, 0, 0);
    g_maps[total] = 0;
    char *p = g_maps;
    while (*p && g_nmaps < MAXMAP) {
        char *line = p;
        while (*p && *p != '\n') p++;
        if (*p) *p++ = 0;
        const char *q = line;
        u32 s = hexnum(&q); if (*q != '-') continue; q++;
        u32 e = hexnum(&q); if (*q != ' ') continue; q++;
        int exec = q[2] == 'x';
        if (g_sp >= s && g_sp < e) g_stack_end = e;
        q += 5;
        u32 off = hexnum(&q);
        /* путь — после пятого поля */
        int field = 3;
        while (*q && field < 6) { while (*q == ' ') q++; if (field == 5) break; while (*q && *q != ' ') q++; field++; }
        while (*q == ' ') q++;
        if (!exec) continue;
        g_ms[g_nmaps] = s; g_me[g_nmaps] = e; g_mo[g_nmaps] = off; g_mp[g_nmaps] = q;
        g_nmaps++;
    }
}

static int find_map(u32 a) {
    for (int i = 0; i < g_nmaps; i++) if (a >= g_ms[i] && a < g_me[i]) return i;
    return -1;
}

static void put_sym(u32 a) {
    int m = find_map(a);
    if (m < 0) { put("?"); return; }
    const char *path = g_mp[m];
    const char *base = path;
    for (const char *c = path; *c; c++) if (*c == '/') base = c + 1;
    /* путь внутри образа прошивки обрезаем до гостевого */
    const char *sys = path;
    for (const char *c = path; *c; c++) if (c[0] == '/' && c[1] == 's' && c[2] == 'y' && c[3] == 's' && c[4] == 't' && c[5] == 'e' && c[6] == 'm' && c[7] == '/') { sys = c; break; }
    put(*sys ? sys : base);
    put("+"); puthex(a - g_ms[m] + g_mo[m]);
}

/* ---- обработчик ---- */

struct bsigaction { void *handler; u32 mask; u32 flags; void *restorer; }; /* struct sigaction bionic (ARM) */

static const int g_sigs[] = { 4, 6, 7, 8, 11, 16 };
#define SIGUSR2 12
#define SYS_tgkill 268
static volatile int g_dump_lock;   /* SIGUSR2: потоки печатают стек по очереди */
static volatile int g_dump_active;
static const char *g_names[] = { "SIGILL", "SIGABRT", "SIGBUS", "SIGFPE", "SIGSEGV", "SIGSTKFLT" };
static volatile int g_busy;

static void dump_current(const char *title, u32 addr, u32 *regs) {
    g_logfd = (int)cr_sys3(SYS_open, (long)"/dev/log/main", 1 | 02000 /* O_WRONLY|O_APPEND */, 0);
    g_sp = regs[13]; g_stack_end = 0;
    load_maps();
    put("*** "); put(title); put(" pid "); putdec((u32)cr_sys3(SYS_getpid, 0, 0, 0));
    put(" tid "); putdec((u32)cr_sys3(SYS_gettid, 0, 0, 0)); put(" адрес "); puthex(addr); flush_line();
    put("pc "); puthex(regs[15]); put(" "); put_sym(regs[15]); flush_line();
    put("lr "); puthex(regs[14]); put(" "); put_sym(regs[14]); flush_line();
    for (int r = 0; r < 13; r += 4) {
        for (int k = r; k < r + 4 && k < 13; k++) { put("r"); putdec((u32)k); put("="); puthex(regs[k]); put(" "); }
        flush_line();
    }
    put("sp "); puthex(regs[13]); flush_line();
    /* стек: всё, что похоже на адрес возврата в исполняемый код */
    u32 *sp = (u32 *)regs[13];
    u32 words = g_stack_end > regs[13] ? (g_stack_end - regs[13]) / 4 : 0;
    if (words > 4096) words = 4096;
    int shown = 0;
    for (u32 i = 0; i < words && shown < 48; i++) {
        u32 w = sp[i];
        if (find_map(w) < 0) continue;
        put("  #"); putdec((u32)shown); put(" sp+"); puthex(i * 4); put("  "); puthex(w); put(" "); put_sym(w & ~1u);
        flush_line();
        shown++;
    }
    put("*** конец"); flush_line();
    if (g_logfd >= 0) cr_sys3(SYS_close, g_logfd, 0, 0);
}

static void handler(int sig, void *info, void *ucv) {
    if (g_busy) return;
    g_busy = 1;
    u32 *uc = (u32 *)ucv;
    /* ucontext ARM: flags, link, stack(3), затем sigcontext: trap_no, error_code, oldmask, r0..r10, fp, ip, sp, lr, pc, cpsr, fault_address */
    u32 *regs = uc + 5 + 3;
    u32 addr = info ? ((u32 *)info)[3] : 0;   /* siginfo.si_addr */
    const char *name = "?";
    for (int i = 0; i < 6; i++) if (g_sigs[i] == sig) name = g_names[i];
    dump_current(name, addr, regs);
    /* дальше — как без нас: действие по умолчанию, инструкция повторится и процесс упадёт */
    struct bsigaction dfl = { 0, 0, 0, 0 };
    sigaction(sig, &dfl, 0);
    g_busy = 0;
}

/*
 * SIGUSR2 — снимок стеков всех потоков процесса (для зависаний native-служб: kill -USR2 <pid>).
 * Первый получивший сигнал поток рассылает его остальным потокам (tgkill), каждый печатает свой стек.
 */
static void usr2_handler(int sig, void *info, void *ucv) {
    (void)sig; (void)info;
    u32 *regs = (u32 *)ucv + 5 + 3;
    int self = (int)cr_sys3(SYS_gettid, 0, 0, 0);
    int pid = (int)cr_sys3(SYS_getpid, 0, 0, 0);
    if (!__sync_lock_test_and_set(&g_dump_active, 1)) {
        /* первый: разослать остальным */
        static char dents[8192];
        long fd = cr_sys3(SYS_open, (long)"/proc/self/task", 0x4000 /* O_DIRECTORY */, 0);
        if (fd >= 0) {
            for (;;) {
                long n = cr_sys3(217 /* getdents64 */, fd, (long)dents, sizeof(dents));
                if (n <= 0) break;
                for (long off = 0; off < n;) {
                    unsigned short reclen = *(unsigned short *)(dents + off + 16);
                    const char *nm = dents + off + 19;
                    int tid = 0;
                    for (const char *c = nm; *c >= '0' && *c <= '9'; c++) tid = tid * 10 + (*c - '0');
                    if (tid > 0 && tid != self) cr_sys3(SYS_tgkill, pid, tid, SIGUSR2);
                    off += reclen;
                }
            }
            cr_sys3(SYS_close, fd, 0, 0);
        }
    }
    while (__sync_lock_test_and_set(&g_dump_lock, 1)) { }
    dump_current("стек потока", 0, regs);
    __sync_lock_release(&g_dump_lock);
}

__attribute__((constructor)) static void crash_init(void) {
    struct bsigaction sa;
    sa.handler = (void *)handler;
    sa.mask = 0;
    sa.flags = 4 | 0x10000000; /* SA_SIGINFO | SA_RESTART */
    sa.restorer = 0;
    for (int i = 0; i < 6; i++) sigaction(g_sigs[i], &sa, 0);
    struct bsigaction su = { (void *)usr2_handler, 0, 4 | 0x10000000, 0 };
    /* SIGUSR2 занимаем, только если процесс его ещё не использует */
    struct bsigaction old = { 0, 0, 0, 0 };
    if (sigaction(SIGUSR2, 0, &old) == 0 && old.handler == 0) sigaction(SIGUSR2, &su, 0);
}
