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
export type RoomStatus = 'ACTIVE' | 'DECOMMISSIONED'

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
  roomId?: number | null
  roomName?: string | null
  printerType: PrinterType
  slots: PrinterSlot[]
}

export interface Department {
  id: number
  name: string
  description?: string | null
  printers?: Printer[]
}

export interface Room {
  id: number
  name: string
  departmentId: number
  departmentName: string
  status: RoomStatus
  comment?: string | null
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

export interface UpsertRoomPayload {
  name: string
  departmentId: number
  status: RoomStatus
  comment?: string
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

export interface ActionLogFilters {
  dateFrom?: string
  dateTo?: string
  actor?: string
  actionType?: string
  targetName?: string
}

export type SystemModuleCode = 'CARTRIDGE_ACCOUNTING' | 'HOTEL_INVENTORY' | 'HALL_REQUESTS'
export type SystemModuleStatus = 'ACTIVE' | 'PLANNED'
export type InventoryAssetStatus = 'IN_USE' | 'IN_STOCK' | 'IN_REPAIR' | 'WRITTEN_OFF'
export type HallRequestPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type HallRequestStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'

export interface SystemModule {
  code: SystemModuleCode
  title: string
  status: SystemModuleStatus
  description: string
  plannedScope?: string
}

export interface InventoryAsset {
  id: number
  inventoryCode: string
  name: string
  category?: string | null
  departmentId?: number | null
  departmentName?: string | null
  roomId?: number | null
  roomName?: string | null
  status: InventoryAssetStatus
  quantity: number
  comment?: string | null
}

export interface UpsertInventoryAssetPayload {
  inventoryCode: string
  name: string
  category?: string
  departmentId?: number
  roomId?: number
  status: InventoryAssetStatus
  quantity: number
  comment?: string
}

export interface TransferInventoryAssetPayload {
  toDepartmentId?: number
  toRoomId?: number
  actor?: string
  comment?: string
  movedAt?: string
}

export interface InventoryAssetMovement {
  id: number
  assetId: number
  assetInventoryCode: string
  assetName: string
  fromDepartmentId?: number | null
  fromDepartmentName?: string | null
  fromRoomId?: number | null
  fromRoomName?: string | null
  toDepartmentId?: number | null
  toDepartmentName?: string | null
  toRoomId?: number | null
  toRoomName?: string | null
  movedAt: string
  actor?: string | null
  comment?: string | null
}

export interface HallRequest {
  id: number
  roomId: number
  roomName: string
  departmentId: number
  departmentName: string
  requesterName: string
  title: string
  description?: string | null
  priority: HallRequestPriority
  status: HallRequestStatus
  requestedAt: string
  plannedAt?: string | null
  completedAt?: string | null
  slaDueAt: string
  slaOverdue: boolean
  slaMinutesRemaining: number
}

export interface UpsertHallRequestPayload {
  roomId: number
  requesterName: string
  title: string
  description?: string
  priority: HallRequestPriority
  status: HallRequestStatus
  plannedAt?: string
}

export type UserRole = 'ADMIN' | 'OPERATOR' | 'VIEWER'

export interface UserPermissions {
  canViewCatalog: boolean
  canEditCatalog: boolean
  canOperate: boolean
  canViewLogs: boolean
  canExportReports: boolean
  canManageUsers: boolean
  canManageThresholds: boolean
  canManualDatetime: boolean
}

export interface AuthUser {
  id: number
  username: string
  fullName: string
  role: UserRole
  active: boolean
  permissions: UserPermissions
}

export interface AuthResponse {
  token: string
  expiresAt: number
  user: AuthUser
}

export interface UserAdminRecord {
  id: number
  username: string
  fullName: string
  role: UserRole
  active: boolean
  permissions: UserPermissions
}

export interface UpsertUserPayload {
  username: string
  fullName: string
  password?: string
  role: UserRole
  active: boolean
}

export interface NotificationAlert {
  cartridgeModelId: number
  cartridgeModelName: string
  departmentId: number
  departmentName: string
  currentQuantity: number
  thresholdQuantity: number
  source: 'DEPARTMENT' | 'MODEL_DEFAULT' | 'MODEL_MINIMUM' | string
}

export interface NotificationThreshold {
  id: number
  cartridgeModelId: number
  cartridgeModelName: string
  departmentId?: number | null
  departmentName?: string | null
  minimumQuantity: number
  active: boolean
  comment?: string | null
}

export interface UpsertNotificationThresholdPayload {
  cartridgeModelId: number
  departmentId?: number
  minimumQuantity: number
  active: boolean
  comment?: string
}

export interface ConsumptionReportRow {
  modelName: string
  installedOperations: number
  installedQuantity: number
  sentToRefillOperations: number
  sentToRefillQuantity: number
  returnedFromRefillOperations: number
  returnedFromRefillQuantity: number
  writtenOffOperations: number
  writtenOffQuantity: number
  totalOperations: number
  totalQuantity: number
}

export interface ConsumptionReport {
  dateFrom: string
  dateTo: string
  generatedAt: string
  totalOperations: number
  totalQuantity: number
  rows: ConsumptionReportRow[]
}

export interface StockByDepartmentRow {
  departmentId: number
  departmentName: string
  inStockQuantity: number
  onRefillQuantity: number
  installedQuantity: number
  writtenOffQuantity: number
  totalQuantity: number
}

export interface StockByModelRow {
  cartridgeModelId: number
  cartridgeModelName: string
  inStockQuantity: number
  onRefillQuantity: number
  installedQuantity: number
  writtenOffQuantity: number
  totalQuantity: number
}

export interface StockByRoomRow {
  roomId?: number | null
  roomName: string
  inStockQuantity: number
  onRefillQuantity: number
  installedQuantity: number
  writtenOffQuantity: number
  totalQuantity: number
}

export interface StockByTypeRow {
  cartridgeType: string
  inStockQuantity: number
  onRefillQuantity: number
  installedQuantity: number
  writtenOffQuantity: number
  totalQuantity: number
}

export interface StockSnapshotReport {
  generatedAt: string
  totalInStock: number
  totalOnRefill: number
  totalInstalled: number
  totalWrittenOff: number
  byDepartment: StockByDepartmentRow[]
  byModel: StockByModelRow[]
  byRoom: StockByRoomRow[]
  byType: StockByTypeRow[]
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
  roomId?: number
  printerType: PrinterType
  slots: Array<{
    name: string
    cartridgeModelId: number
  }>
}

const API_BASE =
  import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '')
let authToken = ''

export function setAuthToken(token: string | null) {
  authToken = token?.trim() || ''
}

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
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
  }
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      ...headers,
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

async function fetchBlob(path: string): Promise<Blob> {
  const headers: Record<string, string> = {
    ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
  }
  const response = await fetch(`${API_BASE}${path}`, { headers })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(extractErrorMessage(response.status, text))
  }
  return response.blob()
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

export function getRooms(filters?: { departmentId?: number }): Promise<Room[]> {
  const params = new URLSearchParams()
  if (filters?.departmentId) params.set('departmentId', String(filters.departmentId))
  const query = params.toString()
  return fetchJson<Room[]>(`/api/rooms${query ? `?${query}` : ''}`)
}

export function createDepartment(payload: CreateDepartmentPayload): Promise<Department> {
  return fetchJson<Department>('/api/departments', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createRoom(payload: UpsertRoomPayload): Promise<Room> {
  return fetchJson<Room>('/api/rooms', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateRoom(id: number, payload: UpsertRoomPayload): Promise<Room> {
  return fetchJson<Room>(`/api/rooms/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteRoom(id: number): Promise<void> {
  return fetchJson<void>(`/api/rooms/${id}`, {
    method: 'DELETE',
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

export function getActionLogs(filters?: ActionLogFilters): Promise<ActionLogRecord[]> {
  const params = new URLSearchParams()
  if (filters?.dateFrom) params.set('dateFrom', filters.dateFrom)
  if (filters?.dateTo) params.set('dateTo', filters.dateTo)
  if (filters?.actor?.trim()) params.set('actor', filters.actor.trim())
  if (filters?.actionType?.trim()) params.set('actionType', filters.actionType.trim())
  if (filters?.targetName?.trim()) params.set('targetName', filters.targetName.trim())
  const query = params.toString()
  return fetchJson<ActionLogRecord[]>(`/api/action-logs${query ? `?${query}` : ''}`)
}

export function getSystemModules(): Promise<SystemModule[]> {
  return fetchJson<SystemModule[]>('/api/system-modules')
}

export function getInventoryAssets(filters?: {
  departmentId?: number
  roomId?: number
  status?: InventoryAssetStatus
}): Promise<InventoryAsset[]> {
  const params = new URLSearchParams()
  if (filters?.departmentId) params.set('departmentId', String(filters.departmentId))
  if (filters?.roomId) params.set('roomId', String(filters.roomId))
  if (filters?.status) params.set('status', filters.status)
  const query = params.toString()
  return fetchJson<InventoryAsset[]>(`/api/inventory-assets${query ? `?${query}` : ''}`)
}

export function createInventoryAsset(payload: UpsertInventoryAssetPayload): Promise<InventoryAsset> {
  return fetchJson<InventoryAsset>('/api/inventory-assets', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateInventoryAsset(id: number, payload: UpsertInventoryAssetPayload): Promise<InventoryAsset> {
  return fetchJson<InventoryAsset>(`/api/inventory-assets/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteInventoryAsset(id: number): Promise<void> {
  return fetchJson<void>(`/api/inventory-assets/${id}`, {
    method: 'DELETE',
  })
}

export function transferInventoryAsset(id: number, payload: TransferInventoryAssetPayload): Promise<InventoryAsset> {
  return fetchJson<InventoryAsset>(`/api/inventory-assets/${id}/transfer`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getInventoryAssetMovements(assetId?: number): Promise<InventoryAssetMovement[]> {
  const params = new URLSearchParams()
  if (assetId) params.set('assetId', String(assetId))
  const query = params.toString()
  return fetchJson<InventoryAssetMovement[]>(`/api/inventory-assets/movements${query ? `?${query}` : ''}`)
}

export function getHallRequests(filters?: { roomId?: number; status?: HallRequestStatus; overdue?: boolean }): Promise<HallRequest[]> {
  const params = new URLSearchParams()
  if (filters?.roomId) params.set('roomId', String(filters.roomId))
  if (filters?.status) params.set('status', filters.status)
  if (typeof filters?.overdue === 'boolean') params.set('overdue', String(filters.overdue))
  const query = params.toString()
  return fetchJson<HallRequest[]>(`/api/hall-requests${query ? `?${query}` : ''}`)
}

export function createHallRequest(payload: UpsertHallRequestPayload): Promise<HallRequest> {
  return fetchJson<HallRequest>('/api/hall-requests', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateHallRequest(id: number, payload: UpsertHallRequestPayload): Promise<HallRequest> {
  return fetchJson<HallRequest>(`/api/hall-requests/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteHallRequest(id: number): Promise<void> {
  return fetchJson<void>(`/api/hall-requests/${id}`, {
    method: 'DELETE',
  })
}

export function signIn(username: string, password: string): Promise<AuthResponse> {
  return fetchJson<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function getCurrentUser(): Promise<AuthUser> {
  return fetchJson<AuthUser>('/api/auth/me')
}

export function getUsers(): Promise<UserAdminRecord[]> {
  return fetchJson<UserAdminRecord[]>('/api/users')
}

export function createUser(payload: UpsertUserPayload): Promise<UserAdminRecord> {
  return fetchJson<UserAdminRecord>('/api/users', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateUser(id: number, payload: UpsertUserPayload): Promise<UserAdminRecord> {
  return fetchJson<UserAdminRecord>(`/api/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function getNotificationAlerts(): Promise<NotificationAlert[]> {
  return fetchJson<NotificationAlert[]>('/api/notifications/alerts')
}

export function getNotificationThresholds(): Promise<NotificationThreshold[]> {
  return fetchJson<NotificationThreshold[]>('/api/notifications/thresholds')
}

export function createNotificationThreshold(payload: UpsertNotificationThresholdPayload): Promise<NotificationThreshold> {
  return fetchJson<NotificationThreshold>('/api/notifications/thresholds', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateNotificationThreshold(id: number, payload: UpsertNotificationThresholdPayload): Promise<NotificationThreshold> {
  return fetchJson<NotificationThreshold>(`/api/notifications/thresholds/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteNotificationThreshold(id: number): Promise<void> {
  return fetchJson<void>(`/api/notifications/thresholds/${id}`, {
    method: 'DELETE',
  })
}

export function getConsumptionReport(dateFrom: string, dateTo: string): Promise<ConsumptionReport> {
  const params = new URLSearchParams({ dateFrom, dateTo })
  return fetchJson<ConsumptionReport>(`/api/reports/consumption?${params.toString()}`)
}

export function downloadConsumptionReportXlsx(dateFrom: string, dateTo: string): Promise<Blob> {
  const params = new URLSearchParams({ dateFrom, dateTo })
  return fetchBlob(`/api/reports/consumption.xlsx?${params.toString()}`)
}

export function downloadConsumptionReportPdf(dateFrom: string, dateTo: string): Promise<Blob> {
  const params = new URLSearchParams({ dateFrom, dateTo })
  return fetchBlob(`/api/reports/consumption.pdf?${params.toString()}`)
}

export function getStockSnapshotReport(): Promise<StockSnapshotReport> {
  return fetchJson<StockSnapshotReport>('/api/reports/stock-snapshot')
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
