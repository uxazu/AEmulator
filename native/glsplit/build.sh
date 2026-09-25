#!/bin/sh
# Сборка переходника libGLES_split.so (ARMv7, без libc, только libdl)
NDK=${NDK:-$ANDROID_NDK_HOME}
BIN="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
[ -d "$BIN" ] || BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT=${1:-../../app/src/main/assets/engines/kk/libGLES_split.so}
SYSROOT="$NDK/toolchains/llvm/prebuilt/windows-x86_64/sysroot"
[ -d "$SYSROOT" ] || SYSROOT="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
"$BIN/clang" --target=armv7a-linux-androideabi21 -march=armv7-a -Os -fPIC -shared -nostdlib \
  -fvisibility=hidden -fno-stack-protector -ffreestanding -fno-builtin -Wl,--hash-style=sysv -Wl,-z,norelro \
  -Wl,-soname,libGLES_split.so -Wl,--build-id=none -o "$OUT" split.c stubs.S \
  -L"$SYSROOT/usr/lib/arm-linux-androideabi/21" -ldl -lc -llog
"$BIN/llvm-strip" "$OUT"
