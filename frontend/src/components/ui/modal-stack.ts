// Shared only by opted-in AppDialog modals; one lock survives nested dialogs.
const stack: number[] = []
const surfaces=new Map<number,HTMLElement>()
let savedOverflow = ''
export function enterModal(id:number, surface?:HTMLElement|null) {
  if(surface)surfaces.set(id,surface)
  if(stack.includes(id)) return
  if(!stack.length){savedOverflow=document.body.style.overflow;document.body.style.overflow='hidden'}
  stack.push(id)
}
export function isTopModal(id:number){return stack.at(-1)===id}
export function hasModal(){return stack.length>0}
export function leaveModal(id:number){const at=stack.indexOf(id);if(at<0)return;stack.splice(at,1);surfaces.delete(id);if(!stack.length)document.body.style.overflow=savedOverflow}

export function focusTopModal(){const id=stack.at(-1);if(id!==undefined)surfaces.get(id)?.focus()}
