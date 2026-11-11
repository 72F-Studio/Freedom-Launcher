// Generates web/tokens.css from tokens.json. Usage (from the repository root): node tools/tokens-css.js . && mv tokens.css web/
const fs=require("fs");const dir=process.argv[2];const T=JSON.parse(fs.readFileSync(dir+"/tokens.json"));
const v=(t,th)=>{let x=typeof t.value==="string"?t.value:(t.value[th]??t.value.light);return x.startsWith("{")?"var(--"+x.slice(1,-1)+")":x};
const cs=[...T.color.tokens,...T.shadow.tokens];
let o="/* Kelvin — generated from tokens.json */\n";
o+=":root, [data-theme=\"light\"] {\n"+cs.map(t=>`  --${t.name}: ${v(t,"light")};`).join("\n")+"\n}\n";
o+="[data-theme=\"dark\"] {\n"+cs.map(t=>`  --${t.name}: ${v(t,"dark")};`).join("\n")+"\n  color-scheme: dark;\n}\n";
const other=Object.keys(T).filter(k=>!["name","version","color","type","shadow"].includes(k)&&T[k].tokens).flatMap(k=>T[k].tokens);
o+=":root {\n"+other.map(t=>`  --${t.name}: ${t.value};`).join("\n")+"\n"+Object.entries(T.type.families).map(([k,s])=>`  --font-${k}: ${s};`).join("\n")+"\n}\n";
// Firm corners (KelvinCorners.Firm): the tighter scale, on any element marked data-k-corners="firm".
const firm=other.filter(t=>t.firm);if(firm.length)o+="[data-k-corners=\"firm\"] {\n"+firm.map(t=>`  --${t.name}: ${t.firm};`).join("\n")+"\n}\n";
for(const g of T.type.groups)for(const s of g.styles)o+=`.${s.name} { font-family: var(--font-${s.family||g.family}); font-size: ${s.fontSize}; line-height: ${s.lineHeight}; font-weight: ${s.fontWeight}; letter-spacing: ${s.letterSpacing}; }\n`;
fs.writeFileSync(dir+"/tokens.css",o);
