export type CartridgeStatus = 'IN_STOCK' | 'INSTALLED' | 'ON_REFILL' | 'WRITTEN_OFF'

export interface CurrentPrinterInstallation {
  cartridgeId: number
  inventoryCode: string
  cartridgeModelName: string
  quantity: number
  refillable?: boolean | null
  empty?: boolean | null
}

export type PrinterType = 'MONOCHROME' | 'COLOR'

export interface PrinterSlot {
  name: string
  id?: number
  cartridgeModelId?: number | null
  cartridgeModelName?: string | null
  previousReplacementDate?: string | null
  lastReplacementDate?: string | null
  currentInstallation?: CurrentPrinterInstallation | null
}

export interface Printer {
  id?: number
  name: string
  departmentId?: number | null
  departmentName?: string | null
  printerType: PrinterType
  slots: PrinterSlot[]
}

export interface Department {
  id: number
  name: string
  description?: string | null
  printers?: Printer[]
}

export interface CartridgeModel {
  id: number
  name: string
  refillable: boolean
  minimumQuantity: number
}

export interface Cartridge {
  id: number
  inventoryCode: string
  cartridgeModelId: number
  cartridgeModelName: string
  departmentId: number
  departmentName: string
  quantity: number
  installedQuantity?: number
  refillable?: boolean | null
  empty?: boolean | null
  status: CartridgeStatus
  refillCount: number
  lastRefillDate: string | null
  comment?: string | null
}

export interface CreateDepartmentPayload {
  name: string
  description?: string
}

export interface CreateCartridgeModelPayload {
  name: string
  refillable: boolean
  minimumQuantity: number
}

export interface UpdateCartridgeModelPayload {
  name: string
  refillable: boolean
  minimumQuantity: number
}

export interface CreateCartridgePayload {
  inventoryCode?: string
  cartridgeModelId: number
  departmentId?: number
  quantity: number
  refillable?: boolean
  status?: CartridgeStatus
  comment?: string
}

export interface RefillHistoryRecord {
  id: number
  cartridgeId: number
  inventoryCode: string
  sentAt: string | null
  returnedAt: string | null
  status: string
  quantity: number
  comment?: string | null
  createdBy?: string | null
}

export interface ActionLogRecord {
  id: number
  actionType: string
  targetName: string
  details?: string | null
  actor?: string | null
  createdAt: string
}

export interface ReplaceCartridgePayload {
  printerId: number
  removedOutcome: 'STOCK' | 'REFILL' | 'WRITE_OFF'
  comment?: string
  actionDate?: string
  createdBy?: string
}

export interface UpsertPrinterPayload {
  name: string
  departmentId: number
  printerType: PrinterType
  slots: Array<{
    name: string
    cartridgeModelId: number
  }>
}

const API_BASE =
  import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '')

function extractErrorMessage(status: number, text: string): string {
  if (!text) {
    return `HTTP ${status}`
  }

  try {
    const parsed = JSON.parse(text) as {
      message?: string
      details?: string[]
      error?: string
    }

    const parts = [parsed.message, parsed.error, parsed.details?.[0]].filter(Boolean)
    if (parts.length > 0) {
      return parts.join('. ')
    }
  } catch {
    // ignore invalid json and fall back to raw text
  }

  return text
}

async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers || {}),
    },
    ...init,
  })

  if (!response.ok) {
    const text = await response.text()
    throw new Error(extractErrorMessage(response.status, text))
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export function getDepartments(): Promise<Department[]> {
  return fetchJson<Department[]>('/api/departments')
}

export function getCartridgeModels(): Promise<CartridgeModel[]> {
  return fetchJson<CartridgeModel[]>('/api/cartridge-models')
}

export function getPrinters(): Promise<Printer[]> {
  return fetchJson<Printer[]>('/api/printers')
}

export function createDepartment(payload: CreateDepartmentPayload): Promise<Department> {
  return fetchJson<Department>('/api/departments', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateDepartment(id: number, payload: CreateDepartmentPayload): Promise<Department> {
  return fetchJson<Department>(`/api/departments/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteDepartment(id: number): Promise<void> {
  return fetchJson<void>(`/api/departments/${id}`, {
    method: 'DELETE',
  })
}

export function createPrinter(payload: UpsertPrinterPayload): Promise<Printer> {
  return fetchJson<Printer>('/api/printers', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePrinter(id: number, payload: UpsertPrinterPayload): Promise<Printer> {
  return fetchJson<Printer>(`/api/printers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deletePrinter(id: number): Promise<void> {
  return fetchJson<void>(`/api/printers/${id}`, {
    method: 'DELETE',
  })
}

export function createCartridgeModel(payload: CreateCartridgeModelPayload): Promise<CartridgeModel> {
  return fetchJson<CartridgeModel>('/api/cartridge-models', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateCartridgeModel(id: number, payload: UpdateCartridgeModelPayload): Promise<CartridgeModel> {
  return fetchJson<CartridgeModel>(`/api/cartridge-models/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteCartridgeModel(id: number): Promise<void> {
  return fetchJson<void>(`/api/cartridge-models/${id}`, {
    method: 'DELETE',
  })
}

export function getCartridges(filters?: {
  departmentId?: number
  status?: CartridgeStatus
}): Promise<Cartridge[]> {
  const params = new URLSearchParams()
  if (filters?.departmentId) params.set('departmentId', String(filters.departmentId))
  if (filters?.status) params.set('status', filters.status)
  const query = params.toString()
  return fetchJson<Cartridge[]>(`/api/cartridges${query ? `?${query}` : ''}`)
}

export function getRefillHistory(cartridgeId: number): Promise<RefillHistoryRecord[]> {
  return fetchJson<RefillHistoryRecord[]>(`/api/refill-history/cartridge/${cartridgeId}`)
}

export function getActionLogs(): Promise<ActionLogRecord[]> {
  return fetchJson<ActionLogRecord[]>('/api/action-logs')
}

export function createCartridge(payload: CreateCartridgePayload): Promise<Cartridge> {
  return fetchJson<Cartridge>('/api/cartridges', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteCartridge(id: number): Promise<void> {
  return fetchJson<void>(`/api/cartridges/${id}`, {
    method: 'DELETE',
  })
}

export function adjustQuantity(id: number, quantity: number, comment: string): Promise<Cartridge> {
  return fetchJson<Cartridge>(`/api/cartridges/${id}/quantity`, {
    method: 'PATCH',
    body: JSON.stringify({ quantity, comment }),
  })
}

export function sendToRefill(
  id: number,
  quantity: number,
  sentAt: string,
  createdBy: string,
  comment: string,
): Promise<Cartridge> {
  return fetchJson<Cartridge>(`/api/cartridges/${id}/send-to-refill`, {
    method: 'POST',
    body: JSON.stringify({ quantity, sentAt, createdBy, comment }),
  })
}

export function installCartridge(
  id: number,
  printerId: number,
  quantity: number,
  comment: string,
): Promise<Cartridge> {
  return fetchJson<Cartridge>(`/api/cartridges/${id}/install`, {
    method: 'POST',
    body: JSON.stringify({ printerId, quantity, comment }),
  })
}

export function replaceCartridge(id: number, payload: ReplaceCartridgePayload): Promise<Cartridge> {
  return fetchJson<Cartridge>(`/api/cartridges/${id}/replace`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function removeCartridgeInstallation(
  id: number,
  printerId: number,
  quantity: number,
  returnToStock: boolean,
  comment: string,
): Promise<Cartridge> {
  return fetchJson<Cartridge>(`/api/cartridges/${id}/remove-installation`, {
    method: 'POST',
    body: JSON.stringify({ printerId, quantity, returnToStock, comment }),
  })
}

export function updateCartridgeRefillable(id: number, refillable: boolean): Promise<Cartridge> {
  return fetchJson<Cartridge>(`/api/cartridges/${id}/refillable`, {
    method: 'PATCH',
    body: JSON.stringify({ refillable }),
  })
}

export function returnFromRefill(
  id: number,
  quantity: number,
  returnedAt: string,
  createdBy: string,
  comment: string,
): Promise<Cartridge> {
  return fetchJson<Cartridge>(`/api/cartridges/${id}/return-from-refill`, {
    method: 'POST',
    body: JSON.stringify({ quantity, returnedAt, createdBy, comment }),
  })
}

export function writeOff(id: number, comment: string): Promise<Cartridge> {
  return fetchJson<Cartridge>(`/api/cartridges/${id}/write-off`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  })
}

export function markCartridgeEmpty(id: number, comment: string): Promise<Cartridge> {
  return fetchJson<Cartridge>(`/api/cartridges/${id}/mark-empty`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  })
}
