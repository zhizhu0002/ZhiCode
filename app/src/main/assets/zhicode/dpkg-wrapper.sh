#!@PREFIX@/bin/bash
# ZHICODE_DPKG_WRAPPER
set -e
REAL='@PREFIX@/bin/dpkg.@SLUG@-real'
PATCH='@PREFIX@/bin/@SLUG@-patch-deb'
for a in "$@"; do case "$a" in *.deb) [ -f "$a" ] && "$PATCH" "$a" ;; esac; done
exec "$REAL" "$@"
