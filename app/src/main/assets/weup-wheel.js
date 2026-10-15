/* Weupperu.com — native-shell companion: uses the *actual* sidebar SVG/icon and selected theme. */
(function () {
  'use strict';
  if (window.__weupWheelInstalled) return true;
  window.__weupWheelInstalled = true;
  const normalize = s => (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  const actions = [
    {id:'note', label:'Nota', nav:/^(mis )?notas?$/, fallback:'<path d="M12 5v14M5 12h14"/>'},
    {id:'space', label:'Espacio', nav:/^espacios?$/, fallback:'<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>'},
    {id:'notebook', label:'Libreta', nav:/^libretas?$/, fallback:'<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"/>'},
    {id:'search', label:'Buscar', nav:/^(buscar|busqueda)$/, fallback:'<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>'}
  ];
  let wrap, main, shade, buttons = [], open=false, down=null, highlighted=-1;
  const $ = (sel, base=document) => base.querySelector(sel);
  function sidebar() { return $('[data-sidebar],aside,[class*="sidebar"],nav,[role="navigation"]'); }
  function iconFor(item) {
    const root=sidebar();
    if (root) {
      const all=Array.from(root.querySelectorAll('a,button,[role="button"]'));
      const exact=all.find(el => {
        const aria=normalize(el.getAttribute('aria-label') || el.getAttribute('title'));
        const txt=normalize(el.textContent).replace(/\s*\d+\s*$/, '');
        return item.nav.test(aria) || item.nav.test(txt);
      });
      if (exact) {
        const icon=exact.querySelector('svg,i,[class*="icon"],img');
        if (icon) {
          const copy=icon.cloneNode(true);
          copy.removeAttribute('id'); copy.removeAttribute('aria-label'); copy.setAttribute('aria-hidden','true');
          copy.style.width='31px';copy.style.height='31px';copy.style.flex='none';copy.style.fontSize='30px';
          if(copy.tagName.toLowerCase()==='svg') { copy.setAttribute('width','31');copy.setAttribute('height','31'); }
          return copy;
        }
      }
    }
    const temp=document.createElement('span');temp.innerHTML='<svg viewBox="0 0 24 24" width="31" height="31" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+item.fallback+'</svg>';
    return temp.firstElementChild;
  }
  const VALID=/^#[0-9a-f]{3,8}$|^rgba?\(/i;
  function accent() {
    const styles=[getComputedStyle(document.documentElement),getComputedStyle(document.body)];
    const vars=['--theme-primary','--theme-color','--color-primary','--primary-color','--primary','--accent-color','--accent','--brand-color','--brand','--weup-primary'];
    for(const s of styles)for(const v of vars){const val=s.getPropertyValue(v).trim();if(VALID.test(val))return val;}
    const root=sidebar();
    if(root) {
      const active=root.querySelector('[aria-current="page"],[aria-selected="true"],.active,[class*="active"]');
      if(active){const s=getComputedStyle(active);if(VALID.test(s.backgroundColor)&&!/^rgba?\(0, 0, 0, 0\)/.test(s.backgroundColor))return s.backgroundColor;}
    }
    return '#e92176';
  }
  function adjustTheme(){ if(wrap){const color=accent();if(wrap.style.getPropertyValue('--weup-wheel-color')!==color)wrap.style.setProperty('--weup-wheel-color',color);} }
  function launch(index){
    const a=actions[index];if(!a)return;
    close();
    window.location.href='weupnotes-action://'+a.id;
  }
  function highlight(index) {highlighted=index; buttons.forEach((b,i)=>b.classList.toggle('weup-wheel-hover',i===index));}
  function openMenu(){open=true;wrap.classList.add('weup-wheel-open');main.setAttribute('aria-expanded','true');main.textContent='×';adjustTheme();buttons.forEach((b,i)=>{b.innerHTML='';b.appendChild(iconFor(actions[i]));const label=document.createElement('span');label.textContent=actions[i].label;b.appendChild(label);});}
  function close(){if(main)main.dataset.wasOpen='no';open=false;wrap.classList.remove('weup-wheel-open');main.setAttribute('aria-expanded','false');main.textContent='+';highlight(-1);}
  function build() {
    if(wrap||!sidebar())return;
    const style=document.createElement('style');style.id='weup-wheel-styles';style.textContent=`
      #weup-wheel {position:fixed!important;right:18px!important;bottom:calc(20px + env(safe-area-inset-bottom, 0px))!important;width:58px!important;height:58px!important;z-index:2147483500!important;pointer-events:none!important;font-family:inherit!important;--weup-wheel-color:#e92176;}
      #weup-wheel *{box-sizing:border-box!important;}
      #weup-wheel .weup-wheel-shade {position:fixed!important;inset:0!important;background:rgba(0,0,0,.27)!important;display:none!important;pointer-events:auto!important;}
      #weup-wheel.weup-wheel-open .weup-wheel-shade{display:block!important;}
      #weup-wheel .weup-wheel-main,#weup-wheel .weup-wheel-option {display:flex!important;justify-content:center!important;align-items:center!important;background:var(--weup-wheel-color)!important;color:white!important;border:none!important;outline:none!important;border-radius:999px!important;box-shadow:0 4px 16px rgba(0,0,0,.17)!important;cursor:pointer!important;pointer-events:auto!important;line-height:1.05!important;text-decoration:none!important;}
      #weup-wheel .weup-wheel-main{position:absolute!important;inset:0!important;width:58px!important;height:58px!important;font-size:34px!important;font-weight:400!important;touch-action:none!important;}
      #weup-wheel .weup-wheel-option{position:absolute!important;width:78px!important;height:78px!important;flex-direction:column!important;gap:3px!important;font-size:11px!important;font-weight:600!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important;transition:transform .16s ease,opacity .16s ease!important;touch-action:none!important;}
      #weup-wheel .weup-wheel-option svg {width:31px!important;height:31px!important;max-width:31px!important;max-height:31px!important;flex:none!important;}
      #weup-wheel.weup-wheel-open .weup-wheel-option{opacity:1!important;visibility:visible!important;pointer-events:auto!important;}
      #weup-wheel .weup-wheel-option.weup-wheel-hover{filter:brightness(.83)!important;transform:scale(1.13)!important;}
      #weup-wheel .weup-wheel-option:nth-of-type(1){right:122px!important;bottom:1px!important;}
      #weup-wheel .weup-wheel-option:nth-of-type(2){right:111px!important;bottom:83px!important;}
      #weup-wheel .weup-wheel-option:nth-of-type(3){right:48px!important;bottom:131px!important;}
      #weup-wheel .weup-wheel-option:nth-of-type(4){right:-10px!important;bottom:148px!important;}
      @media(prefers-reduced-motion:reduce){#weup-wheel .weup-wheel-option{transition:none!important;}}
    `;document.head.appendChild(style);
    wrap=document.createElement('div');wrap.id='weup-wheel';wrap.setAttribute('aria-label','Acciones rápidas');
    shade=document.createElement('div');shade.className='weup-wheel-shade';shade.onclick=close;wrap.appendChild(shade);
    actions.forEach((a,i)=>{const b=document.createElement('button');b.type='button';b.className='weup-wheel-option';b.setAttribute('aria-label',a.label);b.addEventListener('click',()=>launch(i));wrap.appendChild(b);buttons.push(b);});
    main=document.createElement('button');main.type='button';main.className='weup-wheel-main';main.textContent='+';main.setAttribute('aria-label','Acciones rápidas');main.setAttribute('aria-expanded','false');wrap.appendChild(main);
    main.addEventListener('pointerdown',e=>{down={x:e.clientX,y:e.clientY,moved:false};main.setPointerCapture(e.pointerId);if(!open)openMenu();});
    main.addEventListener('pointermove',e=>{if(!down)return;const dx=e.clientX-down.x,dy=e.clientY-down.y;if(Math.hypot(dx,dy)>16)down.moved=true;let chosen=-1,min=49;buttons.forEach((b,i)=>{const rect=b.getBoundingClientRect();const d=Math.hypot(e.clientX-(rect.left+rect.width/2),e.clientY-(rect.top+rect.height/2));if(d<min){min=d;chosen=i;}});highlight(chosen);});
    main.addEventListener('pointerup',e=>{if(!down)return;const selected=highlighted,dragged=down.moved;down=null;if(selected>=0){launch(selected);return;}if(!dragged){if(open && main.textContent==='×') { /* first tap stays open, second closes */ if(main.dataset.wasOpen==='yes'){close();main.dataset.wasOpen='no';}else main.dataset.wasOpen='yes';}}highlight(-1);});
    main.addEventListener('pointercancel',()=>{down=null;highlight(-1);});
    document.body.appendChild(wrap);adjustTheme();
  }
  build();
  let refresh=0;const observer=new MutationObserver(()=>{if(refresh)return;refresh=requestAnimationFrame(()=>{refresh=0;if(!wrap)build();else {adjustTheme();if(open)buttons.forEach((b,i)=>{if(!b.querySelector('svg,i,img,[class*="icon"]'))b.prepend(iconFor(actions[i]));});}});});
  observer.observe(document.documentElement,{attributes:true,attributeFilter:['class','style','data-theme'],childList:true,subtree:true});
  return true;
})();
