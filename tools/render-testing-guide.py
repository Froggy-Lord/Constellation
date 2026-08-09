#!/usr/bin/env python3
import argparse
import html
import json
import re
from pathlib import Path


def inline(value):
    value = html.escape(value)
    value = re.sub(r"`([^`]+)`", r"<code>\1</code>", value)
    value = re.sub(r"\[([^]]+)]\(([^)]+)\)", r'<a href="\2">\1</a>', value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", value)
    return value


def markdown(source):
    out, paragraph, list_kind = [], [], None

    def flush_paragraph():
        if paragraph:
            out.append("<p>" + inline(" ".join(paragraph)) + "</p>")
            paragraph.clear()

    def close_list():
        nonlocal list_kind
        if list_kind:
            out.append(f"</{list_kind}>")
            list_kind = None

    fenced = False
    code = []
    for raw in source.splitlines():
        if raw.startswith("```"):
            flush_paragraph()
            close_list()
            if fenced:
                out.append("<pre><code>" + html.escape("\n".join(code)) + "</code></pre>")
                code.clear()
            fenced = not fenced
            continue
        if fenced:
            code.append(raw)
            continue
        heading = re.match(r"^(#{1,4})\s+(.+)$", raw)
        item = re.match(r"^\s*([-*]|\d+\.)\s+(.+)$", raw)
        if heading:
            flush_paragraph()
            close_list()
            level = len(heading.group(1))
            out.append(f"<h{level}>{inline(heading.group(2))}</h{level}>")
        elif item:
            flush_paragraph()
            wanted = "ol" if item.group(1)[0].isdigit() else "ul"
            if list_kind != wanted:
                close_list()
                list_kind = wanted
                out.append(f"<{wanted}>")
            out.append("<li>" + inline(item.group(2)) + "</li>")
        elif not raw.strip():
            flush_paragraph()
            close_list()
        else:
            paragraph.append(raw.strip())
    flush_paragraph()
    close_list()
    return "\n".join(out)


parser = argparse.ArgumentParser()
parser.add_argument("guide", type=Path)
parser.add_argument("output", type=Path)
parser.add_argument("version")
args = parser.parse_args()
body = markdown(args.guide.read_text(encoding="utf-8"))
version = html.escape(args.version)
version_js = json.dumps(args.version)
page = f'''<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Constellation {version} testing</title>
<style>
:root{{--bg:#090b12;--panel:#121725;--line:#28324a;--text:#edf1fa;--muted:#a6b0c5;--blue:#76a9ff;--good:#62d49b;--issue:#ff7b83;--suggestion:#eebd5c}}
*{{box-sizing:border-box}}body{{margin:0;background:var(--bg);color:var(--text);font:16px/1.65 system-ui,sans-serif}}
header{{position:sticky;top:0;z-index:3;padding:14px 24px;background:rgba(9,11,18,.94);border-bottom:1px solid var(--line);backdrop-filter:blur(12px)}}
header strong{{margin-right:12px}}header span,.muted{{color:var(--muted)}}main{{display:grid;grid-template-columns:minmax(0,760px) 320px;gap:28px;max-width:1160px;margin:32px auto;padding:0 22px 80px}}
article,aside{{min-width:0}}h1{{font-size:2rem}}h2{{margin-top:2.4rem;border-top:1px solid var(--line);padding-top:1.5rem}}h3{{margin-top:1.8rem}}code{{padding:2px 5px;background:#1a2235;border-radius:4px}}pre{{overflow:auto;padding:14px;background:#0d111c;border:1px solid var(--line)}}a{{color:var(--blue)}}li{{margin:.38rem 0}}
aside{{position:sticky;top:78px;align-self:start;max-height:calc(100vh - 100px);overflow:auto;padding:16px;background:var(--panel);border:1px solid var(--line);border-radius:10px}}
.comment{{margin:12px 0;padding:12px;border-left:3px solid var(--suggestion);background:#0d111c;border-radius:4px}}.comment.good{{border-color:var(--good)}}.comment.issue{{border-color:var(--issue)}}.quote{{display:block;margin-bottom:6px;color:var(--muted);font-size:.83rem}}.empty{{color:var(--muted)}}
#composer{{display:none;position:fixed;z-index:5;right:24px;bottom:24px;width:min(430px,calc(100vw - 48px));padding:16px;background:#171d2c;border:1px solid #445372;border-radius:10px;box-shadow:0 18px 60px #000b}}#composer.open{{display:block}}textarea{{width:100%;min-height:100px;margin:10px 0;padding:10px;resize:vertical;background:#090d17;color:var(--text);border:1px solid #445372;border-radius:6px}}select,button{{padding:8px 11px;background:#202a40;color:var(--text);border:1px solid #536483;border-radius:6px}}button.primary{{background:#315fae;border-color:#5986d5}}#status{{margin-left:8px;color:var(--muted);font-size:.86rem}}::highlight(good){{background:#256f4f80}}::highlight(issue){{background:#9c354080}}::highlight(suggestion){{background:#99712580}}
@media(max-width:850px){{main{{display:block}}aside{{position:static;max-height:none;margin-top:36px}}}}
</style></head><body>
<header><strong>Constellation {version}</strong><span>Frozen test guide. Select any instruction to leave feedback.</span></header>
<main><article id="guide">{body}</article><aside><h2 style="margin-top:0;border:0;padding:0">Feedback</h2><p class="muted">Highlight words, a sentence, or a paragraph in the guide. Your comments stay attached to this build.</p><div id="comments"><p class="empty">No feedback yet.</p></div></aside></main>
<div id="composer"><strong id="selected"></strong><textarea id="comment" placeholder="What is good, broken, unclear, or missing?"></textarea><select id="kind"><option value="issue">Issue</option><option value="suggestion">Suggestion</option><option value="good">Good</option></select> <button class="primary" id="save">Save feedback</button> <button id="cancel">Cancel</button><span id="status"></span></div>
<script>
const VERSION={version_js},API='/pages/constellation/feedback?version='+encodeURIComponent(VERSION),guide=document.getElementById('guide');let pending=null,comments=[];
function textNodes(){{const w=document.createTreeWalker(guide,NodeFilter.SHOW_TEXT);let a=[],n,o=0;while(n=w.nextNode()){{a.push({{node:n,start:o,end:o+n.data.length}});o+=n.data.length}}return a}}
function locate(entry){{const all=guide.textContent;let at=all.indexOf(entry.quote),from=0;while(at>=0){{const pre=all.slice(Math.max(0,at-entry.prefix.length),at),suf=all.slice(at+entry.quote.length,at+entry.quote.length+entry.suffix.length);if((!entry.prefix||pre.endsWith(entry.prefix))&&(!entry.suffix||suf.startsWith(entry.suffix)))break;from=at+1;at=all.indexOf(entry.quote,from)}}if(at<0)return null;const nodes=textNodes(),end=at+entry.quote.length,r=document.createRange(),a=nodes.find(x=>x.start<=at&&x.end>=at),b=nodes.find(x=>x.start<=end&&x.end>=end);if(!a||!b)return null;r.setStart(a.node,at-a.start);r.setEnd(b.node,end-b.start);return r}}
function paint(){{if(!CSS.highlights)return;for(const kind of ['good','issue','suggestion']){{const ranges=comments.filter(x=>x.kind===kind).map(locate).filter(Boolean);CSS.highlights.set(kind,new Highlight(...ranges))}}}}
function render(){{const box=document.getElementById('comments');box.innerHTML=comments.length?'':'<p class="empty">No feedback yet.</p>';comments.forEach(x=>{{const d=document.createElement('div');d.className='comment '+x.kind;const q=document.createElement('span');q.className='quote';q.textContent='“'+x.quote.slice(0,180)+(x.quote.length>180?'…':'')+'”';const p=document.createElement('div');p.textContent=x.comment;d.append(q,p);box.append(d)}});paint()}}
async function load(){{const r=await fetch(API);if(r.ok){{comments=(await r.json()).comments||[];render()}}}}
guide.addEventListener('mouseup',()=>{{setTimeout(()=>{{const s=getSelection();if(!s||s.isCollapsed||!guide.contains(s.anchorNode)||!guide.contains(s.focusNode))return;const chosen=s.getRangeAt(0),raw=chosen.toString(),quote=raw.trim();if(!quote)return;const before=document.createRange();before.selectNodeContents(guide);before.setEnd(chosen.startContainer,chosen.startOffset);const all=guide.textContent,at=before.toString().length+raw.indexOf(quote);pending={{quote,prefix:all.slice(Math.max(0,at-120),at),suffix:all.slice(at+quote.length,at+quote.length+120)}};document.getElementById('selected').textContent='“'+quote.slice(0,180)+(quote.length>180?'…':'')+'”';document.getElementById('composer').classList.add('open');document.getElementById('comment').focus()}},0)}});
document.getElementById('cancel').onclick=()=>document.getElementById('composer').classList.remove('open');document.getElementById('save').onclick=async()=>{{const status=document.getElementById('status'),comment=document.getElementById('comment').value.trim();if(!pending||!comment){{status.textContent='Write a comment first.';return}}status.textContent='Saving…';const payload={{...pending,comment,kind:document.getElementById('kind').value}},r=await fetch(API,{{method:'POST',headers:{{'Content-Type':'application/json'}},body:JSON.stringify(payload)}});if(!r.ok){{status.textContent='Could not save.';return}}comments.push((await r.json()).feedback);render();document.getElementById('comment').value='';status.textContent='Saved';setTimeout(()=>{{document.getElementById('composer').classList.remove('open');status.textContent=''}},500)}};load();
</script></body></html>'''
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(page, encoding="utf-8")
