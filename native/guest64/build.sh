#!/bin/sh
# aemu64: runs the firmware's own arm64 programs natively. Static, no libc, linked at a fixed address the
# seccomp filter recognises (syscalls made from this image pass, the guest's path/ioctl/id calls trap into it).
NDK=${NDK:-$ANDROID_NDK_HOME}
BIN="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
[ -x "$BIN/clang" ] || BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT=${1:-../../app/src/main/jniLibs/arm64-v8a/libaemu64.so}
"$BIN/clang" --target=aarch64-linux-android26 -O2 -static -nostdlib -ffreestanding -fno-builtin -fno-stack-protector -mno-outline-atomics \
  -fno-pic -no-pie -Wall -Wno-unused-function -Wl,--image-base=0x2000000000 -Wl,--build-id=none -Wl,-z,norelro \
  -o "$OUT" start.S run64.c
"$BIN/llvm-strip" "$OUT" 2>/dev/null || true
# binderd64: the binder driver in user space for those programs (an ordinary host program)
"$BIN/clang" --target=aarch64-linux-android26 -O2 -Wall -o "$(dirname "$OUT")/libbinderd64.so" binderd.c
"$BIN/llvm-strip" "$(dirname "$OUT")/libbinderd64.so" 2>/dev/null || true
