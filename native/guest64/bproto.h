// Wire protocol between the binder front end inside every guest process (run64.c) and binderd64.
// One SOCK_SEQPACKET connection per guest thread; the connection made by open("/dev/binder") is the process itself.
#ifndef AEMU_BPROTO_H
#define AEMU_BPROTO_H

#define OP_HELLO 1      /* c_hello */
#define OP_BCS 2        /* c_hdr + raw BC_* command stream (no transactions) */
#define OP_TXN 3        /* c_txn + data + offsets, or a memfd with them when F_BIG */
#define OP_READ 4       /* c_hdr only */

#define F_READ 1        /* answer with work as soon as there is some */
#define F_NONBLOCK 2    /* …or with an empty answer right away */
#define F_BIG 4         /* payload travels in a memfd (the first descriptor of the message) */
#define F_TXN 8         /* d_hdr describes a transaction */
#define F_AGAIN 16      /* nothing to read */

#define INLINE_MAX 49152

/* private commands inside a BCS stream */
#define BC_AEMU_FREE 0x4008633cu       /* u64 id: the buffer of a delivered transaction was freed */

struct c_hdr { unsigned op, flags, read_size, euid; };
struct c_hello { struct c_hdr h; unsigned pid, tid, main, pad; };
struct c_txn { struct c_hdr h; unsigned cmd, handle, code, tflags; unsigned long data_size, offsets_size; };

struct d_hdr {
    unsigned br_size, flags;
    unsigned cmd, code, tflags, sender_euid; int sender_pid; unsigned nfds;
    unsigned long ptr, cookie, data_size, offsets_size, id;
};

#endif
