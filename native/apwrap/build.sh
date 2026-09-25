#!/bin/sh
# Сборка обёртки audio_policy: ARMv7, только libc/libdl, классическая хэш-таблица для старого bionic
NDK=${NDK:-$ANDROID_NDK_HOME}
BIN="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
[ -x "$BIN/clang" ] || BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT=${1:-../../app/src/main/assets/engines/kk/audio_policy.wrap.so}
mkdir -p "$(dirname "$OUT")"
"$BIN/clang" --target=armv7a-linux-androideabi21 -march=armv7-a -mthumb -Os -fPIC -shared -nostdlib \
  -ffreestanding -fno-builtin -fvisibility=hidden -fno-stack-protector \
  -Wl,--hash-style=sysv -Wl,-z,norelro -Wl,--build-id=none -Wl,-soname,audio_policy.default.so \
  -o "$OUT" apwrap.c tramp.S -ldl -lc -llog
"$BIN/llvm-strip" "$OUT" 2>/dev/null || true
