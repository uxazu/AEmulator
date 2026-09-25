@ Пустая программа для гостя: сразу exit(0). Ставится вместо iptables/ip6tables — netfilter в эмуляторе нет,
@ а netd считает отказ iptables фатальным для NetworkManagementService.
    .syntax unified
    .arm
    .text
    .globl _start
_start:
    mov r0, #0
    mov r7, #1
    svc #0
