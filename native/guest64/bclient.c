// The binder front end inside a guest process: turns /dev/binder ioctls into messages for binderd64 (bproto.h).
// Included by run64.c; runs inside the SIGSYS handler, so no libc and little stack.

#include "bproto.h"

enum { NR_pwrite64 = 68, NR_sched_yield = 124, NR_getpid = 172, NR_gettid = 178, NR_socket = 198, NR_sendmsg = 211, NR_recvmsg = 212 };

#define BINDER_WRITE_READ 0xc0306201u
#define BINDER_SET_MAX_THREADS 0x40046205u
#define BINDER_SET_CONTEXT_MGR 0x40046207u
#define BINDER_THREAD_EXIT 0x40046208u
#define BINDER_VERSION 0xc0046209u
#define BC_TRANSACTION 0x40406300u
#define BC_REPLY 0x40406301u
#define BC_FREE_BUFFER 0x40086303u
#define BC_AEMU_MAX_THREADS 0x4004633du
#define BC_AEMU_CTX_MGR 0x0000633eu
#define BR_NOOP 0x0000720cu
#define T_FD 0x66642a85u
#define EAGAIN 11
#define EINTR 4
#define EBADF 9

struct bwr { u64 write_size, write_consumed, write_buffer, read_size, read_consumed, read_buffer; };
struct btd { u64 target, cookie; u32 code, flags; int sender_pid; u32 sender_euid; u64 data_size, offsets_size, buffer, offsets; };
struct iov { void *base; u64 len; };
struct msgh { void *name; u32 namelen; struct iov *iov; u64 iovlen; void *control; u64 controllen; int flags; };
struct cmsg { u64 len; int level, type; };

static char g_bsock[108];                       // the daemon's socket (AEMU64_BINDER)
static int g_bpid;                              // the process the state below belongs to
static int g_bfds[4];                           // descriptors open("/dev/binder") returned, +1
#define NTH 509
static struct bth { int tid, bfd, sock; char *scratch; } g_bth[NTH];
#define SCRATCH (INLINE_MAX + 8192)

// buffers of delivered transactions live in the area the guest mapped from the binder descriptor
struct bhdr { u64 size, id; u32 used, big; u64 pad; };
static u64 g_bmap, g_bmap_size;
static volatile int g_block;

static void block(void) { while (__atomic_exchange_n(&g_block, 1, __ATOMIC_ACQUIRE)) S0(NR_sched_yield); }
static void bunlock(void) { __atomic_store_n(&g_block, 0, __ATOMIC_RELEASE); }

static void *balloc(u64 n, u64 id) {
    n = (n + 15) & ~15UL;
    block();
    if (g_bmap) {
        u64 end = g_bmap + g_bmap_size;
        for (struct bhdr *h = (struct bhdr *)g_bmap; (u64)h < end;) {
            struct bhdr *nx = (struct bhdr *)((u64)(h + 1) + h->size);
            if (!h->used) {
                while ((u64)nx < end && !nx->used) { h->size += sizeof *nx + nx->size; nx = (struct bhdr *)((u64)(h + 1) + h->size); }
                if (h->size >= n) {
                    if (h->size >= n + sizeof *h + 64) {
                        struct bhdr *r = (struct bhdr *)((u64)(h + 1) + n);
                        r->size = h->size - n - sizeof *h; r->used = 0; r->big = 0;
                        h->size = n;
                    }
                    h->used = 1; h->big = 0; h->id = id;
                    bunlock();
                    return h + 1;
                }
            }
            h = nx;
        }
    }
    bunlock();
    u64 total = (n + sizeof(struct bhdr) + 4095) & ~4095UL;
    struct bhdr *h = (struct bhdr *)S6(NR_mmap, 0, total, 3, 0x22, -1, 0);
    if ((u64)h >= (u64)-4095) return 0;
    h->size = total; h->used = 1; h->big = 1; h->id = id;
    return h + 1;
}

static u64 bfree(u64 ptr) {
    struct bhdr *h = (struct bhdr *)ptr - 1;
    u64 id = h->id;
    if (h->big) S2(NR_munmap, h, h->size);
    else { block(); h->used = 0; bunlock(); }
    return id;
}

static int is_binder_fd(int fd) {
    for (int i = 0; i < 4; i++) if (g_bfds[i] == fd + 1) return 1;
    return 0;
}

static long bconnect(int main_, int cloexec) {
    long s = S3(NR_socket, 1, 5 /*SOCK_SEQPACKET*/ | (cloexec ? 02000000 : 0), 0);
    if (s < 0) return s;
    struct sockaddr_un sa; sa.family = 1;
    u64 l = slen(g_bsock); memcpy(sa.path, g_bsock, l + 1);
    long r = S3(NR_connect, s, &sa, 2 + l + 1);
    if (r < 0) { S1(NR_close, s); return r; }
    struct c_hello m = { { OP_HELLO, 0, 0, (u32)g_uid }, (u32)S0(NR_getpid), (u32)S0(NR_gettid), (u32)main_, 0 };
    struct iov v = { &m, sizeof m }; struct msgh mh = { 0, 0, &v, 1, 0, 0, 0 };
    S3(NR_sendmsg, s, &mh, 0x4000 /*MSG_NOSIGNAL*/);
    return s;
}

static struct bth *bthread(int bfd, int create) {
    int tid = (int)S0(NR_gettid);
    u32 i = (u32)tid % NTH, j = i;
    for (int n = 0; n < NTH; n++, i = (i + 1) % NTH) {
        struct bth *t = &g_bth[i];
        if (t->tid == tid && t->bfd == bfd) return t;
        if (t->tid == 0) break;
    }
    if (!create) return 0;
    for (int n = 0; n < NTH; n++, j = (j + 1) % NTH) {      // a free slot, or one left by a thread that exited
        struct bth *t = &g_bth[j];
        int z = t->tid;
        if ((z == 0 || z == -1) && __atomic_compare_exchange_n(&t->tid, &z, tid, 0, __ATOMIC_ACQ_REL, __ATOMIC_RELAXED)) {
            t->bfd = bfd; t->sock = 0;
            return t;
        }
    }
    return 0;
}

static long binder_open(long flags) {
    if (!g_bsock[0]) return -ENOENT;
    int pid = (int)S0(NR_getpid);
    if (g_bpid != pid) {                        // a fresh process (or a fork child): nothing is inherited
        memset(g_bth, 0, sizeof g_bth); memset(g_bfds, 0, sizeof g_bfds);
        g_bmap = 0; g_block = 0; g_bpid = pid;
    }
    long s = bconnect(1, (flags & 02000000) != 0);
    if (s < 0) return s;
    for (int i = 0; i < 4; i++) if (!g_bfds[i]) { g_bfds[i] = (int)s + 1; break; }
    struct bth *t = bthread((int)s, 1);
    if (t) t->sock = (int)s;
    return s;
}

static long binder_mmap(u64 len) {
    u64 m = (u64)S6(NR_mmap, 0, len, 3, 0x22, -1, 0);
    if (m >= (u64)-4095) return (long)m;
    block();
    struct bhdr *h = (struct bhdr *)m; h->size = len - sizeof *h; h->used = 0; h->big = 0;
    g_bmap = m; g_bmap_size = len;
    bunlock();
    return (long)m;
}

static long bsend(int sock, void *a, u64 an, void *b, u64 bn, void *c, u64 cn, int *fds, int nfds) {
    struct iov v[4]; int n = 0; static const u64 zero;
    struct { struct cmsg h; int fd[254]; } cm;
    v[n].base = a; v[n++].len = an;
    if (bn) { v[n].base = b; v[n++].len = bn; if (cn && (bn & 7)) { v[n].base = (void *)&zero; v[n++].len = 8 - (bn & 7); } }
    if (cn) { v[n].base = c; v[n++].len = cn; }
    struct msgh mh = { 0, 0, v, (u64)n, 0, 0, 0 };
    if (nfds) {
        cm.h.len = sizeof cm.h + (u64)nfds * 4; cm.h.level = 1; cm.h.type = 1;
        memcpy(cm.fd, fds, (u64)nfds * 4);
        mh.control = &cm; mh.controllen = (sizeof cm.h + (u64)nfds * 4 + 7) & ~7UL;
    }
    long r;
    do r = S3(NR_sendmsg, sock, &mh, 0x4000); while (r == -EINTR);
    return r;
}

static long btxn(struct bth *t, u32 cmd, struct btd *d, u32 flags, u32 read_size) {
    struct c_txn m = { { OP_TXN, flags, read_size, (u32)g_uid }, cmd, (u32)d->target, d->code, d->flags, d->data_size, d->offsets_size };
    int fds[254], nfds = 0;
    u64 *offs = (u64 *)d->offsets;
    for (u64 i = 0; i < d->offsets_size / 8; i++) {
        if (offs[i] + 24 > d->data_size) continue;
        u32 *o = (u32 *)(d->buffer + offs[i]);
        if (o[0] == T_FD && nfds < 252) fds[1 + nfds++] = (int)o[2];
    }
    u64 total = ((d->data_size + 7) & ~7UL) + d->offsets_size;
    if (total > INLINE_MAX) {
        long mf = S2(NR_memfd_create, "aemu-binder", 1);
        if (mf < 0) return mf;
        S4(NR_pwrite64, mf, d->buffer, d->data_size, 0);
        S4(NR_pwrite64, mf, d->offsets, d->offsets_size, (d->data_size + 7) & ~7UL);
        m.h.flags |= F_BIG; fds[0] = (int)mf;
        long r = bsend(t->sock, &m, sizeof m, 0, 0, 0, 0, fds, nfds + 1);
        S1(NR_close, mf);
        return r;
    }
    return bsend(t->sock, &m, sizeof m, (void *)d->buffer, d->data_size, (void *)d->offsets, d->offsets_size, fds + 1, nfds);
}

static long bread(struct bth *t, struct bwr *w) {
    if (!t->scratch) {
        u64 m = (u64)S6(NR_mmap, 0, SCRATCH, 3, 0x22, -1, 0);
        if (m >= (u64)-4095) return -ENOMEM;
        t->scratch = (char *)m;
    }
    struct d_hdr h;
    struct { struct cmsg h; int fd[254]; } cm;
    struct iov v[2] = { { &h, sizeof h }, { t->scratch, SCRATCH } };
    struct msgh mh = { 0, 0, v, 2, &cm, sizeof cm, 0 };
    long r;
    do r = S3(NR_recvmsg, t->sock, &mh, 0x40000000 /*MSG_CMSG_CLOEXEC*/); while (r == -EINTR);
    if (r < (long)sizeof h) return r < 0 ? r : -EBADF;
    if (h.flags & F_AGAIN) return -EAGAIN;
    int nfds = 0, *fds = cm.fd;
    if (mh.controllen >= sizeof cm.h && cm.h.level == 1 && cm.h.type == 1) nfds = (int)((cm.h.len - sizeof cm.h) / 4);
    u32 *out = (u32 *)(w->read_buffer + w->read_consumed);
    *out++ = BR_NOOP;
    memcpy(out, t->scratch, h.br_size); out = (u32 *)((char *)out + h.br_size);
    if (h.flags & F_TXN) {
        u64 dsz = (h.data_size + 7) & ~7UL, total = dsz + h.offsets_size;
        char *buf = balloc(total ? total : 8, h.id);
        if (!buf) return -ENOMEM;
        if (h.flags & F_BIG) {
            u64 got = 0;
            while (got < total) { long k = S4(NR_pread64, fds[0], buf + got, total - got, got); if (k <= 0) break; got += (u64)k; }
            S1(NR_close, fds[0]); fds++; nfds--;
        } else memcpy(buf, t->scratch + h.br_size, total);
        u64 *offs = (u64 *)(buf + dsz); int k = 0;
        for (u64 i = 0; i < h.offsets_size / 8; i++) {
            u32 *o = (u32 *)(buf + offs[i]);
            if (o[0] == T_FD) { o[2] = k < nfds ? (u32)fds[k] : (u32)-1; o[3] = 0; k++; }
        }
        struct btd d = { h.ptr, h.cookie, h.code, h.tflags, h.sender_pid, h.sender_euid, h.data_size, h.offsets_size, (u64)buf, (u64)offs };
        *out++ = h.cmd; memcpy(out, &d, sizeof d); out = (u32 *)((char *)out + sizeof d);
    } else for (int i = 0; i < nfds; i++) S1(NR_close, fds[i]);
    w->read_consumed = (u64)((char *)out - (char *)w->read_buffer);
    return 0;
}

static long bwrite_read(struct bth *t, struct bwr *w) {
    u32 small[256]; u32 ns = 0;
    struct c_hdr sh = { OP_BCS, 0, (u32)w->read_size, (u32)g_uid };
    int want = w->read_size > w->read_consumed, asked = 0;
    char *p = (char *)w->write_buffer + w->write_consumed, *end = (char *)w->write_buffer + w->write_size;
    long r = 0;
    while (p + 4 <= end) {
        u32 cmd = *(u32 *)p, sz = (cmd >> 16) & 0x3fff;
        if (p + 4 + sz > end) break;
        if (cmd == BC_TRANSACTION || cmd == BC_REPLY) {
            if (ns) { if ((r = bsend(t->sock, &sh, sizeof sh, small, ns * 4, 0, 0, 0, 0)) < 0) return r; ns = 0; }
            int last = p + 4 + sz >= end && want;
            if ((r = btxn(t, cmd, (struct btd *)(p + 4), last ? F_READ : 0, (u32)w->read_size)) < 0) return r;
            asked = last;
        } else {
            if (ns + 8 > 256) { if ((r = bsend(t->sock, &sh, sizeof sh, small, ns * 4, 0, 0, 0, 0)) < 0) return r; ns = 0; }
            if (cmd == BC_FREE_BUFFER) {
                u64 ptr; memcpy(&ptr, p + 4, 8);
                u64 id = bfree(ptr);
                small[ns++] = BC_AEMU_FREE; memcpy(small + ns, &id, 8); ns += 2;
            } else if (sz <= 16) {
                small[ns++] = cmd; memcpy(small + ns, p + 4, sz); ns += (sz + 3) / 4;
            }
            asked = 0;
        }
        p += 4 + sz;
    }
    w->write_consumed = w->write_size;
    if (ns) {
        if (want) sh.flags = F_READ;
        if ((r = bsend(t->sock, &sh, sizeof sh, small, ns * 4, 0, 0, 0, 0)) < 0) return r;
        asked = want;
    }
    if (!want) return 0;
    if (!asked) {
        struct c_hdr rh = { OP_READ, F_READ, (u32)w->read_size, (u32)g_uid };
        if ((r = bsend(t->sock, &rh, sizeof rh, 0, 0, 0, 0, 0, 0)) < 0) return r;
    }
    return bread(t, w);
}

static long binder_ioctl(int fd, u32 cmd, u64 arg) {
    struct bth *t = bthread(fd, 1);
    if (!t) return -ENOMEM;
    if (!t->sock) {
        long s = bconnect(0, 1);
        if (s < 0) { t->tid = 0; return s; }
        t->sock = (int)s;
    }
    switch (cmd) {
    case BINDER_WRITE_READ: return bwrite_read(t, (struct bwr *)arg);
    case BINDER_VERSION: *(int *)arg = 8; return 0;
    case BINDER_SET_MAX_THREADS: case BINDER_SET_CONTEXT_MGR: {
        struct { struct c_hdr h; u32 c, v; } m = { { OP_BCS, 0, 0, (u32)g_uid }, cmd == BINDER_SET_MAX_THREADS ? BC_AEMU_MAX_THREADS : BC_AEMU_CTX_MGR, arg ? *(u32 *)arg : 0 };
        return bsend(t->sock, &m, cmd == BINDER_SET_MAX_THREADS ? sizeof m : sizeof m - 4, 0, 0, 0, 0, 0, 0) < 0 ? -EBADF : 0;
    }
    case BINDER_THREAD_EXIT:
        if (t->sock != fd) S1(NR_close, t->sock);
        t->sock = 0; t->bfd = -1; t->tid = -1;      // the slot stays taken: probing must not stop here
        return 0;
    }
    return -EINVAL;
}
