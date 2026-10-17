#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Batch 辅助：把一篇已写好的正式章节登记进索引（topics.json + syllabus_data.py）。

MANIFEST.json 由 quality_check.py --write-manifest 自动重建，本脚本不碰它。

用法:
  python3 tools/add_chapter.py chapters/java/xxx.md \
      --proj "examples/xxx-verify" --tests "10/10" \
      [--prereq JAVA-A,JAVA-B] [--next JAVA-C,JAVA-D] [--source "V4.1 新写（Batch 5）"]

契约:
  - 章节 frontmatter 必须含 id/version/status/batch/priority/domain/sourceVersion/level/reviewState/module/topic
  - topics.json 的 topic_id 集合必须 == 所有章节 frontmatter 的 topic 集合（quality_check Gate A 强校验）
  - syllabus_data.py 中对应 topic_id 的 status 由 planned/v2 翻为 text
"""
import argparse, hashlib, json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SYL = ROOT / "tools" / "syllabus_data.py"
TOPICS = ROOT / "topics.json"


def parse_fm(text: str) -> dict:
    m = re.match(r"\A---\n(.*?)\n---\n", text, re.DOTALL)
    if not m:
        return {}
    out = {}
    for line in m.group(1).splitlines():
        if ":" in line and not line.startswith(" "):
            k, v = line.split(":", 1)
            out[k.strip()] = v.strip().strip('"')
    return out


def sha256_file(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def flip_syllabus(topic_id: str):
    src = SYL.read_text(encoding="utf-8")
    lines = src.splitlines()
    did = False
    for i, ln in enumerate(lines):
        if topic_id in ln and ('"planned"' in ln or '"v2"' in ln):
            new = ln.replace('"planned"', '"text"').replace('"v2"', '"text"')
            if new != ln:
                lines[i] = new
                did = True
                break
    if not did:
        # 可能同文件有多处，再全局找下一个含该 topic_id 的 planned/v2
        for i, ln in enumerate(lines):
            if topic_id in ln and ('"planned"' in ln or '"v2"' in ln):
                lines[i] = ln.replace('"planned"', '"text"').replace('"v2"', '"text"')
                did = True
                break
    if did:
        SYL.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return did


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("chapter")
    ap.add_argument("--proj", default="")
    ap.add_argument("--tests", default="")
    ap.add_argument("--prereq", default="")
    ap.add_argument("--next", default="")
    ap.add_argument("--source", default="V4.1 新写")
    args = ap.parse_args()

    p = (ROOT / args.chapter).resolve()
    if not p.exists():
        print(f"FAIL 文件不存在: {p}"); sys.exit(2)
    text = p.read_text(encoding="utf-8")
    fm = parse_fm(text)
    for k in ("id", "topic", "module", "priority", "domain", "status", "reviewState", "batch"):
        if k not in fm:
            print(f"FAIL frontmatter 缺字段: {k}"); sys.exit(2)

    tid = fm["topic"]
    rel = str(p.relative_to(ROOT))
    sha = sha256_file(p)
    evidence = f"{args.proj}（{args.tests}）" if args.proj else "无验证工程（概念章，见 §20 事实标记）"
    code_verified = fm["reviewState"] == "code_verified"

    idx = json.loads(TOPICS.read_text(encoding="utf-8"))
    existing = {t["topic_id"] for t in idx["topics"]}
    if tid in existing:
        print(f"WARN {tid} 已存在于 topics.json，跳过重复添加（仅回写 syllabus/sha 不重加）")
    else:
        entry = {
            "topic_id": tid,
            "title": next((l.lstrip("# ").strip() for l in text.splitlines() if l.startswith("# ")), fm.get("id")),
            "module": fm["module"],
            "domain": fm["domain"],
            "priority": fm["priority"],
            "source": args.source,
            "file": rel,
            "version": fm.get("version", "4.1.0"),
            "prerequisites": [x for x in args.prereq.split(",") if x] ,
            "next": [x for x in args.next.split(",") if x],
            "content_status": fm["status"],
            "technical_review": "passed" if code_verified else "pending",
            "code_verified": code_verified,
            "code_verified_note": "",
            "human_review": "pending",
            "sha256": sha,
            "evidence": evidence,
        }
        idx["topics"].append(entry)
        idx["totals"]["formal"] = len(idx["topics"])
        TOPICS.write_text(json.dumps(idx, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"OK topics.json 新增 {tid}（formal={idx['totals']['formal']}）")

    did = flip_syllabus(tid)
    print(f"{'OK' if did else 'WARN'} syllabus_data.py status 翻转: {tid} -> {'text' if did else '未找到 planned/v2 行'}")
    print(f"sha256: {sha}")
    print(f"evidence: {evidence}")


if __name__ == "__main__":
    main()
