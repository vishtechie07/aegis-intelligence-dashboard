import { describe, it, expect } from 'vitest'
import { threatLabel } from './insightLabels'

describe('insightLabels', () => {
  it('maps threat tiers aligned with API high filter', () => {
    expect(threatLabel(4)).toBe('Low')
    expect(threatLabel(5)).toBe('Elevated')
    expect(threatLabel(6)).toBe('Elevated')
    expect(threatLabel(7)).toBe('High')
    expect(threatLabel(8)).toBe('High')
    expect(threatLabel(9)).toBe('Critical')
    expect(threatLabel(10)).toBe('Critical')
  })
})
