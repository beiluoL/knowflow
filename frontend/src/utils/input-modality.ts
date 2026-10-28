/** 仅标记焦点视觉来源；不拦截默认行为、输入、快捷键或IME。 */
export function installInputModality(doc: Document = document) {
  const root=doc.documentElement;
  root.dataset.kfInputModality='pointer';
  const pointer=()=>{root.dataset.kfInputModality='pointer'};
  const keyboard=(event:KeyboardEvent)=>{
    if(event.isComposing||event.metaKey||event.ctrlKey||event.altKey)return;
    const target=event.target;
    const editing=target instanceof HTMLElement && target.matches('input,textarea,[contenteditable=true]');
    if(event.key==='Tab'||(!editing&&['ArrowDown','ArrowUp','ArrowLeft','ArrowRight','Home','End'].includes(event.key)))root.dataset.kfInputModality='keyboard';
  };
  doc.addEventListener('pointerdown',pointer,true);
  doc.addEventListener('keydown',keyboard,true);
  return ()=>{doc.removeEventListener('pointerdown',pointer,true);doc.removeEventListener('keydown',keyboard,true);delete root.dataset.kfInputModality};
}
