#!@PREFIX@/bin/bash
set -e
mapfile -t debs
count=${#debs[@]}
i=0
for deb in "${debs[@]}"; do case "$deb" in *.deb) i=$((i+1)); echo "[ZhiCode] 安装包兼容处理 $i/$count: $(basename "$deb")" >&2; "@PREFIX@/libexec/@SLUG@/@SLUG@-deb-patch" "$deb" ;; esac; done
