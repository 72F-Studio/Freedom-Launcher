#!/usr/bin/env python3
"""Moves UI string literals in a Java source into res/values/strings.xml.

Only literals in known UI positions are touched (text(), toast(), menu(), settingRow(), chip(), dialogs,
content descriptions …). Concatenations become format strings ("hold %1$s"), and ternaries are rewritten
branch by branch, so translators see whole phrases. Usage: extract-strings.py Source.java strings.xml
"""
import re, sys
from xml.sax.saxutils import escape

# function name -> UI argument indexes ('*' = every element of a String[]{...} at that index)
UI = {
    'text': [0], 'toast': [0], 'message': [0, 1], 'menu': [0, '1*'], 'settingRow': [0, 1], 'sectionLabel': [0],
    'chip': [0], 'tile': [0, 1], 'tourTitle': [0], 'tourBody': [0], 'tourHint': [1, 2], 'glassSlider': [0],
    'setContentDescription': [0], 'setHint': [0], 'setTitle': [0], 'setMessage': [0], 'setPositiveButton': [0],
    'setNegativeButton': [0], 'setNeutralButton': [0], 'setText': [0], 'setError': [0], 'announceForAccessibility': [0],
    'add': [0],  # options.add(...) in menus; filtered to `options.` receivers below
}

def scan(src):
    """Yields (i, kind) where kind is 'code' for positions outside strings/chars/comments."""
    i, n = 0, len(src)
    mask = [True] * n
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            j = src.find('\n', i); j = n if j < 0 else j
            for k in range(i, j): mask[k] = False
            i = j
        elif src.startswith('/*', i):
            j = src.find('*/', i + 2) + 2
            for k in range(i, j): mask[k] = False
            i = j
        elif c == '"' or c == "'":
            j = i + 1
            while src[j] != c:
                j += 2 if src[j] == '\\' else 1
            for k in range(i, j + 1): mask[k] = False
            i = j + 1
        else:
            i += 1
    return mask

def split_args(src, start, mask):
    """src[start] == '('; returns list of (a, b) argument spans and the index of the closing paren."""
    depth, i, args, a = 0, start, [], start + 1
    while True:
        c = src[i]
        if mask[i]:
            if c in '([{': depth += 1
            elif c in ')]}':
                depth -= 1
                if depth == 0: args.append((a, i)); return args, i
            elif c == ',' and depth == 1: args.append((a, i)); a = i + 1
        i += 1

def top_level(expr, chars):
    """Positions of chars at nesting depth 0 outside literals."""
    mask = scan(expr); depth = 0; out = []
    for i, c in enumerate(expr):
        if not mask[i]: continue
        if c in '([{': depth += 1
        elif c in ')]}': depth -= 1
        elif depth == 0 and c in chars: out.append(i)
    return out

JAVA_KEYWORDS = set('abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null'.split())

LIT = re.compile(r'^"((?:[^"\\]|\\.)*)"$', re.S)

def decode(lit):
    return bytes(lit, 'utf-8').decode('unicode_escape').encode('latin-1').decode('utf-8') if '\\' in lit else lit

class Extractor:
    def __init__(self):
        self.strings = {}  # value -> name
        self.names = set()

    def name_for(self, value, formatted):
        key = (value, formatted)
        if key in self.strings: return self.strings[key]
        words = re.findall(r'[a-z0-9]+', value.lower().replace('%1$s', ' x ').replace('%2$s', ' y ').replace('%3$s', ' z '))
        base = '_'.join(words)[:40].strip('_') or 'text'
        if base[0].isdigit(): base = 's_' + base
        if base in JAVA_KEYWORDS: base += '_label'  # R.string.<name> must be a valid Java identifier
        name, k = base, 2
        while name in self.names: name, k = f'{base}_{k}', k + 1
        self.names.add(name); self.strings[key] = name
        return name

    def ui_expr(self, expr):
        """Returns rewritten code for a UI string expression, or None if it holds no translatable literal."""
        stripped = expr.strip()
        if not stripped: return None
        # Fully parenthesised
        if stripped.startswith('(') and stripped.endswith(')'):
            mask = scan(stripped); depth = 0; whole = True
            for i, c in enumerate(stripped):
                if not mask[i]: continue
                if c == '(': depth += 1
                elif c == ')':
                    depth -= 1
                    if depth == 0 and i != len(stripped) - 1: whole = False; break
            if whole:
                inner = self.ui_expr(stripped[1:-1])
                return None if inner is None else '(' + inner + ')'
        # Ternary: rewrite each branch
        q = top_level(stripped, '?')
        if q:
            qi = q[0]; depth_q = 0; mask = scan(stripped); depth = 0; ci = None
            for i in range(qi + 1, len(stripped)):
                if not mask[i]: continue
                c = stripped[i]
                if c in '([{': depth += 1
                elif c in ')]}': depth -= 1
                elif depth == 0 and c == '?': depth_q += 1
                elif depth == 0 and c == ':':
                    if depth_q == 0: ci = i; break
                    depth_q -= 1
            if ci is not None:
                cond, a, b = stripped[:qi], stripped[qi + 1:ci], stripped[ci + 1:]
                ra, rb = self.ui_expr(a), self.ui_expr(b)
                if ra is None and rb is None: return None
                return cond + '?' + (ra if ra is not None else a) + ':' + (rb if rb is not None else b)
        # Concatenation
        cuts = [i for i in top_level(stripped, '+') if stripped[i - 1:i + 1] != '++' and stripped[i:i + 2] != '++' and stripped[i:i + 2] != '+=']
        parts, last = [], 0
        for i in cuts: parts.append(stripped[last:i].strip()); last = i + 1
        parts.append(stripped[last:].strip())
        literals = [p for p in parts if LIT.match(p)]
        if not literals: return None
        text = ''.join(decode(LIT.match(p).group(1)) for p in literals)
        if not re.search(r'[A-Za-z]', text): return None  # punctuation, separators, arrows
        template, args = '', []
        for p in parts:
            m = LIT.match(p)
            if m: template += decode(m.group(1)).replace('%', '%%')
            else:
                code = self.ui_expr(p)
                args.append(code if code is not None else p)
                template += '%' + str(len(args)) + '$s'
        formatted = bool(args)
        value = template if formatted else template.replace('%%', '%')
        name = self.name_for(value, formatted)
        return f'str(R.string.{name}' + ''.join(', ' + a for a in args) + ')'

    def rewrite(self, src):
        mask = scan(src)
        edits = []
        for m in re.finditer(r'\b([A-Za-z_]\w*)\s*\(', src):
            fn = m.group(1); paren = m.end() - 1
            if fn not in UI or not mask[m.start()]: continue
            if fn == 'add' and not src[max(0, m.start() - 8):m.start()].endswith('options.'): continue
            if src[max(0, m.start() - 8):m.start()].rstrip().endswith(('void', 'View', 'String', 'TextView', 'LinearLayout')): continue  # declarations
            args, close = split_args(src, paren, mask)
            for spec in UI[fn]:
                idx = int(str(spec).rstrip('*'))
                if idx >= len(args): continue
                a, b = args[idx]
                arg = src[a:b]
                if str(spec).endswith('*'):
                    m2 = re.match(r'(\s*new\s+String\[\]\s*\{)(.*)(\}\s*)$', arg, re.S)
                    if not m2: continue
                    body = m2.group(2); bmask = scan(body); depth = 0; cuts = []
                    for i, c in enumerate(body):
                        if not bmask[i]: continue
                        if c in '([{': depth += 1
                        elif c in ')]}': depth -= 1
                        elif c == ',' and depth == 0: cuts.append(i)
                    pieces, last, out = [], 0, []
                    for i in cuts + [len(body)]: pieces.append(body[last:i]); last = i + 1
                    changed = False
                    for p in pieces:
                        r = self.ui_expr(p)
                        out.append(r if r is not None else self.rewrite(p))
                        changed |= r is not None
                    if changed or out != pieces: edits.append((a, b, m2.group(1) + ','.join(out) + m2.group(3)))
                else:
                    r = self.ui_expr(arg)
                    if r is not None: edits.append((a, b, ' ' * (len(arg) - len(arg.lstrip())) + r))
        # apply outermost-first without overlaps; nested calls inside replaced args were rewritten by ui_expr only if
        # they were operands, so re-run until stable to catch calls inside untouched regions.
        edits.sort(key=lambda e: (e[0], -e[1]))
        result, pos, kept = [], 0, []
        for a, b, r in edits:
            if a < pos: continue
            kept.append((a, b, r)); pos = b
        out = src
        for a, b, r in reversed(kept): out = out[:a] + r + out[b:]
        return out

def xml_value(v):
    v = escape(v).replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if v[:1] in '@?': v = '\\' + v
    return v

if __name__ == '__main__':
    java, res = sys.argv[1], sys.argv[2]
    src = open(java, encoding='utf-8').read()
    ex = Extractor()
    for _ in range(4):  # nested UI calls inside rewritten arguments
        nxt = ex.rewrite(src)
        if nxt == src: break
        src = nxt
    open(java, 'w', encoding='utf-8').write(src)
    existing = open(res, encoding='utf-8').read()
    lines = []
    for (value, formatted), name in sorted(ex.strings.items(), key=lambda kv: kv[1]):
        attr = '' if formatted or '%' not in value else ' formatted="false"'
        lines.append(f'    <string name="{name}"{attr}>{xml_value(value)}</string>')
    existing = existing.replace('</resources>', '\n'.join(lines) + '\n</resources>')
    open(res, 'w', encoding='utf-8').write(existing)
    print(len(ex.strings), 'strings extracted')
