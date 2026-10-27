import {describe,it,expect} from 'vitest'
import {placeSelect, placeAnchoredPanel} from '../select-position'

const anchor = (left: number, top: number, width = 240, height = 44) => ({
  left,
  right: left + width,
  top,
  bottom: top + height,
  width,
})

describe('anchored date/time panel placement', () => {
  it('opens below and aligns with the trigger when there is room', () => {
    const a = anchor(200, 120)
    const p = placeAnchoredPanel(a, 320, 360, { width: 1440, height: 900 })
    expect(p.placement).toBe('bottom')
    expect(p.left).toBe(200)
    expect(p.top).toBe(a.bottom + 4)
    expect(p.width).toBe(320)
    expect(p.maxHeight).toBe(360)
  })

  it('flips above and sits on the trigger top edge when the viewport bottom is short', () => {
    const a = anchor(200, 760)
    const p = placeAnchoredPanel(a, 320, 360, { width: 1440, height: 900 })
    expect(p.placement).toBe('top')
    expect(p.top + p.maxHeight).toBe(a.top - 4)
    expect(p.top).toBeGreaterThanOrEqual(8)
  })

  it('clamps to the right edge instead of overflowing the viewport', () => {
    const a = anchor(1200, 200, 200)
    const p = placeAnchoredPanel(a, 320, 320, { width: 1440, height: 900 })
    expect(p.left + p.width).toBe(1440 - 8)
    expect(p.left).toBeLessThan(a.left)
  })

  it('shrinks panel height into a short viewport and keeps 8px margins', () => {
    const a = anchor(200, 100)
    const p = placeAnchoredPanel(a, 320, 520, { width: 1024, height: 400 })
    expect(p.top).toBeGreaterThanOrEqual(8)
    expect(p.top + p.maxHeight).toBeLessThanOrEqual(392)
    expect(p.maxHeight).toBeLessThan(520)
  })

  it('respects a visualViewport offset (page zoom / on-screen keyboard)', () => {
    const a = anchor(300, 220)
    const viewport = { width: 800, height: 400, left: 40, top: 60 }
    const p = placeAnchoredPanel(a, 320, 300, viewport)
    expect(p.left).toBeGreaterThanOrEqual(48)
    expect(p.top).toBeGreaterThanOrEqual(68)
    expect(p.left + p.width).toBeLessThanOrEqual(40 + 800 - 8)
    expect(p.top + p.maxHeight).toBeLessThanOrEqual(60 + 400 - 8)
  })

  it('never lets the panel cover the trigger when flipping', () => {
    const a = anchor(500, 700)
    const p = placeAnchoredPanel(a, 320, 600, { width: 1200, height: 768 })
    const panelBottom = p.top + p.maxHeight
    expect(panelBottom <= a.top).toBe(true)
    expect(p.maxHeight).toBeGreaterThan(0)
  })

  it('shrinks oversized width to the viewport minus safe margins', () => {
    const a = anchor(10, 100, 60)
    const p = placeAnchoredPanel(a, 400, 300, { width: 300, height: 640 })
    expect(p.width).toBe(284)
    expect(p.left).toBe(8)
  })
})

describe('shared select placement',()=>{
 it('flips upward and clamps to the right viewport edge',()=>{const p=placeSelect({left:950,right:1000,top:700,bottom:740,width:50},240,300,{width:1024,height:768});expect(p.left+p.width).toBeLessThanOrEqual(1016);expect(p.top).toBeLessThan(700);expect(p.top+p.maxHeight).toBeLessThanOrEqual(760)})
 it('opens a submenu to the left when the right has no room',()=>{const p=placeSelect({left:780,right:1000,top:600,bottom:640,width:220},224,300,{width:1024,height:768},true);expect(p.left).toBe(552);expect(p.top+p.maxHeight).toBeLessThanOrEqual(760)})
})

it('shrinks to the available side without overlapping the trigger',()=>{const a={left:80,right:320,top:200,bottom:240,width:240};const p=placeSelect(a,240,352,{width:1000,height:500});expect(p.top).toBe(244);expect(p.maxHeight).toBe(248);expect(p.top+p.maxHeight).toBeLessThanOrEqual(492)})
