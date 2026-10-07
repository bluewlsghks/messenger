#!/usr/bin/env python3
"""Query OSV with exact resolved public dependency coordinates (no source code or credentials).
Network/schema failures are errors, NOT clean results. Exit 2 means findings need review.
"""
from __future__ import annotations
import argparse, datetime, json, pathlib, sys, time, urllib.request

def request(payload: dict) -> dict:
    data = json.dumps(payload).encode()
    for attempt in range(3):
        try:
            req=urllib.request.Request('https://api.osv.dev/v1/querybatch',data=data,headers={'Content-Type':'application/json'})
            with urllib.request.urlopen(req,timeout=45) as response: return json.load(response)
        except (OSError, ValueError):
            if attempt==2: raise
            time.sleep(2**attempt)
    raise RuntimeError('Unreachable retry state')

def audit(coordinates: list[dict]) -> list[dict]:
    findings=[]
    for start in range(0,len(coordinates),50):
        batch=coordinates[start:start+50]
        pending=[({'package':{'name':c['name'],'ecosystem':c.get('ecosystem','Maven')},'version':c['version']},c) for c in batch]
        pages=0
        while pending:
            pages+=1
            if pages>100: raise ValueError('OSV pagination limit exceeded; audit is incomplete.')
            result=request({'queries':[query for query,_ in pending]}).get('results')
            if not isinstance(result,list) or len(result)!=len(pending): raise ValueError('Invalid OSV batch result.')
            more=[]
            for (query,coordinate),item in zip(pending,result):
                if not isinstance(item,dict): raise ValueError('Invalid OSV result entry.')
                for vuln in item.get('vulns',[]):
                    findings.append({**coordinate,'id':vuln['id'],'modified':vuln.get('modified')})
                if item.get('next_page_token'):
                    more.append(({**query,'page_token':item['next_page_token']},coordinate))
            pending=more
    return findings

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('coordinates',type=pathlib.Path)
    parser.add_argument('--report',type=pathlib.Path,default=pathlib.Path('build/security/osv-report.json'))
    args=parser.parse_args();coordinates=json.loads(args.coordinates.read_text())
    if not isinstance(coordinates,list) or not coordinates: raise ValueError('Resolved dependency list is empty.')
    for c in coordinates:
        if not isinstance(c,dict) or not c.get('name') or not c.get('version'): raise ValueError('Missing exact dependency coordinates.')
    findings=audit(coordinates)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps({'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'packages':len(coordinates),'findings':findings,'complete':True},indent=2))
    print(f'OSV: {len(coordinates)} exact resolved dependencies; {len(findings)} findings. Review {args.report}.')
    return 2 if findings else 0
if __name__=='__main__':
    try: sys.exit(main())
    except (OSError,ValueError,KeyError) as error:
        print(f'Security audit INCOMPLETE ({type(error).__name__}); no clean result claimed.',file=sys.stderr);sys.exit(1)
