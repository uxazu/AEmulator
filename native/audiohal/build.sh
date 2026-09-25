#!/bin/sh
# Сборка звукового HAL с раскладкой AOSP 4.2–4.4: ARMv7, только libc, классическая хэш-таблица
NDK=${NDK:-$ANDROID_NDK_HOME}
BIN="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
[ -x "$BIN/clang" ] || BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT=${1:-../../app/src/main/assets/engines/kk/audio.primary.aosp.so}
mkdir -p "$(dirname "$OUT")"
"$BIN/clang" --target=armv7a-linux-androideabi21 -march=armv7-a -mthumb -mfloat-abi=softfp -Os -fPIC -shared \
  -nostdlib -ffreestanding -fno-builtin -fvisibility=hidden -fno-stack-protector \
  -Wl,--hash-style=sysv -Wl,-z,norelro -Wl,--build-id=none -Wl,-soname,audio.primary.default.so \
  -o "$OUT" audio_hal.c -lc
"$BIN/llvm-strip" "$OUT" 2>/dev/null || true
