#!/bin/sh
# 把历史里的旧 bootstrap 统一到一个版本 —— filter-branch 的 --index-filter。
#
# ## 背景
#
# app/src/main/assets/bootstrap-aarch64.zip 约 33MB，是**压缩数据**，git 对它做不出
# 有效 delta，所以历史里每留一个不同版本就实打实再占一份。
#
# 实测（`.git` 68M → 37M，即省下约 31MB）：
#
#   版本                                       原始大小      处理
#   ----                                       --------      ----
#   708f3d388005b65c9ad34a727e901ba7f9ce450d   32,176,084    统一到 KEEP
#   572c2ba92141e996d8c0bbcb8510839ee26be7eb   33,485,088    统一到 KEEP
#   ee5d3753d1cf305c968640da4dee46895984cd80   33,484,104    KEEP（保留）
#   4cb25afc782c762e616e77a223ed418b7068da89   33,119,132    **不替换**
#
# 关于最后一个（`4cb25af`）—— 它是历史上最早的**真实** bootstrap（2026-09-26 那 8 条
# 提交用它），不是占位文件。不替换它的理由不是"它没用"，而是**性价比**：在重写后的
# pack 里它已经被存成相对 KEEP 的 delta，实际只占 2,425,858 字节（约 2.3MB）：

    # 注意：这里必须用 cat-file 看大小，
    # verify-pack -v 的 size 列对 **deltified 对象**给的是 delta 的大小，不是对象大小。
    git cat-file -s 4cb25afc782c762e616e77a223ed418b7068da89     # 33119132
    git verify-pack -v .git/objects/pack/*.idx \
      | grep '^4cb25afc782c762e616e77a223ed418b7068da89 '        # ... blob 2574417 2425858 ... depth 1 base ee5d3753...

# 为了 2.3MB 抹掉一个真实的历史版本不划算，所以留着。
#
# ⚠️ 这个脚本最早把 `4cb25af` 当成"94KB 占位桩"，就是因为**误读了 verify-pack 的
#    delta 列**（把 delta 大小当成了对象大小）。凡是判断"某个对象值不值得处理"，
#    一律用 `git cat-file -s <sha>` 取大小。这一条值得单独记下来，
#    因为它会让人做出方向相反的决策。
#
# ## 用法（仓库根目录，--index-filter 里必须用绝对路径）
#
#   FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f \
#     --index-filter "sh $PWD/tools/rewrite-history-index-filter.sh" \
#     --env-filter 'export GIT_AUTHOR_NAME=zhizhu0002;
#                   export GIT_AUTHOR_EMAIL=zhizhu0002@users.noreply.github.com;
#                   export GIT_COMMITTER_NAME=zhizhu0002;
#                   export GIT_COMMITTER_EMAIL=zhizhu0002@users.noreply.github.com' \
#     -- --all
#
#   # 然后清掉 filter-branch 自留的备份引用与 reflog，否则对象不会被回收、体积也不会降
#   git for-each-ref --format='%(refname)' refs/original/ | xargs -r -n1 git update-ref -d
#   git reflog expire --expire=now --all
#   git gc --prune=now --aggressive
#
# 建议先在克隆里干跑一遍再动真实仓库（`git clone --no-hardlinks . /tmp/x`），
# 因为改写会改掉全部提交哈希。
#
# ⚠️ 做完必须核对 `git rev-parse HEAD^{tree}` 与改写前一致 —— 内容一个字节都不该变。
#
# ⚠️ --env-filter 里的身份是**硬编码**的。换人接手时要改这里，
#    否则会把历史又搅成两种身份。
set -e

PATHSPEC=app/src/main/assets/bootstrap-aarch64.zip
OLD_A=708f3d388005b65c9ad34a727e901ba7f9ce450d   # 32,176,084 字节
OLD_B=572c2ba92141e996d8c0bbcb8510839ee26be7eb   # 33,485,088 字节
KEEP=ee5d3753d1cf305c968640da4dee46895984cd80    # 33,484,104 字节

# filter-branch 会把「正在重写的那个提交」的索引放到 GIT_INDEX_FILE，
# git ls-files / git update-index 都遵守它，所以这里读写的正是该提交的索引。
cur=$(git ls-files -s -- "$PATHSPEC" | awk '{print $2}')
if [ "$cur" = "$OLD_A" ] || [ "$cur" = "$OLD_B" ]; then
    git update-index --cacheinfo 100644,"$KEEP","$PATHSPEC"
fi
