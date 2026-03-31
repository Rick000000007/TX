#!/bin/sh

ROOTFS=/data/data/com.tx.terminal/files/rootfs

exec $ROOTFS/bin/proot \
    -0 \
    -r $ROOTFS \
    -b /dev \
    -b /proc \
    -b /sys \
    -w /root \
    /bin/sh
