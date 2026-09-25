#!/bin/sh
# Сборка гостевой прослойки: ARMv7, без libc, с классической хэш-таблицей для линкера bionic 2.3
NDK=${NDK:-$ANDROID_NDK_HOME}
CC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/clang"
[ -x "$CC" ] || CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
OUT=${1:-../../app/src/main/assets/engines/common/libaemushim.so}
mkdir -p "$(dirname "$OUT")"
"$CC" --target=armv7a-linux-androideabi21 -march=armv7-a -mthumb -Os -fPIC -shared -nostdlib \
  -fvisibility=hidden -fno-stack-protector -ffreestanding -fno-builtin -Wl,--hash-style=sysv -Wl,-z,norelro -Wl,--no-undefined \
  -Wl,-soname,libaemushim.so -Wl,--build-id=none -o "$OUT" aemushim.c crash.c -lc
"$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip" "$OUT" 2>/dev/null || true
# пустая программа вместо iptables/ip6tables
BIN="$(dirname "$CC")"
"$CC" --target=armv7a-linux-androideabi21 -nostdlib -static -Wl,--build-id=none \
  -o "$(dirname "$OUT")/aemu_true.so" true.S
"$BIN/llvm-strip" "$(dirname "$OUT")/aemu_true.so" 2>/dev/null || true
