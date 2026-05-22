const STORAGE_KEY = 'aegis_session_id'
export const AEGIS_SESSION_HEADER = 'X-Aegis-Session'

export function getAegisSessionId(): string {
  let id = sessionStorage.getItem(STORAGE_KEY)
  if (!id) {
    id = crypto.randomUUID()
    sessionStorage.setItem(STORAGE_KEY, id)
  }
  return id
}

export function aegisSessionHeaders(extra?: Record<string, string>): Record<string, string> {
  return {
    [AEGIS_SESSION_HEADER]: getAegisSessionId(),
    ...extra,
  }
}
