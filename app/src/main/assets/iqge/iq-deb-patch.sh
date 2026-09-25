#!@PREFIX@/bin/bash
# 把 Termux 官方 .deb 里写死的 @OLD@ 改写成 @NEW@。
# 每个包打一次标记（<deb>.iqge-ok），签名变了才会重跑。
set -euo pipefail
OLD='@OLD@'
NEW='@NEW@'
sig(){ stat -c '%s:%Y' "$1" 2>/dev/null || echo missing; }
patched(){ deb="$1"; marker="$deb.iqge-ok"; [ -f "$marker" ] && [ "$(cat "$marker" 2>/dev/null || true)" = "$(sig "$deb")" ]; }
run_stage(){ label="$1"; shift; "$@" & pid=$!; ( sleep 10; while kill -0 "$pid" 2>/dev/null; do echo "[IQGE] $label，仍在处理中…" >&2; sleep 10; done ) & hb=$!; set +e; wait "$pid"; rc=$?; set -e; kill "$hb" 2>/dev/null || true; wait "$hb" 2>/dev/null || true; return $rc; }
patch_file(){ f="$1"; [ -f "$f" ] || return 0; mode=$(stat -c %a "$f" 2>/dev/null || echo 600); LC_ALL=C sed -i "s|$OLD|$NEW|g" "$f"; chmod "$mode" "$f" 2>/dev/null || true; }
patch_deb(){ ( deb="$1"; [ -f "$deb" ] || exit 0; case "$deb" in *.deb) ;; *) exit 0;; esac; if patched "$deb"; then echo "[IQGE] 已缓存兼容转换: $(basename "$deb")" >&2; exit 0; fi; name=$(basename "$deb"); start=$(date +%s); tmp="${TMPDIR:-/tmp}/iqge-deb-$$-$RANDOM"; out="$deb.iqge-new"; cleanup(){ rm -rf "$tmp"; rm -f "$out"; }; trap cleanup EXIT INT TERM; cleanup; mkdir -p "$tmp"; echo "[IQGE] 正在准备安装包: $name" >&2; run_stage "正在解包 $name" dpkg-deb -R "$deb" "$tmp"; echo "[IQGE] 正在转换 Termux 路径: $name" >&2; while IFS= read -r -d '' f; do if LC_ALL=C grep -aqF "$OLD" "$f" 2>/dev/null; then patch_file "$f"; fi; done < <(find "$tmp" -type f -print0); find "$tmp" -type l -print0 | while IFS= read -r -d '' l; do t=$(readlink "$l"); n=${t//$OLD/$NEW}; if [ "$n" != "$t" ]; then rm -f "$l"; ln -s "$n" "$l"; fi; done; if [ -d "$tmp/@OLDDIRFRAG@" ]; then mkdir -p "$tmp/@NEWPARENTFRAG@"; rm -rf "$tmp/@NEWDIRFRAG@"; mv "$tmp/@OLDDIRFRAG@" "$tmp/@NEWDIRFRAG@"; fi; for sc in preinst postinst prerm postrm config; do [ -f "$tmp/DEBIAN/$sc" ] && chmod 755 "$tmp/DEBIAN/$sc"; done; rm -f "$tmp/DEBIAN/md5sums"; echo "[IQGE] 正在快速重新打包: $name" >&2; if ! run_stage "正在重新打包 $name" dpkg-deb -Zgzip -z1 -b "$tmp" "$out" >/dev/null; then rm -f "$out"; run_stage "正在重新打包 $name" dpkg-deb -b "$tmp" "$out" >/dev/null; fi; mv -f "$out" "$deb"; rm -rf "$tmp"; sig "$deb" > "$deb.iqge-ok"; trap - EXIT INT TERM; elapsed=$(( $(date +%s) - start )); echo "[IQGE] $name 转换完成，用时 ${elapsed}s" >&2; ) ; }
for deb in "$@"; do patch_deb "$deb"; done
