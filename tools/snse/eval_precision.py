#!/usr/bin/env python3
"""Strict exact-output held-out evaluation through a host llama-cli binary."""
import argparse, json, re, subprocess, sys, time, tempfile
from pathlib import Path
CWE={"CWE-89":"sqli","CWE-327":"crypto","CWE-326":"crypto","CWE-328":"crypto","CWE-330":"crypto",
"CWE-287":"auth","CWE-306":"auth","CWE-798":"auth","CWE-259":"auth","CWE-295":"network","CWE-319":"network","CWE-297":"network",
"CWE-312":"storage","CWE-522":"storage","CWE-921":"storage","CWE-532":"logging","CWE-117":"logging","CWE-502":"deserialization","CWE-400":"deserialization"}
def norm(s): return re.sub(r"\s+", " ", s).strip()
def main():
 p=argparse.ArgumentParser();p.add_argument('--test',required=True);p.add_argument('--shards',default='models/shards');p.add_argument('--llama-cli',default='build/snse/host/bin/llama-cli');p.add_argument('--threshold-precision',type=float,default=.85);p.add_argument('--threshold-recall',type=float,default=.75);p.add_argument('--limit',type=int,default=100);a=p.parse_args()
 rows=[json.loads(x) for x in Path(a.test).read_text(encoding='utf-8').splitlines() if x.strip()]
 if len(rows)<a.limit: raise SystemExit(f'Need at least {a.limit} held-out records; got {len(rows)}')
 rows=rows[:a.limit];correct=0;generated=0;durations=[];missing=0
 for i,r in enumerate(rows,1):
  shard=CWE.get(r.get('cwe')); model=Path(a.shards)/f'{shard}.gguf' if shard else None
  if not model or not model.is_file(): missing+=1;continue
  prompt=("### Task\nRepair the vulnerable source while preserving unrelated behavior.\n"
          f"Specialist: {shard}\nCWE: {r['cwe']}\n"
          "Return only a JSON object with patch (complete corrected source), confidence (0..1), and rationale. Do not execute code.\n"
          f"### Vulnerable source\n{r['input']}\n### Corrected source JSON:\n")
  with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', suffix='.prompt') as pf:
   pf.write(prompt); pf.flush()
   t=time.monotonic()
   try:
    cp=subprocess.run([a.llama_cli,'-m',str(model),'-f',pf.name,'-n','1024','--temp','0','--no-display-prompt','--simple-io'],capture_output=True,text=True,timeout=180)
   except subprocess.TimeoutExpired:
    print(f'WARN case {i}: timeout',file=sys.stderr); continue
  durations.append(time.monotonic()-t)
  if cp.returncode: print(f'WARN case {i}: llama-cli exit {cp.returncode}',file=sys.stderr);continue
  raw=cp.stdout.strip(); generated+=bool(raw)
  pred=""
  try:
   start=raw.find('{')
   if start >= 0:
    obj,_=json.JSONDecoder().raw_decode(raw[start:])
    pred=obj.get('patch','') if isinstance(obj,dict) else ''
  except (json.JSONDecodeError, AttributeError): pass
  if pred and norm(pred)==norm(r['output']):correct+=1
  if i%10==0:print(f'evaluated {i}/{len(rows)}',file=sys.stderr)
 precision=correct/generated if generated else 0.0;recall=correct/len(rows)
 print(json.dumps({'cases':len(rows),'correctExact':correct,'generated':generated,'missingShard':missing,'precisionExact':precision,'recallExact':recall,'meanLatencySeconds':sum(durations)/len(durations) if durations else None},indent=2))
 if precision<a.threshold_precision or recall<a.threshold_recall or missing: raise SystemExit(1)
if __name__=='__main__':main()
