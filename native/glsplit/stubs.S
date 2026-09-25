/* автоматически создано gen.py Ч не править */
    .syntax unified
    .arm
    .text
    .global eglBindAPI
    .type eglBindAPI,%function
    .align 2
eglBindAPI:
    push {r0-r3, lr}
    movw r0, #0
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglBindTexImage
    .type eglBindTexImage,%function
    .align 2
eglBindTexImage:
    push {r0-r3, lr}
    movw r0, #1
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglChooseConfig
    .type eglChooseConfig,%function
    .align 2
eglChooseConfig:
    push {r0-r3, lr}
    movw r0, #2
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglClientWaitSyncKHR
    .type eglClientWaitSyncKHR,%function
    .align 2
eglClientWaitSyncKHR:
    push {r0-r3, lr}
    movw r0, #3
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglCopyBuffers
    .type eglCopyBuffers,%function
    .align 2
eglCopyBuffers:
    push {r0-r3, lr}
    movw r0, #4
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglCreateImageKHR
    .type eglCreateImageKHR,%function
    .align 2
eglCreateImageKHR:
    push {r0-r3, lr}
    movw r0, #6
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglCreatePbufferFromClientBuffer
    .type eglCreatePbufferFromClientBuffer,%function
    .align 2
eglCreatePbufferFromClientBuffer:
    push {r0-r3, lr}
    movw r0, #7
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglCreatePbufferSurface
    .type eglCreatePbufferSurface,%function
    .align 2
eglCreatePbufferSurface:
    push {r0-r3, lr}
    movw r0, #8
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglCreatePixmapSurface
    .type eglCreatePixmapSurface,%function
    .align 2
eglCreatePixmapSurface:
    push {r0-r3, lr}
    movw r0, #9
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglCreateSyncKHR
    .type eglCreateSyncKHR,%function
    .align 2
eglCreateSyncKHR:
    push {r0-r3, lr}
    movw r0, #10
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglCreateWindowSurface
    .type eglCreateWindowSurface,%function
    .align 2
eglCreateWindowSurface:
    push {r0-r3, lr}
    movw r0, #11
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglDestroyContext
    .type eglDestroyContext,%function
    .align 2
eglDestroyContext:
    push {r0-r3, lr}
    movw r0, #12
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglDestroyImageKHR
    .type eglDestroyImageKHR,%function
    .align 2
eglDestroyImageKHR:
    push {r0-r3, lr}
    movw r0, #13
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglDestroySurface
    .type eglDestroySurface,%function
    .align 2
eglDestroySurface:
    push {r0-r3, lr}
    movw r0, #14
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglDestroySyncKHR
    .type eglDestroySyncKHR,%function
    .align 2
eglDestroySyncKHR:
    push {r0-r3, lr}
    movw r0, #15
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetConfigAttrib
    .type eglGetConfigAttrib,%function
    .align 2
eglGetConfigAttrib:
    push {r0-r3, lr}
    movw r0, #16
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetConfigs
    .type eglGetConfigs,%function
    .align 2
eglGetConfigs:
    push {r0-r3, lr}
    movw r0, #17
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetCurrentContext
    .type eglGetCurrentContext,%function
    .align 2
eglGetCurrentContext:
    push {r0-r3, lr}
    movw r0, #18
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetCurrentDisplay
    .type eglGetCurrentDisplay,%function
    .align 2
eglGetCurrentDisplay:
    push {r0-r3, lr}
    movw r0, #19
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetCurrentSurface
    .type eglGetCurrentSurface,%function
    .align 2
eglGetCurrentSurface:
    push {r0-r3, lr}
    movw r0, #20
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetDisplay
    .type eglGetDisplay,%function
    .align 2
eglGetDisplay:
    push {r0-r3, lr}
    movw r0, #21
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetError
    .type eglGetError,%function
    .align 2
eglGetError:
    push {r0-r3, lr}
    movw r0, #22
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetProcAddress
    .type eglGetProcAddress,%function
    .align 2
eglGetProcAddress:
    push {r0-r3, lr}
    movw r0, #23
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetRenderBufferANDROID
    .type eglGetRenderBufferANDROID,%function
    .align 2
eglGetRenderBufferANDROID:
    push {r0-r3, lr}
    movw r0, #24
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetSyncAttribKHR
    .type eglGetSyncAttribKHR,%function
    .align 2
eglGetSyncAttribKHR:
    push {r0-r3, lr}
    movw r0, #25
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetSystemTimeFrequencyNV
    .type eglGetSystemTimeFrequencyNV,%function
    .align 2
eglGetSystemTimeFrequencyNV:
    push {r0-r3, lr}
    movw r0, #26
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglGetSystemTimeNV
    .type eglGetSystemTimeNV,%function
    .align 2
eglGetSystemTimeNV:
    push {r0-r3, lr}
    movw r0, #27
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglInitialize
    .type eglInitialize,%function
    .align 2
eglInitialize:
    push {r0-r3, lr}
    movw r0, #28
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglLockSurfaceKHR
    .type eglLockSurfaceKHR,%function
    .align 2
eglLockSurfaceKHR:
    push {r0-r3, lr}
    movw r0, #29
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglQueryAPI
    .type eglQueryAPI,%function
    .align 2
eglQueryAPI:
    push {r0-r3, lr}
    movw r0, #31
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglQueryContext
    .type eglQueryContext,%function
    .align 2
eglQueryContext:
    push {r0-r3, lr}
    movw r0, #32
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglQueryString
    .type eglQueryString,%function
    .align 2
eglQueryString:
    push {r0-r3, lr}
    movw r0, #33
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglQuerySurface
    .type eglQuerySurface,%function
    .align 2
eglQuerySurface:
    push {r0-r3, lr}
    movw r0, #34
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglReleaseTexImage
    .type eglReleaseTexImage,%function
    .align 2
eglReleaseTexImage:
    push {r0-r3, lr}
    movw r0, #35
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglReleaseThread
    .type eglReleaseThread,%function
    .align 2
eglReleaseThread:
    push {r0-r3, lr}
    movw r0, #36
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglSetSwapRectangleANDROID
    .type eglSetSwapRectangleANDROID,%function
    .align 2
eglSetSwapRectangleANDROID:
    push {r0-r3, lr}
    movw r0, #37
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglSurfaceAttrib
    .type eglSurfaceAttrib,%function
    .align 2
eglSurfaceAttrib:
    push {r0-r3, lr}
    movw r0, #38
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglSwapBuffers
    .type eglSwapBuffers,%function
    .align 2
eglSwapBuffers:
    push {r0-r3, lr}
    movw r0, #39
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglSwapInterval
    .type eglSwapInterval,%function
    .align 2
eglSwapInterval:
    push {r0-r3, lr}
    movw r0, #40
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglTerminate
    .type eglTerminate,%function
    .align 2
eglTerminate:
    push {r0-r3, lr}
    movw r0, #41
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglUnlockSurfaceKHR
    .type eglUnlockSurfaceKHR,%function
    .align 2
eglUnlockSurfaceKHR:
    push {r0-r3, lr}
    movw r0, #42
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglWaitClient
    .type eglWaitClient,%function
    .align 2
eglWaitClient:
    push {r0-r3, lr}
    movw r0, #43
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglWaitGL
    .type eglWaitGL,%function
    .align 2
eglWaitGL:
    push {r0-r3, lr}
    movw r0, #44
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global eglWaitNative
    .type eglWaitNative,%function
    .align 2
eglWaitNative:
    push {r0-r3, lr}
    movw r0, #45
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glActiveTexture
    .type glActiveTexture,%function
    .align 2
glActiveTexture:
    push {r0-r3, lr}
    movw r0, #46
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glAlphaFunc
    .type glAlphaFunc,%function
    .align 2
glAlphaFunc:
    push {r0-r3, lr}
    movw r0, #47
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glAlphaFuncx
    .type glAlphaFuncx,%function
    .align 2
glAlphaFuncx:
    push {r0-r3, lr}
    movw r0, #48
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glAttachShader
    .type glAttachShader,%function
    .align 2
glAttachShader:
    push {r0-r3, lr}
    movw r0, #49
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBeginQuery
    .type glBeginQuery,%function
    .align 2
glBeginQuery:
    push {r0-r3, lr}
    movw r0, #50
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBeginTransformFeedback
    .type glBeginTransformFeedback,%function
    .align 2
glBeginTransformFeedback:
    push {r0-r3, lr}
    movw r0, #51
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindAttribLocation
    .type glBindAttribLocation,%function
    .align 2
glBindAttribLocation:
    push {r0-r3, lr}
    movw r0, #52
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindBufferBase
    .type glBindBufferBase,%function
    .align 2
glBindBufferBase:
    push {r0-r3, lr}
    movw r0, #54
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindBufferRange
    .type glBindBufferRange,%function
    .align 2
glBindBufferRange:
    push {r0-r3, lr}
    movw r0, #55
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindFramebuffer
    .type glBindFramebuffer,%function
    .align 2
glBindFramebuffer:
    push {r0-r3, lr}
    movw r0, #56
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindFramebufferOES
    .type glBindFramebufferOES,%function
    .align 2
glBindFramebufferOES:
    push {r0-r3, lr}
    movw r0, #57
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindRenderbuffer
    .type glBindRenderbuffer,%function
    .align 2
glBindRenderbuffer:
    push {r0-r3, lr}
    movw r0, #58
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindRenderbufferOES
    .type glBindRenderbufferOES,%function
    .align 2
glBindRenderbufferOES:
    push {r0-r3, lr}
    movw r0, #59
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindSampler
    .type glBindSampler,%function
    .align 2
glBindSampler:
    push {r0-r3, lr}
    movw r0, #60
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindTexture
    .type glBindTexture,%function
    .align 2
glBindTexture:
    push {r0-r3, lr}
    movw r0, #61
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindTransformFeedback
    .type glBindTransformFeedback,%function
    .align 2
glBindTransformFeedback:
    push {r0-r3, lr}
    movw r0, #62
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindVertexArray
    .type glBindVertexArray,%function
    .align 2
glBindVertexArray:
    push {r0-r3, lr}
    movw r0, #63
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBindVertexArrayOES
    .type glBindVertexArrayOES,%function
    .align 2
glBindVertexArrayOES:
    push {r0-r3, lr}
    movw r0, #64
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBlendColor
    .type glBlendColor,%function
    .align 2
glBlendColor:
    push {r0-r3, lr}
    movw r0, #65
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBlendEquation
    .type glBlendEquation,%function
    .align 2
glBlendEquation:
    push {r0-r3, lr}
    movw r0, #66
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBlendEquationOES
    .type glBlendEquationOES,%function
    .align 2
glBlendEquationOES:
    push {r0-r3, lr}
    movw r0, #67
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBlendEquationSeparate
    .type glBlendEquationSeparate,%function
    .align 2
glBlendEquationSeparate:
    push {r0-r3, lr}
    movw r0, #68
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBlendEquationSeparateOES
    .type glBlendEquationSeparateOES,%function
    .align 2
glBlendEquationSeparateOES:
    push {r0-r3, lr}
    movw r0, #69
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBlendFunc
    .type glBlendFunc,%function
    .align 2
glBlendFunc:
    push {r0-r3, lr}
    movw r0, #70
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBlendFuncSeparate
    .type glBlendFuncSeparate,%function
    .align 2
glBlendFuncSeparate:
    push {r0-r3, lr}
    movw r0, #71
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBlendFuncSeparateOES
    .type glBlendFuncSeparateOES,%function
    .align 2
glBlendFuncSeparateOES:
    push {r0-r3, lr}
    movw r0, #72
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBlitFramebuffer
    .type glBlitFramebuffer,%function
    .align 2
glBlitFramebuffer:
    push {r0-r3, lr}
    movw r0, #73
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBufferData
    .type glBufferData,%function
    .align 2
glBufferData:
    push {r0-r3, lr}
    movw r0, #74
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glBufferSubData
    .type glBufferSubData,%function
    .align 2
glBufferSubData:
    push {r0-r3, lr}
    movw r0, #75
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCheckFramebufferStatus
    .type glCheckFramebufferStatus,%function
    .align 2
glCheckFramebufferStatus:
    push {r0-r3, lr}
    movw r0, #76
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCheckFramebufferStatusOES
    .type glCheckFramebufferStatusOES,%function
    .align 2
glCheckFramebufferStatusOES:
    push {r0-r3, lr}
    movw r0, #77
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClear
    .type glClear,%function
    .align 2
glClear:
    push {r0-r3, lr}
    movw r0, #78
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClearBufferfi
    .type glClearBufferfi,%function
    .align 2
glClearBufferfi:
    push {r0-r3, lr}
    movw r0, #79
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClearBufferfv
    .type glClearBufferfv,%function
    .align 2
glClearBufferfv:
    push {r0-r3, lr}
    movw r0, #80
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClearBufferiv
    .type glClearBufferiv,%function
    .align 2
glClearBufferiv:
    push {r0-r3, lr}
    movw r0, #81
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClearBufferuiv
    .type glClearBufferuiv,%function
    .align 2
glClearBufferuiv:
    push {r0-r3, lr}
    movw r0, #82
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClearColor
    .type glClearColor,%function
    .align 2
glClearColor:
    push {r0-r3, lr}
    movw r0, #83
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClearColorx
    .type glClearColorx,%function
    .align 2
glClearColorx:
    push {r0-r3, lr}
    movw r0, #84
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClearDepthf
    .type glClearDepthf,%function
    .align 2
glClearDepthf:
    push {r0-r3, lr}
    movw r0, #85
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClearDepthx
    .type glClearDepthx,%function
    .align 2
glClearDepthx:
    push {r0-r3, lr}
    movw r0, #86
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClearStencil
    .type glClearStencil,%function
    .align 2
glClearStencil:
    push {r0-r3, lr}
    movw r0, #87
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClientActiveTexture
    .type glClientActiveTexture,%function
    .align 2
glClientActiveTexture:
    push {r0-r3, lr}
    movw r0, #88
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClientWaitSync
    .type glClientWaitSync,%function
    .align 2
glClientWaitSync:
    push {r0-r3, lr}
    movw r0, #89
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClipPlanef
    .type glClipPlanef,%function
    .align 2
glClipPlanef:
    push {r0-r3, lr}
    movw r0, #90
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glClipPlanex
    .type glClipPlanex,%function
    .align 2
glClipPlanex:
    push {r0-r3, lr}
    movw r0, #91
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glColor4f
    .type glColor4f,%function
    .align 2
glColor4f:
    push {r0-r3, lr}
    movw r0, #92
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glColor4ub
    .type glColor4ub,%function
    .align 2
glColor4ub:
    push {r0-r3, lr}
    movw r0, #93
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glColor4x
    .type glColor4x,%function
    .align 2
glColor4x:
    push {r0-r3, lr}
    movw r0, #94
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glColorMask
    .type glColorMask,%function
    .align 2
glColorMask:
    push {r0-r3, lr}
    movw r0, #95
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glColorPointer
    .type glColorPointer,%function
    .align 2
glColorPointer:
    push {r0-r3, lr}
    movw r0, #96
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCompileShader
    .type glCompileShader,%function
    .align 2
glCompileShader:
    push {r0-r3, lr}
    movw r0, #97
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCompressedTexImage2D
    .type glCompressedTexImage2D,%function
    .align 2
glCompressedTexImage2D:
    push {r0-r3, lr}
    movw r0, #98
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCompressedTexImage3D
    .type glCompressedTexImage3D,%function
    .align 2
glCompressedTexImage3D:
    push {r0-r3, lr}
    movw r0, #99
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCompressedTexSubImage2D
    .type glCompressedTexSubImage2D,%function
    .align 2
glCompressedTexSubImage2D:
    push {r0-r3, lr}
    movw r0, #100
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCompressedTexSubImage3D
    .type glCompressedTexSubImage3D,%function
    .align 2
glCompressedTexSubImage3D:
    push {r0-r3, lr}
    movw r0, #101
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCopyBufferSubData
    .type glCopyBufferSubData,%function
    .align 2
glCopyBufferSubData:
    push {r0-r3, lr}
    movw r0, #102
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCopyTexImage2D
    .type glCopyTexImage2D,%function
    .align 2
glCopyTexImage2D:
    push {r0-r3, lr}
    movw r0, #103
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCopyTexSubImage2D
    .type glCopyTexSubImage2D,%function
    .align 2
glCopyTexSubImage2D:
    push {r0-r3, lr}
    movw r0, #104
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCopyTexSubImage3D
    .type glCopyTexSubImage3D,%function
    .align 2
glCopyTexSubImage3D:
    push {r0-r3, lr}
    movw r0, #105
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCreateProgram
    .type glCreateProgram,%function
    .align 2
glCreateProgram:
    push {r0-r3, lr}
    movw r0, #106
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCreateShader
    .type glCreateShader,%function
    .align 2
glCreateShader:
    push {r0-r3, lr}
    movw r0, #107
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glCullFace
    .type glCullFace,%function
    .align 2
glCullFace:
    push {r0-r3, lr}
    movw r0, #108
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteBuffers
    .type glDeleteBuffers,%function
    .align 2
glDeleteBuffers:
    push {r0-r3, lr}
    movw r0, #109
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteFramebuffers
    .type glDeleteFramebuffers,%function
    .align 2
glDeleteFramebuffers:
    push {r0-r3, lr}
    movw r0, #110
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteFramebuffersOES
    .type glDeleteFramebuffersOES,%function
    .align 2
glDeleteFramebuffersOES:
    push {r0-r3, lr}
    movw r0, #111
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteProgram
    .type glDeleteProgram,%function
    .align 2
glDeleteProgram:
    push {r0-r3, lr}
    movw r0, #112
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteQueries
    .type glDeleteQueries,%function
    .align 2
glDeleteQueries:
    push {r0-r3, lr}
    movw r0, #113
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteRenderbuffers
    .type glDeleteRenderbuffers,%function
    .align 2
glDeleteRenderbuffers:
    push {r0-r3, lr}
    movw r0, #114
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteRenderbuffersOES
    .type glDeleteRenderbuffersOES,%function
    .align 2
glDeleteRenderbuffersOES:
    push {r0-r3, lr}
    movw r0, #115
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteSamplers
    .type glDeleteSamplers,%function
    .align 2
glDeleteSamplers:
    push {r0-r3, lr}
    movw r0, #116
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteShader
    .type glDeleteShader,%function
    .align 2
glDeleteShader:
    push {r0-r3, lr}
    movw r0, #117
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteSync
    .type glDeleteSync,%function
    .align 2
glDeleteSync:
    push {r0-r3, lr}
    movw r0, #118
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteTextures
    .type glDeleteTextures,%function
    .align 2
glDeleteTextures:
    push {r0-r3, lr}
    movw r0, #119
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteTransformFeedbacks
    .type glDeleteTransformFeedbacks,%function
    .align 2
glDeleteTransformFeedbacks:
    push {r0-r3, lr}
    movw r0, #120
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteVertexArrays
    .type glDeleteVertexArrays,%function
    .align 2
glDeleteVertexArrays:
    push {r0-r3, lr}
    movw r0, #121
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDeleteVertexArraysOES
    .type glDeleteVertexArraysOES,%function
    .align 2
glDeleteVertexArraysOES:
    push {r0-r3, lr}
    movw r0, #122
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDepthFunc
    .type glDepthFunc,%function
    .align 2
glDepthFunc:
    push {r0-r3, lr}
    movw r0, #123
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDepthMask
    .type glDepthMask,%function
    .align 2
glDepthMask:
    push {r0-r3, lr}
    movw r0, #124
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDepthRangef
    .type glDepthRangef,%function
    .align 2
glDepthRangef:
    push {r0-r3, lr}
    movw r0, #125
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDepthRangex
    .type glDepthRangex,%function
    .align 2
glDepthRangex:
    push {r0-r3, lr}
    movw r0, #126
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDetachShader
    .type glDetachShader,%function
    .align 2
glDetachShader:
    push {r0-r3, lr}
    movw r0, #127
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDisable
    .type glDisable,%function
    .align 2
glDisable:
    push {r0-r3, lr}
    movw r0, #128
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDisableClientState
    .type glDisableClientState,%function
    .align 2
glDisableClientState:
    push {r0-r3, lr}
    movw r0, #129
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDisableVertexAttribArray
    .type glDisableVertexAttribArray,%function
    .align 2
glDisableVertexAttribArray:
    push {r0-r3, lr}
    movw r0, #130
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDiscardFramebufferEXT
    .type glDiscardFramebufferEXT,%function
    .align 2
glDiscardFramebufferEXT:
    push {r0-r3, lr}
    movw r0, #131
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDrawArrays
    .type glDrawArrays,%function
    .align 2
glDrawArrays:
    push {r0-r3, lr}
    movw r0, #132
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDrawArraysInstanced
    .type glDrawArraysInstanced,%function
    .align 2
glDrawArraysInstanced:
    push {r0-r3, lr}
    movw r0, #133
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDrawBuffers
    .type glDrawBuffers,%function
    .align 2
glDrawBuffers:
    push {r0-r3, lr}
    movw r0, #134
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDrawElements
    .type glDrawElements,%function
    .align 2
glDrawElements:
    push {r0-r3, lr}
    movw r0, #135
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDrawElementsInstanced
    .type glDrawElementsInstanced,%function
    .align 2
glDrawElementsInstanced:
    push {r0-r3, lr}
    movw r0, #136
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDrawRangeElements
    .type glDrawRangeElements,%function
    .align 2
glDrawRangeElements:
    push {r0-r3, lr}
    movw r0, #137
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDrawTexfOES
    .type glDrawTexfOES,%function
    .align 2
glDrawTexfOES:
    push {r0-r3, lr}
    movw r0, #138
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDrawTexiOES
    .type glDrawTexiOES,%function
    .align 2
glDrawTexiOES:
    push {r0-r3, lr}
    movw r0, #139
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glDrawTexsOES
    .type glDrawTexsOES,%function
    .align 2
glDrawTexsOES:
    push {r0-r3, lr}
    movw r0, #140
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glEGLImageTargetRenderbufferStorageOES
    .type glEGLImageTargetRenderbufferStorageOES,%function
    .align 2
glEGLImageTargetRenderbufferStorageOES:
    push {r0-r3, lr}
    movw r0, #141
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glEGLImageTargetTexture2DOES
    .type glEGLImageTargetTexture2DOES,%function
    .align 2
glEGLImageTargetTexture2DOES:
    push {r0-r3, lr}
    movw r0, #142
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glEnable
    .type glEnable,%function
    .align 2
glEnable:
    push {r0-r3, lr}
    movw r0, #143
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glEnableClientState
    .type glEnableClientState,%function
    .align 2
glEnableClientState:
    push {r0-r3, lr}
    movw r0, #144
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glEnableVertexAttribArray
    .type glEnableVertexAttribArray,%function
    .align 2
glEnableVertexAttribArray:
    push {r0-r3, lr}
    movw r0, #145
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glEndQuery
    .type glEndQuery,%function
    .align 2
glEndQuery:
    push {r0-r3, lr}
    movw r0, #146
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glEndTransformFeedback
    .type glEndTransformFeedback,%function
    .align 2
glEndTransformFeedback:
    push {r0-r3, lr}
    movw r0, #147
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFenceSync
    .type glFenceSync,%function
    .align 2
glFenceSync:
    push {r0-r3, lr}
    movw r0, #148
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFinish
    .type glFinish,%function
    .align 2
glFinish:
    push {r0-r3, lr}
    movw r0, #149
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFlush
    .type glFlush,%function
    .align 2
glFlush:
    push {r0-r3, lr}
    movw r0, #150
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFlushMappedBufferRange
    .type glFlushMappedBufferRange,%function
    .align 2
glFlushMappedBufferRange:
    push {r0-r3, lr}
    movw r0, #151
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFogf
    .type glFogf,%function
    .align 2
glFogf:
    push {r0-r3, lr}
    movw r0, #152
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFogfv
    .type glFogfv,%function
    .align 2
glFogfv:
    push {r0-r3, lr}
    movw r0, #153
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFogx
    .type glFogx,%function
    .align 2
glFogx:
    push {r0-r3, lr}
    movw r0, #154
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFogxv
    .type glFogxv,%function
    .align 2
glFogxv:
    push {r0-r3, lr}
    movw r0, #155
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFramebufferRenderbuffer
    .type glFramebufferRenderbuffer,%function
    .align 2
glFramebufferRenderbuffer:
    push {r0-r3, lr}
    movw r0, #156
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFramebufferRenderbufferOES
    .type glFramebufferRenderbufferOES,%function
    .align 2
glFramebufferRenderbufferOES:
    push {r0-r3, lr}
    movw r0, #157
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFramebufferTexture2D
    .type glFramebufferTexture2D,%function
    .align 2
glFramebufferTexture2D:
    push {r0-r3, lr}
    movw r0, #158
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFramebufferTexture2DOES
    .type glFramebufferTexture2DOES,%function
    .align 2
glFramebufferTexture2DOES:
    push {r0-r3, lr}
    movw r0, #159
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFramebufferTextureLayer
    .type glFramebufferTextureLayer,%function
    .align 2
glFramebufferTextureLayer:
    push {r0-r3, lr}
    movw r0, #160
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFrontFace
    .type glFrontFace,%function
    .align 2
glFrontFace:
    push {r0-r3, lr}
    movw r0, #161
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFrustumf
    .type glFrustumf,%function
    .align 2
glFrustumf:
    push {r0-r3, lr}
    movw r0, #162
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glFrustumx
    .type glFrustumx,%function
    .align 2
glFrustumx:
    push {r0-r3, lr}
    movw r0, #163
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenBuffers
    .type glGenBuffers,%function
    .align 2
glGenBuffers:
    push {r0-r3, lr}
    movw r0, #164
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenFramebuffers
    .type glGenFramebuffers,%function
    .align 2
glGenFramebuffers:
    push {r0-r3, lr}
    movw r0, #165
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenFramebuffersOES
    .type glGenFramebuffersOES,%function
    .align 2
glGenFramebuffersOES:
    push {r0-r3, lr}
    movw r0, #166
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenQueries
    .type glGenQueries,%function
    .align 2
glGenQueries:
    push {r0-r3, lr}
    movw r0, #167
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenRenderbuffers
    .type glGenRenderbuffers,%function
    .align 2
glGenRenderbuffers:
    push {r0-r3, lr}
    movw r0, #168
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenRenderbuffersOES
    .type glGenRenderbuffersOES,%function
    .align 2
glGenRenderbuffersOES:
    push {r0-r3, lr}
    movw r0, #169
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenSamplers
    .type glGenSamplers,%function
    .align 2
glGenSamplers:
    push {r0-r3, lr}
    movw r0, #170
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenTextures
    .type glGenTextures,%function
    .align 2
glGenTextures:
    push {r0-r3, lr}
    movw r0, #171
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenTransformFeedbacks
    .type glGenTransformFeedbacks,%function
    .align 2
glGenTransformFeedbacks:
    push {r0-r3, lr}
    movw r0, #172
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenVertexArrays
    .type glGenVertexArrays,%function
    .align 2
glGenVertexArrays:
    push {r0-r3, lr}
    movw r0, #173
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenVertexArraysOES
    .type glGenVertexArraysOES,%function
    .align 2
glGenVertexArraysOES:
    push {r0-r3, lr}
    movw r0, #174
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenerateMipmap
    .type glGenerateMipmap,%function
    .align 2
glGenerateMipmap:
    push {r0-r3, lr}
    movw r0, #175
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGenerateMipmapOES
    .type glGenerateMipmapOES,%function
    .align 2
glGenerateMipmapOES:
    push {r0-r3, lr}
    movw r0, #176
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetActiveAttrib
    .type glGetActiveAttrib,%function
    .align 2
glGetActiveAttrib:
    push {r0-r3, lr}
    movw r0, #177
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetActiveUniform
    .type glGetActiveUniform,%function
    .align 2
glGetActiveUniform:
    push {r0-r3, lr}
    movw r0, #178
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetActiveUniformBlockName
    .type glGetActiveUniformBlockName,%function
    .align 2
glGetActiveUniformBlockName:
    push {r0-r3, lr}
    movw r0, #179
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetActiveUniformBlockiv
    .type glGetActiveUniformBlockiv,%function
    .align 2
glGetActiveUniformBlockiv:
    push {r0-r3, lr}
    movw r0, #180
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetActiveUniformsiv
    .type glGetActiveUniformsiv,%function
    .align 2
glGetActiveUniformsiv:
    push {r0-r3, lr}
    movw r0, #181
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetAttachedShaders
    .type glGetAttachedShaders,%function
    .align 2
glGetAttachedShaders:
    push {r0-r3, lr}
    movw r0, #182
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetAttribLocation
    .type glGetAttribLocation,%function
    .align 2
glGetAttribLocation:
    push {r0-r3, lr}
    movw r0, #183
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetBooleanv
    .type glGetBooleanv,%function
    .align 2
glGetBooleanv:
    push {r0-r3, lr}
    movw r0, #184
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetBufferParameteri64v
    .type glGetBufferParameteri64v,%function
    .align 2
glGetBufferParameteri64v:
    push {r0-r3, lr}
    movw r0, #185
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetBufferParameteriv
    .type glGetBufferParameteriv,%function
    .align 2
glGetBufferParameteriv:
    push {r0-r3, lr}
    movw r0, #186
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetBufferPointerv
    .type glGetBufferPointerv,%function
    .align 2
glGetBufferPointerv:
    push {r0-r3, lr}
    movw r0, #187
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetBufferPointervOES
    .type glGetBufferPointervOES,%function
    .align 2
glGetBufferPointervOES:
    push {r0-r3, lr}
    movw r0, #188
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetClipPlanef
    .type glGetClipPlanef,%function
    .align 2
glGetClipPlanef:
    push {r0-r3, lr}
    movw r0, #189
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetClipPlanex
    .type glGetClipPlanex,%function
    .align 2
glGetClipPlanex:
    push {r0-r3, lr}
    movw r0, #190
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetError
    .type glGetError,%function
    .align 2
glGetError:
    push {r0-r3, lr}
    movw r0, #191
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetFixedv
    .type glGetFixedv,%function
    .align 2
glGetFixedv:
    push {r0-r3, lr}
    movw r0, #192
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetFloatv
    .type glGetFloatv,%function
    .align 2
glGetFloatv:
    push {r0-r3, lr}
    movw r0, #193
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetFragDataLocation
    .type glGetFragDataLocation,%function
    .align 2
glGetFragDataLocation:
    push {r0-r3, lr}
    movw r0, #194
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetFramebufferAttachmentParameteriv
    .type glGetFramebufferAttachmentParameteriv,%function
    .align 2
glGetFramebufferAttachmentParameteriv:
    push {r0-r3, lr}
    movw r0, #195
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetFramebufferAttachmentParameterivOES
    .type glGetFramebufferAttachmentParameterivOES,%function
    .align 2
glGetFramebufferAttachmentParameterivOES:
    push {r0-r3, lr}
    movw r0, #196
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetInteger64i_v
    .type glGetInteger64i_v,%function
    .align 2
glGetInteger64i_v:
    push {r0-r3, lr}
    movw r0, #197
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetInteger64v
    .type glGetInteger64v,%function
    .align 2
glGetInteger64v:
    push {r0-r3, lr}
    movw r0, #198
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetIntegeri_v
    .type glGetIntegeri_v,%function
    .align 2
glGetIntegeri_v:
    push {r0-r3, lr}
    movw r0, #199
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetIntegerv
    .type glGetIntegerv,%function
    .align 2
glGetIntegerv:
    push {r0-r3, lr}
    movw r0, #200
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetInternalformativ
    .type glGetInternalformativ,%function
    .align 2
glGetInternalformativ:
    push {r0-r3, lr}
    movw r0, #201
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetLightfv
    .type glGetLightfv,%function
    .align 2
glGetLightfv:
    push {r0-r3, lr}
    movw r0, #202
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetLightxv
    .type glGetLightxv,%function
    .align 2
glGetLightxv:
    push {r0-r3, lr}
    movw r0, #203
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetMaterialfv
    .type glGetMaterialfv,%function
    .align 2
glGetMaterialfv:
    push {r0-r3, lr}
    movw r0, #204
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetMaterialxv
    .type glGetMaterialxv,%function
    .align 2
glGetMaterialxv:
    push {r0-r3, lr}
    movw r0, #205
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetProgramBinary
    .type glGetProgramBinary,%function
    .align 2
glGetProgramBinary:
    push {r0-r3, lr}
    movw r0, #206
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetProgramInfoLog
    .type glGetProgramInfoLog,%function
    .align 2
glGetProgramInfoLog:
    push {r0-r3, lr}
    movw r0, #207
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetProgramiv
    .type glGetProgramiv,%function
    .align 2
glGetProgramiv:
    push {r0-r3, lr}
    movw r0, #208
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetQueryObjectuiv
    .type glGetQueryObjectuiv,%function
    .align 2
glGetQueryObjectuiv:
    push {r0-r3, lr}
    movw r0, #209
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetQueryiv
    .type glGetQueryiv,%function
    .align 2
glGetQueryiv:
    push {r0-r3, lr}
    movw r0, #210
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetRenderbufferParameteriv
    .type glGetRenderbufferParameteriv,%function
    .align 2
glGetRenderbufferParameteriv:
    push {r0-r3, lr}
    movw r0, #211
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetRenderbufferParameterivOES
    .type glGetRenderbufferParameterivOES,%function
    .align 2
glGetRenderbufferParameterivOES:
    push {r0-r3, lr}
    movw r0, #212
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetSamplerParameterfv
    .type glGetSamplerParameterfv,%function
    .align 2
glGetSamplerParameterfv:
    push {r0-r3, lr}
    movw r0, #213
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetSamplerParameteriv
    .type glGetSamplerParameteriv,%function
    .align 2
glGetSamplerParameteriv:
    push {r0-r3, lr}
    movw r0, #214
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetShaderInfoLog
    .type glGetShaderInfoLog,%function
    .align 2
glGetShaderInfoLog:
    push {r0-r3, lr}
    movw r0, #215
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetShaderPrecisionFormat
    .type glGetShaderPrecisionFormat,%function
    .align 2
glGetShaderPrecisionFormat:
    push {r0-r3, lr}
    movw r0, #216
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetShaderSource
    .type glGetShaderSource,%function
    .align 2
glGetShaderSource:
    push {r0-r3, lr}
    movw r0, #217
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetShaderiv
    .type glGetShaderiv,%function
    .align 2
glGetShaderiv:
    push {r0-r3, lr}
    movw r0, #218
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetString
    .type glGetString,%function
    .align 2
glGetString:
    push {r0-r3, lr}
    movw r0, #219
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetStringi
    .type glGetStringi,%function
    .align 2
glGetStringi:
    push {r0-r3, lr}
    movw r0, #220
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetSynciv
    .type glGetSynciv,%function
    .align 2
glGetSynciv:
    push {r0-r3, lr}
    movw r0, #221
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTexEnvfv
    .type glGetTexEnvfv,%function
    .align 2
glGetTexEnvfv:
    push {r0-r3, lr}
    movw r0, #222
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTexEnviv
    .type glGetTexEnviv,%function
    .align 2
glGetTexEnviv:
    push {r0-r3, lr}
    movw r0, #223
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTexEnvxv
    .type glGetTexEnvxv,%function
    .align 2
glGetTexEnvxv:
    push {r0-r3, lr}
    movw r0, #224
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTexGenfvOES
    .type glGetTexGenfvOES,%function
    .align 2
glGetTexGenfvOES:
    push {r0-r3, lr}
    movw r0, #225
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTexGenivOES
    .type glGetTexGenivOES,%function
    .align 2
glGetTexGenivOES:
    push {r0-r3, lr}
    movw r0, #226
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTexGenxvOES
    .type glGetTexGenxvOES,%function
    .align 2
glGetTexGenxvOES:
    push {r0-r3, lr}
    movw r0, #227
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTexParameterfv
    .type glGetTexParameterfv,%function
    .align 2
glGetTexParameterfv:
    push {r0-r3, lr}
    movw r0, #228
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTexParameteriv
    .type glGetTexParameteriv,%function
    .align 2
glGetTexParameteriv:
    push {r0-r3, lr}
    movw r0, #229
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTexParameterxv
    .type glGetTexParameterxv,%function
    .align 2
glGetTexParameterxv:
    push {r0-r3, lr}
    movw r0, #230
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetTransformFeedbackVarying
    .type glGetTransformFeedbackVarying,%function
    .align 2
glGetTransformFeedbackVarying:
    push {r0-r3, lr}
    movw r0, #231
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetUniformBlockIndex
    .type glGetUniformBlockIndex,%function
    .align 2
glGetUniformBlockIndex:
    push {r0-r3, lr}
    movw r0, #232
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetUniformIndices
    .type glGetUniformIndices,%function
    .align 2
glGetUniformIndices:
    push {r0-r3, lr}
    movw r0, #233
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetUniformLocation
    .type glGetUniformLocation,%function
    .align 2
glGetUniformLocation:
    push {r0-r3, lr}
    movw r0, #234
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetUniformfv
    .type glGetUniformfv,%function
    .align 2
glGetUniformfv:
    push {r0-r3, lr}
    movw r0, #235
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetUniformiv
    .type glGetUniformiv,%function
    .align 2
glGetUniformiv:
    push {r0-r3, lr}
    movw r0, #236
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetUniformuiv
    .type glGetUniformuiv,%function
    .align 2
glGetUniformuiv:
    push {r0-r3, lr}
    movw r0, #237
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetVertexAttribIiv
    .type glGetVertexAttribIiv,%function
    .align 2
glGetVertexAttribIiv:
    push {r0-r3, lr}
    movw r0, #238
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetVertexAttribIuiv
    .type glGetVertexAttribIuiv,%function
    .align 2
glGetVertexAttribIuiv:
    push {r0-r3, lr}
    movw r0, #239
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetVertexAttribPointerv
    .type glGetVertexAttribPointerv,%function
    .align 2
glGetVertexAttribPointerv:
    push {r0-r3, lr}
    movw r0, #240
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetVertexAttribfv
    .type glGetVertexAttribfv,%function
    .align 2
glGetVertexAttribfv:
    push {r0-r3, lr}
    movw r0, #241
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glGetVertexAttribiv
    .type glGetVertexAttribiv,%function
    .align 2
glGetVertexAttribiv:
    push {r0-r3, lr}
    movw r0, #242
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glHint
    .type glHint,%function
    .align 2
glHint:
    push {r0-r3, lr}
    movw r0, #243
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glInvalidateFramebuffer
    .type glInvalidateFramebuffer,%function
    .align 2
glInvalidateFramebuffer:
    push {r0-r3, lr}
    movw r0, #244
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glInvalidateSubFramebuffer
    .type glInvalidateSubFramebuffer,%function
    .align 2
glInvalidateSubFramebuffer:
    push {r0-r3, lr}
    movw r0, #245
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsBuffer
    .type glIsBuffer,%function
    .align 2
glIsBuffer:
    push {r0-r3, lr}
    movw r0, #246
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsEnabled
    .type glIsEnabled,%function
    .align 2
glIsEnabled:
    push {r0-r3, lr}
    movw r0, #247
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsFramebuffer
    .type glIsFramebuffer,%function
    .align 2
glIsFramebuffer:
    push {r0-r3, lr}
    movw r0, #248
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsFramebufferOES
    .type glIsFramebufferOES,%function
    .align 2
glIsFramebufferOES:
    push {r0-r3, lr}
    movw r0, #249
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsProgram
    .type glIsProgram,%function
    .align 2
glIsProgram:
    push {r0-r3, lr}
    movw r0, #250
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsQuery
    .type glIsQuery,%function
    .align 2
glIsQuery:
    push {r0-r3, lr}
    movw r0, #251
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsRenderbuffer
    .type glIsRenderbuffer,%function
    .align 2
glIsRenderbuffer:
    push {r0-r3, lr}
    movw r0, #252
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsRenderbufferOES
    .type glIsRenderbufferOES,%function
    .align 2
glIsRenderbufferOES:
    push {r0-r3, lr}
    movw r0, #253
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsSampler
    .type glIsSampler,%function
    .align 2
glIsSampler:
    push {r0-r3, lr}
    movw r0, #254
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsShader
    .type glIsShader,%function
    .align 2
glIsShader:
    push {r0-r3, lr}
    movw r0, #255
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsSync
    .type glIsSync,%function
    .align 2
glIsSync:
    push {r0-r3, lr}
    movw r0, #256
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsTexture
    .type glIsTexture,%function
    .align 2
glIsTexture:
    push {r0-r3, lr}
    movw r0, #257
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsTransformFeedback
    .type glIsTransformFeedback,%function
    .align 2
glIsTransformFeedback:
    push {r0-r3, lr}
    movw r0, #258
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsVertexArray
    .type glIsVertexArray,%function
    .align 2
glIsVertexArray:
    push {r0-r3, lr}
    movw r0, #259
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glIsVertexArrayOES
    .type glIsVertexArrayOES,%function
    .align 2
glIsVertexArrayOES:
    push {r0-r3, lr}
    movw r0, #260
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLightModelf
    .type glLightModelf,%function
    .align 2
glLightModelf:
    push {r0-r3, lr}
    movw r0, #261
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLightModelfv
    .type glLightModelfv,%function
    .align 2
glLightModelfv:
    push {r0-r3, lr}
    movw r0, #262
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLightModelx
    .type glLightModelx,%function
    .align 2
glLightModelx:
    push {r0-r3, lr}
    movw r0, #263
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLightModelxv
    .type glLightModelxv,%function
    .align 2
glLightModelxv:
    push {r0-r3, lr}
    movw r0, #264
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLightf
    .type glLightf,%function
    .align 2
glLightf:
    push {r0-r3, lr}
    movw r0, #265
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLightfv
    .type glLightfv,%function
    .align 2
glLightfv:
    push {r0-r3, lr}
    movw r0, #266
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLightx
    .type glLightx,%function
    .align 2
glLightx:
    push {r0-r3, lr}
    movw r0, #267
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLightxv
    .type glLightxv,%function
    .align 2
glLightxv:
    push {r0-r3, lr}
    movw r0, #268
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLineWidth
    .type glLineWidth,%function
    .align 2
glLineWidth:
    push {r0-r3, lr}
    movw r0, #269
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLineWidthx
    .type glLineWidthx,%function
    .align 2
glLineWidthx:
    push {r0-r3, lr}
    movw r0, #270
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLinkProgram
    .type glLinkProgram,%function
    .align 2
glLinkProgram:
    push {r0-r3, lr}
    movw r0, #271
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLoadIdentity
    .type glLoadIdentity,%function
    .align 2
glLoadIdentity:
    push {r0-r3, lr}
    movw r0, #272
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLoadMatrixf
    .type glLoadMatrixf,%function
    .align 2
glLoadMatrixf:
    push {r0-r3, lr}
    movw r0, #273
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glLoadMatrixx
    .type glLoadMatrixx,%function
    .align 2
glLoadMatrixx:
    push {r0-r3, lr}
    movw r0, #274
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMapBufferOES
    .type glMapBufferOES,%function
    .align 2
glMapBufferOES:
    push {r0-r3, lr}
    movw r0, #275
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMapBufferRange
    .type glMapBufferRange,%function
    .align 2
glMapBufferRange:
    push {r0-r3, lr}
    movw r0, #276
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMaterialf
    .type glMaterialf,%function
    .align 2
glMaterialf:
    push {r0-r3, lr}
    movw r0, #277
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMaterialfv
    .type glMaterialfv,%function
    .align 2
glMaterialfv:
    push {r0-r3, lr}
    movw r0, #278
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMaterialx
    .type glMaterialx,%function
    .align 2
glMaterialx:
    push {r0-r3, lr}
    movw r0, #279
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMaterialxv
    .type glMaterialxv,%function
    .align 2
glMaterialxv:
    push {r0-r3, lr}
    movw r0, #280
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMatrixMode
    .type glMatrixMode,%function
    .align 2
glMatrixMode:
    push {r0-r3, lr}
    movw r0, #281
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMultMatrixf
    .type glMultMatrixf,%function
    .align 2
glMultMatrixf:
    push {r0-r3, lr}
    movw r0, #282
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMultMatrixx
    .type glMultMatrixx,%function
    .align 2
glMultMatrixx:
    push {r0-r3, lr}
    movw r0, #283
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMultiTexCoord4f
    .type glMultiTexCoord4f,%function
    .align 2
glMultiTexCoord4f:
    push {r0-r3, lr}
    movw r0, #284
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glMultiTexCoord4x
    .type glMultiTexCoord4x,%function
    .align 2
glMultiTexCoord4x:
    push {r0-r3, lr}
    movw r0, #285
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glNormal3f
    .type glNormal3f,%function
    .align 2
glNormal3f:
    push {r0-r3, lr}
    movw r0, #286
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glNormal3x
    .type glNormal3x,%function
    .align 2
glNormal3x:
    push {r0-r3, lr}
    movw r0, #287
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glNormalPointer
    .type glNormalPointer,%function
    .align 2
glNormalPointer:
    push {r0-r3, lr}
    movw r0, #288
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glOrthof
    .type glOrthof,%function
    .align 2
glOrthof:
    push {r0-r3, lr}
    movw r0, #289
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glOrthox
    .type glOrthox,%function
    .align 2
glOrthox:
    push {r0-r3, lr}
    movw r0, #290
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPauseTransformFeedback
    .type glPauseTransformFeedback,%function
    .align 2
glPauseTransformFeedback:
    push {r0-r3, lr}
    movw r0, #291
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPointParameterf
    .type glPointParameterf,%function
    .align 2
glPointParameterf:
    push {r0-r3, lr}
    movw r0, #293
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPointParameterfv
    .type glPointParameterfv,%function
    .align 2
glPointParameterfv:
    push {r0-r3, lr}
    movw r0, #294
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPointParameterx
    .type glPointParameterx,%function
    .align 2
glPointParameterx:
    push {r0-r3, lr}
    movw r0, #295
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPointParameterxv
    .type glPointParameterxv,%function
    .align 2
glPointParameterxv:
    push {r0-r3, lr}
    movw r0, #296
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPointSize
    .type glPointSize,%function
    .align 2
glPointSize:
    push {r0-r3, lr}
    movw r0, #297
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPointSizex
    .type glPointSizex,%function
    .align 2
glPointSizex:
    push {r0-r3, lr}
    movw r0, #298
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPolygonOffset
    .type glPolygonOffset,%function
    .align 2
glPolygonOffset:
    push {r0-r3, lr}
    movw r0, #299
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPolygonOffsetx
    .type glPolygonOffsetx,%function
    .align 2
glPolygonOffsetx:
    push {r0-r3, lr}
    movw r0, #300
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPopMatrix
    .type glPopMatrix,%function
    .align 2
glPopMatrix:
    push {r0-r3, lr}
    movw r0, #301
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glProgramBinary
    .type glProgramBinary,%function
    .align 2
glProgramBinary:
    push {r0-r3, lr}
    movw r0, #302
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glProgramParameteri
    .type glProgramParameteri,%function
    .align 2
glProgramParameteri:
    push {r0-r3, lr}
    movw r0, #303
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glPushMatrix
    .type glPushMatrix,%function
    .align 2
glPushMatrix:
    push {r0-r3, lr}
    movw r0, #304
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glReadBuffer
    .type glReadBuffer,%function
    .align 2
glReadBuffer:
    push {r0-r3, lr}
    movw r0, #305
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glReadPixels
    .type glReadPixels,%function
    .align 2
glReadPixels:
    push {r0-r3, lr}
    movw r0, #306
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glReleaseShaderCompiler
    .type glReleaseShaderCompiler,%function
    .align 2
glReleaseShaderCompiler:
    push {r0-r3, lr}
    movw r0, #307
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glRenderbufferStorage
    .type glRenderbufferStorage,%function
    .align 2
glRenderbufferStorage:
    push {r0-r3, lr}
    movw r0, #308
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glRenderbufferStorageMultisample
    .type glRenderbufferStorageMultisample,%function
    .align 2
glRenderbufferStorageMultisample:
    push {r0-r3, lr}
    movw r0, #309
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glRenderbufferStorageOES
    .type glRenderbufferStorageOES,%function
    .align 2
glRenderbufferStorageOES:
    push {r0-r3, lr}
    movw r0, #310
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glResumeTransformFeedback
    .type glResumeTransformFeedback,%function
    .align 2
glResumeTransformFeedback:
    push {r0-r3, lr}
    movw r0, #311
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glRotatef
    .type glRotatef,%function
    .align 2
glRotatef:
    push {r0-r3, lr}
    movw r0, #312
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glRotatex
    .type glRotatex,%function
    .align 2
glRotatex:
    push {r0-r3, lr}
    movw r0, #313
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glSampleCoverage
    .type glSampleCoverage,%function
    .align 2
glSampleCoverage:
    push {r0-r3, lr}
    movw r0, #314
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glSampleCoveragex
    .type glSampleCoveragex,%function
    .align 2
glSampleCoveragex:
    push {r0-r3, lr}
    movw r0, #315
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glSamplerParameterf
    .type glSamplerParameterf,%function
    .align 2
glSamplerParameterf:
    push {r0-r3, lr}
    movw r0, #316
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glSamplerParameterfv
    .type glSamplerParameterfv,%function
    .align 2
glSamplerParameterfv:
    push {r0-r3, lr}
    movw r0, #317
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glSamplerParameteri
    .type glSamplerParameteri,%function
    .align 2
glSamplerParameteri:
    push {r0-r3, lr}
    movw r0, #318
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glSamplerParameteriv
    .type glSamplerParameteriv,%function
    .align 2
glSamplerParameteriv:
    push {r0-r3, lr}
    movw r0, #319
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glScalef
    .type glScalef,%function
    .align 2
glScalef:
    push {r0-r3, lr}
    movw r0, #320
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glScalex
    .type glScalex,%function
    .align 2
glScalex:
    push {r0-r3, lr}
    movw r0, #321
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glScissor
    .type glScissor,%function
    .align 2
glScissor:
    push {r0-r3, lr}
    movw r0, #322
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glShadeModel
    .type glShadeModel,%function
    .align 2
glShadeModel:
    push {r0-r3, lr}
    movw r0, #323
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glShaderBinary
    .type glShaderBinary,%function
    .align 2
glShaderBinary:
    push {r0-r3, lr}
    movw r0, #324
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glShaderSource
    .type glShaderSource,%function
    .align 2
glShaderSource:
    push {r0-r3, lr}
    movw r0, #325
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glStencilFunc
    .type glStencilFunc,%function
    .align 2
glStencilFunc:
    push {r0-r3, lr}
    movw r0, #326
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glStencilFuncSeparate
    .type glStencilFuncSeparate,%function
    .align 2
glStencilFuncSeparate:
    push {r0-r3, lr}
    movw r0, #327
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glStencilMask
    .type glStencilMask,%function
    .align 2
glStencilMask:
    push {r0-r3, lr}
    movw r0, #328
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glStencilMaskSeparate
    .type glStencilMaskSeparate,%function
    .align 2
glStencilMaskSeparate:
    push {r0-r3, lr}
    movw r0, #329
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glStencilOp
    .type glStencilOp,%function
    .align 2
glStencilOp:
    push {r0-r3, lr}
    movw r0, #330
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glStencilOpSeparate
    .type glStencilOpSeparate,%function
    .align 2
glStencilOpSeparate:
    push {r0-r3, lr}
    movw r0, #331
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexCoordPointer
    .type glTexCoordPointer,%function
    .align 2
glTexCoordPointer:
    push {r0-r3, lr}
    movw r0, #332
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexEnvf
    .type glTexEnvf,%function
    .align 2
glTexEnvf:
    push {r0-r3, lr}
    movw r0, #333
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexEnvfv
    .type glTexEnvfv,%function
    .align 2
glTexEnvfv:
    push {r0-r3, lr}
    movw r0, #334
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexEnvi
    .type glTexEnvi,%function
    .align 2
glTexEnvi:
    push {r0-r3, lr}
    movw r0, #335
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexEnviv
    .type glTexEnviv,%function
    .align 2
glTexEnviv:
    push {r0-r3, lr}
    movw r0, #336
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexEnvx
    .type glTexEnvx,%function
    .align 2
glTexEnvx:
    push {r0-r3, lr}
    movw r0, #337
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexEnvxv
    .type glTexEnvxv,%function
    .align 2
glTexEnvxv:
    push {r0-r3, lr}
    movw r0, #338
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexGenfOES
    .type glTexGenfOES,%function
    .align 2
glTexGenfOES:
    push {r0-r3, lr}
    movw r0, #339
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexGenfvOES
    .type glTexGenfvOES,%function
    .align 2
glTexGenfvOES:
    push {r0-r3, lr}
    movw r0, #340
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexGeniOES
    .type glTexGeniOES,%function
    .align 2
glTexGeniOES:
    push {r0-r3, lr}
    movw r0, #341
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexGenivOES
    .type glTexGenivOES,%function
    .align 2
glTexGenivOES:
    push {r0-r3, lr}
    movw r0, #342
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexGenxOES
    .type glTexGenxOES,%function
    .align 2
glTexGenxOES:
    push {r0-r3, lr}
    movw r0, #343
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexGenxvOES
    .type glTexGenxvOES,%function
    .align 2
glTexGenxvOES:
    push {r0-r3, lr}
    movw r0, #344
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexImage3D
    .type glTexImage3D,%function
    .align 2
glTexImage3D:
    push {r0-r3, lr}
    movw r0, #346
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexParameterf
    .type glTexParameterf,%function
    .align 2
glTexParameterf:
    push {r0-r3, lr}
    movw r0, #347
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexParameterfv
    .type glTexParameterfv,%function
    .align 2
glTexParameterfv:
    push {r0-r3, lr}
    movw r0, #348
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexParameteri
    .type glTexParameteri,%function
    .align 2
glTexParameteri:
    push {r0-r3, lr}
    movw r0, #349
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexParameteriv
    .type glTexParameteriv,%function
    .align 2
glTexParameteriv:
    push {r0-r3, lr}
    movw r0, #350
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexParameterx
    .type glTexParameterx,%function
    .align 2
glTexParameterx:
    push {r0-r3, lr}
    movw r0, #351
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexParameterxv
    .type glTexParameterxv,%function
    .align 2
glTexParameterxv:
    push {r0-r3, lr}
    movw r0, #352
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexStorage2D
    .type glTexStorage2D,%function
    .align 2
glTexStorage2D:
    push {r0-r3, lr}
    movw r0, #353
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexStorage3D
    .type glTexStorage3D,%function
    .align 2
glTexStorage3D:
    push {r0-r3, lr}
    movw r0, #354
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTexSubImage3D
    .type glTexSubImage3D,%function
    .align 2
glTexSubImage3D:
    push {r0-r3, lr}
    movw r0, #356
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTransformFeedbackVaryings
    .type glTransformFeedbackVaryings,%function
    .align 2
glTransformFeedbackVaryings:
    push {r0-r3, lr}
    movw r0, #357
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTranslatef
    .type glTranslatef,%function
    .align 2
glTranslatef:
    push {r0-r3, lr}
    movw r0, #358
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glTranslatex
    .type glTranslatex,%function
    .align 2
glTranslatex:
    push {r0-r3, lr}
    movw r0, #359
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform1f
    .type glUniform1f,%function
    .align 2
glUniform1f:
    push {r0-r3, lr}
    movw r0, #360
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform1fv
    .type glUniform1fv,%function
    .align 2
glUniform1fv:
    push {r0-r3, lr}
    movw r0, #361
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform1i
    .type glUniform1i,%function
    .align 2
glUniform1i:
    push {r0-r3, lr}
    movw r0, #362
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform1iv
    .type glUniform1iv,%function
    .align 2
glUniform1iv:
    push {r0-r3, lr}
    movw r0, #363
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform1ui
    .type glUniform1ui,%function
    .align 2
glUniform1ui:
    push {r0-r3, lr}
    movw r0, #364
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform1uiv
    .type glUniform1uiv,%function
    .align 2
glUniform1uiv:
    push {r0-r3, lr}
    movw r0, #365
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform2f
    .type glUniform2f,%function
    .align 2
glUniform2f:
    push {r0-r3, lr}
    movw r0, #366
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform2fv
    .type glUniform2fv,%function
    .align 2
glUniform2fv:
    push {r0-r3, lr}
    movw r0, #367
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform2i
    .type glUniform2i,%function
    .align 2
glUniform2i:
    push {r0-r3, lr}
    movw r0, #368
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform2iv
    .type glUniform2iv,%function
    .align 2
glUniform2iv:
    push {r0-r3, lr}
    movw r0, #369
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform2ui
    .type glUniform2ui,%function
    .align 2
glUniform2ui:
    push {r0-r3, lr}
    movw r0, #370
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform2uiv
    .type glUniform2uiv,%function
    .align 2
glUniform2uiv:
    push {r0-r3, lr}
    movw r0, #371
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform3f
    .type glUniform3f,%function
    .align 2
glUniform3f:
    push {r0-r3, lr}
    movw r0, #372
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform3fv
    .type glUniform3fv,%function
    .align 2
glUniform3fv:
    push {r0-r3, lr}
    movw r0, #373
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform3i
    .type glUniform3i,%function
    .align 2
glUniform3i:
    push {r0-r3, lr}
    movw r0, #374
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform3iv
    .type glUniform3iv,%function
    .align 2
glUniform3iv:
    push {r0-r3, lr}
    movw r0, #375
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform3ui
    .type glUniform3ui,%function
    .align 2
glUniform3ui:
    push {r0-r3, lr}
    movw r0, #376
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform3uiv
    .type glUniform3uiv,%function
    .align 2
glUniform3uiv:
    push {r0-r3, lr}
    movw r0, #377
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform4f
    .type glUniform4f,%function
    .align 2
glUniform4f:
    push {r0-r3, lr}
    movw r0, #378
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform4fv
    .type glUniform4fv,%function
    .align 2
glUniform4fv:
    push {r0-r3, lr}
    movw r0, #379
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform4i
    .type glUniform4i,%function
    .align 2
glUniform4i:
    push {r0-r3, lr}
    movw r0, #380
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform4iv
    .type glUniform4iv,%function
    .align 2
glUniform4iv:
    push {r0-r3, lr}
    movw r0, #381
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform4ui
    .type glUniform4ui,%function
    .align 2
glUniform4ui:
    push {r0-r3, lr}
    movw r0, #382
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniform4uiv
    .type glUniform4uiv,%function
    .align 2
glUniform4uiv:
    push {r0-r3, lr}
    movw r0, #383
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformBlockBinding
    .type glUniformBlockBinding,%function
    .align 2
glUniformBlockBinding:
    push {r0-r3, lr}
    movw r0, #384
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformMatrix2fv
    .type glUniformMatrix2fv,%function
    .align 2
glUniformMatrix2fv:
    push {r0-r3, lr}
    movw r0, #385
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformMatrix2x3fv
    .type glUniformMatrix2x3fv,%function
    .align 2
glUniformMatrix2x3fv:
    push {r0-r3, lr}
    movw r0, #386
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformMatrix2x4fv
    .type glUniformMatrix2x4fv,%function
    .align 2
glUniformMatrix2x4fv:
    push {r0-r3, lr}
    movw r0, #387
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformMatrix3fv
    .type glUniformMatrix3fv,%function
    .align 2
glUniformMatrix3fv:
    push {r0-r3, lr}
    movw r0, #388
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformMatrix3x2fv
    .type glUniformMatrix3x2fv,%function
    .align 2
glUniformMatrix3x2fv:
    push {r0-r3, lr}
    movw r0, #389
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformMatrix3x4fv
    .type glUniformMatrix3x4fv,%function
    .align 2
glUniformMatrix3x4fv:
    push {r0-r3, lr}
    movw r0, #390
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformMatrix4fv
    .type glUniformMatrix4fv,%function
    .align 2
glUniformMatrix4fv:
    push {r0-r3, lr}
    movw r0, #391
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformMatrix4x2fv
    .type glUniformMatrix4x2fv,%function
    .align 2
glUniformMatrix4x2fv:
    push {r0-r3, lr}
    movw r0, #392
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUniformMatrix4x3fv
    .type glUniformMatrix4x3fv,%function
    .align 2
glUniformMatrix4x3fv:
    push {r0-r3, lr}
    movw r0, #393
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUnmapBuffer
    .type glUnmapBuffer,%function
    .align 2
glUnmapBuffer:
    push {r0-r3, lr}
    movw r0, #394
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUnmapBufferOES
    .type glUnmapBufferOES,%function
    .align 2
glUnmapBufferOES:
    push {r0-r3, lr}
    movw r0, #395
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glUseProgram
    .type glUseProgram,%function
    .align 2
glUseProgram:
    push {r0-r3, lr}
    movw r0, #396
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glValidateProgram
    .type glValidateProgram,%function
    .align 2
glValidateProgram:
    push {r0-r3, lr}
    movw r0, #397
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttrib1f
    .type glVertexAttrib1f,%function
    .align 2
glVertexAttrib1f:
    push {r0-r3, lr}
    movw r0, #398
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttrib1fv
    .type glVertexAttrib1fv,%function
    .align 2
glVertexAttrib1fv:
    push {r0-r3, lr}
    movw r0, #399
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttrib2f
    .type glVertexAttrib2f,%function
    .align 2
glVertexAttrib2f:
    push {r0-r3, lr}
    movw r0, #400
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttrib2fv
    .type glVertexAttrib2fv,%function
    .align 2
glVertexAttrib2fv:
    push {r0-r3, lr}
    movw r0, #401
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttrib3f
    .type glVertexAttrib3f,%function
    .align 2
glVertexAttrib3f:
    push {r0-r3, lr}
    movw r0, #402
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttrib3fv
    .type glVertexAttrib3fv,%function
    .align 2
glVertexAttrib3fv:
    push {r0-r3, lr}
    movw r0, #403
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttrib4f
    .type glVertexAttrib4f,%function
    .align 2
glVertexAttrib4f:
    push {r0-r3, lr}
    movw r0, #404
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttrib4fv
    .type glVertexAttrib4fv,%function
    .align 2
glVertexAttrib4fv:
    push {r0-r3, lr}
    movw r0, #405
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttribDivisor
    .type glVertexAttribDivisor,%function
    .align 2
glVertexAttribDivisor:
    push {r0-r3, lr}
    movw r0, #406
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttribI4i
    .type glVertexAttribI4i,%function
    .align 2
glVertexAttribI4i:
    push {r0-r3, lr}
    movw r0, #407
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttribI4iv
    .type glVertexAttribI4iv,%function
    .align 2
glVertexAttribI4iv:
    push {r0-r3, lr}
    movw r0, #408
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttribI4ui
    .type glVertexAttribI4ui,%function
    .align 2
glVertexAttribI4ui:
    push {r0-r3, lr}
    movw r0, #409
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttribI4uiv
    .type glVertexAttribI4uiv,%function
    .align 2
glVertexAttribI4uiv:
    push {r0-r3, lr}
    movw r0, #410
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttribIPointer
    .type glVertexAttribIPointer,%function
    .align 2
glVertexAttribIPointer:
    push {r0-r3, lr}
    movw r0, #411
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexAttribPointer
    .type glVertexAttribPointer,%function
    .align 2
glVertexAttribPointer:
    push {r0-r3, lr}
    movw r0, #412
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glVertexPointer
    .type glVertexPointer,%function
    .align 2
glVertexPointer:
    push {r0-r3, lr}
    movw r0, #413
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glViewport
    .type glViewport,%function
    .align 2
glViewport:
    push {r0-r3, lr}
    movw r0, #414
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
    .global glWaitSync
    .type glWaitSync,%function
    .align 2
glWaitSync:
    push {r0-r3, lr}
    movw r0, #415
    bl aemu_split_resolve
    mov r12, r0
    pop {r0-r3, lr}
    bx r12
