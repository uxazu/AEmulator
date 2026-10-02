// binderd64 — the binder driver for natively running guests, in user space.
//
//   binderd64 <socket path>
//
// Every guest thread talks to it over its own SOCK_SEQPACKET connection (see bproto.h); the daemon keeps what the
// kernel driver keeps: nodes, references, transaction stacks, death notifications, looper bookkeeping. It is
// single-threaded: one epoll loop, no locks. A process dies when its main connection closes.

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include "bproto.h"

typedef uint64_t u64;
typedef uint32_t u32;

#define BC_TRANSACTION 0x40406300u
#define BC_REPLY 0x40406301u
#define BC_FREE_BUFFER 0x40086303u
#define BC_INCREFS 0x40046304u
#define BC_ACQUIRE 0x40046305u
#define BC_RELEASE 0x40046306u
#define BC_DECREFS 0x40046307u
#define BC_INCREFS_DONE 0x40106308u
#define BC_ACQUIRE_DONE 0x40106309u
#define BC_REGISTER_LOOPER 0x0000630bu
#define BC_ENTER_LOOPER 0x0000630cu
#define BC_EXIT_LOOPER 0x0000630du
#define BC_REQUEST_DEATH_NOTIFICATION 0x400c630eu
#define BC_CLEAR_DEATH_NOTIFICATION 0x400c630fu
#define BC_DEAD_BINDER_DONE 0x40086310u
/* private: ioctls the front end forwards */
#define BC_AEMU_MAX_THREADS 0x4004633du
#define BC_AEMU_CTX_MGR 0x0000633eu

#define BR_TRANSACTION 0x80407202u
#define BR_REPLY 0x80407203u
#define BR_DEAD_REPLY 0x00007205u
#define BR_TRANSACTION_COMPLETE 0x00007206u
#define BR_INCREFS 0x80107207u
#define BR_ACQUIRE 0x80107208u
#define BR_RELEASE 0x80107209u
#define BR_DECREFS 0x8010720au
#define BR_SPAWN_LOOPER 0x0000720du
#define BR_DEAD_BINDER 0x8008720fu
#define BR_CLEAR_DEATH_NOTIFICATION_DONE 0x80087210u
#define BR_FAILED_REPLY 0x00007211u

#define T_BINDER 0x73622a85u
#define T_WEAK_BINDER 0x77622a85u
#define T_HANDLE 0x73682a85u
#define T_WEAK_HANDLE 0x77682a85u
#define T_FD 0x66642a85u
#define TF_ONE_WAY 1

struct fbo { u32 type, flags; u64 binder, cookie; };   /* flat_binder_object, 64-bit; handle = low word of binder */

struct proc; struct thread; struct node; struct txn;

enum { W_TXN = 1, W_COMPLETE, W_NODE, W_DEAD_BINDER, W_CLEAR_DONE, W_ERROR };
struct work { struct work *next; int type, deferred; struct txn *t; struct node *node; u64 cookie; u32 err; };
struct wq { struct work *head, *tail; };

struct ref { struct proc *proc; u32 desc; struct node *node; int strong, weak, has_death; u64 death; struct ref *next_in_node; };
struct node {
    struct proc *owner; u64 ptr, cookie; int internal_strong, local_strong, local_weak, nrefs, has_strong, has_weak, queued, has_async;
    int pend_strong, pend_weak;     /* BR_ACQUIRE / BR_INCREFS sent, not yet confirmed: the node keeps a reference of its own until then */
    struct ref *refs; struct wq async; struct node *hnext;
};
struct held { struct ref *ref; struct node *node; int strong; };      /* what a transaction buffer keeps alive */
struct dbuf { struct dbuf *prev, *next; struct proc *proc; struct node *async_node; int nheld; struct held *held; };
struct txn {
    struct thread *from, *to_thread; struct proc *to_proc; struct txn *from_parent, *to_parent;
    struct node *target; u32 code, flags; int reply, need_reply, sender_pid; u32 sender_euid;
    u64 data_size, offsets_size; char *data; int memfd; int nfds, *fds; int nheld; struct held *held;
};
struct thread {
    int fd, tid, is_main, waiting, nonblock, looper, woken; u32 read_size;
    struct proc *proc; struct wq todo; struct txn *stack; struct thread *next;
};
#define NHASH 256
struct proc {
    int pid, max_threads, requested, started, kick; u32 key; struct thread *threads; struct wq todo;
    struct ref **refs; u32 nrefs; struct node *nodes[NHASH]; struct dbuf *bufs; struct proc *next;
};

static struct proc *procs;
static struct node *ctx_mgr;
/* the thread whose message is being handled: reference changes of its own objects are reported to it first,
 * before it can drop the object it has just sent (the driver does the same) */
static struct thread *cur;
static int kick_any;
static int ep, verbose;

static void logf_(const char *fmt, ...) {
    va_list ap; va_start(ap, fmt); vfprintf(stderr, fmt, ap); va_end(ap); fputc('\n', stderr);
}
#define V(...) do { if (verbose) logf_(__VA_ARGS__); } while (0)

static void *zalloc(size_t n) { void *p = calloc(1, n); if (!p) { logf_("binderd64: out of memory"); exit(1); } return p; }

static void wq_push(struct wq *q, struct work *w) { w->next = 0; if (q->tail) q->tail->next = w; else q->head = w; q->tail = w; }
static struct work *wq_pop(struct wq *q) { struct work *w = q->head; if (w) { q->head = w->next; if (!q->head) q->tail = 0; } return w; }
static struct work *mkwork(int type) { struct work *w = zalloc(sizeof *w); w->type = type; return w; }

static void deliver(struct thread *th);
static void kick_proc(struct proc *p);

// ------------------------------------------------------------------------------------------- nodes and refs

static void node_work(struct node *n) {
    if (!n->owner || n->queued) return;
    int strong = n->internal_strong || n->local_strong, weak = n->nrefs || n->local_weak || strong;
    if ((weak && !n->has_weak) || (strong && !n->has_strong) || (!strong && n->has_strong) || (!weak && n->has_weak)) {
        struct work *w = mkwork(W_NODE); w->node = n; n->queued = 1;
        if (cur && cur->proc == n->owner) wq_push(&cur->todo, w);
        else { wq_push(&n->owner->todo, w); n->owner->kick = 1; kick_any = 1; }   /* delivered once the caller is done with the node */
    }
}

static void node_free_if_unused(struct node *n) {
    if (n->nrefs || n->internal_strong || n->local_strong || n->local_weak || n->queued || n->has_strong || n->has_weak) {
        if (n->owner) return;
        if (n->nrefs || n->local_strong || n->queued) return;     /* a dead node lives as long as someone refers to it */
    }
    if (n->owner) {
        struct node **pp = &n->owner->nodes[(n->ptr >> 4) % NHASH];
        while (*pp && *pp != n) pp = &(*pp)->hnext;
        if (*pp) *pp = n->hnext;
    }
    if (ctx_mgr == n) ctx_mgr = 0;
    for (struct work *w; (w = wq_pop(&n->async));) free(w);   /* the transactions themselves are released by the caller */
    free(n);
}

static struct node *node_get(struct proc *p, u64 ptr, u64 cookie, int create) {
    struct node **b = &p->nodes[(ptr >> 4) % NHASH];
    for (struct node *n = *b; n; n = n->hnext) if (n->ptr == ptr) {
        if (create && n->cookie != cookie) { V("  node %d ptr %llx: cookie %llx -> %llx", p->pid, (unsigned long long)ptr, (unsigned long long)n->cookie, (unsigned long long)cookie); n->cookie = cookie; }
        return n;
    }
    if (!create) return 0;
    struct node *n = zalloc(sizeof *n);
    n->owner = p; n->ptr = ptr; n->cookie = cookie; n->hnext = *b; *b = n;
    V("  new node %d ptr %llx cookie %llx", p->pid, (unsigned long long)ptr, (unsigned long long)cookie);
    return n;
}

static struct ref *ref_for_node(struct proc *p, struct node *n) {
    for (struct ref *r = n->refs; r; r = r->next_in_node) if (r->proc == p) return r;
    u32 d = n == ctx_mgr ? 0 : 1;
    for (; d < p->nrefs; d++) if (!p->refs[d] && (d || n == ctx_mgr)) break;
    if (d >= p->nrefs) {
        u32 nn = p->nrefs ? p->nrefs * 2 : 64; while (nn <= d) nn *= 2;
        p->refs = realloc(p->refs, nn * sizeof *p->refs);
        memset(p->refs + p->nrefs, 0, (nn - p->nrefs) * sizeof *p->refs);
        p->nrefs = nn;
    }
    struct ref *r = zalloc(sizeof *r);
    r->proc = p; r->desc = d; r->node = n; r->next_in_node = n->refs; n->refs = r; n->nrefs++;
    p->refs[d] = r;
    node_work(n);
    return r;
}

static struct ref *ref_get(struct proc *p, u32 desc) { return desc < p->nrefs ? p->refs[desc] : 0; }

static void ref_delete(struct ref *r) {
    struct node *n = r->node;
    struct ref **pp = &n->refs; while (*pp && *pp != r) pp = &(*pp)->next_in_node;
    if (*pp) *pp = r->next_in_node;
    n->nrefs--;
    if (r->strong) n->internal_strong--;
    r->proc->refs[r->desc] = 0;
    free(r);
    node_work(n);
    node_free_if_unused(n);
}

static void ref_inc(struct ref *r, int strong) {
    if (strong) { if (r->strong++ == 0) { r->node->internal_strong++; node_work(r->node); } }
    else r->weak++;
}

static void ref_dec(struct ref *r, int strong) {
    if (strong) {
        if (r->strong <= 0) return;
        if (--r->strong == 0) { r->node->internal_strong--; node_work(r->node); }
    } else { if (r->weak <= 0) return; r->weak--; }
    if (r->strong == 0 && r->weak == 0) ref_delete(r);
}

static void held_release(struct held *h, int n) {
    for (int i = 0; i < n; i++) {
        if (h[i].ref) ref_dec(h[i].ref, h[i].strong);
        else if (h[i].node) { h[i].node->local_strong--; node_work(h[i].node); node_free_if_unused(h[i].node); }
    }
    free(h);
}


// ------------------------------------------------------------------------------------------- transactions

static void txn_free(struct txn *t) {
    if (t->held) held_release(t->held, t->nheld);
    for (int i = 0; i < t->nfds; i++) close(t->fds[i]);
    free(t->fds);
    if (t->memfd >= 0) close(t->memfd);
    free(t->data);
    free(t);
}

static void thread_error(struct thread *th, u32 err) {
    struct work *w = mkwork(W_ERROR); w->err = err;
    wq_push(&th->todo, w);
    deliver(th);
}

/* the caller of t will never get an answer */
static void txn_fail(struct txn *t, u32 err) {
    struct thread *from = t->from;
    if (from && from->stack == t) { from->stack = t->from_parent; thread_error(from, err); }
    txn_free(t);
}

static int translate(struct thread *th, struct proc *to, struct txn *t, char *data) {
    u64 n = t->offsets_size / 8, *offs = (u64 *)(data + ((t->data_size + 7) & ~7UL));
    struct proc *p = th->proc;
    t->held = zalloc((n + 2) * sizeof *t->held);
    int fdi = 0;
    for (u64 i = 0; i < n; i++) {
        if (offs[i] + sizeof(struct fbo) > t->data_size) return -1;
        struct fbo *o = (struct fbo *)(data + offs[i]);
        struct held *h = &t->held[t->nheld];
        switch (o->type) {
        case T_BINDER: case T_WEAK_BINDER: {
            struct node *nd = node_get(p, o->binder, o->cookie, 1);
            struct ref *r = ref_for_node(to, nd);
            int strong = o->type == T_BINDER;
            o->type = strong ? T_HANDLE : T_WEAK_HANDLE; o->binder = r->desc; o->cookie = 0;
            ref_inc(r, strong); h->ref = r; h->strong = strong; t->nheld++;
            break;
        }
        case T_HANDLE: case T_WEAK_HANDLE: {
            struct ref *r = ref_get(p, (u32)o->binder);
            int strong = o->type == T_HANDLE;
            if (!r) return -1;
            if (r->node->owner == to) {
                o->type = strong ? T_BINDER : T_WEAK_BINDER; o->binder = r->node->ptr; o->cookie = r->node->cookie;
                if (strong) { r->node->local_strong++; node_work(r->node); h->node = r->node; h->strong = 1; t->nheld++; }
            } else {
                struct ref *nr = ref_for_node(to, r->node);
                o->binder = nr->desc; o->cookie = 0;
                ref_inc(nr, strong); h->ref = nr; h->strong = strong; t->nheld++;
            }
            break;
        }
        case T_FD:
            if (fdi >= t->nfds) return -1;
            fdi++;
            break;
        default: return -1;
        }
    }
    return 0;
}

static struct thread *ready_thread(struct proc *p) {
    for (struct thread *th = p->threads; th; th = th->next)
        if (th->waiting && !th->stack && !th->todo.head) return th;
    return 0;
}

static void kick_proc(struct proc *p) {
    struct thread *th;
    while (p->todo.head && (th = ready_thread(p))) deliver(th);
    if (p->todo.head) for (th = p->threads; th; th = th->next) if (!th->waiting) deliver(th);
}

static void queue_txn(struct txn *t, struct thread *target) {
    struct work *w = mkwork(W_TXN); w->t = t;
    if (target) { wq_push(&target->todo, w); deliver(target); }
    else { wq_push(&t->to_proc->todo, w); kick_proc(t->to_proc); }
}

static void do_txn(struct thread *th, struct c_txn *m, char *payload, int *fds, int nfds) {
    struct proc *p = th->proc;
    struct txn *t = zalloc(sizeof *t);
    int reply = m->cmd == BC_REPLY;
    t->memfd = -1; t->code = m->code; t->flags = m->tflags; t->reply = reply;
    t->sender_pid = p->pid; t->sender_euid = m->h.euid;
    t->data_size = m->data_size; t->offsets_size = m->offsets_size;
    u64 total = ((t->data_size + 7) & ~7UL) + t->offsets_size;
    char *data;
    if (m->h.flags & F_BIG) {
        if (nfds < 1) { free(t); thread_error(th, BR_FAILED_REPLY); return; }
        t->memfd = fds[0]; fds++; nfds--;
        data = mmap(0, total ? total : 1, PROT_READ | PROT_WRITE, MAP_SHARED, t->memfd, 0);
        if (data == MAP_FAILED) { close(t->memfd); for (int i = 0; i < nfds; i++) close(fds[i]); free(t); thread_error(th, BR_FAILED_REPLY); return; }
    } else {
        data = t->data = malloc(total + 8);
        memcpy(data, payload, total);
    }
    if (nfds) { t->fds = malloc(nfds * sizeof(int)); memcpy(t->fds, fds, nfds * sizeof(int)); t->nfds = nfds; }

    struct thread *target_thread = 0; struct proc *to = 0; struct txn *in_reply_to = 0;
    u32 err = BR_FAILED_REPLY;
    if (reply) {
        in_reply_to = th->stack;
        if (!in_reply_to || in_reply_to->to_thread != th) goto fail;
        th->stack = in_reply_to->to_parent;
        target_thread = in_reply_to->from;
        if (!target_thread) { err = BR_DEAD_REPLY; in_reply_to->from = 0; txn_free(in_reply_to); in_reply_to = 0; goto fail; }
        to = target_thread->proc;
    } else {
        struct node *n;
        if (m->handle) { struct ref *r = ref_get(p, m->handle); n = r ? r->node : 0; }
        else n = ctx_mgr;
        if (!n) goto fail;
        if (!n->owner) { err = BR_DEAD_REPLY; goto fail; }
        t->target = n; to = n->owner;
        if (!(t->flags & TF_ONE_WAY) && th->stack) {
            for (struct txn *x = th->stack; x; x = x->from_parent)
                if (x->from && x->from->proc == to) target_thread = x->from;
        }
    }
    t->to_proc = to;
    if (translate(th, to, t, data) < 0) { if (t->memfd >= 0) munmap(data, total ? total : 1); goto fail; }
    if (t->memfd >= 0) munmap(data, total ? total : 1);
    if (t->target) {                        /* the target stays alive until the buffer is freed */
        t->target->local_strong++; node_work(t->target);
        t->held[t->nheld].node = t->target; t->held[t->nheld++].strong = 1;
    }

    struct work *done = mkwork(W_COMPLETE);
    if (reply) {
        target_thread->stack = in_reply_to->from_parent;
        in_reply_to->from = 0; txn_free(in_reply_to);
        wq_push(&th->todo, done);
        queue_txn(t, target_thread);
    } else if (!(t->flags & TF_ONE_WAY)) {
        t->need_reply = 1; t->from = th; t->from_parent = th->stack; th->stack = t;
        done->deferred = 1;                 /* goes out together with the reply or a nested call */
        wq_push(&th->todo, done);
        queue_txn(t, target_thread);
    } else {
        wq_push(&th->todo, done);
        if (t->target->has_async) { struct work *w = mkwork(W_TXN); w->t = t; wq_push(&t->target->async, w); }
        else { t->target->has_async = 1; queue_txn(t, 0); }
    }
    return;
fail:
    V("txn failed: pid %d handle %u code %u err %x", p->pid, m->handle, m->code, err);
    txn_free(t);
    if (reply && in_reply_to) { th->stack = in_reply_to->to_parent; txn_fail(in_reply_to, BR_FAILED_REPLY); }
    thread_error(th, err);
}

// ------------------------------------------------------------------------------------------- delivery

static int emit_node(struct node *n, u32 *br) {
    int k = 0, strong = n->internal_strong || n->local_strong, weak = n->nrefs || n->local_weak || strong;
#define PUT(c) do { V("  node %d ptr %llx cookie %llx: %x", n->owner ? n->owner->pid : 0, (unsigned long long)n->ptr, (unsigned long long)n->cookie, (unsigned)c); br[k] = c; memcpy(br + k + 1, &n->ptr, 8); memcpy(br + k + 3, &n->cookie, 8); k += 5; } while (0)
    /* the owner must not be told to drop a reference before it has taken it: until BC_*_DONE the node holds one itself */
    if (weak && !n->has_weak) { PUT(BR_INCREFS); n->has_weak = 1; n->pend_weak = 1; n->local_weak++; }
    if (strong && !n->has_strong) { PUT(BR_ACQUIRE); n->has_strong = 1; n->pend_strong = 1; n->local_strong++; }
    if (!strong && n->has_strong) { PUT(BR_RELEASE); n->has_strong = 0; }
    if (!weak && n->has_weak) { PUT(BR_DECREFS); n->has_weak = 0; }
#undef PUT
    return k;
}

static int deliverable(struct thread *th) {
    for (struct work *w = th->todo.head; w; w = w->next) if (!w->deferred) return 1;
    return !th->stack && !th->todo.head && th->proc->todo.head;
}

static void deliver(struct thread *th) {
    if (!th->waiting) {
        /* a looper that polls the descriptor instead of sitting in read (healthd): one empty message wakes it */
        if ((th->looper & 2) && !th->woken && deliverable(th)) {
            struct d_hdr h = { 0 }; h.flags = F_WAKE; th->woken = 1;
            send(th->fd, &h, sizeof h, MSG_DONTWAIT | MSG_NOSIGNAL);
        }
        return;
    }
    if (!deliverable(th)) {
        if (!th->nonblock) return;
        if (th->todo.head) { for (struct work *w = th->todo.head; w; w = w->next) w->deferred = 0; }
        else { struct d_hdr h = { 0 }; h.flags = F_AGAIN; th->waiting = 0; send(th->fd, &h, sizeof h, MSG_DONTWAIT | MSG_NOSIGNAL); return; }
    }
    struct proc *p = th->proc;
    u32 br[64]; int k = 0;
    int room = (int)(th->read_size / 4) - 1 - 17 - 1;     /* BR_NOOP, one transaction, BR_SPAWN_LOOPER */
    if (room < 20) room = 20;
    if (room > 40) room = 40;
    struct txn *t = 0;
    struct wq *q = th->todo.head ? &th->todo : &p->todo;
    while (q->head && !t) {
        struct work *w = q->head;
        if (w->type != W_TXN && k + 20 > room) break;
        wq_pop(q);
        switch (w->type) {
        case W_COMPLETE: br[k++] = BR_TRANSACTION_COMPLETE; break;
        case W_ERROR: br[k++] = w->err; break;
        case W_NODE: w->node->queued = 0; k += emit_node(w->node, br + k); node_free_if_unused(w->node); break;
        case W_DEAD_BINDER: br[k] = BR_DEAD_BINDER; memcpy(br + k + 1, &w->cookie, 8); k += 3; break;
        case W_CLEAR_DONE: br[k] = BR_CLEAR_DEATH_NOTIFICATION_DONE; memcpy(br + k + 1, &w->cookie, 8); k += 3; break;
        case W_TXN: t = w->t; break;
        }
        free(w);
    }
    th->waiting = 0;
    if ((th->looper & 3) && !p->requested && p->started < p->max_threads && !ready_thread(p)) {
        p->requested++; memmove(br + 1, br, k * 4); br[0] = BR_SPAWN_LOOPER; k++;
    }
    struct d_hdr h = { 0 };
    struct iovec iov[3]; int ni = 0;
    char cbuf[CMSG_SPACE(sizeof(int) * 254)];
    struct msghdr mh = { 0 };
    int pass[254], np = 0;
    h.br_size = (u32)k * 4;
    iov[ni].iov_base = &h; iov[ni++].iov_len = sizeof h;
    if (k) { iov[ni].iov_base = br; iov[ni++].iov_len = h.br_size; }
    if (t) {
        struct dbuf *b = zalloc(sizeof *b);
        b->proc = p; b->held = t->held; b->nheld = t->nheld; t->held = 0; t->nheld = 0;
        b->next = p->bufs; if (p->bufs) p->bufs->prev = b; p->bufs = b;
        h.flags |= F_TXN; h.cmd = t->reply ? BR_REPLY : BR_TRANSACTION;
        h.code = t->code; h.tflags = t->flags; h.sender_pid = t->from || t->reply ? t->sender_pid : 0; h.sender_euid = t->sender_euid;
        if (t->target) { h.ptr = t->target->ptr; h.cookie = t->target->cookie; }
        h.data_size = t->data_size; h.offsets_size = t->offsets_size; h.id = (u64)(uintptr_t)b;
        if (t->memfd >= 0) { h.flags |= F_BIG; pass[np++] = t->memfd; }
        else if (t->data_size || t->offsets_size) { iov[ni].iov_base = t->data; iov[ni++].iov_len = ((t->data_size + 7) & ~7UL) + t->offsets_size; }
        for (int i = 0; i < t->nfds && np < 254; i++) pass[np++] = t->fds[i];
        h.nfds = (u32)t->nfds;
        if (!t->reply && (t->flags & TF_ONE_WAY)) b->async_node = t->target;
    }
    mh.msg_iov = iov; mh.msg_iovlen = ni;
    if (np) {
        mh.msg_control = cbuf; mh.msg_controllen = CMSG_SPACE(sizeof(int) * np);
        struct cmsghdr *c = CMSG_FIRSTHDR(&mh);
        c->cmsg_level = SOL_SOCKET; c->cmsg_type = SCM_RIGHTS; c->cmsg_len = CMSG_LEN(sizeof(int) * np);
        memcpy(CMSG_DATA(c), pass, sizeof(int) * np);
    }
    V("  -> %d/%d: br %u words%s%s", p->pid, th->tid, (u32)k, t ? (t->reply ? " + reply" : " + transaction") : "", np ? " + fds" : "");
    if (sendmsg(th->fd, &mh, MSG_DONTWAIT | MSG_NOSIGNAL) < 0) V("send to %d/%d failed: %s", p->pid, th->tid, strerror(errno));
    if (t) {
        if (t->need_reply) { t->to_parent = th->stack; t->to_thread = th; th->stack = t;
            for (int i = 0; i < t->nfds; i++) close(t->fds[i]);
            free(t->fds); t->fds = 0; t->nfds = 0;
            if (t->memfd >= 0) { close(t->memfd); t->memfd = -1; }
            free(t->data); t->data = 0;
        } else txn_free(t);
    }
}

// ------------------------------------------------------------------------------------------- commands

static void buf_free(struct proc *p, struct dbuf *b) {
    if (b->prev) b->prev->next = b->next; else p->bufs = b->next;
    if (b->next) b->next->prev = b->prev;
    if (b->async_node) {
        struct node *n = b->async_node;
        struct work *w = wq_pop(&n->async);
        if (w) { if (n->owner) { wq_push(&n->owner->todo, w); kick_proc(n->owner); } else { txn_free(w->t); free(w); } }
        else n->has_async = 0;
    }
    held_release(b->held, b->nheld);
    free(b);
}

static void do_bcs(struct thread *th, u32 *c, int words) {
    struct proc *p = th->proc;
    for (int i = 0; i < words;) {
        u32 cmd = c[i++]; u64 a = 0, b = 0;
        int sz = (int)((cmd >> 16) & 0x3fff);
        if (i * 4 + sz > words * 4) break;
        if (sz == 4) a = c[i];
        else if (sz == 12) { a = c[i]; memcpy(&b, c + i + 1, 8); }       /* handle + cookie, packed */
        else if (sz >= 8) { memcpy(&a, c + i, 8); if (sz == 16) memcpy(&b, c + i + 2, 8); }
        i += (sz + 3) / 4;
        switch (cmd) {
        case BC_INCREFS: case BC_ACQUIRE: {
            struct ref *r = ref_get(p, (u32)a);
            if (!r && (u32)a == 0 && ctx_mgr) r = ref_for_node(p, ctx_mgr);
            /* a strong reference cannot be made out of a weak one once the object has no strong references left
             * (its owner may have destroyed it); the driver refuses this the same way */
            if (r && cmd == BC_ACQUIRE && !r->strong && !r->node->internal_strong && r->node != ctx_mgr) { V("pid %d: acquire of a released node refused", p->pid); break; }
            if (r) ref_inc(r, cmd == BC_ACQUIRE);
            break;
        }
        case BC_RELEASE: case BC_DECREFS: { struct ref *r = ref_get(p, (u32)a); if (r) ref_dec(r, cmd == BC_RELEASE); break; }
        case BC_INCREFS_DONE: case BC_ACQUIRE_DONE: {
            struct node *n = node_get(p, a, b, 0);
            if (!n) break;
            if (cmd == BC_ACQUIRE_DONE && n->pend_strong) { n->pend_strong = 0; n->local_strong--; }
            if (cmd == BC_INCREFS_DONE && n->pend_weak) { n->pend_weak = 0; n->local_weak--; }
            node_work(n); node_free_if_unused(n);
            break;
        }
        case BC_REGISTER_LOOPER: th->looper |= 1; if (p->requested) { p->requested--; p->started++; } break;
        case BC_ENTER_LOOPER: th->looper |= 2; break;
        case BC_EXIT_LOOPER: th->looper |= 4; break;
        case BC_REQUEST_DEATH_NOTIFICATION: {
            struct ref *r = ref_get(p, (u32)a);
            if (!r) break;
            r->has_death = 1; r->death = b;
            if (!r->node->owner) { struct work *w = mkwork(W_DEAD_BINDER); w->cookie = b; wq_push(&p->todo, w); kick_proc(p); }
            break;
        }
        case BC_CLEAR_DEATH_NOTIFICATION: {
            struct ref *r = ref_get(p, (u32)a);
            if (r) r->has_death = 0;
            struct work *w = mkwork(W_CLEAR_DONE); w->cookie = b; wq_push(&th->todo, w);
            break;
        }
        case BC_DEAD_BINDER_DONE: break;
        case BC_AEMU_FREE: { struct dbuf *d = (struct dbuf *)(uintptr_t)a; if (d && d->proc == p) buf_free(p, d); break; }
        case BC_AEMU_MAX_THREADS: p->max_threads = (int)a; break;
        case BC_AEMU_CTX_MGR: if (!ctx_mgr) { ctx_mgr = node_get(p, 0, 0, 1); ctx_mgr->local_strong++; ctx_mgr->has_strong = ctx_mgr->has_weak = 1; } break;
        default: V("pid %d: unknown command %x", p->pid, cmd); return;
        }
    }
}

// ------------------------------------------------------------------------------------------- lifetime

static void thread_release(struct thread *th) {
    struct proc *p = th->proc;
    for (struct txn *t = th->stack; t;) {
        struct txn *nx;
        if (t->to_thread == th) {           /* we were serving it: the caller gets a dead reply */
            nx = t->to_parent; t->to_thread = 0;
            struct thread *from = t->from;
            if (from && from->stack == t) { from->stack = t->from_parent; thread_error(from, BR_DEAD_REPLY); }
            t->from = 0; txn_free(t);
        } else {                            /* we were waiting for its answer: nobody to answer to any more */
            nx = t->from_parent; t->from = 0;
            if (!t->to_thread) { /* still queued: stays there and is dropped at reply time */ }
        }
        t = nx;
    }
    th->stack = 0;
    for (struct work *w; (w = wq_pop(&th->todo));) {
        if (w->type == W_TXN) txn_fail(w->t, BR_DEAD_REPLY);
        else if (w->type == W_NODE) { w->node->queued = 0; if (p) node_work(w->node); }
        free(w);
    }
    if (cur == th) cur = 0;
    if (p) { struct thread **pp = &p->threads; while (*pp && *pp != th) pp = &(*pp)->next; if (*pp) *pp = th->next; }
    epoll_ctl(ep, EPOLL_CTL_DEL, th->fd, 0);
    close(th->fd);
    free(th);
}

static void proc_release(struct proc *p) {
    V("pid %d died", p->pid);
    struct proc **pp = &procs; while (*pp && *pp != p) pp = &(*pp)->next; if (*pp) *pp = p->next;
    while (p->threads) thread_release(p->threads);
    for (struct work *w; (w = wq_pop(&p->todo));) {
        if (w->type == W_TXN) txn_fail(w->t, BR_DEAD_REPLY);
        else if (w->type == W_NODE) w->node->queued = 0;
        free(w);
    }
    while (p->bufs) buf_free(p, p->bufs);
    /* calls still queued behind one-way calls hold references of this process: drop them first */
    for (int i = 0; i < NHASH; i++) for (struct node *n = p->nodes[i]; n; n = n->hnext)
        for (struct work *w; (w = wq_pop(&n->async));) { txn_free(w->t); free(w); }
    for (u32 i = 0; i < p->nrefs; i++) if (p->refs[i]) ref_delete(p->refs[i]);
    free(p->refs);
    for (int i = 0; i < NHASH; i++) for (struct node *n = p->nodes[i], *nx; n; n = nx) {
        nx = n->hnext;
        n->owner = 0; n->queued = 0; n->hnext = 0;
        if (n->pend_strong) { n->pend_strong = 0; n->local_strong--; }
        if (n->pend_weak) { n->pend_weak = 0; n->local_weak--; }
        for (struct work *w; (w = wq_pop(&n->async));) { txn_free(w->t); free(w); }
        for (struct ref *r = n->refs; r; r = r->next_in_node) if (r->has_death) {
            struct work *w = mkwork(W_DEAD_BINDER); w->cookie = r->death; wq_push(&r->proc->todo, w);
        }
        if (ctx_mgr == n) ctx_mgr = 0;
        if (!n->nrefs && !n->local_strong) free(n);
    }
    struct proc *q, *qn;
    for (q = procs; q; q = qn) { qn = q->next; kick_proc(q); }
    free(p);
}

static void kick_pending(void) {
    while (kick_any) {
        kick_any = 0;
        for (struct proc *q = procs; q; q = q->next) if (q->kick) { q->kick = 0; kick_proc(q); }
    }
}

static void on_message(struct thread *th) {
    static char buf[INLINE_MAX + 4096 + 512];
    char cbuf[CMSG_SPACE(sizeof(int) * 254)];
    struct iovec iov = { buf, sizeof buf };
    struct msghdr mh = { 0 };
    mh.msg_iov = &iov; mh.msg_iovlen = 1; mh.msg_control = cbuf; mh.msg_controllen = sizeof cbuf;
    ssize_t n = recvmsg(th->fd, &mh, MSG_DONTWAIT | MSG_CMSG_CLOEXEC);
    if (n < 0 && (errno == EAGAIN || errno == EINTR)) return;
    if (n <= 0) {
        if (th->is_main && th->proc) proc_release(th->proc); else thread_release(th);
        kick_pending();
        return;
    }
    int fds[254], nfds = 0;
    for (struct cmsghdr *c = CMSG_FIRSTHDR(&mh); c; c = CMSG_NXTHDR(&mh, c))
        if (c->cmsg_level == SOL_SOCKET && c->cmsg_type == SCM_RIGHTS) {
            nfds = (int)((c->cmsg_len - CMSG_LEN(0)) / sizeof(int));
            memcpy(fds, CMSG_DATA(c), nfds * sizeof(int));
        }
    struct c_hdr *h = (struct c_hdr *)buf;
    if ((size_t)n < sizeof *h) return;
    if (h->op == OP_HELLO) {
        struct c_hello *m = (struct c_hello *)buf;
        /* every open("/dev/binder") is a process of its own, as in the driver; its threads name it by pid + descriptor */
        struct proc *p = procs;
        while (p && !(p->pid == (int)m->pid && p->key == m->pad)) p = p->next;
        if (m->main || !p) {
            p = zalloc(sizeof *p); p->pid = (int)m->pid; p->key = m->pad; p->next = procs; procs = p;
            V("pid %d opened binder (fd %u)", p->pid, p->key);
        }
        th->proc = p; th->tid = (int)m->tid; th->is_main = (int)m->main;
        th->next = p->threads; p->threads = th;
        return;
    }
    if (!th->proc) return;
    cur = th;
    V("%d/%d: op %u flags %u len %zd fds %d", th->proc->pid, th->tid, h->op, h->flags, n, nfds);
    if (h->op == OP_BCS) do_bcs(th, (u32 *)(h + 1), (int)((n - sizeof *h) / 4));
    else if (h->op == OP_TXN && (size_t)n >= sizeof(struct c_txn)) { do_txn(th, (struct c_txn *)buf, buf + sizeof(struct c_txn), fds, nfds); nfds = 0; }
    for (int i = 0; i < nfds; i++) close(fds[i]);
    if (h->flags & F_READ) {
        th->woken = 0; th->waiting = 1; th->nonblock = !!(h->flags & F_NONBLOCK); th->read_size = h->read_size;
        deliver(th);
        if (th->waiting && !th->stack && !th->todo.head) kick_proc(th->proc);
    }
    cur = 0;
    kick_pending();
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: binderd64 <socket>\n"); return 2; }
    verbose = getenv("AEMU_BINDER_VERBOSE") != 0;
    signal(SIGPIPE, SIG_IGN);
    setvbuf(stderr, 0, _IOLBF, 0);
    struct rlimit rl; if (!getrlimit(RLIMIT_NOFILE, &rl)) { rl.rlim_cur = rl.rlim_max; setrlimit(RLIMIT_NOFILE, &rl); }
    int ls = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    struct sockaddr_un sa = { .sun_family = AF_UNIX };
    strncpy(sa.sun_path, argv[1], sizeof sa.sun_path - 1);
    unlink(argv[1]);
    if (bind(ls, (struct sockaddr *)&sa, sizeof sa) < 0 || listen(ls, 128) < 0) { perror("binderd64: bind"); return 1; }
    ep = epoll_create1(EPOLL_CLOEXEC);
    struct epoll_event ev = { .events = EPOLLIN, .data.ptr = 0 };
    epoll_ctl(ep, EPOLL_CTL_ADD, ls, &ev);
    logf_("binderd64: listening on %s", argv[1]);
    for (;;) {
        struct epoll_event evs[64];
        int n = epoll_wait(ep, evs, 64, -1);
        for (int i = 0; i < n; i++) {
            if (!evs[i].data.ptr) {
                int fd = accept4(ls, 0, 0, SOCK_CLOEXEC);
                if (fd < 0) continue;
                struct thread *th = zalloc(sizeof *th);
                th->fd = fd;
                ev.events = EPOLLIN; ev.data.ptr = th;
                epoll_ctl(ep, EPOLL_CTL_ADD, fd, &ev);
            } else {
                on_message(evs[i].data.ptr);
                break;      /* the handler may have freed threads named by the remaining events: poll again */
            }
        }
    }
}
