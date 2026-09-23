"""Capture actionable browser diagnostics before Playwright closes a failed test page."""
import json
import pathlib
import runpy
from playwright.sync_api import Locator

OUT = pathlib.Path('build/e2e-artifacts')
OUT.mkdir(parents=True, exist_ok=True)
original_click = Locator.click


def diagnostic_click(self, *args, **kwargs):
    try:
        return original_click(self, *args, **kwargs)
    except Exception:
        page = self.page
        try:
            page.screenshot(path=str(OUT / 'failure.png'), full_page=True)
            (OUT / 'failure.html').write_text(page.content(), encoding='utf-8')
            metrics = page.evaluate("""() => ({
                viewport: {width: innerWidth, height: innerHeight},
                sheets: [...document.styleSheets].map(s => ({href:s.href, rules:s.cssRules.length})),
                nodes: ['.app-shell','.server-rail','#create-server','.toast-stack','.toast'].map(selector => {
                    const node=document.querySelector(selector); if(!node)return {selector};
                    const s=getComputedStyle(node), r=node.getBoundingClientRect();
                    return {selector,rect:r.toJSON(),display:s.display,position:s.position,
                        width:s.width,height:s.height,top:s.top,right:s.right,bottom:s.bottom,left:s.left,
                        pointerEvents:s.pointerEvents,zIndex:s.zIndex};
                })
            })""")
            print('BROWSER_DIAGNOSTICS ' + json.dumps(metrics, ensure_ascii=False), flush=True)
            (OUT / 'failure-layout.json').write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding='utf-8')
        except Exception as capture_error:
            print('Diagnostic capture failed:', str(capture_error), flush=True)
        raise

Locator.click = diagnostic_click
runpy.run_path('src/test/e2e/workspace.py', run_name='__main__')
