/* Weupperu.com | WeupNotes Android 1.1.2. Online actions; theme follows the current user appearance. */
(function () {
  'use strict';
  if (window.__weupWheelVersion === 112) { window.__weupWheelRefresh?.(); return true; }
  window.__weupWheelVersion = 112;
  const ROOT_ID = 'weup-wheel';
  const DEFAULT = '#40536b'; // Neutral when the web has not exposed its theme; never force brand pink.
  const actions = [
    { id:'note', label:'Nota', nav:/^(mis )?notas?$/, fallback:'<path d="M12 5v14M5 12h14"/>' },
    { id:'space', label:'Espacio', nav:/^espacios?$/, fallback:'<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>' },
    { id:'notebook', label:'Libreta', nav:/^libretas?$/, fallback:'<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"/>' },
    { id:'search', label:'Buscar', nav:/^(buscar|busqueda)$/, fallback:'<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>' }
  ];
  const normalize = s => (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  const $ = (sel, context=document) => context.querySelector(sel);
  const isVisible = el => !!el && !!el.getClientRects().length;
  const sidebar = () => $('[data-sidebar], aside, [class*="sidebar"], nav, [role="navigation"]');
  let wrap, main, shade, buttons=[], opened=false, down=null, highlighted=-1;

  function normalizeColor(raw, context=document.body) {
    if (typeof raw !== 'string' || raw.length>110) return '';
    const value=raw.trim();
    if (!value || value==='transparent' || value==='inherit' || value==='currentColor' || !CSS.supports('color',value)) return '';
    if (/^#[0-9a-f]{3,8}$/i.test(value)) return value;
    if (/^rgba?\(/i.test(value)) return /rgba\([^)]*[, /]\s*0(?:\.0+)?\s*\)$/i.test(value) ? '' : value;
    const probe=document.createElement('span');probe.className='weup-wheel-probe';
    probe.style.cssText='position:absolute!important;visibility:hidden!important;pointer-events:none!important;color:'+value+'!important';
    (context && context.isConnected ? context : document.body).appendChild(probe);
    const result=getComputedStyle(probe).color;
    probe.remove();
    if (!result || /(?:rgba?\(\s*\d+[, ]+\s*\d+[, ]+\s*\d+[, /]+\s*0(?:\.0+)?\s*\))/.test(result)) return '';
    return result;
  }
  function storageAccent() {
    try {
      // Only appearance keys; never read or expose notes, sessions or auth data.
      for(let i=0;i<Math.min(localStorage.length,80);i++){
        const key=localStorage.key(i);
        if(!/(?:appearance|accent|palette|theme|primary.?color)/i.test(key||'')) continue;
        const raw=localStorage.getItem(key);
        if(!raw || raw.length>20000) continue;
        const direct=normalizeColor(raw); if(direct) return direct;
        let value;try{value=JSON.parse(raw)}catch(_){continue;}
        if(value && typeof value==='object') {
          const visit=(obj,depth)=>{
            if(!obj || typeof obj!=='object' || depth>3) return '';
            const keys=['accentColor','accent_color','accent','themeColor','theme_color','primaryColor','primary_color','selectedColor','selected_color','color'];
            for(const k of keys) if(Object.prototype.hasOwnProperty.call(obj,k)) {
              const c=normalizeColor(obj[k]); if(c) return c;
            }
            for(const k of ['appearance','theme','palette','preferences']) if(obj[k] && typeof obj[k]==='object') {
              const c=visit(obj[k],depth+1); if(c) return c;
            }
            return '';
          };
          const picked=visit(value,0); if(picked)return picked;
        }
      }
    } catch(_) { /* Storage can be restricted; computed styles still work. */ }
    return '';
  }
  function swatchAccent() {
    const candidates=document.querySelectorAll('[aria-checked="true"], [aria-pressed="true"], .theme-swatch.selected, .theme-color.active, [data-theme-option].selected, [data-accent].selected, input[type="radio"]:checked');
    for(const node of candidates) {
      const parent=node.closest('[class*="theme"],[class*="appearance"],[class*="accent"],[data-theme-option],[data-color],fieldset');
      if(!parent || (!/(?:theme|appearance|accent|color|palette)/i.test((parent.className||'').toString()+' '+(parent.getAttribute('aria-label')||'')+' '+(parent.getAttribute('data-theme-option')||'')))) continue;
      const el=node.matches('input') ? (node.nextElementSibling||parent) : node;
      const declared=el.getAttribute('data-color')||el.getAttribute('data-accent')||el.style.backgroundColor;
      const c=normalizeColor(declared,el)||normalizeColor(getComputedStyle(el).backgroundColor,el);
      if(c) return c;
    }
    return '';
  }
  function variableAccent() {
    const names=['--app-accent','--weup-accent','--notes-accent','--theme-accent','--accent-color','--color-accent','--accent','--primary-color','--theme-primary','--color-primary','--primary','--theme-color','--brand-color'];
    const elements=[document.documentElement, document.body, $('#app'), $('#root'), $('[data-theme]'), sidebar()].filter(Boolean);
    const colors=[];
    for(const node of elements){const style=getComputedStyle(node);for(const name of names){const v=style.getPropertyValue(name).trim();if(v){const c=normalizeColor(v,node);if(c)colors.push(c);}}}
    // Avoid an old, static brand variable overriding another live theme variable.
    return colors.find(c=>!/rgb\(233,\s*33,\s*118\)/i.test(c))||colors[0]||'';
  }
  function liveAccent() {
    const nav=sidebar();
    const primary=Array.from(document.querySelectorAll('button,[role="button"],a')).find(el=>{
      if(!isVisible(el) || el.closest('#'+ROOT_ID))return false;
      const label=normalize([el.getAttribute('aria-label'),el.getAttribute('title'),el.innerText].filter(Boolean).join(' '));
      return /^(?:\+\s*)?(?:nueva nota|crear nota|agregar nota|anadir nota|nuevo espacio|crear libreta|guardar)$/.test(label);
    });
    const candidates=[];
    if(primary) candidates.push(getComputedStyle(primary).backgroundColor);
    if(nav){const active=nav.querySelector('[aria-current="page"],[aria-selected="true"],.active,[class*="active"]');
      if(active){const icon=active.querySelector('svg,i,[class*="icon"]');if(icon)candidates.push(getComputedStyle(icon).color);candidates.push(getComputedStyle(active).color,getComputedStyle(active).backgroundColor);}}
    for(const c of candidates){const x=normalizeColor(c);if(x && !/^rgb\(\s*(?:255,\s*255,\s*255|0,\s*0,\s*0)\s*\)$/.test(x))return x;}
    return '';
  }
  function currentAccent() {
    return swatchAccent() || storageAccent() || liveAccent() || variableAccent() || DEFAULT;
  }
  function textOn(color) {
    let nums;
    if(/^#[0-9a-f]{3}$/i.test(color)) nums=color.slice(1).split('').map(x=>parseInt(x+x,16));
    else if(/^#[0-9a-f]{6,8}$/i.test(color)) nums=[1,3,5].map(i=>parseInt(color.slice(i,i+2),16));
    else nums=(color.match(/[\d.]+/g)||[]).slice(0,3).map(Number);
    if(nums.length<3)return '#fff';
    const luminance=(nums[0]*299+nums[1]*587+nums[2]*114)/1000;
    return luminance>174 ? '#192536' : '#ffffff';
  }
  function adjustTheme() {
    if(!wrap || !wrap.isConnected)return;
    const color=currentAccent();
    if(wrap.style.getPropertyValue('--weup-wheel-color')!==color){
      wrap.style.setProperty('--weup-wheel-color',color);
      wrap.style.setProperty('--weup-wheel-text',textOn(color));
    }
  }
  function iconFor(item) {
    const root=sidebar();
    if(root){
      const control=Array.from(root.querySelectorAll('a,button,[role="button"]')).find(el=>{
        const aria=normalize(el.getAttribute('aria-label')||el.getAttribute('title'));
        const label=normalize(el.textContent).replace(/\s*\d+\s*$/,'');
        return item.nav.test(aria)||item.nav.test(label);
      });
      const original=control?.querySelector('svg,i,[class*="icon"],img');
      if(original){
        const copy=original.cloneNode(true);
        copy.removeAttribute('id');copy.removeAttribute('aria-label');copy.setAttribute('aria-hidden','true');
        copy.style.width='27px';copy.style.height='27px';copy.style.fontSize='27px';copy.style.flex='none';
        copy.style.color='inherit';
        if(copy.tagName.toLowerCase()==='svg'){
          copy.setAttribute('width','27');copy.setAttribute('height','27');
          if(copy.getAttribute('stroke') && copy.getAttribute('stroke')!=='none')copy.setAttribute('stroke','currentColor');
        }
        return copy;
      }
    }
    const span=document.createElement('span');
    span.innerHTML='<svg viewBox="0 0 24 24" width="27" height="27" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+item.fallback+'</svg>';
    return span.firstElementChild;
  }
  function setHighlight(i){highlighted=i;buttons.forEach((b,n)=>b.classList.toggle('weup-wheel-hover',i===n));}
  function close(){opened=false;if(!wrap)return;wrap.classList.remove('weup-wheel-open');main.textContent='+';main.setAttribute('aria-expanded','false');setHighlight(-1);}
  function open(){opened=true;adjustTheme();wrap.classList.add('weup-wheel-open');main.textContent='×';main.setAttribute('aria-expanded','true');buttons.forEach((b,i)=>{b.replaceChildren(iconFor(actions[i]));const label=document.createElement('span');label.textContent=actions[i].label;b.appendChild(label);});}
  function launch(i){const action=actions[i];if(!action)return;close();window.location.href='weupnotes-action://'+action.id;}
  function build(){
    if(wrap && wrap.isConnected)return;
    if(!sidebar() || !document.body)return;
    document.getElementById(ROOT_ID)?.remove();
    let style=document.getElementById('weup-wheel-styles');if(!style){
      style=document.createElement('style');style.id='weup-wheel-styles';style.textContent=`
        #weup-wheel {position:fixed!important;right:18px!important;bottom:calc(20px + env(safe-area-inset-bottom,0px))!important;width:56px!important;height:56px!important;z-index:2147483500!important;pointer-events:none!important;font-family:inherit!important;--weup-wheel-color:#40536b;--weup-wheel-text:#fff;}
        #weup-wheel *{box-sizing:border-box!important;}
        #weup-wheel .weup-wheel-shade{position:fixed!important;inset:0!important;display:none!important;background:rgba(0,0,0,.23)!important;pointer-events:auto!important;}
        #weup-wheel.weup-wheel-open .weup-wheel-shade{display:block!important;}
        #weup-wheel .weup-wheel-main,#weup-wheel .weup-wheel-option{position:absolute!important;display:flex!important;align-items:center!important;justify-content:center!important;appearance:none!important;background:var(--weup-wheel-color)!important;color:var(--weup-wheel-text)!important;border:0!important;outline:0!important;border-radius:999px!important;box-shadow:0 3px 12px rgba(0,0,0,.18)!important;pointer-events:auto!important;cursor:pointer!important;text-decoration:none!important;line-height:1.05!important;}
        #weup-wheel .weup-wheel-main{inset:0!important;width:56px!important;height:56px!important;font-size:32px!important;font-weight:400!important;touch-action:none!important;}
        #weup-wheel .weup-wheel-option{width:60px!important;height:60px!important;flex-direction:column!important;gap:1px!important;padding:4px!important;font-size:9px!important;font-weight:600!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important;transition:transform .15s ease,opacity .15s ease!important;touch-action:none!important;}
        #weup-wheel .weup-wheel-option svg,#weup-wheel .weup-wheel-option img{width:27px!important;height:27px!important;min-width:27px!important;min-height:27px!important;max-width:27px!important;max-height:27px!important;flex:none!important;}
        #weup-wheel .weup-wheel-option i{font-size:27px!important;line-height:27px!important;}
        #weup-wheel.weup-wheel-open .weup-wheel-option{opacity:1!important;visibility:visible!important;pointer-events:auto!important;}
        #weup-wheel .weup-wheel-option.weup-wheel-hover{filter:brightness(.88)!important;transform:scale(1.08)!important;}
        #weup-wheel .weup-wheel-option:nth-of-type(1){right:104px!important;bottom:0!important;}
        #weup-wheel .weup-wheel-option:nth-of-type(2){right:98px!important;bottom:72px!important;}
        #weup-wheel .weup-wheel-option:nth-of-type(3){right:48px!important;bottom:116px!important;}
        #weup-wheel .weup-wheel-option:nth-of-type(4){right:-5px!important;bottom:143px!important;}
        @media(prefers-reduced-motion:reduce){#weup-wheel .weup-wheel-option{transition:none!important;}}
      `;document.head.appendChild(style);
    }
    wrap=document.createElement('div');wrap.id=ROOT_ID;wrap.setAttribute('aria-label','Acciones rápidas');
    shade=document.createElement('div');shade.className='weup-wheel-shade';shade.addEventListener('click',close);wrap.appendChild(shade);
    buttons=[];
    actions.forEach((a,i)=>{const b=document.createElement('button');b.type='button';b.className='weup-wheel-option';b.setAttribute('aria-label',a.label);b.addEventListener('click',()=>launch(i));wrap.appendChild(b);buttons.push(b);});
    main=document.createElement('button');main.type='button';main.className='weup-wheel-main';main.textContent='+';main.setAttribute('aria-label','Acciones rápidas');main.setAttribute('aria-expanded','false');wrap.appendChild(main);
    main.addEventListener('pointerdown',e=>{down={x:e.clientX,y:e.clientY,wasOpen:opened,moved:false};main.setPointerCapture(e.pointerId);if(!opened)open();});
    main.addEventListener('pointermove',e=>{if(!down)return;const dx=e.clientX-down.x,dy=e.clientY-down.y;if(Math.hypot(dx,dy)>14)down.moved=true;let selected=-1,min=38;buttons.forEach((b,i)=>{const r=b.getBoundingClientRect();const dist=Math.hypot(e.clientX-(r.left+r.width/2),e.clientY-(r.top+r.height/2));if(dist<min){min=dist;selected=i;}});setHighlight(selected);});
    main.addEventListener('pointerup',()=>{if(!down)return;const target=highlighted,prev=down.wasOpen;down=null;if(target>=0){launch(target);return;}if(prev)close();else setHighlight(-1);});
    main.addEventListener('pointercancel',()=>{down=null;setHighlight(-1);});
    document.body.appendChild(wrap);adjustTheme();
  }
  window.__weupWheelClose=()=>{if(opened){close();return true;}return false;};
  window.__weupWheelRefresh=()=>{build();adjustTheme();};
  build();
  let pending=0;
  const observer=new MutationObserver(records=>{
    const relevant=records.some(r=>{
      if(r.target.nodeType===1 && (r.target.classList?.contains('weup-wheel-probe') || r.target.closest?.('#'+ROOT_ID)))return false;
      if(r.type==='childList' && [...r.addedNodes,...r.removedNodes].every(n=>n.nodeType===1 && n.classList?.contains('weup-wheel-probe')))return false;
      return true;
    });
    if(!relevant || pending)return;
    pending=requestAnimationFrame(()=>{pending=0;build();adjustTheme();});
  });
  observer.observe(document.documentElement,{attributes:true,attributeFilter:['class','style','data-theme','data-accent','aria-checked','aria-pressed'],childList:true,subtree:true});
  // Some theme pickers persist only to localStorage without changing an observed root attribute.
  setInterval(()=>{if(!document.hidden)adjustTheme();},1500);
  return true;
})();
