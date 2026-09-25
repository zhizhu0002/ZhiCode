#!@PREFIX@/bin/bash
# IQGE_DPKG_WRAPPER
set -e
REAL='@PREFIX@/bin/dpkg.iqge-real'
PATCH='@PREFIX@/bin/iq-patch-deb'
for a in "$@"; do case "$a" in *.deb) [ -f "$a" ] && "$PATCH" "$a" ;; esac; done
exec "$REAL" "$@"
