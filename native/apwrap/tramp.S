@ Трамплины: r0 = обёртка -> r0 = настоящая политика, переход в её слот i (остальные аргументы не трогаем).
    .syntax unified
    .thumb
    .text
    .globl aemu_ap_tramp_table
    .hidden aemu_ap_tramp_table
    .p2align 4
aemu_ap_tramp_table:
    .irp i, 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39
    .p2align 4
    ldr.w r0, [r0, #160]
    ldr.w r12, [r0, #(\i * 4)]
    bx r12
    .endr
