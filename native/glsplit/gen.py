#!/usr/bin/env python3
"""Генератор переходника libGLES_split: каждая функция EGL/GL прыгает в единственный экземпляр
GL-моста /system/lib/egl/libGLES_bridge.so. Функции из SPECIAL реализованы в split.c."""
syms = [s.strip() for s in open('symbols.txt') if s.strip()]
SPECIAL = {'glPixelStorei', 'glTexImage2D', 'glTexSubImage2D', 'glBindBuffer', 'eglCreateContext', 'eglMakeCurrent'}
import re
# функции, которых нет в ES 1.x: в контексте ES1 переходник делает их пустыми (см. split.c)
ES2ONLY = re.compile(r'^gl(?!.*(OES|EXT|APPLE|IMG|QCOM|NV|AMD|ANGLE|ARM)$)(.*(Shader|Program|Uniform|VertexAttrib|AttribLocation|'
    r'Framebuffer|Renderbuffer|GenerateMipmap|Separate|BlendColor|TransformFeedback|Sampler|Quer|Sync|VertexArray|DrawBuffers|'
    r'ReadBuffer|Instanced|RangeElements|TexImage3D|TexSubImage3D|TexStorage|MapBufferRange|FlushMappedBufferRange|UnmapBuffer|'
    r'CopyBufferSubData|Integer64|Internalformat|InvalidateFramebuffer|InvalidateSubFramebuffer|BlitFramebuffer|ClearBuffer|'
    r'ProgramBinary|FragDataLocation|CopyTexSubImage3D|CompressedTex.*3D|GetStringi|IsEnabledi|BufferPointerv|ProgramParameter|'
    r'ActiveUniform|AttachedShaders|ShaderPrecision|ReleaseShaderCompiler|ValidateProgram|UseProgram|LinkProgram|IsShader|IsProgram))')
out = ['/* автоматически создано gen.py — не править */', '    .syntax unified', '    .arm', '    .text']
for i, s in enumerate(syms):
    if s in SPECIAL:
        continue  # экспортирует split.c
    out += [f'    .global {s}', f'    .type {s},%function', '    .align 2', f'{s}:',
            '    push {r0-r3, lr}', f'    movw r0, #{i}', '    bl aemu_split_resolve',
            '    mov r12, r0', '    pop {r0-r3, lr}', '    bx r12']
open('stubs.S', 'w').write('\n'.join(out) + '\n')
with open('names.h', 'w') as f:
    f.write('/* автоматически создано gen.py */\nstatic const char *const kNames[] = {\n')
    for s in syms: f.write(f'    "{s}",\n')
    f.write('};\n')
    for s in sorted(SPECIAL): f.write(f'#define IDX_{s} {syms.index(s)}\n')
    es2 = set(i for i, s in enumerate(syms) if ES2ONLY.match(s))
    f.write('static const unsigned char kEs2Only[] = {')
    f.write(','.join('1' if i in es2 else '0' for i in range(len(syms))))
    f.write('};\n')
    print('ES2-only:', len(es2))
