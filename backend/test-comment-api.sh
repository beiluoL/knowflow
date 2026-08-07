#!/bin/bash
# 社区评论功能接口自测脚本
# 覆盖：登录 -> 评论列表 -> 发表 -> 回复 -> 点赞幂等 -> 编辑 -> 权限校验 -> 删除级联 -> 计数一致性
BASE="http://localhost:8080"
PASS=0
FAIL=0

ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $1 | 实际: $2"; FAIL=$((FAIL+1)); }
sect() { echo ""; echo "━━━ $1 ━━━"; }

jqv() { python3 -c "import sys,json;d=json.load(sys.stdin);
import functools
p='$1'.split('.')
for k in p:
    if k=='' : continue
    if isinstance(d,list): d=d[int(k)]
    else: d=d.get(k) if d else None
print(d if d is not None else '')" 2>/dev/null; }

login() {
  curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"admin123\"}" | jqv 'data.token'
}

sect "0. 登录取 Token"
T_USER1=$(login user1)
T_USER2=$(login user2)
T_ADMIN=$(login admin)
[ -n "$T_USER1" ] && ok "user1 登录成功" || bad "user1 登录" "空 token"
[ -n "$T_USER2" ] && ok "user2 登录成功" || bad "user2 登录" "空 token"
[ -n "$T_ADMIN" ] && ok "admin 登录成功" || bad "admin 登录" "空 token"

AU1="-H \"Authorization: Bearer $T_USER1\""
POST_ID=1

sect "1. 评论列表（匿名可访问，验证公开 GET 放行）"
R=$(curl -s "$BASE/api/community/comments/post/$POST_ID?pageNum=1&pageSize=10&sortBy=latest")
CODE=$(echo "$R" | jqv 'code')
[ "$CODE" = "200" ] && ok "匿名 GET 评论列表 200" || bad "匿名 GET 评论列表" "$R"
BASE_TOTAL=$(echo "$R" | jqv 'data.total')
echo "  当前评论总数(顶级): $BASE_TOTAL"

sect "1b. 排序参数校验"
for S in latest hottest oldest; do
  C=$(curl -s "$BASE/api/community/comments/post/$POST_ID?sortBy=$S" | jqv 'code')
  [ "$C" = "200" ] && ok "sortBy=$S 正常" || bad "sortBy=$S" "$C"
done
C=$(curl -s "$BASE/api/community/comments/post/$POST_ID?pageNum=0&pageSize=999" | jqv 'code')
[ "$C" = "200" ] && ok "越界分页参数被纠正（未报错）" || bad "越界分页参数" "$C"

sect "2. 未登录发表评论应被拒绝"
R=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/community/comments" \
  -H 'Content-Type: application/json' -d "{\"postId\":$POST_ID,\"content\":\"匿名尝试\"}")
[ "$R" = "401" ] || [ "$R" = "403" ] && ok "匿名发评论被拒（HTTP ${R}）" || bad "匿名发评论应被拒" "HTTP $R"

sect "3. 帖子计数基线"
P_BEFORE=$(curl -s "$BASE/api/community/posts/$POST_ID" | jqv 'data.commentCount')
echo "  帖子 commentCount 基线: $P_BEFORE"

sect "4. user1 发表顶级评论"
R=$(curl -s -X POST "$BASE/api/community/comments" -H "Authorization: Bearer $T_USER1" \
  -H 'Content-Type: application/json' \
  -d "{\"postId\":$POST_ID,\"content\":\"【自测】这是 user1 的顶级评论\"}")
C1=$(echo "$R" | jqv 'data.id')
[ -n "$C1" ] && ok "发表成功 commentId=$C1" || bad "发表顶级评论" "$R"
AUTHOR=$(echo "$R" | jqv 'data.nickname')
CANEDIT=$(echo "$R" | jqv 'data.canEdit')
echo "  作者=$AUTHOR canEdit=$CANEDIT"
[ "$CANEDIT" = "True" ] && ok "作者 canEdit=true" || bad "作者 canEdit" "$CANEDIT"

P_AFTER=$(curl -s "$BASE/api/community/posts/$POST_ID" | jqv 'data.commentCount')
[ "$P_AFTER" = "$((P_BEFORE+1))" ] && ok "帖子 commentCount 原子+1 (${P_BEFORE}→$P_AFTER)" || bad "commentCount 未+1" "${P_BEFORE}→$P_AFTER"

sect "5. 内容校验（空内容 / 超长）"
R=$(curl -s -X POST "$BASE/api/community/comments" -H "Authorization: Bearer $T_USER1" \
  -H 'Content-Type: application/json' -d "{\"postId\":$POST_ID,\"content\":\"   \"}")
C=$(echo "$R" | jqv 'code')
[ "$C" != "200" ] && ok "空白内容被拒（code=${C}）" || bad "空白内容应被拒" "$R"

LONG=$(python3 -c "print('x'*1001)")
R=$(curl -s -X POST "$BASE/api/community/comments" -H "Authorization: Bearer $T_USER1" \
  -H 'Content-Type: application/json' -d "{\"postId\":$POST_ID,\"content\":\"$LONG\"}")
C=$(echo "$R" | jqv 'code')
[ "$C" != "200" ] && ok "超长内容(1001字)被拒（code=${C}）" || bad "超长内容应被拒" "$C"

sect "6. user2 回复 user1 的评论"
R=$(curl -s -X POST "$BASE/api/community/comments" -H "Authorization: Bearer $T_USER2" \
  -H 'Content-Type: application/json' \
  -d "{\"postId\":$POST_ID,\"parentId\":$C1,\"replyToCommentId\":$C1,\"content\":\"【自测】user2 回复你\"}")
C2=$(echo "$R" | jqv 'data.id')
RT=$(echo "$R" | jqv 'data.replyToNickname')
[ -n "$C2" ] && ok "回复成功 replyId=$C2 (回复给: $RT)" || bad "发表回复" "$R"

R=$(curl -s "$BASE/api/community/comments/$C1/replies?pageNum=1&pageSize=10")
RC=$(echo "$R" | jqv 'data.total')
[ "$RC" -ge "1" ] && ok "回复列表返回 total=$RC" || bad "回复列表" "$R"

sect "6b. 二级回复应被扁平化到顶级评论下"
R=$(curl -s -X POST "$BASE/api/community/comments" -H "Authorization: Bearer $T_USER1" \
  -H 'Content-Type: application/json' \
  -d "{\"postId\":$POST_ID,\"parentId\":$C2,\"replyToCommentId\":$C2,\"content\":\"【自测】user1 回复 user2 的回复\"}")
C3=$(echo "$R" | jqv 'data.id')
PARENT3=$(echo "$R" | jqv 'data.parentId')
[ "$PARENT3" = "$C1" ] && ok "二级回复扁平化 parentId=${PARENT3}（=顶级评论，未无限嵌套）" || bad "扁平化失败" "parentId=$PARENT3 期望 $C1"

sect "7. 评论点赞（幂等切换）"
R=$(curl -s -X POST "$BASE/api/community/comments/$C1/like" -H "Authorization: Bearer $T_USER2")
L1=$(echo "$R" | jqv 'data.liked'); N1=$(echo "$R" | jqv 'data.likeCount')
[ "$L1" = "True" ] && [ "$N1" = "1" ] && ok "首次点赞 liked=true count=1" || bad "首次点赞" "liked=$L1 count=$N1"

R=$(curl -s -X POST "$BASE/api/community/comments/$C1/like" -H "Authorization: Bearer $T_USER2")
L2=$(echo "$R" | jqv 'data.liked'); N2=$(echo "$R" | jqv 'data.likeCount')
[ "$L2" = "False" ] && [ "$N2" = "0" ] && ok "再次点击取消点赞 liked=false count=0" || bad "取消点赞" "liked=$L2 count=$N2"

curl -s -X POST "$BASE/api/community/comments/$C1/like" -H "Authorization: Bearer $T_USER2" >/dev/null
R=$(curl -s "$BASE/api/community/comments/post/$POST_ID?sortBy=latest&pageSize=50" -H "Authorization: Bearer $T_USER2")
MYLIKE=$(python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['records']
t=[x for x in d if x['id']==$C1]
print(t[0]['liked'] if t else 'NOTFOUND')" <<< "$R")
[ "$MYLIKE" = "True" ] && ok "列表回显 liked=true（按用户隔离）" || bad "列表 liked 回显" "$MYLIKE"

MYLIKE_ANON=$(python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['records']
t=[x for x in d if x['id']==$C1]
print(t[0]['liked'] if t else 'NOTFOUND')" <<< "$(curl -s "$BASE/api/community/comments/post/$POST_ID?sortBy=latest&pageSize=50")")
[ "$MYLIKE_ANON" = "False" ] && ok "匿名访问 liked=false（未串号）" || bad "匿名 liked 隔离" "$MYLIKE_ANON"

sect "8. 编辑评论"
R=$(curl -s -X PUT "$BASE/api/community/comments/$C1" -H "Authorization: Bearer $T_USER1" \
  -H 'Content-Type: application/json' -d '{"content":"【自测】user1 编辑后的内容"}')
NEW=$(echo "$R" | jqv 'data.content')
[ "$NEW" = "【自测】user1 编辑后的内容" ] && ok "作者编辑成功" || bad "作者编辑" "$R"

R=$(curl -s -X PUT "$BASE/api/community/comments/$C1" -H "Authorization: Bearer $T_USER2" \
  -H 'Content-Type: application/json' -d '{"content":"越权编辑"}')
C=$(echo "$R" | jqv 'code')
[ "$C" = "403" ] && ok "非作者编辑被拒 403（code=${C}）" || bad "非作者编辑应被拒" "$R"

sect "9. 删除权限"
R=$(curl -s -X DELETE "$BASE/api/community/comments/$C2" -H "Authorization: Bearer $T_USER1")
C=$(echo "$R" | jqv 'code')
[ "$C" = "403" ] && ok "非作者删除他人回复被拒 403（code=${C}）" || bad "非作者删除应被拒" "$R"

R=$(curl -s -X DELETE "$BASE/api/community/comments/$C2" -H "Authorization: Bearer $T_USER2")
C=$(echo "$R" | jqv 'code')
[ "$C" = "200" ] && ok "作者删除自己的回复成功" || bad "作者删除自己回复" "$R"

sect "10. 删除顶级评论 → 级联删除子回复 + 计数回退"
P_MID=$(curl -s "$BASE/api/community/posts/$POST_ID" | jqv 'data.commentCount')
echo "  删除前帖子 commentCount = $P_MID （C1 + 剩余子回复 C3）"
R=$(curl -s -X DELETE "$BASE/api/community/comments/$C1" -H "Authorization: Bearer $T_ADMIN")
C=$(echo "$R" | jqv 'code')
[ "$C" = "200" ] && ok "管理员删除任意评论成功" || bad "管理员删除" "$R"

P_END=$(curl -s "$BASE/api/community/posts/$POST_ID" | jqv 'data.commentCount')
[ "$P_END" = "$P_BEFORE" ] && ok "级联删除后 commentCount 精确回退到基线 (${P_MID}→$P_END, 基线 $P_BEFORE)" \
  || bad "计数回退不精确" "${P_MID}→$P_END 期望 $P_BEFORE"

C=$(curl -s "$BASE/api/community/comments/$C1/replies" | jqv 'code')
[ "$C" = "404" ] && ok "父评论已删，查其回复返回 404（符合预期）" || bad "已删父评论的 replies 响应码" "$C"

# 子回复 C3 应从帖子评论流中彻底消失（含预加载 replies 里）
GONE=$(curl -s "$BASE/api/community/comments/post/$POST_ID?pageSize=50" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']['records']
ids=set()
for r in d:
    ids.add(r['id'])
    for x in (r.get('replies') or []): ids.add(x['id'])
print('GONE' if $C3 not in ids and $C1 not in ids else 'STILL_THERE')")
[ "$GONE" = "GONE" ] && ok "子回复 C3 与顶级 C1 均已从评论流消失（级联逻辑删除生效）" || bad "级联删除未生效" "$GONE"

sect "10b. 点赞记录物理清理（防唯一键残留）"
CT=$(curl -s -X POST "$BASE/api/community/comments" -H "Authorization: Bearer $T_USER1" \
  -H 'Content-Type: application/json' -d "{\"postId\":$POST_ID,\"content\":\"【自测】点赞往返测试\"}" | jqv 'data.id')
for round in 1 2 3; do
  R=$(curl -s -X POST "$BASE/api/community/comments/$CT/like" -H "Authorization: Bearer $T_USER2")
  L=$(echo "$R" | jqv 'data.liked'); N=$(echo "$R" | jqv 'data.likeCount')
  EXP_L=$([ $((round % 2)) -eq 1 ] && echo "True" || echo "False")
  EXP_N=$([ $((round % 2)) -eq 1 ] && echo "1" || echo "0")
  [ "$L" = "$EXP_L" ] && [ "$N" = "$EXP_N" ] \
    && ok "第${round}次 toggle: liked=$L count=${N}（符合预期）" \
    || bad "第${round}次 toggle" "liked=$L count=$N 期望 liked=$EXP_L count=$EXP_N"
done
curl -s -X DELETE "$BASE/api/community/comments/$CT" -H "Authorization: Bearer $T_USER1" >/dev/null

sect "11. 非法/不存在资源"
R=$(curl -s -X POST "$BASE/api/community/comments" -H "Authorization: Bearer $T_USER1" \
  -H 'Content-Type: application/json' -d '{"postId":999999,"content":"给不存在的帖子评论"}')
C=$(echo "$R" | jqv 'code')
[ "$C" != "200" ] && ok "对不存在帖子评论被拒（code=${C}）" || bad "不存在帖子应被拒" "$R"

R=$(curl -s -X POST "$BASE/api/community/comments/999999/like" -H "Authorization: Bearer $T_USER1")
C=$(echo "$R" | jqv 'code')
[ "$C" != "200" ] && ok "点赞不存在评论被拒（code=${C}）" || bad "点赞不存在评论应被拒" "$R"

sect "12. 兼容端点（旧路由仍可用）"
C=$(curl -s "$BASE/api/community/posts/$POST_ID/comments?pageNum=1&pageSize=5" | jqv 'code')
[ "$C" = "200" ] && ok "GET /posts/{id}/comments 兼容端点正常" || bad "兼容 GET 端点" "$C"
R=$(curl -s -X POST "$BASE/api/community/posts/$POST_ID/comments" -H "Authorization: Bearer $T_USER1" \
  -H 'Content-Type: application/json' -d '{"content":"【自测】兼容端点发表"}')
CX=$(echo "$R" | jqv 'data.id')
[ -n "$CX" ] && ok "POST /posts/{id}/comments 兼容端点正常 (id=$CX)" || bad "兼容 POST 端点" "$R"
curl -s -X DELETE "$BASE/api/community/comments/$CX" -H "Authorization: Bearer $T_USER1" >/dev/null

echo ""
echo "═══════════════════════════════════"
echo "  通过: $PASS   失败: $FAIL"
echo "═══════════════════════════════════"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
