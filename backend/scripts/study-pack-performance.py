"""Summarize exported detail JSONL, with measured event timestamps only.
Usage: python3 scripts/study-pack-performance.py samples.jsonl
Each row: {tier, startup, concurrency, provider, model, pack: <detail response data>}.
No network calls; missing measurements remain unknown. Never certifies mock SLA.
"""
import json, sys, math
from datetime import datetime
from collections import defaultdict

def stamp(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00')).timestamp() * 1000

def summarize(rows):
    groups = defaultdict(list)
    for row in rows:
        groups[(row['tier'], row['startup'], row['concurrency'], row['provider'], row['model'])].append(row)
    output = []
    for key, samples in groups.items():
        ttfu, full, queue = [], [], []
        retries = defaultdict(int)
        for row in samples:
            p = row['pack']; events = p.get('events', [])
            origin = stamp(p['createTime'])
            usable = [stamp(e['createTime']) for e in events if e['state'] == 'SUCCEEDED' and e['kind'] in ('NOTE', 'CARDS', 'QUIZ', 'TUTOR')]
            if usable: ttfu.append(min(usable) - origin)
            running = [stamp(e['createTime']) for e in events if e['state'] == 'RUNNING']
            if running: queue.append(min(running) - origin)
            terminal = [stamp(e['createTime']) for e in events if e['kind'] == 'JOB' and e['state'] == 'DRAFT_READY']
            if terminal: full.append(min(terminal) - origin)
            for s in p['steps']: retries[s['kind']] += max(0, s['runCount'] - 1)
        def metrics(values):
            values = sorted(values)
            return {'n': len(values), 'p50Ms': values[math.ceil(len(values)*.5)-1], 'p95Ms': values[math.ceil(len(values)*.95)-1], 'maxMs': max(values)} if values else None
        output.append(dict(group=key, samples=len(samples), ttfu=metrics(ttfu), full=metrics(full), queue=metrics(queue), failureRate=sum(r['pack']['state'] in ('FAILED','PARTIAL_READY') for r in samples)/len(samples), cancellationRate=sum(r['pack']['state']=='CANCELLED' for r in samples)/len(samples), retries=dict(retries), tokens=None, cost=None, embeddingReuse=None, certification='NOT_VERIFIED: requires real provider, document identities, repeats and complete scenario coverage'))
    return output

if __name__ == '__main__':
    with open(sys.argv[1]) as source:
        print(json.dumps(summarize([json.loads(line) for line in source if line.strip()]), ensure_ascii=False, indent=2))
