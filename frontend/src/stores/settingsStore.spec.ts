import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSettingsStore } from './settingsStore'

describe('settingsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(globalThis.fetch).mockReset()
    localStorage.clear()
    sessionStorage.clear()
  })

  it('initial state is not configured', () => {
    const store = useSettingsStore()
    expect(store.isConfigured).toBe(false)
    expect(store.isRuntimeKey).toBe(false)
    expect(store.isSaving).toBe(false)
    expect(store.error).toBeNull()
  })

  it('saveKey with remember false persists to sessionStorage', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      text: async () => JSON.stringify({ status: 'Key accepted' }),
    } as Response)

    const store = useSettingsStore()
    const ok = await store.saveKey('sk-proj-session', false)

    expect(ok).toBe(true)
    expect(sessionStorage.setItem).toHaveBeenCalledWith('aegis_openai_key_session', 'sk-proj-session')
    expect(localStorage.setItem).toHaveBeenCalledWith('aegis_key_remember', '0')
  })

  it('saveKey calls PUT endpoint and persists to localStorage on success', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      text: async () => JSON.stringify({ status: 'Key accepted — AI agents are now active' }),
    } as Response)

    const store = useSettingsStore()
    const result = await store.saveKey('sk-proj-testkey1234567890')

    expect(result).toBe(true)
    expect(store.isConfigured).toBe(true)
    expect(store.isRuntimeKey).toBe(true)
    expect(store.successMessage).toContain('Key accepted')
    expect(localStorage.setItem).toHaveBeenCalledWith('aegis_openai_key', 'sk-proj-testkey1234567890')
    expect(localStorage.setItem).toHaveBeenCalledWith('aegis_key_remember', '1')
  })

  it('saveKey sets error on backend rejection', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      text: async () => JSON.stringify({ error: 'Invalid OpenAI API key format' }),
    } as Response)

    const store = useSettingsStore()
    const result = await store.saveKey('bad-key')

    expect(result).toBe(false)
    expect(store.error).toBe('Invalid OpenAI API key format')
    expect(store.isConfigured).toBe(false)
  })

  it('saveKey sets network error on fetch failure', async () => {
    vi.mocked(globalThis.fetch).mockRejectedValueOnce(new Error('Network unreachable'))

    const store = useSettingsStore()
    const result = await store.saveKey('sk-proj-test')

    expect(result).toBe(false)
    expect(store.error).toContain('Network error')
  })

  it('saveKey 401 sets aiKeyIssueBanner', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: false,
      status: 401,
      text: async () => JSON.stringify({ error: 'Unauthorized' }),
    } as Response)

    const store = useSettingsStore()
    await store.saveKey('bad')

    expect(store.aiKeyIssueBanner).toContain('OpenAI rejected')
  })

  it('clearKey removes from localStorage and resets state', async () => {
    vi.mocked(globalThis.fetch)
      .mockResolvedValueOnce({
        ok: true,
        text: async () => JSON.stringify({ status: 'Key accepted' }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => JSON.stringify({ status: 'Reverted to server default API key' }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ configured: false, runtimeKeySet: false, serverKeyAvailable: false }),
      } as Response)

    const store = useSettingsStore()
    await store.saveKey('sk-proj-somekey')
    await store.clearKey()

    expect(store.isConfigured).toBe(false)
    expect(store.isRuntimeKey).toBe(false)
    expect(localStorage.removeItem).toHaveBeenCalledWith('aegis_openai_key')
    expect(localStorage.removeItem).toHaveBeenCalledWith('aegis_key_remember')
    expect(sessionStorage.removeItem).toHaveBeenCalledWith('aegis_openai_key_session')
  })

  it('checkStatus updates isConfigured from backend response', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ configured: true, runtimeKeySet: false, serverKeyAvailable: true }),
    } as Response)

    const store = useSettingsStore()
    await store.checkStatus()

    expect(store.isConfigured).toBe(true)
    expect(store.isRuntimeKey).toBe(false)
  })

  it('isSaving is true during save and false after', async () => {
    let resolveFetch!: (value: Response) => void
    vi.mocked(globalThis.fetch).mockReturnValueOnce(
      new Promise<Response>(res => { resolveFetch = res })
    )

    const store = useSettingsStore()
    const promise = store.saveKey('sk-proj-key')
    expect(store.isSaving).toBe(true)

    resolveFetch({ ok: true, text: async () => JSON.stringify({ status: 'Key accepted' }) } as Response)
    await promise
    expect(store.isSaving).toBe(false)
  })
})
