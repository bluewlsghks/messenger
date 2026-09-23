"""Retain screenshots and incoming transport diagnostics for every E2E failure."""
import ast
import json
import pathlib
from playwright.sync_api import BrowserContext

OUT = pathlib.Path('build/e2e-artifacts')
OUT.mkdir(parents=True, exist_ok=True)
transport = {}
original_page = BrowserContext.new_page


def instrumented_page(self, *args, **kwargs):
    page = original_page(self, *args, **kwargs)
    events = []
    transport[page] = events
    def record(kind, value):
        events.append({'kind': kind, 'value': value})
        del events[:-100]
    page.on('websocket', lambda ws: ws.on('framereceived', lambda frame: record('incoming', str(frame))))
    page.on('response', lambda response: record('http', {'status': response.status, 'url': response.url})
            if response.status >= 400 else None)
    page.on('pageerror', lambda error: record('javascript', str(error)))
    page.on('console', lambda message: record('console', message.text) if message.type == 'error' else None)
    return page

BrowserContext.new_page = instrumented_page


def capture_failure(namespace):
    report = []
    for index, page in enumerate(list(transport)):
        if page.is_closed():
            continue
        try:
            page.screenshot(path=str(OUT / f'failure-{index}.png'), full_page=True, timeout=5000)
            (OUT / f'failure-{index}.html').write_text(page.content(), encoding='utf-8')
            state = page.evaluate("""() => ({
                url: location.href,
                viewport: {width: innerWidth, height: innerHeight},
                connection: document.getElementById('connection-state')?.textContent,
                activeTitle: document.getElementById('chat-title')?.textContent,
                hidden: document.hidden, focused: document.hasFocus(),
                toasts: document.getElementById('toast-stack')?.textContent,
                preferences: Object.fromEntries(Object.entries(localStorage).filter(([key]) => key.startsWith('messenger:prefs:'))),
                errors: [...document.querySelectorAll('.error-banner')].map(n => n.textContent),
                layout: ['.app-shell','.server-rail','#create-server','.toast-stack','.toast'].map(selector => {
                    const node=document.querySelector(selector); if(!node)return {selector};
                    const style=getComputedStyle(node);
                    return {selector,rect:node.getBoundingClientRect().toJSON(),display:style.display,
                        position:style.position,pointerEvents:style.pointerEvents};
                })
            })""")
            state['transport'] = transport[page]
            report.append(state)
        except Exception as error:
            report.append({'page': index, 'captureError': str(error)})
    (OUT / 'failure-diagnostics.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Browser failure diagnostics saved in messenger-validation artifact.', flush=True)

# Capture while the Playwright context and pages still exist, including assertion failures.
source = pathlib.Path('src/test/e2e/workspace.py').read_text(encoding='utf-8')
tree = ast.parse(source, filename='src/test/e2e/workspace.py')
for node in tree.body:
    if isinstance(node, ast.With):
        handler = ast.ExceptHandler(type=ast.Name(id='BaseException', ctx=ast.Load()), name=None,
            body=ast.parse('_capture_failure(globals())\nraise').body)
        node.body = [ast.Try(body=node.body, handlers=[handler], orelse=[], finalbody=[])]
ast.fix_missing_locations(tree)
namespace = {'__name__': '__main__', '_capture_failure': capture_failure}
exec(compile(tree, 'src/test/e2e/workspace.py', 'exec'), namespace)
