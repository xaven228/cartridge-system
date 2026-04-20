import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  adjustQuantity,
  createHallRequest,
  createInventoryAsset,
  createCartridge,
  createCartridgeModel,
  createDepartment,
  createNotificationThreshold,
  createUser,
  createRoom,
  createPrinter,
  downloadConsumptionReportPdf,
  downloadConsumptionReportXlsx,
  downloadStockSnapshotReportPdf,
  downloadStockSnapshotReportXlsx,
  deleteCartridge,
  deleteCartridgeModel,
  deleteDepartment,
  deleteHallRequest,
  deleteInventoryAsset,
  deleteNotificationThreshold,
  deletePrinter,
  deleteRoom,
  getCartridges,
  getCartridgeModels,
  getConsumptionReport,
  getStockSnapshotReport,
  getActionLogs,
  getDepartments,
  getHallRequests,
  getInventoryAssets,
  getInventoryAssetMovements,
  getNotificationAlerts,
  getNotificationThresholds,
  getPrinters,
  getRooms,
  getSystemModules,
  getUsers,
  installCartridge,
  markCartridgeEmpty,
  replaceCartridge,
  removeCartridgeInstallation,
  refreshAuthSession,
  setUnauthorizedHandler,
  returnFromRefill,
  setAuthToken,
  sendToRefill,
  signIn,
  transferInventoryAsset,
  updateCartridgeModel,
  updateDepartment,
  updateHallRequest,
  updateInventoryAsset,
  updateNotificationThreshold,
  updatePrinter,
  updateRoom,
  updateUser,
  writeOff,
} from './api'
import type { ActionLogRecord, AuthUser, Cartridge, CartridgeModel, CartridgeStatus, Department, DepartmentStatus, HallRequest, HallRequestPriority, HallRequestStatus, InventoryAsset, InventoryAssetStatus, NotificationAlert, NotificationThreshold, Printer, PrinterColorMode, PrinterDeviceType, PrinterStatus, Room, RoomStatus, UpsertHallRequestPayload, UpsertInventoryAssetPayload, UserAdminRecord, UserPermissions, UserRole } from './api'
import type { InventoryAssetMovement, TransferInventoryAssetPayload } from './api'
import type { ConsumptionReport, StockSnapshotReport } from './api'
import type { SystemModule } from './api'

const STATUS_LIST: CartridgeStatus[] = ['IN_STOCK', 'RESERVE', 'INSTALLED', 'ON_REFILL', 'WRITTEN_OFF']
const PAGE_SIZE = 8
const STOCK_DEPARTMENT_NAME = 'Склад'
const AUTH_STORAGE_KEY = 'cartridge-auth-session'
const SESSION_REFRESH_WINDOW_MS = 5 * 60 * 1000
const SESSION_ACTIVITY_THROTTLE_MS = 15 * 1000

type SortKey = 'departmentName' | 'status' | 'quantity' | 'refillCount'
type ToastKind = 'success' | 'error'
type TabKey = 'overview' | 'stock' | 'departments' | 'printers' | 'history' | 'create' | 'notifications' | 'reports' | 'users' | 'inventory' | 'hall-requests'
type DetailAction = 'send' | 'return' | 'writeoff' | null
type RemovalOutcome = 'STOCK' | 'REFILL' | 'WRITE_OFF'
type BatchEntry = { quantity: number; comment: string }
type PrinterSlotForm = { name: string; cartridgeModelId: number | '' }
type WriteOffRequest = { source: 'stock' | 'detail'; cartridgeId: number; label: string }
type ReportSummaryView = 'department' | 'model' | 'room' | 'type' | 'printer'
type ReportItemsView = 'stock' | 'reserve' | 'refill' | 'writtenOff'
type ModelCatalogTypeFilter = 'all' | 'refillable' | 'disposable'
type ModelCatalogBalanceFilter = 'all' | 'deficit' | 'minimum' | 'surplus'
type DeleteTarget =
  | { kind: 'printer'; id: number; label: string }
  | { kind: 'department'; id: number; label: string }
  | { kind: 'room'; id: number; label: string }
  | { kind: 'threshold'; id: number; label: string }
  | { kind: 'inventory-asset'; id: number; label: string }
  | { kind: 'hall-request'; id: number; label: string }
  | { kind: 'cartridge'; id: number; label: string }
  | { kind: 'model'; id: number; label: string }

interface Toast {
  id: number
  kind: ToastKind
  text: string
}

const STATUS_LABELS: Record<CartridgeStatus, string> = {
  IN_STOCK: 'На складе',
  RESERVE: 'Резерв',
  INSTALLED: 'Установлен',
  ON_REFILL: 'На заправке',
  WRITTEN_OFF: 'Списан',
}

const STATUS_TONES: Record<CartridgeStatus, string> = {
  IN_STOCK: 'status-in_stock',
  RESERVE: 'status-reserve',
  INSTALLED: 'status-installed',
  ON_REFILL: 'status-on_refill',
  WRITTEN_OFF: 'status-written_off',
}

const ACTION_LOG_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Все действия' },
  { value: 'USER_LOGIN', label: 'Вход в систему' },
  { value: 'USER_CREATED', label: 'Создание пользователя' },
  { value: 'USER_UPDATED', label: 'Изменение пользователя' },
  { value: 'CARTRIDGE_CREATED', label: 'Приход' },
  { value: 'CARTRIDGE_QUANTITY_CHANGED', label: 'Изменение остатка' },
  { value: 'CARTRIDGE_INSTALLED', label: 'Установка' },
  { value: 'CARTRIDGE_REMOVED', label: 'Снятие' },
  { value: 'CARTRIDGE_SENT_TO_REFILL', label: 'Отправка на заправку' },
  { value: 'CARTRIDGE_RETURNED_FROM_REFILL', label: 'Возврат с заправки' },
  { value: 'CARTRIDGE_WRITTEN_OFF', label: 'Списание' },
  { value: 'CARTRIDGE_MARKED_EMPTY', label: 'Пометка пустым' },
  { value: 'CARTRIDGE_REFILLABLE_CHANGED', label: 'Изменение типа' },
  { value: 'CARTRIDGE_DELETED', label: 'Удаление остатка' },
  { value: 'DEPARTMENT_CREATED', label: 'Создание отдела' },
  { value: 'DEPARTMENT_UPDATED', label: 'Изменение отдела' },
  { value: 'DEPARTMENT_DELETED', label: 'Удаление отдела' },
  { value: 'DEPARTMENT_DECOMMISSIONED', label: 'Вывод отдела из использования' },
  { value: 'ROOM_CREATED', label: 'Создание кабинета' },
  { value: 'ROOM_UPDATED', label: 'Изменение кабинета' },
  { value: 'ROOM_DELETED', label: 'Удаление кабинета' },
  { value: 'ROOM_DECOMMISSIONED', label: 'Вывод кабинета из использования' },
  { value: 'PRINTER_CREATED', label: 'Создание принтера' },
  { value: 'PRINTER_UPDATED', label: 'Изменение принтера' },
  { value: 'PRINTER_WRITTEN_OFF', label: 'Списание принтера' },
  { value: 'THRESHOLD_CREATED', label: 'Создание порога' },
  { value: 'THRESHOLD_UPDATED', label: 'Изменение порога' },
  { value: 'THRESHOLD_DELETED', label: 'Удаление порога' },
  { value: 'INVENTORY_ASSET_CREATED', label: 'Создание актива' },
  { value: 'INVENTORY_ASSET_UPDATED', label: 'Изменение актива' },
  { value: 'INVENTORY_ASSET_DELETED', label: 'Удаление актива' },
  { value: 'INVENTORY_ASSET_TRANSFERRED', label: 'Перемещение актива' },
  { value: 'HALL_REQUEST_CREATED', label: 'Создание заявки по залу' },
  { value: 'HALL_REQUEST_UPDATED', label: 'Изменение заявки по залу' },
  { value: 'HALL_REQUEST_DELETED', label: 'Удаление заявки по залу' },
  { value: 'HALL_REQUEST_ESCALATED', label: 'SLA-эскалация заявки' },
  { value: 'CARTRIDGE_MODEL_CREATED', label: 'Создание модели' },
  { value: 'CARTRIDGE_MODEL_DELETED', label: 'Удаление модели' },
]

const ACTION_LOG_ENTITY_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Все сущности' },
  { value: 'AUTH', label: 'Авторизация' },
  { value: 'USER', label: 'Пользователь' },
  { value: 'DEPARTMENT', label: 'Отдел' },
  { value: 'ROOM', label: 'Кабинет' },
  { value: 'PRINTER', label: 'Принтер' },
  { value: 'CARTRIDGE', label: 'Картридж' },
  { value: 'CARTRIDGE_MODEL', label: 'Модель картриджа' },
  { value: 'NOTIFICATION_THRESHOLD', label: 'Порог уведомления' },
  { value: 'INVENTORY_ASSET', label: 'Инвентарный актив' },
  { value: 'HALL_REQUEST', label: 'Заявка по залу' },
]

const ACTION_LOG_RESULT_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Любой результат' },
  { value: 'SUCCESS', label: 'Успешно' },
  { value: 'FAILED', label: 'Ошибка' },
]

const USER_ROLE_OPTIONS: Array<{ value: UserRole; label: string }> = [
  { value: 'ADMIN', label: 'Администратор' },
  { value: 'OPERATOR', label: 'Оператор' },
  { value: 'VIEWER', label: 'Наблюдатель' },
]

const DEFAULT_PERMISSIONS_BY_ROLE: Record<UserRole, UserPermissions> = {
  ADMIN: {
    canViewCatalog: true,
    canEditCatalog: true,
    canOperate: true,
    canViewLogs: true,
    canExportReports: true,
    canManageUsers: true,
    canManageThresholds: true,
    canManualDatetime: true,
  },
  OPERATOR: {
    canViewCatalog: true,
    canEditCatalog: true,
    canOperate: true,
    canViewLogs: true,
    canExportReports: false,
    canManageUsers: false,
    canManageThresholds: false,
    canManualDatetime: false,
  },
  VIEWER: {
    canViewCatalog: true,
    canEditCatalog: false,
    canOperate: false,
    canViewLogs: false,
    canExportReports: false,
    canManageUsers: false,
    canManageThresholds: false,
    canManualDatetime: false,
  },
}

const INVENTORY_STATUS_OPTIONS: Array<{ value: InventoryAssetStatus; label: string }> = [
  { value: 'IN_USE', label: 'В использовании' },
  { value: 'IN_STOCK', label: 'На складе' },
  { value: 'IN_REPAIR', label: 'В ремонте' },
  { value: 'WRITTEN_OFF', label: 'Списан' },
]

const HALL_REQUEST_PRIORITY_OPTIONS: Array<{ value: HallRequestPriority; label: string }> = [
  { value: 'LOW', label: 'Низкий' },
  { value: 'MEDIUM', label: 'Средний' },
  { value: 'HIGH', label: 'Высокий' },
  { value: 'URGENT', label: 'Срочный' },
]

const HALL_REQUEST_STATUS_OPTIONS: Array<{ value: HallRequestStatus; label: string }> = [
  { value: 'OPEN', label: 'Открыта' },
  { value: 'IN_PROGRESS', label: 'В работе' },
  { value: 'DONE', label: 'Выполнена' },
  { value: 'CANCELLED', label: 'Отменена' },
]

function getStockStateMeta(cartridge: Cartridge): { label: string; tone: string } {
  if (cartridge.status === 'RESERVE') {
    return { label: 'Резерв', tone: 'status-reserve' }
  }
  if (cartridge.status === 'ON_REFILL') {
    return { label: 'На заправке', tone: 'status-on_refill' }
  }
  if (cartridge.status === 'WRITTEN_OFF') {
    return { label: 'Списан', tone: 'status-written_off' }
  }
  if (cartridge.refillable === false) {
    return { label: 'Одноразовый', tone: 'status-disposable' }
  }
  if (cartridge.empty === true) {
    return { label: 'Пустой', tone: 'status-empty' }
  }
  return { label: 'Готов', tone: 'status-ready' }
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

function sixMonthsAgo(): string {
  const value = new Date()
  value.setMonth(value.getMonth() - 6)
  return value.toISOString().slice(0, 10)
}

function clampOperationQuantity(value: number, max: number): number {
  if (!Number.isFinite(value) || value < 1) return 1
  return Math.min(value, Math.max(1, max))
}

function paginateItems<T>(items: T[], page: number, size = PAGE_SIZE): T[] {
  const from = (page - 1) * size
  return items.slice(from, from + size)
}

function parseCompatiblePrinterModels(value: string): string[] {
  return value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
    .filter((item, index, list) => list.indexOf(item) === index)
}

function formatCompatiblePrinterModels(values?: string[] | null): string {
  return (values ?? []).join('\n')
}

function isCartridgeModelCompatibleWithPrinter(cartridgeModel: CartridgeModel, printerModel: string): boolean {
  const normalizedPrinterModel = printerModel.trim().toLowerCase()
  if (!normalizedPrinterModel) return true

  const compatibleModels = (cartridgeModel.compatiblePrinterModels ?? [])
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean)

  if (compatibleModels.length === 0) return true
  return compatibleModels.includes(normalizedPrinterModel)
}

function formatActionType(actionType: string): string {
  const labels: Record<string, string> = {
    CARTRIDGE_CREATED: 'Приход',
    CARTRIDGE_QUANTITY_CHANGED: 'Изменение остатка',
    CARTRIDGE_INSTALLED: 'Установка',
    CARTRIDGE_REMOVED: 'Снятие',
    CARTRIDGE_SENT_TO_REFILL: 'Отправка на заправку',
    CARTRIDGE_RETURNED_FROM_REFILL: 'Возврат с заправки',
    CARTRIDGE_WRITTEN_OFF: 'Списание',
    CARTRIDGE_MARKED_EMPTY: 'Пометка пустым',
    CARTRIDGE_REFILLABLE_CHANGED: 'Изменение типа',
    CARTRIDGE_DELETED: 'Удаление остатка',
    USER_LOGIN: 'Вход в систему',
    USER_CREATED: 'Создание пользователя',
    USER_UPDATED: 'Изменение пользователя',
    DEPARTMENT_CREATED: 'Создание отдела',
    DEPARTMENT_UPDATED: 'Изменение отдела',
    DEPARTMENT_DELETED: 'Удаление отдела',
    DEPARTMENT_DECOMMISSIONED: 'Вывод отдела из использования',
    ROOM_CREATED: 'Создание кабинета',
    ROOM_UPDATED: 'Изменение кабинета',
    ROOM_DELETED: 'Удаление кабинета',
    ROOM_DECOMMISSIONED: 'Вывод кабинета из использования',
    PRINTER_CREATED: 'Создание принтера',
    PRINTER_UPDATED: 'Изменение принтера',
    PRINTER_WRITTEN_OFF: 'Списание принтера',
    THRESHOLD_CREATED: 'Создание порога',
    THRESHOLD_UPDATED: 'Изменение порога',
    THRESHOLD_DELETED: 'Удаление порога',
    INVENTORY_ASSET_CREATED: 'Создание актива',
    INVENTORY_ASSET_UPDATED: 'Изменение актива',
    INVENTORY_ASSET_DELETED: 'Удаление актива',
    INVENTORY_ASSET_TRANSFERRED: 'Перемещение актива',
    HALL_REQUEST_CREATED: 'Создание заявки по залу',
    HALL_REQUEST_UPDATED: 'Изменение заявки по залу',
    HALL_REQUEST_DELETED: 'Удаление заявки по залу',
    HALL_REQUEST_ESCALATED: 'SLA-эскалация заявки',
    CARTRIDGE_MODEL_CREATED: 'Создание модели',
    CARTRIDGE_MODEL_DELETED: 'Удаление модели',
  }
  return labels[actionType] || actionType
}

function formatActionEntityType(entityType: string): string {
  const labels: Record<string, string> = {
    AUTH: 'Авторизация',
    USER: 'Пользователь',
    DEPARTMENT: 'Отдел',
    ROOM: 'Кабинет',
    PRINTER: 'Принтер',
    CARTRIDGE: 'Картридж',
    CARTRIDGE_MODEL: 'Модель картриджа',
    NOTIFICATION_THRESHOLD: 'Порог уведомления',
    INVENTORY_ASSET: 'Инвентарный актив',
    HALL_REQUEST: 'Заявка по залу',
    SYSTEM: 'Система',
  }
  return labels[entityType] || entityType
}

function formatActionResult(result: string): string {
  if (result === 'SUCCESS') return 'Успешно'
  if (result === 'FAILED') return 'Ошибка'
  return result
}

function formatPrinterDeviceType(value: PrinterDeviceType): string {
  return value === 'MFP' ? 'МФУ' : 'Принтер'
}

function formatPrinterColorMode(value: PrinterColorMode): string {
  return value === 'COLOR' ? 'Цветной' : 'Ч/Б'
}

function formatPrinterStatus(value: PrinterStatus): string {
  if (value === 'IN_STOCK') return 'На складе'
  if (value === 'IN_REPAIR') return 'В ремонте'
  if (value === 'WRITTEN_OFF') return 'Списан'
  return 'В эксплуатации'
}

function formatDepartmentStatus(value: DepartmentStatus): string {
  return value === 'DECOMMISSIONED' ? 'Демонтирован / не используется' : 'Действует'
}

function formatRoomStatus(value: RoomStatus): string {
  return value === 'DECOMMISSIONED' ? 'Демонтирован / не используется' : 'Действует'
}

function getStructureStatusTone(value: DepartmentStatus | RoomStatus): string {
  return value === 'DECOMMISSIONED' ? 'status-decommissioned' : 'status-active'
}

function formatDate(value?: string | null): string {
  if (!value) return '-'
  return value.split('-').reverse().join('.')
}

function formatDateTime(value?: string | null): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function formatLocation(departmentName?: string | null, roomName?: string | null): string {
  const department = departmentName || 'Без отдела'
  const room = roomName || 'без кабинета'
  return `${department} / ${room}`
}

function balanceTone(value: number): string {
  if (value < 0) return 'metric-negative'
  if (value === 0) return 'metric-warning'
  return 'metric-positive'
}

function canDeleteCartridge(cartridge: Cartridge): boolean {
  return cartridge.status !== 'ON_REFILL' && (cartridge.installedQuantity ?? 0) === 0
}

function getDeleteCartridgeHint(cartridge: Cartridge): string {
  if (cartridge.status === 'ON_REFILL') {
    return 'Нельзя удалить остаток, пока он на заправке.'
  }
  if ((cartridge.installedQuantity ?? 0) > 0) {
    return 'Нельзя удалить остаток, пока часть количества установлена.'
  }
  return 'Удалить остаток'
}

function getDeleteDialogMeta(target: DeleteTarget): {
  title: string
  subject: string
  confirmLabel: string
  description: string
} {
  if (target.kind === 'printer') {
    return {
      title: 'Списание принтера',
      subject: `Списать: ${target.label}`,
      confirmLabel: 'Списать',
      description: 'Принтер останется в системе и перейдёт в архивный статус.',
    }
  }
  if (target.kind === 'department') {
    return {
      title: 'Вывод отдела из использования',
      subject: `Вывести из использования: ${target.label}`,
      confirmLabel: 'Вывести из использования',
      description: 'Отдел не будет удалён физически и перейдёт в статус «Демонтирован / не используется».',
    }
  }
  if (target.kind === 'room') {
    return {
      title: 'Вывод кабинета из использования',
      subject: `Вывести из использования: ${target.label}`,
      confirmLabel: 'Вывести из использования',
      description: 'Кабинет останется в системе и перейдёт в статус «Демонтирован / не используется».',
    }
  }
  return {
    title: 'Удаление',
    subject: `Удалить: ${target.label}`,
    confirmLabel: 'Удалить',
    description: 'Это действие нельзя отменить.',
  }
}

export default function App() {
  const [departments, setDepartments] = useState<Department[]>([])
  const [rooms, setRooms] = useState<Room[]>([])
  const [printers, setPrinters] = useState<Printer[]>([])
  const [models, setModels] = useState<CartridgeModel[]>([])
  const [cartridges, setCartridges] = useState<Cartridge[]>([])
  const [actionLogs, setActionLogs] = useState<ActionLogRecord[]>([])
  const [users, setUsers] = useState<UserAdminRecord[]>([])
  const [systemModules, setSystemModules] = useState<SystemModule[]>([])
  const [notificationAlerts, setNotificationAlerts] = useState<NotificationAlert[]>([])
  const [notificationThresholds, setNotificationThresholds] = useState<NotificationThreshold[]>([])
  const [consumptionReport, setConsumptionReport] = useState<ConsumptionReport | null>(null)
  const [stockSnapshotReport, setStockSnapshotReport] = useState<StockSnapshotReport | null>(null)
  const [inventoryAssets, setInventoryAssets] = useState<InventoryAsset[]>([])
  const [inventoryMovements, setInventoryMovements] = useState<InventoryAssetMovement[]>([])
  const [hallRequests, setHallRequests] = useState<HallRequest[]>([])

  const [departmentFilter, setDepartmentFilter] = useState<number | 'all'>('all')
  const [statusFilter, setStatusFilter] = useState<CartridgeStatus | 'all'>('all')
  const [searchTerm, setSearchTerm] = useState('')
  const [activeTab, setActiveTab] = useState<TabKey>('departments')
  const [sortKey, setSortKey] = useState<SortKey>('quantity')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [currentPage, setCurrentPage] = useState(1)
  const [departmentUsageSearch, setDepartmentUsageSearch] = useState('')
  const [departmentUsageDepartmentFilter, setDepartmentUsageDepartmentFilter] = useState<number | 'all'>('all')
  const [departmentUsagePage, setDepartmentUsagePage] = useState(1)
  const [departmentListSearch, setDepartmentListSearch] = useState('')
  const [departmentListStatusFilter, setDepartmentListStatusFilter] = useState<DepartmentStatus | 'all'>('all')
  const [departmentListPage, setDepartmentListPage] = useState(1)
  const [roomListSearch, setRoomListSearch] = useState('')
  const [roomListDepartmentFilter, setRoomListDepartmentFilter] = useState<number | 'all'>('all')
  const [roomListStatusFilter, setRoomListStatusFilter] = useState<RoomStatus | 'all'>('all')
  const [roomListPage, setRoomListPage] = useState(1)
  const [printerSearchTerm, setPrinterSearchTerm] = useState('')
  const [printerDepartmentFilter, setPrinterDepartmentFilter] = useState<number | 'all'>('all')
  const [printerStatusFilter, setPrinterStatusFilter] = useState<PrinterStatus | 'all'>('all')
  const [printerPage, setPrinterPage] = useState(1)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailAction, setDetailAction] = useState<DetailAction>(null)
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null)
  const [writeOffRequest, setWriteOffRequest] = useState<WriteOffRequest | null>(null)

  const [selectedCartridgeId, setSelectedCartridgeId] = useState<number | ''>('')
  const [quantity, setQuantity] = useState<number>(0)
  const [comment, setComment] = useState('')
  const [actor, setActor] = useState('Администратор')
  const [dateValue, setDateValue] = useState(today())
  const [detailQuantity, setDetailQuantity] = useState<number>(0)
  const [detailComment, setDetailComment] = useState('')
  const [detailActor, setDetailActor] = useState('Администратор')
  const [detailDateValue, setDetailDateValue] = useState(today())

  const [departmentName, setDepartmentName] = useState('')
  const [departmentDescription, setDepartmentDescription] = useState('')
  const [departmentStatus, setDepartmentStatus] = useState<DepartmentStatus>('ACTIVE')
  const [editingDepartmentId, setEditingDepartmentId] = useState<number | null>(null)
  const [roomName, setRoomName] = useState('')
  const [roomDepartmentId, setRoomDepartmentId] = useState<number | ''>('')
  const [roomStatus, setRoomStatus] = useState<RoomStatus>('ACTIVE')
  const [roomComment, setRoomComment] = useState('')
  const [editingRoomId, setEditingRoomId] = useState<number | null>(null)
  const [printerName, setPrinterName] = useState('')
  const [printerModel, setPrinterModel] = useState('')
  const [printerIpAddress, setPrinterIpAddress] = useState('')
  const [printerSerialNumber, setPrinterSerialNumber] = useState('')
  const [printerDepartmentId, setPrinterDepartmentId] = useState<number | ''>('')
  const [printerRoomId, setPrinterRoomId] = useState<number | ''>('')
  const [printerDeviceType, setPrinterDeviceType] = useState<PrinterDeviceType>('PRINTER')
  const [printerColorMode, setPrinterColorMode] = useState<PrinterColorMode>('MONOCHROME')
  const [printerStatus, setPrinterStatus] = useState<PrinterStatus>('IN_OPERATION')
  const [printerCommissionedAt, setPrinterCommissionedAt] = useState('')
  const [printerWrittenOffAt, setPrinterWrittenOffAt] = useState('')
  const [printerComment, setPrinterComment] = useState('')
  const [printerSlots, setPrinterSlots] = useState<PrinterSlotForm[]>([{ name: 'Основной', cartridgeModelId: '' }])
  const [editingPrinterId, setEditingPrinterId] = useState<number | null>(null)
  const [thresholdModelId, setThresholdModelId] = useState<number | ''>('')
  const [thresholdDepartmentId, setThresholdDepartmentId] = useState<number | ''>('')
  const [thresholdMinimum, setThresholdMinimum] = useState(0)
  const [thresholdActive, setThresholdActive] = useState(true)
  const [thresholdComment, setThresholdComment] = useState('')
  const [editingThresholdId, setEditingThresholdId] = useState<number | null>(null)
  const [reportDateFrom, setReportDateFrom] = useState(sixMonthsAgo())
  const [reportDateTo, setReportDateTo] = useState(today())
  const [historyDateFrom, setHistoryDateFrom] = useState(sixMonthsAgo())
  const [historyDateTo, setHistoryDateTo] = useState(today())
  const [historyActor, setHistoryActor] = useState('')
  const [historyActionType, setHistoryActionType] = useState('')
  const [historyEntityType, setHistoryEntityType] = useState('')
  const [historyResult, setHistoryResult] = useState('')
  const [historyTargetName, setHistoryTargetName] = useState('')
  const [historySearchTerm, setHistorySearchTerm] = useState('')
  const [historyPage, setHistoryPage] = useState(1)
  const [notificationAlertSearch, setNotificationAlertSearch] = useState('')
  const [notificationAlertDepartmentFilter, setNotificationAlertDepartmentFilter] = useState<number | 'all'>('all')
  const [notificationAlertSourceFilter, setNotificationAlertSourceFilter] = useState<string>('all')
  const [notificationAlertPage, setNotificationAlertPage] = useState(1)
  const [thresholdSearchTerm, setThresholdSearchTerm] = useState('')
  const [thresholdDepartmentFilter, setThresholdDepartmentFilter] = useState<number | 'all'>('all')
  const [thresholdActiveFilter, setThresholdActiveFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [thresholdPage, setThresholdPage] = useState(1)
  const [reportConsumptionSearchTerm, setReportConsumptionSearchTerm] = useState('')
  const [reportConsumptionPage, setReportConsumptionPage] = useState(1)
  const [reportSummaryView, setReportSummaryView] = useState<ReportSummaryView>('department')
  const [reportSummarySearchTerm, setReportSummarySearchTerm] = useState('')
  const [reportSummaryPage, setReportSummaryPage] = useState(1)
  const [reportItemsView, setReportItemsView] = useState<ReportItemsView>('stock')
  const [reportItemsSearchTerm, setReportItemsSearchTerm] = useState('')
  const [reportItemsPage, setReportItemsPage] = useState(1)
  const [userSearchTerm, setUserSearchTerm] = useState('')
  const [userRoleFilter, setUserRoleFilter] = useState<UserRole | 'all'>('all')
  const [userActiveFilter, setUserActiveFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [userPage, setUserPage] = useState(1)
  const [modelCatalogSearchTerm, setModelCatalogSearchTerm] = useState('')
  const [modelCatalogTypeFilter, setModelCatalogTypeFilter] = useState<ModelCatalogTypeFilter>('all')
  const [modelCatalogBalanceFilter, setModelCatalogBalanceFilter] = useState<ModelCatalogBalanceFilter>('all')
  const [modelCatalogPage, setModelCatalogPage] = useState(1)
  const [assetFilterDepartmentId, setAssetFilterDepartmentId] = useState<number | ''>('')
  const [assetFilterRoomId, setAssetFilterRoomId] = useState<number | ''>('')
  const [assetFilterStatus, setAssetFilterStatus] = useState<InventoryAssetStatus | ''>('')
  const [assetMovementFilterAssetId, setAssetMovementFilterAssetId] = useState<number | ''>('')
  const [assetSearchTerm, setAssetSearchTerm] = useState('')
  const [assetPage, setAssetPage] = useState(1)
  const [movementSearchTerm, setMovementSearchTerm] = useState('')
  const [movementPage, setMovementPage] = useState(1)
  const [hallFilterRoomId, setHallFilterRoomId] = useState<number | ''>('')
  const [hallFilterStatus, setHallFilterStatus] = useState<HallRequestStatus | ''>('')
  const [hallOverdueOnly, setHallOverdueOnly] = useState(false)
  const [hallSearchTerm, setHallSearchTerm] = useState('')
  const [hallPriorityFilter, setHallPriorityFilter] = useState<HallRequestPriority | ''>('')
  const [hallPage, setHallPage] = useState(1)
  const [assetInventoryCode, setAssetInventoryCode] = useState('')
  const [assetName, setAssetName] = useState('')
  const [assetCategory, setAssetCategory] = useState('')
  const [assetDepartmentId, setAssetDepartmentId] = useState<number | ''>('')
  const [assetRoomId, setAssetRoomId] = useState<number | ''>('')
  const [assetStatus, setAssetStatus] = useState<InventoryAssetStatus>('IN_USE')
  const [assetQuantity, setAssetQuantity] = useState(1)
  const [assetComment, setAssetComment] = useState('')
  const [editingAssetId, setEditingAssetId] = useState<number | null>(null)
  const [transferAssetId, setTransferAssetId] = useState<number | ''>('')
  const [transferDepartmentId, setTransferDepartmentId] = useState<number | ''>('')
  const [transferRoomId, setTransferRoomId] = useState<number | ''>('')
  const [transferActor, setTransferActor] = useState('Администратор')
  const [transferComment, setTransferComment] = useState('')
  const [transferMovedAt, setTransferMovedAt] = useState('')
  const [hallRoomId, setHallRoomId] = useState<number | ''>('')
  const [hallRequesterName, setHallRequesterName] = useState('')
  const [hallTitle, setHallTitle] = useState('')
  const [hallDescription, setHallDescription] = useState('')
  const [hallPriority, setHallPriority] = useState<HallRequestPriority>('MEDIUM')
  const [hallStatus, setHallStatus] = useState<HallRequestStatus>('OPEN')
  const [hallPlannedAt, setHallPlannedAt] = useState('')
  const [editingHallRequestId, setEditingHallRequestId] = useState<number | null>(null)
  const [modelName, setModelName] = useState('')
  const [modelRefillable, setModelRefillable] = useState(true)
  const [modelMinimumQuantity, setModelMinimumQuantity] = useState(0)
  const [modelCompatiblePrintersText, setModelCompatiblePrintersText] = useState('')
  const [modelCompatibilityDrafts, setModelCompatibilityDrafts] = useState<Record<number, string>>({})
  const [selectedBatchModelIds, setSelectedBatchModelIds] = useState<number[]>([])
  const [batchEntries, setBatchEntries] = useState<Record<number, BatchEntry>>({})
  const [activeBatchIndex, setActiveBatchIndex] = useState(0)
  const [batchTargetStatus, setBatchTargetStatus] = useState<CartridgeStatus>('IN_STOCK')
  const [installPrinterId, setInstallPrinterId] = useState<number | ''>('')
  const [preferredPrinterId, setPreferredPrinterId] = useState<number | ''>('')
  const [replaceOutcome, setReplaceOutcome] = useState<RemovalOutcome>('STOCK')
  const [removePrinterId, setRemovePrinterId] = useState<number | ''>('')
  const [removeQuantity, setRemoveQuantity] = useState(1)
  const [removeOutcome, setRemoveOutcome] = useState<RemovalOutcome>('STOCK')

  const [loading, setLoading] = useState(false)
  const [toasts, setToasts] = useState<Toast[]>([])

  const [authLogin, setAuthLogin] = useState('')
  const [authPassword, setAuthPassword] = useState('')
  const [authError, setAuthError] = useState('')
  const [isAuthed, setIsAuthed] = useState(false)
  const [sessionUser, setSessionUser] = useState('')
  const [sessionExpiresAt, setSessionExpiresAt] = useState<number | null>(null)
  const [authUser, setAuthUser] = useState<AuthUser | null>(null)
  const sessionRefreshPromiseRef = useRef<Promise<void> | null>(null)
  const lastActivityAtRef = useRef(Date.now())
  const lastPersistedActivityAtRef = useRef(0)
  const canOperate = authUser?.permissions.canOperate === true
  const canViewLogs = authUser?.permissions.canViewLogs === true
  const canExportReports = authUser?.permissions.canExportReports === true
  const canManageUsers = authUser?.permissions.canManageUsers === true
  const canManageThresholds = authUser?.permissions.canManageThresholds === true
  const [editingUserId, setEditingUserId] = useState<number | null>(null)
  const [userLogin, setUserLogin] = useState('')
  const [userFullName, setUserFullName] = useState('')
  const [userPassword, setUserPassword] = useState('')
  const [userRole, setUserRole] = useState<UserRole>('OPERATOR')
  const [userActive, setUserActive] = useState(true)
  const [userPermissions, setUserPermissions] = useState<UserPermissions>({ ...DEFAULT_PERMISSIONS_BY_ROLE.OPERATOR })

  const clearStoredSession = useCallback(() => {
    localStorage.removeItem(AUTH_STORAGE_KEY)
    setAuthToken(null)
    setIsAuthed(false)
    setSessionUser('')
    setSessionExpiresAt(null)
    setAuthUser(null)
  }, [])

  const persistSession = useCallback((token: string, user: AuthUser, expiresAt: number) => {
    const now = Date.now()
    lastActivityAtRef.current = now
    lastPersistedActivityAtRef.current = now
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token, user, expiresAt, lastActivityAt: now }))
    setAuthToken(token)
    setIsAuthed(true)
    setSessionUser(user.fullName)
    setSessionExpiresAt(expiresAt)
    setAuthUser(user)
  }, [])

  const pushToast = useCallback((kind: ToastKind, text: string) => {
    const id = Date.now() + Math.floor(Math.random() * 1000)
    setToasts((current) => [...current, { id, kind, text }])
    window.setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id))
    }, 4000)
  }, [])

  const handleUnauthorized = useCallback(() => {
    clearStoredSession()
    setAuthPassword('')
    setActiveTab('departments')
    pushToast('error', 'Авторизация истекла или была отозвана. Войдите снова.')
  }, [clearStoredSession, pushToast])

  const selectedCartridge = useMemo(
    () => cartridges.find((item) => item.id === selectedCartridgeId),
    [cartridges, selectedCartridgeId],
  )

  const visibleCartridges = useMemo(() => {
    const needle = searchTerm.trim().toLowerCase()
    if (!needle) return cartridges
    return cartridges.filter(
      (item) =>
        item.cartridgeModelName.toLowerCase().includes(needle) ||
        item.departmentName.toLowerCase().includes(needle),
    )
  }, [cartridges, searchTerm])

  const stockTableCartridges = useMemo(() => {
    const collapsedWrittenOff = new Map<string, Cartridge>()

    for (const item of visibleCartridges) {
      if (item.status !== 'WRITTEN_OFF') {
        collapsedWrittenOff.set(`row-${item.id}`, item)
        continue
      }

      const key = [
        item.cartridgeModelId,
        item.departmentId,
        item.refillable === false ? 'DISPOSABLE' : 'REFILLABLE',
        item.status,
      ].join(':')

      const existing = collapsedWrittenOff.get(key)
      if (!existing || item.id > existing.id) {
        collapsedWrittenOff.set(key, item)
      }
    }

    return Array.from(collapsedWrittenOff.values())
  }, [visibleCartridges])

  const sortedCartridges = useMemo(() => {
    const list = [...stockTableCartridges]
    list.sort((a, b) => {
      const va = a[sortKey]
      const vb = b[sortKey]
      if (typeof va === 'number' && typeof vb === 'number') {
        return sortDir === 'asc' ? va - vb : vb - va
      }
      const sa = String(va).toLowerCase()
      const sb = String(vb).toLowerCase()
      return sortDir === 'asc' ? sa.localeCompare(sb) : sb.localeCompare(sa)
    })
    return list
  }, [sortDir, sortKey, stockTableCartridges])

  const totalPages = Math.max(1, Math.ceil(sortedCartridges.length / PAGE_SIZE))
  const paginatedCartridges = useMemo(() => {
    const from = (currentPage - 1) * PAGE_SIZE
    return sortedCartridges.slice(from, from + PAGE_SIZE)
  }, [currentPage, sortedCartridges])

  const dashboardStats = useMemo(() => {
    const stock = stockSnapshotReport
    const printersInOperation = printers.filter((item) => item.status === 'IN_OPERATION').length
    const printersInStock = printers.filter((item) => item.status === 'IN_STOCK').length
    const printersInRepair = printers.filter((item) => item.status === 'IN_REPAIR').length
    const printersWrittenOff = printers.filter((item) => item.status === 'WRITTEN_OFF').length

    return {
      totalPrinters: printers.length,
      printersInOperation,
      printersInStock,
      printersInRepair,
      printersWrittenOff,
      totalCartridgeUnits:
        (stock?.totalInStock ?? 0) +
        (stock?.totalReserve ?? 0) +
        (stock?.totalOnRefill ?? 0) +
        (stock?.totalInstalled ?? 0) +
        (stock?.totalWrittenOff ?? 0),
      cartridgesInStock: stock?.totalInStock ?? 0,
      cartridgesReserve: stock?.totalReserve ?? 0,
      cartridgesOnRefill: stock?.totalOnRefill ?? 0,
      cartridgesInstalled: stock?.totalInstalled ?? 0,
      cartridgesWrittenOff: stock?.totalWrittenOff ?? 0,
      alertCount: notificationAlerts.length,
    }
  }, [notificationAlerts.length, printers, stockSnapshotReport])

  const userDepartments = useMemo(
    () => departments.filter((department) => department.name !== STOCK_DEPARTMENT_NAME),
    [departments],
  )

  const activeUserDepartments = useMemo(
    () => userDepartments.filter((department) => department.status === 'ACTIVE'),
    [userDepartments],
  )

  const availableDepartmentsForRoomForm = useMemo(() => {
    const selectedDepartment = userDepartments.find((department) => department.id === roomDepartmentId)
    if (!selectedDepartment || selectedDepartment.status === 'ACTIVE') {
      return activeUserDepartments
    }
    return [...activeUserDepartments, selectedDepartment]
  }, [activeUserDepartments, roomDepartmentId, userDepartments])

  const activeRooms = useMemo(
    () => rooms.filter((room) => room.status === 'ACTIVE'),
    [rooms],
  )

  const availableRoomsForPrinter = useMemo(
    () => activeRooms.filter((room) => printerDepartmentId && room.departmentId === printerDepartmentId),
    [activeRooms, printerDepartmentId],
  )

  const printerCompatibleModels = useMemo(
    () => models.filter((model) => isCartridgeModelCompatibleWithPrinter(model, printerModel)),
    [models, printerModel],
  )

  const printerStrictCompatibleModels = useMemo(
    () =>
      models.filter(
        (model) =>
          (model.compatiblePrinterModels?.length ?? 0) > 0 &&
          isCartridgeModelCompatibleWithPrinter(model, printerModel),
      ),
    [models, printerModel],
  )

  const hasExplicitPrinterCompatibility = useMemo(
    () => models.some((model) => (model.compatiblePrinterModels?.length ?? 0) > 0),
    [models],
  )

  const availableRoomsForAsset = useMemo(
    () => activeRooms.filter((room) => assetDepartmentId && room.departmentId === assetDepartmentId),
    [activeRooms, assetDepartmentId],
  )

  const availableRoomsForTransfer = useMemo(
    () => activeRooms.filter((room) => transferDepartmentId && room.departmentId === transferDepartmentId),
    [activeRooms, transferDepartmentId],
  )

  const departmentStats = useMemo(() => {
    return userDepartments
      .map((department) => {
        const ownCartridges = cartridges.filter((item) => item.departmentId === department.id)
        return {
          ...department,
          cartridgeCount: ownCartridges.length,
          totalQuantity: ownCartridges.reduce((sum, item) => sum + item.quantity, 0),
          onRefill: ownCartridges.filter((item) => item.status === 'ON_REFILL').length,
          printerCount: (department.printers ?? []).length,
        }
      })
      .sort((a, b) => b.totalQuantity - a.totalQuantity)
  }, [cartridges, userDepartments])

  const recentActivity = useMemo(() => actionLogs.slice(0, 6), [actionLogs])
  const overviewAlerts = useMemo(() => notificationAlerts.slice(0, 5), [notificationAlerts])
  const selectedCartridgeRefillable = selectedCartridge?.refillable !== false
  const selectedCartridgeEmpty = selectedCartridge?.empty === true
  const sessionRemainingMinutes = sessionExpiresAt
    ? Math.max(0, Math.ceil((sessionExpiresAt - Date.now()) / 60000))
    : 0

  const noteSessionActivity = useCallback(() => {
    const now = Date.now()
    lastActivityAtRef.current = now

    if (now - lastPersistedActivityAtRef.current < SESSION_ACTIVITY_THROTTLE_MS) {
      return
    }

    const rawSession = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!rawSession) return

    try {
      const parsed = JSON.parse(rawSession) as { token?: string; user?: AuthUser; expiresAt?: number }
      if (!parsed.token || !parsed.user || !parsed.expiresAt) return

      localStorage.setItem(
        AUTH_STORAGE_KEY,
        JSON.stringify({ ...parsed, lastActivityAt: now }),
      )
      lastPersistedActivityAtRef.current = now
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEY)
    }
  }, [])

  const refreshSessionIfNeeded = useCallback(async (force = false) => {
    if (!isAuthed || !sessionExpiresAt) return

    const now = Date.now()
    const recentlyActive = now - lastActivityAtRef.current <= SESSION_REFRESH_WINDOW_MS
    const shouldRefresh = force || (recentlyActive && sessionExpiresAt - now <= SESSION_REFRESH_WINDOW_MS)
    if (!shouldRefresh) return

    if (sessionRefreshPromiseRef.current) {
      await sessionRefreshPromiseRef.current
      return
    }

    const refreshPromise = (async () => {
      const response = await refreshAuthSession()
      persistSession(response.token, response.user, response.expiresAt)
      setActor(response.user.fullName)
      setDetailActor(response.user.fullName)
    })()

    sessionRefreshPromiseRef.current = refreshPromise
    try {
      await refreshPromise
    } catch (error) {
      clearStoredSession()
      pushToast('error', error instanceof Error ? error.message : 'Сессия завершена. Войдите снова.')
    } finally {
      sessionRefreshPromiseRef.current = null
    }
  }, [clearStoredSession, isAuthed, persistSession, pushToast, sessionExpiresAt])

  useEffect(() => {
    setUnauthorizedHandler(handleUnauthorized)
    return () => setUnauthorizedHandler(null)
  }, [handleUnauthorized])

  useEffect(() => {
    const rawSession = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!rawSession) return

    try {
      const parsed = JSON.parse(rawSession) as {
        token?: string
        user?: AuthUser
        expiresAt?: number
        lastActivityAt?: number
      }
      if (!parsed.token || !parsed.user || !parsed.expiresAt || parsed.expiresAt <= Date.now()) {
        localStorage.removeItem(AUTH_STORAGE_KEY)
        return
      }

      lastActivityAtRef.current = parsed.lastActivityAt ?? Date.now()
      lastPersistedActivityAtRef.current = parsed.lastActivityAt ?? 0
      setAuthToken(parsed.token)
      setIsAuthed(true)
      setSessionUser(parsed.user.fullName)
      setSessionExpiresAt(parsed.expiresAt)
      setActor(parsed.user.fullName)
      setDetailActor(parsed.user.fullName)
      setAuthUser(parsed.user)
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEY)
    }
  }, [])

  useEffect(() => {
    setDetailOpen(false)
    setDetailAction(null)
  }, [activeTab])

  const departmentUsageRows = useMemo(
    () =>
      userDepartments.flatMap((department) =>
        (department.printers ?? []).flatMap((printer) =>
          (printer.slots ?? []).map((slot) => ({
          id: `${department.id}-${printer.id ?? printer.name}-${slot.id ?? slot.name}`,
          printerId: printer.id ?? null,
          slotId: slot.id ?? null,
          departmentId: department.id,
          departmentName: department.name,
          roomName: printer.roomName ?? '-',
          printerName: printer.name,
          slotName: slot.name,
          cartridgeModelId: slot.cartridgeModelId ?? null,
          cartridgeModelName: slot.cartridgeModelName ?? 'Не назначен',
          previousReplacementDate: slot.previousReplacementDate ?? null,
          lastReplacementDate: slot.lastReplacementDate ?? null,
          currentInstallation: slot.currentInstallation ?? null,
        }))),
      ),
    [userDepartments],
  )

  const filteredDepartmentUsageRows = useMemo(() => {
    const needle = departmentUsageSearch.trim().toLowerCase()
    return departmentUsageRows.filter((row) => {
      if (departmentUsageDepartmentFilter !== 'all' && row.departmentId !== departmentUsageDepartmentFilter) {
        return false
      }
      if (!needle) return true
      return [
        row.departmentName,
        row.roomName,
        row.printerName,
        row.slotName,
        row.cartridgeModelName,
        row.currentInstallation?.cartridgeModelName ?? '',
        row.currentInstallation?.inventoryCode ?? '',
      ].some((value) => value.toLowerCase().includes(needle))
    })
  }, [departmentUsageDepartmentFilter, departmentUsageRows, departmentUsageSearch])

  const departmentUsageTotalPages = Math.max(1, Math.ceil(filteredDepartmentUsageRows.length / PAGE_SIZE))

  const paginatedDepartmentUsageRows = useMemo(() => {
    const from = (departmentUsagePage - 1) * PAGE_SIZE
    return filteredDepartmentUsageRows.slice(from, from + PAGE_SIZE)
  }, [departmentUsagePage, filteredDepartmentUsageRows])

  const selectedDepartmentSlots = useMemo(() => {
    if (!selectedCartridge) return []
    return departmentUsageRows.filter(
      (row) =>
        row.departmentId === selectedCartridge.departmentId &&
        row.cartridgeModelId === selectedCartridge.cartridgeModelId,
    )
  }, [departmentUsageRows, selectedCartridge])

  useEffect(() => {
    if (!selectedDepartmentSlots.some((slot) => slot.slotId === preferredPrinterId)) {
      setPreferredPrinterId('')
    }
  }, [preferredPrinterId, selectedDepartmentSlots])

  useEffect(() => {
    if (preferredPrinterId && selectedDepartmentSlots.some((slot) => slot.slotId === preferredPrinterId)) {
      setInstallPrinterId(preferredPrinterId)
    }
  }, [preferredPrinterId, selectedDepartmentSlots])

  const cartridgeDemandSummary = useMemo(() => {
    const stockByModel = cartridges.reduce<Record<string, number>>((acc, cartridge) => {
      if (cartridge.status !== 'IN_STOCK' && cartridge.status !== 'RESERVE') return acc
      acc[cartridge.cartridgeModelName] = (acc[cartridge.cartridgeModelName] ?? 0) + cartridge.quantity
      return acc
    }, {})

    const demandByModel = departmentUsageRows.reduce<Record<string, number>>((acc, row) => {
      if (row.cartridgeModelName === 'Не назначен') return acc
      acc[row.cartridgeModelName] = (acc[row.cartridgeModelName] ?? 0) + 1
      return acc
    }, {})

    return Object.keys({ ...stockByModel, ...demandByModel })
      .map((modelName) => {
        const stock = stockByModel[modelName] ?? 0
        const demand = demandByModel[modelName] ?? 0
        return {
          modelName,
          stock,
          demand,
          replacementBalance: stock - demand,
        }
      })
      .sort((a, b) => a.replacementBalance - b.replacementBalance || a.modelName.localeCompare(b.modelName))
  }, [cartridges, departmentUsageRows])

  const filteredDepartmentCards = useMemo(() => {
    const needle = departmentListSearch.trim().toLowerCase()
    return departmentStats.filter((department) => {
      if (departmentListStatusFilter !== 'all' && department.status !== departmentListStatusFilter) {
        return false
      }
      if (!needle) return true
      return [
        department.name,
        department.description ?? '',
        formatDepartmentStatus(department.status),
      ].some((value) => value.toLowerCase().includes(needle))
    })
  }, [departmentListSearch, departmentListStatusFilter, departmentStats])

  const departmentListTotalPages = Math.max(1, Math.ceil(filteredDepartmentCards.length / PAGE_SIZE))

  const paginatedDepartmentCards = useMemo(() => {
    const from = (departmentListPage - 1) * PAGE_SIZE
    return filteredDepartmentCards.slice(from, from + PAGE_SIZE)
  }, [departmentListPage, filteredDepartmentCards])

  const filteredRoomCards = useMemo(() => {
    const needle = roomListSearch.trim().toLowerCase()
    return rooms.filter((room) => {
      if (roomListDepartmentFilter !== 'all' && room.departmentId !== roomListDepartmentFilter) {
        return false
      }
      if (roomListStatusFilter !== 'all' && room.status !== roomListStatusFilter) {
        return false
      }
      if (!needle) return true
      return [
        room.name,
        room.departmentName,
        room.comment ?? '',
        formatRoomStatus(room.status),
      ].some((value) => value.toLowerCase().includes(needle))
    })
  }, [roomListDepartmentFilter, roomListSearch, roomListStatusFilter, rooms])

  const roomListTotalPages = Math.max(1, Math.ceil(filteredRoomCards.length / PAGE_SIZE))

  const paginatedRoomCards = useMemo(() => {
    const from = (roomListPage - 1) * PAGE_SIZE
    return filteredRoomCards.slice(from, from + PAGE_SIZE)
  }, [roomListPage, filteredRoomCards])

  const filteredPrinters = useMemo(() => {
    const needle = printerSearchTerm.trim().toLowerCase()
    return printers.filter((printer) => {
      if (printerDepartmentFilter !== 'all' && printer.departmentId !== printerDepartmentFilter) {
        return false
      }
      if (printerStatusFilter !== 'all' && printer.status !== printerStatusFilter) {
        return false
      }
      if (!needle) return true
      const slotSummary = printer.slots.map((slot) => `${slot.name} ${slot.cartridgeModelName ?? ''}`).join(' ')
      return [
        printer.name,
        printer.model ?? '',
        printer.ipAddress ?? '',
        printer.serialNumber ?? '',
        printer.departmentName ?? '',
        printer.roomName ?? '',
        printer.comment ?? '',
        formatPrinterDeviceType(printer.deviceType),
        formatPrinterColorMode(printer.colorMode),
        formatPrinterStatus(printer.status),
        slotSummary,
      ].some((value) => value.toLowerCase().includes(needle))
    })
  }, [printerDepartmentFilter, printerSearchTerm, printerStatusFilter, printers])

  const printerTotalPages = Math.max(1, Math.ceil(filteredPrinters.length / PAGE_SIZE))

  const paginatedPrinters = useMemo(() => {
    const from = (printerPage - 1) * PAGE_SIZE
    return filteredPrinters.slice(from, from + PAGE_SIZE)
  }, [filteredPrinters, printerPage])

  const filteredActionLogs = useMemo(() => {
    const needle = historySearchTerm.trim().toLowerCase()
    if (!needle) return actionLogs
    return actionLogs.filter((entry) =>
      [
        entry.targetName ?? '',
        entry.details ?? '',
        entry.actor ?? '',
        entry.deviceInfo ?? '',
        entry.oldValues ?? '',
        entry.newValues ?? '',
        formatActionEntityType(entry.entityType),
        formatActionType(entry.actionType),
        formatActionResult(entry.result),
      ].some((value) => value.toLowerCase().includes(needle)),
    )
  }, [actionLogs, historySearchTerm])

  const historyTotalPages = Math.max(1, Math.ceil(filteredActionLogs.length / PAGE_SIZE))

  const paginatedActionLogs = useMemo(() => {
    const from = (historyPage - 1) * PAGE_SIZE
    return filteredActionLogs.slice(from, from + PAGE_SIZE)
  }, [filteredActionLogs, historyPage])

  const filteredNotificationAlerts = useMemo(() => {
    const needle = notificationAlertSearch.trim().toLowerCase()
    return notificationAlerts.filter((alert) => {
      if (notificationAlertDepartmentFilter !== 'all' && alert.departmentId !== notificationAlertDepartmentFilter) {
        return false
      }
      if (notificationAlertSourceFilter !== 'all' && alert.source !== notificationAlertSourceFilter) {
        return false
      }
      if (!needle) return true
      return [
        alert.departmentName,
        alert.cartridgeModelName,
        alert.source,
      ].some((value) => value.toLowerCase().includes(needle))
    })
  }, [notificationAlertDepartmentFilter, notificationAlertSearch, notificationAlertSourceFilter, notificationAlerts])

  const notificationAlertTotalPages = Math.max(1, Math.ceil(filteredNotificationAlerts.length / PAGE_SIZE))

  const paginatedNotificationAlerts = useMemo(() => {
    const from = (notificationAlertPage - 1) * PAGE_SIZE
    return filteredNotificationAlerts.slice(from, from + PAGE_SIZE)
  }, [filteredNotificationAlerts, notificationAlertPage])

  const filteredThresholds = useMemo(() => {
    const needle = thresholdSearchTerm.trim().toLowerCase()
    return notificationThresholds.filter((item) => {
      if (thresholdDepartmentFilter !== 'all' && item.departmentId !== thresholdDepartmentFilter) {
        return false
      }
      if (thresholdActiveFilter === 'active' && !item.active) {
        return false
      }
      if (thresholdActiveFilter === 'inactive' && item.active) {
        return false
      }
      if (!needle) return true
      return [
        item.cartridgeModelName,
        item.departmentName ?? 'Все отделы',
        item.comment ?? '',
      ].some((value) => value.toLowerCase().includes(needle))
    })
  }, [notificationThresholds, thresholdActiveFilter, thresholdDepartmentFilter, thresholdSearchTerm])

  const thresholdTotalPages = Math.max(1, Math.ceil(filteredThresholds.length / PAGE_SIZE))

  const paginatedThresholds = useMemo(() => {
    const from = (thresholdPage - 1) * PAGE_SIZE
    return filteredThresholds.slice(from, from + PAGE_SIZE)
  }, [filteredThresholds, thresholdPage])

  const filteredConsumptionRows = useMemo(() => {
    const rows = consumptionReport?.rows ?? []
    const needle = reportConsumptionSearchTerm.trim().toLowerCase()
    if (!needle) return rows
    return rows.filter((row) => row.modelName.toLowerCase().includes(needle))
  }, [consumptionReport, reportConsumptionSearchTerm])

  const reportConsumptionTotalPages = Math.max(1, Math.ceil(filteredConsumptionRows.length / PAGE_SIZE))

  const paginatedConsumptionRows = useMemo(
    () => paginateItems(filteredConsumptionRows, reportConsumptionPage),
    [filteredConsumptionRows, reportConsumptionPage],
  )

  const reportSummaryDataset = useMemo(() => {
    if (!stockSnapshotReport) {
      return {
        title: '',
        headers: [] as string[],
        rows: [] as Array<{ key: string; cells: Array<string | number>; searchText: string }>,
      }
    }

    if (reportSummaryView === 'department') {
      return {
        title: 'Остатки по отделам',
        headers: ['Отдел', 'На складе', 'В резерве', 'На заправке', 'Установлено', 'Списано', 'Всего'],
        rows: stockSnapshotReport.byDepartment.map((row) => ({
          key: String(row.departmentId),
          cells: [
            row.departmentName,
            row.inStockQuantity,
            row.reserveQuantity,
            row.onRefillQuantity,
            row.installedQuantity,
            row.writtenOffQuantity,
            row.totalQuantity,
          ],
          searchText: row.departmentName,
        })),
      }
    }

    if (reportSummaryView === 'model') {
      return {
        title: 'Остатки по моделям картриджей',
        headers: ['Модель картриджа', 'На складе', 'В резерве', 'На заправке', 'Установлено', 'Списано', 'Всего'],
        rows: stockSnapshotReport.byModel.map((row) => ({
          key: String(row.cartridgeModelId),
          cells: [
            row.cartridgeModelName,
            row.inStockQuantity,
            row.reserveQuantity,
            row.onRefillQuantity,
            row.installedQuantity,
            row.writtenOffQuantity,
            row.totalQuantity,
          ],
          searchText: row.cartridgeModelName,
        })),
      }
    }

    if (reportSummaryView === 'room') {
      return {
        title: 'Остатки по кабинетам',
        headers: ['Кабинет', 'На складе', 'В резерве', 'На заправке', 'Установлено', 'Списано', 'Всего'],
        rows: stockSnapshotReport.byRoom.map((row) => ({
          key: `${row.roomId ?? 'none'}-${row.roomName}`,
          cells: [
            row.roomName,
            row.inStockQuantity,
            row.reserveQuantity,
            row.onRefillQuantity,
            row.installedQuantity,
            row.writtenOffQuantity,
            row.totalQuantity,
          ],
          searchText: row.roomName,
        })),
      }
    }

    if (reportSummaryView === 'type') {
      return {
        title: 'Остатки по типам картриджей',
        headers: ['Тип картриджа', 'На складе', 'В резерве', 'На заправке', 'Установлено', 'Списано', 'Всего'],
        rows: stockSnapshotReport.byType.map((row) => ({
          key: row.cartridgeType,
          cells: [
            row.cartridgeType,
            row.inStockQuantity,
            row.reserveQuantity,
            row.onRefillQuantity,
            row.installedQuantity,
            row.writtenOffQuantity,
            row.totalQuantity,
          ],
          searchText: row.cartridgeType,
        })),
      }
    }

    return {
      title: 'Принтеры по моделям',
      headers: ['Модель принтера', 'В эксплуатации', 'На складе', 'В ремонте', 'Списано', 'Всего'],
      rows: stockSnapshotReport.byPrinterModel.map((row) => ({
        key: row.printerModelName,
        cells: [
          row.printerModelName,
          row.inOperationCount,
          row.inStockCount,
          row.inRepairCount,
          row.writtenOffCount,
          row.totalCount,
        ],
        searchText: row.printerModelName,
      })),
    }
  }, [reportSummaryView, stockSnapshotReport])

  const filteredReportSummaryRows = useMemo(() => {
    const needle = reportSummarySearchTerm.trim().toLowerCase()
    if (!needle) return reportSummaryDataset.rows
    return reportSummaryDataset.rows.filter((row) => row.searchText.toLowerCase().includes(needle))
  }, [reportSummaryDataset.rows, reportSummarySearchTerm])

  const reportSummaryTotalPages = Math.max(1, Math.ceil(filteredReportSummaryRows.length / PAGE_SIZE))

  const paginatedReportSummaryRows = useMemo(
    () => paginateItems(filteredReportSummaryRows, reportSummaryPage),
    [filteredReportSummaryRows, reportSummaryPage],
  )

  const reportItemsDataset = useMemo(() => {
    if (!stockSnapshotReport) {
      return {
        title: '',
        rows: [] as Array<{ key: string; cells: Array<string | number>; searchText: string }>,
      }
    }

    const config = reportItemsView === 'stock'
      ? { title: 'Позиции на складе', rows: stockSnapshotReport.inStockItems }
      : reportItemsView === 'reserve'
        ? { title: 'Позиции в резерве', rows: stockSnapshotReport.reserveItems }
        : reportItemsView === 'refill'
          ? { title: 'Позиции на заправке', rows: stockSnapshotReport.onRefillItems }
          : { title: 'Списанные позиции', rows: stockSnapshotReport.writtenOffItems }

    return {
      title: config.title,
      rows: config.rows.map((row) => ({
        key: `${reportItemsView}-${row.cartridgeId}`,
        cells: [
          row.inventoryCode,
          row.cartridgeModelName,
          row.departmentName,
          row.roomName ?? '-',
          row.quantity,
          row.cartridgeType,
        ],
        searchText: [
          row.inventoryCode,
          row.cartridgeModelName,
          row.departmentName,
          row.roomName ?? '',
          row.cartridgeType,
        ].join(' '),
      })),
    }
  }, [reportItemsView, stockSnapshotReport])

  const filteredReportItemRows = useMemo(() => {
    const needle = reportItemsSearchTerm.trim().toLowerCase()
    if (!needle) return reportItemsDataset.rows
    return reportItemsDataset.rows.filter((row) => row.searchText.toLowerCase().includes(needle))
  }, [reportItemsDataset.rows, reportItemsSearchTerm])

  const reportItemsTotalPages = Math.max(1, Math.ceil(filteredReportItemRows.length / PAGE_SIZE))

  const paginatedReportItemRows = useMemo(
    () => paginateItems(filteredReportItemRows, reportItemsPage),
    [filteredReportItemRows, reportItemsPage],
  )

  const filteredUsers = useMemo(() => {
    const needle = userSearchTerm.trim().toLowerCase()
    return users.filter((user) => {
      if (userRoleFilter !== 'all' && user.role !== userRoleFilter) {
        return false
      }
      if (userActiveFilter === 'active' && !user.active) {
        return false
      }
      if (userActiveFilter === 'inactive' && user.active) {
        return false
      }
      if (!needle) return true
      return [
        user.username,
        user.fullName,
        user.role,
        user.active ? 'Активен' : 'Отключен',
      ].some((value) => value.toLowerCase().includes(needle))
    })
  }, [userActiveFilter, userRoleFilter, userSearchTerm, users])

  const userTotalPages = Math.max(1, Math.ceil(filteredUsers.length / PAGE_SIZE))

  const paginatedUsers = useMemo(() => {
    const from = (userPage - 1) * PAGE_SIZE
    return filteredUsers.slice(from, from + PAGE_SIZE)
  }, [filteredUsers, userPage])

  const filteredInventoryAssets = useMemo(() => {
    const needle = assetSearchTerm.trim().toLowerCase()
    if (!needle) return inventoryAssets
    return inventoryAssets.filter((asset) =>
      [
        asset.inventoryCode,
        asset.name,
        asset.category ?? '',
        asset.departmentName ?? '',
        asset.roomName ?? '',
        asset.comment ?? '',
        INVENTORY_STATUS_OPTIONS.find((item) => item.value === asset.status)?.label ?? asset.status,
      ].some((value) => value.toLowerCase().includes(needle)),
    )
  }, [assetSearchTerm, inventoryAssets])

  const assetTotalPages = Math.max(1, Math.ceil(filteredInventoryAssets.length / PAGE_SIZE))

  const paginatedInventoryAssets = useMemo(() => {
    const from = (assetPage - 1) * PAGE_SIZE
    return filteredInventoryAssets.slice(from, from + PAGE_SIZE)
  }, [assetPage, filteredInventoryAssets])

  const filteredInventoryMovements = useMemo(() => {
    const needle = movementSearchTerm.trim().toLowerCase()
    if (!needle) return inventoryMovements
    return inventoryMovements.filter((movement) =>
      [
        movement.assetInventoryCode,
        movement.assetName,
        movement.fromDepartmentName ?? '',
        movement.fromRoomName ?? '',
        movement.toDepartmentName ?? '',
        movement.toRoomName ?? '',
        movement.actor ?? '',
        movement.comment ?? '',
      ].some((value) => value.toLowerCase().includes(needle)),
    )
  }, [inventoryMovements, movementSearchTerm])

  const movementTotalPages = Math.max(1, Math.ceil(filteredInventoryMovements.length / PAGE_SIZE))

  const paginatedInventoryMovements = useMemo(() => {
    const from = (movementPage - 1) * PAGE_SIZE
    return filteredInventoryMovements.slice(from, from + PAGE_SIZE)
  }, [filteredInventoryMovements, movementPage])

  const filteredHallRequests = useMemo(() => {
    const needle = hallSearchTerm.trim().toLowerCase()
    return hallRequests.filter((request) => {
      if (hallPriorityFilter && request.priority !== hallPriorityFilter) {
        return false
      }
      if (!needle) return true
      return [
        request.roomName,
        request.departmentName,
        request.requesterName,
        request.title,
        request.description ?? '',
        HALL_REQUEST_PRIORITY_OPTIONS.find((item) => item.value === request.priority)?.label ?? request.priority,
        HALL_REQUEST_STATUS_OPTIONS.find((item) => item.value === request.status)?.label ?? request.status,
      ].some((value) => value.toLowerCase().includes(needle))
    })
  }, [hallPriorityFilter, hallRequests, hallSearchTerm])

  const hallTotalPages = Math.max(1, Math.ceil(filteredHallRequests.length / PAGE_SIZE))

  const paginatedHallRequests = useMemo(() => {
    const from = (hallPage - 1) * PAGE_SIZE
    return filteredHallRequests.slice(from, from + PAGE_SIZE)
  }, [filteredHallRequests, hallPage])

  const selectedBatchModels = useMemo(
    () => selectedBatchModelIds.map((id) => models.find((item) => item.id === id)).filter(Boolean) as CartridgeModel[],
    [models, selectedBatchModelIds],
  )

  const activeBatchModel = selectedBatchModels[activeBatchIndex] ?? null
  const activeBatchEntry = activeBatchModel ? batchEntries[activeBatchModel.id] : undefined

  const modelStockSummary = useMemo(() => {
    const usageByModelId = departmentUsageRows.reduce<Record<number, number>>((acc, row) => {
      if (!row.cartridgeModelId) return acc
      acc[row.cartridgeModelId] = (acc[row.cartridgeModelId] ?? 0) + 1
      return acc
    }, {})

    return models
      .map((model) => {
        const related = cartridges.filter((item) => item.cartridgeModelId === model.id)
        const ready = related
          .filter((item) => (item.status === 'IN_STOCK' || item.status === 'RESERVE') && item.empty !== true)
          .reduce((sum, item) => sum + item.quantity, 0)
        const empty = related
          .filter((item) => (item.status === 'IN_STOCK' || item.status === 'RESERVE') && item.empty === true)
          .reduce((sum, item) => sum + item.quantity, 0)
        const reserve = related
          .filter((item) => item.status === 'RESERVE')
          .reduce((sum, item) => sum + item.quantity, 0)
        const onRefill = related
          .filter((item) => item.status === 'ON_REFILL')
          .reduce((sum, item) => sum + item.quantity, 0)
        const minimumQuantity = model.minimumQuantity ?? 0
        const balance = ready - minimumQuantity
        return {
          ...model,
          ready,
          empty,
          reserve,
          onRefill,
          assignedPoints: usageByModelId[model.id] ?? 0,
          balance,
        }
      })
      .sort((a, b) => a.balance - b.balance || a.name.localeCompare(b.name))
  }, [cartridges, departmentUsageRows, models])

  const refillableModelSummary = useMemo(
    () => modelStockSummary.filter((item) => item.refillable),
    [modelStockSummary],
  )

  const disposableModelSummary = useMemo(
    () => modelStockSummary.filter((item) => !item.refillable),
    [modelStockSummary],
  )

  const modelSummaryById = useMemo(
    () => Object.fromEntries(modelStockSummary.map((item) => [item.id, item])),
    [modelStockSummary],
  )

  const filteredModelCatalogItems = useMemo(() => {
    const needle = modelCatalogSearchTerm.trim().toLowerCase()
    return models.filter((item) => {
      if (modelCatalogTypeFilter === 'refillable' && !item.refillable) {
        return false
      }
      if (modelCatalogTypeFilter === 'disposable' && item.refillable) {
        return false
      }

      const balance = modelSummaryById[item.id]?.balance ?? 0
      if (modelCatalogBalanceFilter === 'deficit' && balance >= 0) {
        return false
      }
      if (modelCatalogBalanceFilter === 'minimum' && balance !== 0) {
        return false
      }
      if (modelCatalogBalanceFilter === 'surplus' && balance <= 0) {
        return false
      }

      if (!needle) return true

      return [
        item.name,
        item.refillable ? 'Заправляемый' : 'Одноразовый',
        item.compatiblePrinterModels.join(' '),
      ].some((value) => value.toLowerCase().includes(needle))
    })
  }, [modelCatalogBalanceFilter, modelCatalogSearchTerm, modelCatalogTypeFilter, modelSummaryById, models])

  const modelCatalogTotalPages = Math.max(1, Math.ceil(filteredModelCatalogItems.length / PAGE_SIZE))

  const paginatedModelCatalogItems = useMemo(
    () => paginateItems(filteredModelCatalogItems, modelCatalogPage),
    [filteredModelCatalogItems, modelCatalogPage],
  )

  const disposableWrittenOffByModel = useMemo(() => {
    const createdByModel = actionLogs.reduce<Record<string, number>>((acc, log) => {
      if (log.actionType !== 'CARTRIDGE_CREATED') return acc
      if (!log.details?.includes('тип: одноразовый')) return acc

      const match = log.details.match(/Приход в остаток:\s*(\d+)/)
      const quantity = match ? Number(match[1]) : 0
      if (quantity > 0) {
        acc[log.targetName] = (acc[log.targetName] ?? 0) + quantity
      }
      return acc
    }, {})

    const activeByModel = cartridges.reduce<Record<string, number>>((acc, cartridge) => {
      if (cartridge.refillable !== false) return acc
      if (cartridge.status === 'WRITTEN_OFF') return acc
      acc[cartridge.cartridgeModelName] = (acc[cartridge.cartridgeModelName] ?? 0) + cartridge.quantity
      return acc
    }, {})

    return Object.keys(createdByModel).reduce<Record<string, number>>((acc, modelName) => {
      acc[modelName] = Math.max(0, (createdByModel[modelName] ?? 0) - (activeByModel[modelName] ?? 0))
      return acc
    }, {})
  }, [actionLogs, cartridges])

  const loadActionLogs = useCallback(async () => {
    const normalizedFrom =
      historyDateFrom && historyDateTo && historyDateFrom > historyDateTo ? historyDateTo : historyDateFrom
    const normalizedTo =
      historyDateFrom && historyDateTo && historyDateFrom > historyDateTo ? historyDateFrom : historyDateTo
    const logs = await getActionLogs({
      dateFrom: normalizedFrom || undefined,
      dateTo: normalizedTo || undefined,
      actor: historyActor || undefined,
      actionType: historyActionType || undefined,
      entityType: historyEntityType || undefined,
      result: historyResult || undefined,
      targetName: historyTargetName || undefined,
    })
    setActionLogs(logs)
  }, [historyActionType, historyActor, historyDateFrom, historyDateTo, historyEntityType, historyResult, historyTargetName])

  const loadInventoryAssets = useCallback(async () => {
    const rows = await getInventoryAssets({
      departmentId: assetFilterDepartmentId || undefined,
      roomId: assetFilterRoomId || undefined,
      status: assetFilterStatus || undefined,
    })
    setInventoryAssets(rows)
  }, [assetFilterDepartmentId, assetFilterRoomId, assetFilterStatus])

  const loadInventoryMovements = useCallback(async () => {
    const rows = await getInventoryAssetMovements(assetMovementFilterAssetId || undefined)
    setInventoryMovements(rows)
  }, [assetMovementFilterAssetId])

  const loadHallRequests = useCallback(async () => {
    const rows = await getHallRequests({
      roomId: hallFilterRoomId || undefined,
      status: hallFilterStatus || undefined,
      overdue: hallOverdueOnly ? true : undefined,
    })
    setHallRequests(rows)
  }, [hallFilterRoomId, hallFilterStatus, hallOverdueOnly])

  const refreshCatalog = useCallback(async () => {
    setLoading(true)
    try {
      const [deps, loadedRooms, loadedPrinters, loadedModels, cart, moduleCatalog, alerts, thresholds, snapshot] = await Promise.all([
        getDepartments(),
        getRooms(),
        getPrinters(),
        getCartridgeModels(),
        getCartridges(),
        getSystemModules(),
        getNotificationAlerts(),
        canManageThresholds ? getNotificationThresholds() : Promise.resolve([]),
        getStockSnapshotReport(),
      ])
      setDepartments(deps)
      setRooms(loadedRooms)
      setPrinters(loadedPrinters)
      setModels(loadedModels)
      setCartridges(cart)
      setSystemModules(moduleCatalog)
      setNotificationAlerts(alerts)
      setNotificationThresholds(thresholds)
      setStockSnapshotReport(snapshot)
      if (canViewLogs) {
        await loadActionLogs()
      } else {
        setActionLogs([])
      }
      await Promise.all([loadInventoryAssets(), loadInventoryMovements(), loadHallRequests()])
      if (canManageUsers) {
        setUsers(await getUsers())
      } else {
        setUsers([])
      }
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Не удалось загрузить данные.')
    } finally {
      setLoading(false)
    }
  }, [canManageThresholds, canManageUsers, canViewLogs, loadActionLogs, loadHallRequests, loadInventoryAssets, loadInventoryMovements, pushToast])

  useEffect(() => {
    if (isAuthed) void refreshCatalog()
  }, [isAuthed, refreshCatalog])

  useEffect(() => {
    if (!selectedCartridge) return
    setQuantity(selectedCartridge.quantity)
    setComment(selectedCartridge.comment || '')
    setDetailQuantity(selectedCartridge.quantity)
    setDetailComment(selectedCartridge.comment || '')
    setDetailActor(actor)
    setDetailDateValue(today())
    setInstallPrinterId('')
    setReplaceOutcome('STOCK')
    setRemovePrinterId('')
    setRemoveQuantity(1)
    setRemoveOutcome('STOCK')
  }, [selectedCartridge, actor])

  useEffect(() => {
    if (!isAuthed || activeTab !== 'reports') return
    void loadConsumptionReport()
  }, [activeTab, isAuthed, reportDateFrom, reportDateTo])

  useEffect(() => {
    if (!isAuthed) return
    void loadActionLogs()
  }, [isAuthed, loadActionLogs])

  useEffect(() => {
    if (!isAuthed) return
    void loadInventoryAssets()
  }, [isAuthed, loadInventoryAssets])

  useEffect(() => {
    if (!isAuthed) return
    void loadInventoryMovements()
  }, [isAuthed, loadInventoryMovements])

  useEffect(() => {
    if (!isAuthed) return
    void loadHallRequests()
  }, [isAuthed, loadHallRequests])

  useEffect(() => {
    if (canManageUsers) return
    setUsers([])
    resetUserForm()
    if (activeTab === 'users') {
      setActiveTab('overview')
    }
  }, [activeTab, canManageUsers])

  useEffect(() => {
    if (activeTab === 'history' && !canViewLogs) {
      setActiveTab('overview')
    } else if (activeTab === 'reports' && !canExportReports) {
      setActiveTab('overview')
    } else if (activeTab === 'create' && !canOperate) {
      setActiveTab('overview')
    }
  }, [activeTab, canExportReports, canOperate, canViewLogs])

  useEffect(() => {
    setCurrentPage(1)
  }, [searchTerm, departmentFilter, statusFilter])

  useEffect(() => {
    if (currentPage > totalPages) setCurrentPage(totalPages)
  }, [currentPage, totalPages])

  useEffect(() => {
    setDepartmentUsagePage(1)
  }, [departmentUsageDepartmentFilter, departmentUsageSearch])

  useEffect(() => {
    if (departmentUsagePage > departmentUsageTotalPages) setDepartmentUsagePage(departmentUsageTotalPages)
  }, [departmentUsagePage, departmentUsageTotalPages])

  useEffect(() => {
    setDepartmentListPage(1)
  }, [departmentListSearch, departmentListStatusFilter])

  useEffect(() => {
    if (departmentListPage > departmentListTotalPages) setDepartmentListPage(departmentListTotalPages)
  }, [departmentListPage, departmentListTotalPages])

  useEffect(() => {
    setRoomListPage(1)
  }, [roomListDepartmentFilter, roomListSearch, roomListStatusFilter])

  useEffect(() => {
    if (roomListPage > roomListTotalPages) setRoomListPage(roomListTotalPages)
  }, [roomListPage, roomListTotalPages])

  useEffect(() => {
    setPrinterPage(1)
  }, [printerDepartmentFilter, printerSearchTerm, printerStatusFilter])

  useEffect(() => {
    if (printerPage > printerTotalPages) setPrinterPage(printerTotalPages)
  }, [printerPage, printerTotalPages])

  useEffect(() => {
    setHistoryPage(1)
  }, [historySearchTerm, actionLogs])

  useEffect(() => {
    if (historyPage > historyTotalPages) setHistoryPage(historyTotalPages)
  }, [historyPage, historyTotalPages])

  useEffect(() => {
    setNotificationAlertPage(1)
  }, [notificationAlertDepartmentFilter, notificationAlertSearch, notificationAlertSourceFilter])

  useEffect(() => {
    if (notificationAlertPage > notificationAlertTotalPages) setNotificationAlertPage(notificationAlertTotalPages)
  }, [notificationAlertPage, notificationAlertTotalPages])

  useEffect(() => {
    setThresholdPage(1)
  }, [thresholdActiveFilter, thresholdDepartmentFilter, thresholdSearchTerm])

  useEffect(() => {
    if (thresholdPage > thresholdTotalPages) setThresholdPage(thresholdTotalPages)
  }, [thresholdPage, thresholdTotalPages])

  useEffect(() => {
    setReportConsumptionPage(1)
  }, [reportConsumptionSearchTerm, consumptionReport])

  useEffect(() => {
    if (reportConsumptionPage > reportConsumptionTotalPages) setReportConsumptionPage(reportConsumptionTotalPages)
  }, [reportConsumptionPage, reportConsumptionTotalPages])

  useEffect(() => {
    setReportSummaryPage(1)
  }, [reportSummarySearchTerm, reportSummaryView, stockSnapshotReport])

  useEffect(() => {
    if (reportSummaryPage > reportSummaryTotalPages) setReportSummaryPage(reportSummaryTotalPages)
  }, [reportSummaryPage, reportSummaryTotalPages])

  useEffect(() => {
    setReportItemsPage(1)
  }, [reportItemsSearchTerm, reportItemsView, stockSnapshotReport])

  useEffect(() => {
    if (reportItemsPage > reportItemsTotalPages) setReportItemsPage(reportItemsTotalPages)
  }, [reportItemsPage, reportItemsTotalPages])

  useEffect(() => {
    setUserPage(1)
  }, [userActiveFilter, userRoleFilter, userSearchTerm])

  useEffect(() => {
    if (userPage > userTotalPages) setUserPage(userTotalPages)
  }, [userPage, userTotalPages])

  useEffect(() => {
    setModelCatalogPage(1)
  }, [modelCatalogBalanceFilter, modelCatalogSearchTerm, modelCatalogTypeFilter, models])

  useEffect(() => {
    if (modelCatalogPage > modelCatalogTotalPages) setModelCatalogPage(modelCatalogTotalPages)
  }, [modelCatalogPage, modelCatalogTotalPages])

  useEffect(() => {
    setAssetPage(1)
  }, [assetSearchTerm, inventoryAssets])

  useEffect(() => {
    if (assetPage > assetTotalPages) setAssetPage(assetTotalPages)
  }, [assetPage, assetTotalPages])

  useEffect(() => {
    setMovementPage(1)
  }, [assetMovementFilterAssetId, movementSearchTerm, inventoryMovements])

  useEffect(() => {
    if (movementPage > movementTotalPages) setMovementPage(movementTotalPages)
  }, [movementPage, movementTotalPages])

  useEffect(() => {
    setHallPage(1)
  }, [hallPriorityFilter, hallSearchTerm, hallRequests])

  useEffect(() => {
    if (hallPage > hallTotalPages) setHallPage(hallTotalPages)
  }, [hallPage, hallTotalPages])

  useEffect(() => {
    if (!printerRoomId) return
    if (!availableRoomsForPrinter.some((room) => room.id === printerRoomId)) {
      setPrinterRoomId('')
    }
  }, [availableRoomsForPrinter, printerRoomId])

  useEffect(() => {
    if (!assetRoomId) return
    if (!availableRoomsForAsset.some((room) => room.id === assetRoomId)) {
      setAssetRoomId('')
    }
  }, [assetRoomId, availableRoomsForAsset])

  useEffect(() => {
    if (!transferRoomId) return
    if (!availableRoomsForTransfer.some((room) => room.id === transferRoomId)) {
      setTransferRoomId('')
    }
  }, [availableRoomsForTransfer, transferRoomId])

  useEffect(() => {
    setSelectedBatchModelIds((current) => current.filter((id) => models.some((model) => model.id === id)))
    setBatchEntries((current) => {
      const next = Object.fromEntries(
        Object.entries(current).filter(([key]) => models.some((model) => model.id === Number(key))),
      )
      return next
    })
  }, [models])

  useEffect(() => {
    if (activeBatchIndex >= selectedBatchModels.length) {
      setActiveBatchIndex(Math.max(0, selectedBatchModels.length - 1))
    }
  }, [activeBatchIndex, selectedBatchModels.length])

  useEffect(() => {
    if (!isAuthed || !sessionExpiresAt) return
    if (sessionExpiresAt <= Date.now()) {
      clearStoredSession()
      return
    }

    const timer = window.setTimeout(() => {
      clearStoredSession()
      pushToast('error', 'Сессия истекла. Войдите снова.')
    }, Math.max(0, sessionExpiresAt - Date.now()))

    return () => window.clearTimeout(timer)
  }, [clearStoredSession, isAuthed, pushToast, sessionExpiresAt])

  useEffect(() => {
    if (!isAuthed) return

    const handleActivity = () => {
      noteSessionActivity()
      void refreshSessionIfNeeded()
    }
    const handleVisibilityChange = () => {
      if (document.hidden) return
      handleActivity()
    }

    const events: Array<keyof WindowEventMap> = ['pointerdown', 'keydown', 'scroll', 'focus']
    events.forEach((eventName) => window.addEventListener(eventName, handleActivity, { passive: true }))
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      events.forEach((eventName) => window.removeEventListener(eventName, handleActivity))
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [isAuthed, noteSessionActivity, refreshSessionIfNeeded])

  useEffect(() => {
    if (!isAuthed) return

    const interval = window.setInterval(() => {
      if (document.hidden) return

      if (sessionExpiresAt && sessionExpiresAt <= Date.now()) {
        clearStoredSession()
        pushToast('error', 'Сессия истекла из-за неактивности. Войдите снова.')
        return
      }

      void refreshSessionIfNeeded()
    }, 60 * 1000)

    return () => window.clearInterval(interval)
  }, [clearStoredSession, isAuthed, pushToast, refreshSessionIfNeeded, sessionExpiresAt])

  async function applyFilters() {
    setLoading(true)
    try {
      const data = await getCartridges({
        departmentId: departmentFilter === 'all' ? undefined : departmentFilter,
        status: statusFilter === 'all' ? undefined : statusFilter,
      })
      setCartridges(data)
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Не удалось применить фильтры.')
    } finally {
      setLoading(false)
    }
  }

  async function withAction(action: () => Promise<void>, successText: string) {
    try {
      await action()
      pushToast('success', successText)
      await refreshCatalog()
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Операция завершилась ошибкой.')
    }
  }

  function confirmAdminPin(): boolean {
    if (!isAuthed) {
      setAuthError('Для этого действия нужно войти заново.')
      return false
    }
    return true
  }

  function beginDepartmentEdit(department: Department) {
    setEditingDepartmentId(department.id)
    setDepartmentName(department.name)
    setDepartmentDescription(department.description || '')
    setDepartmentStatus(department.status)
    setActiveTab('departments')
  }

  function resetDepartmentForm() {
    setEditingDepartmentId(null)
    setDepartmentName('')
    setDepartmentDescription('')
    setDepartmentStatus('ACTIVE')
  }

  function beginRoomEdit(room: Room) {
    setEditingRoomId(room.id)
    setRoomName(room.name)
    setRoomDepartmentId(room.departmentId)
    setRoomStatus(room.status)
    setRoomComment(room.comment || '')
    setActiveTab('departments')
  }

  function resetRoomForm() {
    setEditingRoomId(null)
    setRoomName('')
    setRoomDepartmentId('')
    setRoomStatus('ACTIVE')
    setRoomComment('')
  }

  function beginThresholdEdit(item: NotificationThreshold) {
    setEditingThresholdId(item.id)
    setThresholdModelId(item.cartridgeModelId)
    setThresholdDepartmentId(item.departmentId ?? '')
    setThresholdMinimum(item.minimumQuantity)
    setThresholdActive(item.active)
    setThresholdComment(item.comment || '')
    setActiveTab('notifications')
  }

  function resetThresholdForm() {
    setEditingThresholdId(null)
    setThresholdModelId('')
    setThresholdDepartmentId('')
    setThresholdMinimum(0)
    setThresholdActive(true)
    setThresholdComment('')
  }

  function beginAssetEdit(asset: InventoryAsset) {
    setEditingAssetId(asset.id)
    setAssetInventoryCode(asset.inventoryCode)
    setAssetName(asset.name)
    setAssetCategory(asset.category || '')
    setAssetDepartmentId(asset.departmentId ?? '')
    setAssetRoomId(asset.roomId ?? '')
    setAssetStatus(asset.status)
    setAssetQuantity(asset.quantity)
    setAssetComment(asset.comment || '')
    setTransferAssetId(asset.id)
    setActiveTab('inventory')
  }

  function resetAssetForm() {
    setEditingAssetId(null)
    setAssetInventoryCode('')
    setAssetName('')
    setAssetCategory('')
    setAssetDepartmentId('')
    setAssetRoomId('')
    setAssetStatus('IN_USE')
    setAssetQuantity(1)
    setAssetComment('')
  }

  function beginAssetTransfer(asset: InventoryAsset) {
    setTransferAssetId(asset.id)
    setTransferDepartmentId(asset.departmentId ?? '')
    setTransferRoomId(asset.roomId ?? '')
    setTransferActor(actor || 'Администратор')
    setTransferComment('')
    setTransferMovedAt('')
    setActiveTab('inventory')
  }

  function resetTransferForm() {
    setTransferAssetId('')
    setTransferDepartmentId('')
    setTransferRoomId('')
    setTransferActor(actor || 'Администратор')
    setTransferComment('')
    setTransferMovedAt('')
  }

  function beginHallRequestEdit(request: HallRequest) {
    setEditingHallRequestId(request.id)
    setHallRoomId(request.roomId)
    setHallRequesterName(request.requesterName)
    setHallTitle(request.title)
    setHallDescription(request.description || '')
    setHallPriority(request.priority)
    setHallStatus(request.status)
    setHallPlannedAt(request.plannedAt ? request.plannedAt.slice(0, 16) : '')
    setActiveTab('hall-requests')
  }

  function resetHallRequestForm() {
    setEditingHallRequestId(null)
    setHallRoomId('')
    setHallRequesterName('')
    setHallTitle('')
    setHallDescription('')
    setHallPriority('MEDIUM')
    setHallStatus('OPEN')
    setHallPlannedAt('')
  }

  function beginUserEdit(user: UserAdminRecord) {
    setEditingUserId(user.id)
    setUserLogin(user.username)
    setUserFullName(user.fullName)
    setUserPassword('')
    setUserRole(user.role)
    setUserActive(user.active)
    setUserPermissions({ ...user.permissions })
    setActiveTab('users')
  }

  function resetUserForm() {
    setEditingUserId(null)
    setUserLogin('')
    setUserFullName('')
    setUserPassword('')
    setUserRole('OPERATOR')
    setUserActive(true)
    setUserPermissions({ ...DEFAULT_PERMISSIONS_BY_ROLE.OPERATOR })
  }

  function applyRolePreset(role: UserRole) {
    setUserRole(role)
    setUserPermissions({ ...DEFAULT_PERMISSIONS_BY_ROLE[role] })
  }

  function updateUserPermission(permission: keyof UserPermissions, value: boolean) {
    setUserPermissions((current) => ({
      ...current,
      [permission]: value,
    }))
  }

  function requestDelete(target: DeleteTarget) {
    if (!confirmAdminPin()) return
    setDeleteTarget(target)
  }

  function confirmDeleteTarget() {
    if (!deleteTarget) return

    switch (deleteTarget.kind) {
      case 'printer':
        void withAction(async () => {
          await deletePrinter(deleteTarget.id)
          if (editingPrinterId === deleteTarget.id) resetPrinterForm()
          setDeleteTarget(null)
        }, 'Принтер списан.')
        break
      case 'department':
        void withAction(async () => {
          await deleteDepartment(deleteTarget.id)
          if (editingDepartmentId === deleteTarget.id) resetDepartmentForm()
          setDeleteTarget(null)
        }, 'Отдел выведен из использования.')
        break
      case 'room':
        void withAction(async () => {
          await deleteRoom(deleteTarget.id)
          if (editingRoomId === deleteTarget.id) resetRoomForm()
          setDeleteTarget(null)
        }, 'Кабинет выведен из использования.')
        break
      case 'threshold':
        void withAction(async () => {
          await deleteNotificationThreshold(deleteTarget.id)
          if (editingThresholdId === deleteTarget.id) resetThresholdForm()
          setDeleteTarget(null)
        }, 'Порог уведомления удален.')
        break
      case 'inventory-asset':
        void withAction(async () => {
          await deleteInventoryAsset(deleteTarget.id)
          if (editingAssetId === deleteTarget.id) resetAssetForm()
          setDeleteTarget(null)
        }, 'Инвентарный актив удален.')
        break
      case 'hall-request':
        void withAction(async () => {
          await deleteHallRequest(deleteTarget.id)
          if (editingHallRequestId === deleteTarget.id) resetHallRequestForm()
          setDeleteTarget(null)
        }, 'Заявка по залу удалена.')
        break
      case 'cartridge':
        void withAction(async () => {
          await deleteCartridge(deleteTarget.id)
          if (selectedCartridgeId === deleteTarget.id) {
            setSelectedCartridgeId('')
            setDetailOpen(false)
          }
          setDeleteTarget(null)
        }, 'Картриджный остаток удален.')
        break
      case 'model':
        void withAction(async () => {
          await deleteCartridgeModel(deleteTarget.id)
          setSelectedBatchModelIds((current) => current.filter((item) => item !== deleteTarget.id))
          setBatchEntries((current) => {
            const next = { ...current }
            delete next[deleteTarget.id]
            return next
          })
          setDeleteTarget(null)
        }, 'Модель картриджа удалена.')
        break
    }
  }

  function requestWriteOff(source: 'stock' | 'detail', cartridgeId: number, label: string) {
    if (!confirmAdminPin()) return
    setWriteOffRequest({ source, cartridgeId, label })
  }

  function confirmWriteOffRequest() {
    if (!writeOffRequest) return

    const effectiveComment = writeOffRequest.source === 'detail' ? detailComment : comment
    void withAction(async () => {
      await writeOff(writeOffRequest.cartridgeId, effectiveComment)
      setWriteOffRequest(null)
      if (writeOffRequest.source === 'detail') {
        setDetailAction(null)
      }
    }, 'Картридж списан.')
  }

  async function handleQuickReplace(slotId: number, cartridgeModelId: number, hasInstalled: boolean) {
    const availableCartridge = cartridges.find(
      (item) =>
        item.cartridgeModelId === cartridgeModelId &&
        item.status === 'IN_STOCK' &&
        item.quantity > 0 &&
        item.empty !== true,
    )

    if (!availableCartridge) {
      pushToast('error', 'Нет доступного остатка для этой точки замены.')
      return
    }

    let removedOutcome: RemovalOutcome = 'WRITE_OFF'
    let successText = 'Картридж установлен, остаток уменьшен.'
    if (hasInstalled) {
      const installedCartridgeId = departmentUsageRows.find((row) => row.slotId === slotId)?.currentInstallation?.cartridgeId
      const installedCartridge = installedCartridgeId
        ? cartridges.find((item) => item.id === installedCartridgeId)
        : undefined

      removedOutcome = installedCartridge?.refillable === false ? 'WRITE_OFF' : 'REFILL'
      successText = removedOutcome === 'REFILL'
        ? 'Замена выполнена. Старый картридж возвращен в остаток как пустой.'
        : 'Замена выполнена. Старый картридж списан.'
    }

      await withAction(async () => {
      if (hasInstalled) {
        await replaceCartridge(availableCartridge.id, {
          printerId: slotId,
          removedOutcome,
          comment: '',
          actionDate: today(),
          createdBy: actor,
        })
        return
      }

      await installCartridge(availableCartridge.id, slotId, 1, '')
    }, successText)
  }

  function onQuickMarkEmpty(cartridgeId: number) {
    void withAction(async () => {
      await markCartridgeEmpty(cartridgeId, 'Помечен пустым в отделе')
    }, 'Картридж помечен как пустой.')
  }

  function onQuickRemove(slotId: number, installation: NonNullable<(typeof departmentUsageRows)[number]['currentInstallation']>) {
    void withAction(async () => {
      await removeCartridgeInstallation(
        installation.cartridgeId,
        slotId,
        1,
        true,
        'Убран из принтера'
      )
    }, installation.refillable === false
      ? 'Картридж убран и списан.'
      : 'Картридж убран и возвращен в остаток как пустой.')
  }

  function requireCartridge(): number | null {
    if (!selectedCartridgeId) {
      pushToast('error', 'Сначала выберите картридж.')
      return null
    }
    return selectedCartridgeId
  }

  function onAdjustQuantity(event: FormEvent) {
    event.preventDefault()
    const id = requireCartridge()
    if (!id) return
    void withAction(async () => {
      await adjustQuantity(id, quantity, comment)
    }, 'Количество обновлено.')
  }

  function onSendToRefill(event: FormEvent) {
    event.preventDefault()
    if (!selectedCartridgeRefillable) {
      pushToast('error', 'Этот тип картриджа не заправляется. Его нужно списывать и заменять.')
      return
    }
    if (!selectedCartridgeEmpty) {
      pushToast('error', 'На заправку можно отправлять только пустой картридж.')
      return
    }
    const id = requireCartridge()
    if (!id) return
    const refillQuantity = clampOperationQuantity(quantity, selectedCartridge?.quantity ?? 1)
    void withAction(async () => {
      await sendToRefill(id, refillQuantity, dateValue, actor, comment)
    }, 'Картридж отправлен на заправку.')
  }

  function onReturnFromRefill(event: FormEvent) {
    event.preventDefault()
    const id = requireCartridge()
    if (!id) return
    const returnedQuantity = clampOperationQuantity(quantity, selectedCartridge?.quantity ?? 1)
    void withAction(async () => {
      await returnFromRefill(id, returnedQuantity, dateValue, actor, comment)
    }, 'Картридж возвращен с заправки.')
  }

  function onWriteOff(event: FormEvent) {
    event.preventDefault()
    const id = requireCartridge()
    if (!id) return
    requestWriteOff('stock', id, selectedCartridge?.cartridgeModelName || `Картридж #${id}`)
  }

  function onReplaceCartridge(event: FormEvent) {
    event.preventDefault()
    const id = requireCartridge()
    if (!id) return
    if (!installPrinterId) {
      pushToast('error', 'Выберите точку замены.')
      return
    }
    void withAction(async () => {
      await replaceCartridge(id, {
        printerId: installPrinterId,
        removedOutcome: replaceOutcome,
        comment,
        actionDate: dateValue,
        createdBy: actor,
      })
    }, 'Замена картриджа выполнена.')
  }

  function onRemoveInstallation(event: FormEvent) {
    event.preventDefault()
    const id = requireCartridge()
    if (!id) return
    if (!removePrinterId) {
      pushToast('error', 'Выберите принтер, с которого снимается картридж.')
      return
    }
    if (removeOutcome === 'REFILL' && !selectedCartridgeRefillable) {
      pushToast('error', 'Этот картридж сейчас помечен как одноразовый. Сначала переключите его на "Заправляется".')
      return
    }
    void withAction(async () => {
      if (removeOutcome === 'STOCK') {
        await removeCartridgeInstallation(id, removePrinterId, removeQuantity, true, comment)
        return
      }

      if (removeOutcome === 'REFILL') {
        await removeCartridgeInstallation(id, removePrinterId, removeQuantity, true, comment)
        return
      }

      await removeCartridgeInstallation(id, removePrinterId, removeQuantity, false, comment)
      await writeOff(id, comment)
    }, removeOutcome === 'STOCK'
      ? 'Картридж снят и возвращен в остаток.'
      : removeOutcome === 'REFILL'
        ? 'Картридж снят и возвращен в остаток как пустой.'
        : 'Картридж снят и списан.')
  }

  function onSignIn(event: FormEvent) {
    event.preventDefault()
    setAuthError('')
    if (!authLogin.trim() || !authPassword.trim()) {
      setAuthError('Введите логин и пароль.')
      return
    }

    void (async () => {
      try {
        const response = await signIn(authLogin.trim(), authPassword)
        persistSession(response.token, response.user, response.expiresAt)
        setActor(response.user.fullName)
        setDetailActor(response.user.fullName)
        setActiveTab('departments')
      } catch (e) {
        setAuthError(e instanceof Error ? e.message : 'Не удалось выполнить вход.')
      }
    })()
  }

  function onLogout() {
    clearStoredSession()
    setAuthPassword('')
    setSelectedCartridgeId('')
    setUsers([])
    setInventoryAssets([])
    setInventoryMovements([])
    setHallRequests([])
    setAssetMovementFilterAssetId('')
    setHallOverdueOnly(false)
    resetUserForm()
    resetAssetForm()
    resetTransferForm()
    resetHallRequestForm()
    setToasts([])
    setActiveTab('departments')
  }

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
      return
    }
    setSortKey(key)
    setSortDir('asc')
  }

  function openDetails(id: number) {
    setSelectedCartridgeId(id)
    setDetailAction(null)
    setDetailOpen(true)
  }

  function onDetailSendToRefill(event: FormEvent) {
    event.preventDefault()
    if (!selectedCartridge) return
    if (!selectedCartridgeRefillable) {
      pushToast('error', 'Этот тип картриджа не заправляется. Его нужно списывать и заменять.')
      return
    }
    if (!selectedCartridgeEmpty) {
      pushToast('error', 'На заправку можно отправлять только пустой картридж.')
      return
    }
    const refillQuantity = clampOperationQuantity(detailQuantity, selectedCartridge.quantity)
    void withAction(async () => {
      await sendToRefill(selectedCartridge.id, refillQuantity, detailDateValue, detailActor, detailComment)
    }, 'Картридж отправлен на заправку.')
  }

  function onDetailReturnFromRefill(event: FormEvent) {
    event.preventDefault()
    if (!selectedCartridge) return
    const returnedQuantity = clampOperationQuantity(detailQuantity, selectedCartridge.quantity)
    void withAction(async () => {
      await returnFromRefill(selectedCartridge.id, returnedQuantity, detailDateValue, detailActor, detailComment)
    }, 'Картридж возвращен с заправки.')
  }

  function onDetailWriteOff(event: FormEvent) {
    event.preventDefault()
    if (!selectedCartridge) return
    requestWriteOff('detail', selectedCartridge.id, selectedCartridge.cartridgeModelName)
  }

  function onCreateDepartment(event: FormEvent) {
    event.preventDefault()
    if (!departmentName.trim()) {
      pushToast('error', 'Введите название отдела.')
      return
    }
    void withAction(async () => {
      const payload = {
        name: departmentName.trim(),
        description: departmentDescription.trim(),
        status: departmentStatus,
      }

      if (editingDepartmentId) {
        await updateDepartment(editingDepartmentId, payload)
      } else {
        await createDepartment(payload)
      }
      resetDepartmentForm()
    }, editingDepartmentId ? 'Отдел обновлен.' : 'Отдел создан.')
  }

  function onCreateRoom(event: FormEvent) {
    event.preventDefault()
    if (!roomName.trim()) {
      pushToast('error', 'Введите название кабинета.')
      return
    }
    if (!roomDepartmentId) {
      pushToast('error', 'Выберите отдел для кабинета.')
      return
    }

    const selectedDepartment = userDepartments.find((department) => department.id === roomDepartmentId)
    const editingRoom = editingRoomId ? rooms.find((room) => room.id === editingRoomId) : null
    const keepsCurrentArchivedDepartment = Boolean(
      editingRoom &&
      selectedDepartment &&
      editingRoom.departmentId === selectedDepartment.id &&
      selectedDepartment.status === 'DECOMMISSIONED',
    )

    if (!selectedDepartment) {
      pushToast('error', 'Выбранный отдел не найден.')
      return
    }

    if (selectedDepartment.status !== 'ACTIVE' && !keepsCurrentArchivedDepartment) {
      pushToast('error', 'Нельзя привязать кабинет к отделу, который выведен из использования.')
      return
    }

    void withAction(async () => {
      const payload = {
        name: roomName.trim(),
        departmentId: roomDepartmentId,
        status: roomStatus,
        comment: roomComment.trim(),
      }

      if (editingRoomId) {
        await updateRoom(editingRoomId, payload)
      } else {
        await createRoom(payload)
      }
      resetRoomForm()
    }, editingRoomId ? 'Кабинет обновлен.' : 'Кабинет создан.')
  }

  function onSaveThreshold(event: FormEvent) {
    event.preventDefault()
    if (!thresholdModelId) {
      pushToast('error', 'Выберите модель картриджа для порога.')
      return
    }

    const payload = {
      cartridgeModelId: thresholdModelId,
      departmentId: thresholdDepartmentId || undefined,
      minimumQuantity: Math.max(0, thresholdMinimum),
      active: thresholdActive,
      comment: thresholdComment.trim(),
    }

    void withAction(async () => {
      if (editingThresholdId) {
        await updateNotificationThreshold(editingThresholdId, payload)
      } else {
        await createNotificationThreshold(payload)
      }
      resetThresholdForm()
    }, editingThresholdId ? 'Порог обновлен.' : 'Порог создан.')
  }

  function onSaveUser(event: FormEvent) {
    event.preventDefault()
    if (!canManageUsers) {
      pushToast('error', 'Недостаточно прав для управления пользователями.')
      return
    }
    if (!userLogin.trim()) {
      pushToast('error', 'Введите логин пользователя.')
      return
    }
    if (!userFullName.trim()) {
      pushToast('error', 'Введите ФИО пользователя.')
      return
    }
    if (!editingUserId && !userPassword.trim()) {
      pushToast('error', 'Для нового пользователя нужен пароль.')
      return
    }

    void withAction(async () => {
      const payload = {
        username: userLogin.trim(),
        fullName: userFullName.trim(),
        password: userPassword.trim() || undefined,
        role: userRole,
        active: userActive,
        permissions: userPermissions,
      }

      if (editingUserId) {
        await updateUser(editingUserId, payload)
      } else {
        await createUser(payload)
      }
      resetUserForm()
    }, editingUserId ? 'Пользователь обновлен.' : 'Пользователь создан.')
  }

  function onSaveInventoryAsset(event: FormEvent) {
    event.preventDefault()
    if (!assetInventoryCode.trim()) {
      pushToast('error', 'Введите инвентарный номер актива.')
      return
    }
    if (!assetName.trim()) {
      pushToast('error', 'Введите название актива.')
      return
    }

    void withAction(async () => {
      const payload: UpsertInventoryAssetPayload = {
        inventoryCode: assetInventoryCode.trim(),
        name: assetName.trim(),
        category: assetCategory.trim() || undefined,
        departmentId: assetDepartmentId || undefined,
        roomId: assetRoomId || undefined,
        status: assetStatus,
        quantity: Math.max(0, assetQuantity),
        comment: assetComment.trim() || undefined,
      }

      if (editingAssetId) {
        await updateInventoryAsset(editingAssetId, payload)
      } else {
        await createInventoryAsset(payload)
      }
      resetAssetForm()
    }, editingAssetId ? 'Инвентарный актив обновлен.' : 'Инвентарный актив создан.')
  }

  function onTransferInventoryAsset(event: FormEvent) {
    event.preventDefault()
    if (!transferAssetId) {
      pushToast('error', 'Выберите актив для перемещения.')
      return
    }

    const payload: TransferInventoryAssetPayload = {
      toDepartmentId: transferDepartmentId || undefined,
      toRoomId: transferRoomId || undefined,
      actor: transferActor.trim() || undefined,
      comment: transferComment.trim() || undefined,
      movedAt: transferMovedAt || undefined,
    }

    void withAction(async () => {
      await transferInventoryAsset(transferAssetId, payload)
      setAssetMovementFilterAssetId(transferAssetId)
      resetTransferForm()
    }, 'Актив перемещен.')
  }

  function onSaveHallRequest(event: FormEvent) {
    event.preventDefault()
    if (!hallRoomId) {
      pushToast('error', 'Выберите кабинет.')
      return
    }
    if (!hallRequesterName.trim()) {
      pushToast('error', 'Введите имя заявителя.')
      return
    }
    if (!hallTitle.trim()) {
      pushToast('error', 'Введите тему заявки.')
      return
    }

    void withAction(async () => {
      const payload: UpsertHallRequestPayload = {
        roomId: hallRoomId,
        requesterName: hallRequesterName.trim(),
        title: hallTitle.trim(),
        description: hallDescription.trim() || undefined,
        priority: hallPriority,
        status: hallStatus,
        plannedAt: hallPlannedAt || undefined,
      }

      if (editingHallRequestId) {
        await updateHallRequest(editingHallRequestId, payload)
      } else {
        await createHallRequest(payload)
      }
      resetHallRequestForm()
    }, editingHallRequestId ? 'Заявка по залу обновлена.' : 'Заявка по залу создана.')
  }

  function onCreateModel(event: FormEvent) {
    event.preventDefault()
    if (!modelName.trim()) {
      pushToast('error', 'Введите название модели картриджа.')
      return
    }
    void withAction(async () => {
      await createCartridgeModel({
        name: modelName.trim(),
        refillable: modelRefillable,
        minimumQuantity: Math.max(0, modelMinimumQuantity),
        compatiblePrinterModels: parseCompatiblePrinterModels(modelCompatiblePrintersText),
      })
      setModelName('')
      setModelRefillable(true)
      setModelMinimumQuantity(0)
      setModelCompatiblePrintersText('')
    }, 'Модель картриджа создана.')
  }

  function toggleBatchModel(modelId: number) {
    setSelectedBatchModelIds((current) => {
      if (current.includes(modelId)) {
        return current.filter((id) => id !== modelId)
      }
      return [...current, modelId]
    })
    setBatchEntries((current) => ({
      ...current,
      [modelId]: current[modelId] ?? { quantity: 1, comment: '' },
    }))
  }

  function updateBatchEntry(modelId: number, patch: Partial<BatchEntry>) {
    setBatchEntries((current) => ({
      ...current,
      [modelId]: {
        quantity: current[modelId]?.quantity ?? 1,
        comment: current[modelId]?.comment ?? '',
        ...patch,
      },
    }))
  }

  function onCreateCartridge(event: FormEvent) {
    event.preventDefault()
    if (selectedBatchModels.length === 0) {
      pushToast('error', 'Выберите хотя бы одну модель для пополнения.')
      return
    }
    void withAction(async () => {
      for (const model of selectedBatchModels) {
        const entry = batchEntries[model.id] ?? { quantity: 1, comment: '' }
        await createCartridge({
          cartridgeModelId: model.id,
          quantity: Math.max(0, entry.quantity),
          status: batchTargetStatus,
          comment: entry.comment.trim(),
        })
      }
      setSelectedBatchModelIds([])
      setBatchEntries({})
      setActiveBatchIndex(0)
      setBatchTargetStatus('IN_STOCK')
      setActiveTab('stock')
    }, 'Партия картриджей добавлена в остаток.')
  }

  function onSaveModelSettings(model: CartridgeModel) {
    void withAction(async () => {
      await updateCartridgeModel(model.id, {
        name: model.name,
        refillable: model.refillable,
        minimumQuantity: Math.max(0, model.minimumQuantity ?? 0),
        compatiblePrinterModels: model.compatiblePrinterModels ?? [],
      })
      setModelCompatibilityDrafts((current) => {
        const next = { ...current }
        delete next[model.id]
        return next
      })
    }, `Параметры модели "${model.name}" сохранены.`)
  }

  function onChangeModelField(id: number, patch: Partial<CartridgeModel>) {
    setModels((current) =>
      current.map((item) =>
        item.id === id
          ? {
              ...item,
              ...patch,
            }
          : item,
      ),
    )
  }

  function resetPrinterForm() {
    setEditingPrinterId(null)
    setPrinterName('')
    setPrinterModel('')
    setPrinterIpAddress('')
    setPrinterSerialNumber('')
    setPrinterDepartmentId('')
    setPrinterRoomId('')
    setPrinterDeviceType('PRINTER')
    setPrinterColorMode('MONOCHROME')
    setPrinterStatus('IN_OPERATION')
    setPrinterCommissionedAt('')
    setPrinterWrittenOffAt('')
    setPrinterComment('')
    setPrinterSlots([{ name: 'Основной', cartridgeModelId: '' }])
  }

  function beginPrinterEdit(printer: Printer) {
    setEditingPrinterId(printer.id ?? null)
    setPrinterName(printer.name)
    setPrinterModel(printer.model ?? '')
    setPrinterIpAddress(printer.ipAddress ?? '')
    setPrinterSerialNumber(printer.serialNumber ?? '')
    setPrinterDepartmentId(printer.departmentId ?? '')
    setPrinterRoomId(printer.roomId ?? '')
    setPrinterDeviceType(printer.deviceType)
    setPrinterColorMode(printer.colorMode)
    setPrinterStatus(printer.status)
    setPrinterCommissionedAt(printer.commissionedAt ?? '')
    setPrinterWrittenOffAt(printer.writtenOffAt ?? '')
    setPrinterComment(printer.comment ?? '')
    setPrinterSlots(
      (printer.slots ?? []).map((slot) => ({
        name: slot.name,
        cartridgeModelId: slot.cartridgeModelId ?? '',
      })),
    )
    setActiveTab('printers')
  }

  function applyPrinterColorMode(colorMode: PrinterColorMode) {
    setPrinterColorMode(colorMode)
    if (colorMode === 'MONOCHROME') {
      setPrinterSlots((current) => [
        {
          name: current[0]?.name?.trim() ? current[0].name : 'Основной',
          cartridgeModelId: current[0]?.cartridgeModelId ?? '',
        },
      ])
      return
    }

    setPrinterSlots((current) =>
      current.length >= 4
        ? current
        : [
            { name: 'Black', cartridgeModelId: current[0]?.cartridgeModelId ?? '' },
            { name: 'Cyan', cartridgeModelId: '' },
            { name: 'Magenta', cartridgeModelId: '' },
            { name: 'Yellow', cartridgeModelId: '' },
          ],
    )
  }

  function onCreatePrinter(event: FormEvent) {
    event.preventDefault()
    if (!printerName.trim()) {
      pushToast('error', 'Введите название принтера.')
      return
    }
    if (!printerDepartmentId) {
      pushToast('error', 'Выберите отдел для принтера.')
      return
    }
    if (!printerRoomId) {
      pushToast('error', 'Выберите кабинет для принтера.')
      return
    }
    if (printerSlots.some((slot) => !slot.name.trim() || !slot.cartridgeModelId)) {
      pushToast('error', 'У каждого слота должно быть имя и модель картриджа.')
      return
    }

    const incompatibleSlots = printerSlots
      .map((slot) => ({
        slotName: slot.name.trim(),
        cartridgeModel: models.find((model) => model.id === Number(slot.cartridgeModelId)),
      }))
      .filter(
        (item): item is { slotName: string; cartridgeModel: CartridgeModel } =>
          Boolean(item.cartridgeModel) &&
          !isCartridgeModelCompatibleWithPrinter(item.cartridgeModel!, printerModel),
      )

    if (printerModel.trim() && incompatibleSlots.length > 0) {
      const summary = incompatibleSlots
        .map((item) => `${item.slotName}: ${item.cartridgeModel.name}`)
        .join('; ')
      pushToast('error', `Для модели принтера "${printerModel.trim()}" выбраны несовместимые картриджи: ${summary}.`)
      return
    }

    const payload = {
      name: printerName.trim(),
      model: printerModel.trim() || undefined,
      ipAddress: printerIpAddress.trim() || undefined,
      serialNumber: printerSerialNumber.trim() || undefined,
      departmentId: printerDepartmentId,
      roomId: printerRoomId,
      deviceType: printerDeviceType,
      colorMode: printerColorMode,
      status: printerStatus,
      commissionedAt: printerCommissionedAt || undefined,
      writtenOffAt: printerStatus === 'WRITTEN_OFF' ? printerWrittenOffAt || undefined : undefined,
      comment: printerComment.trim() || undefined,
      slots: printerSlots.map((slot) => ({
        name: slot.name.trim(),
        cartridgeModelId: Number(slot.cartridgeModelId),
      })),
    }

    void withAction(async () => {
      if (editingPrinterId) {
        await updatePrinter(editingPrinterId, payload)
      } else {
        await createPrinter(payload)
      }
      resetPrinterForm()
    }, editingPrinterId ? 'Принтер обновлен.' : 'Принтер создан.')
  }

  function onDeletePrinter(id: number, name: string) {
    requestDelete({ kind: 'printer', id, label: name })
  }

  function onDeleteDepartment(id: number, name: string) {
    requestDelete({ kind: 'department', id, label: name })
  }

  function onDeleteRoom(id: number, name: string) {
    requestDelete({ kind: 'room', id, label: name })
  }

  function onDeleteThreshold(id: number, label: string) {
    requestDelete({ kind: 'threshold', id, label })
  }

  function onDeleteCartridge(id: number, title: string) {
    requestDelete({ kind: 'cartridge', id, label: title })
  }

  function onDeleteCartridgeModel(id: number, name: string) {
    requestDelete({ kind: 'model', id, label: name })
  }

  function onDeleteInventoryAsset(id: number, name: string) {
    requestDelete({ kind: 'inventory-asset', id, label: name })
  }

  function onDeleteHallRequest(id: number, title: string) {
    requestDelete({ kind: 'hall-request', id, label: title })
  }

  async function loadConsumptionReport() {
    try {
      const [report, snapshot] = await Promise.all([
        getConsumptionReport(reportDateFrom, reportDateTo),
        getStockSnapshotReport(),
      ])
      setConsumptionReport(report)
      setStockSnapshotReport(snapshot)
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Не удалось загрузить отчет.')
    }
  }

  function saveBlob(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  }

  async function onExportReportXlsx() {
    try {
      const blob = await downloadConsumptionReportXlsx(reportDateFrom, reportDateTo)
      saveBlob(blob, `consumption-${reportDateFrom}-${reportDateTo}.xlsx`)
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Не удалось выгрузить Excel-отчет.')
    }
  }

  async function onExportReportPdf() {
    try {
      const blob = await downloadConsumptionReportPdf(reportDateFrom, reportDateTo)
      saveBlob(blob, `consumption-${reportDateFrom}-${reportDateTo}.pdf`)
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Не удалось выгрузить PDF-отчет.')
    }
  }

  async function onExportStockSnapshotXlsx() {
    try {
      const blob = await downloadStockSnapshotReportXlsx()
      saveBlob(blob, 'stock-snapshot-report.xlsx')
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Не удалось выгрузить Excel-отчет по остаткам.')
    }
  }

  async function onExportStockSnapshotPdf() {
    try {
      const blob = await downloadStockSnapshotReportPdf()
      saveBlob(blob, 'stock-snapshot-report.pdf')
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Не удалось выгрузить PDF-отчет по остаткам.')
    }
  }

  return (
    <div className="app-shell">
      {!isAuthed && (
        <section className="auth-overlay">
          <div className="auth-panel admin-auth-panel">
            <p className="eyebrow">Admin Access</p>
            <h1>Вход в панель</h1>
            <p className="subtitle">Сессия хранится 30 минут. Вход по логину и паролю.</p>
            <form className="auth-form" onSubmit={onSignIn}>
              <label>
                Логин
                <input
                  value={authLogin}
                  onChange={(e) => setAuthLogin(e.target.value)}
                  placeholder="Введите логин"
                  autoFocus
                />
              </label>
              <label>
                Пароль
                <input
                  type="password"
                  value={authPassword}
                  onChange={(e) => setAuthPassword(e.target.value)}
                  placeholder="Введите пароль"
                />
              </label>
              <button type="submit">Войти в панель</button>
            </form>
            {authError && <p className="error">{authError}</p>}
          </div>
        </section>
      )}
      <header className="simple-header">
        <div>
          <h1>Учет картриджей</h1>
          <p className="table-hint">Отделы, остатки, замены и журнал действий.</p>
        </div>
        <div className="table-actions">
          {loading && <span className="table-muted">Синхронизация...</span>}
          <span className="table-muted">{sessionUser}</span>
          {authUser && <span className="table-muted">Роль: {authUser.role}</span>}
          {isAuthed && <span className="table-muted">Сессия: {sessionRemainingMinutes} мин.</span>}
          <button className="ghost" onClick={onLogout}>Выйти</button>
        </div>
      </header>

      {toasts.length > 0 && <div className={`status-line status-${toasts[toasts.length - 1].kind}`}>{toasts[toasts.length - 1].text}</div>}

      <section className="module-strip">
        {systemModules.map((moduleItem) => (
          <article key={moduleItem.code} className={`module-card module-${moduleItem.status.toLowerCase()}`}>
            <div className="module-card-head">
              <strong>{moduleItem.title}</strong>
              <span className={`module-badge module-badge-${moduleItem.status.toLowerCase()}`}>
                {moduleItem.status === 'ACTIVE' ? 'Активен' : 'Планируется'}
              </span>
            </div>
            <p>{moduleItem.description}</p>
            {moduleItem.plannedScope && <small>Дальше: {moduleItem.plannedScope}</small>}
          </article>
        ))}
      </section>

      <nav className="tabs tabs-admin">
        <button className={activeTab === 'overview' ? 'active' : ''} onClick={() => setActiveTab('overview')}>
          Обзор
        </button>
        <button className={activeTab === 'stock' ? 'active' : ''} onClick={() => setActiveTab('stock')}>Картриджи</button>
        <button className={activeTab === 'departments' ? 'active' : ''} onClick={() => setActiveTab('departments')}>
          Отделы
        </button>
        <button className={activeTab === 'printers' ? 'active' : ''} onClick={() => setActiveTab('printers')}>
          Принтеры
        </button>
        {canViewLogs && (
          <button className={activeTab === 'history' ? 'active' : ''} onClick={() => setActiveTab('history')}>
            История
          </button>
        )}
        {canOperate && (
          <button className={activeTab === 'create' ? 'active' : ''} onClick={() => setActiveTab('create')}>
            Пополнение
          </button>
        )}
        <button className={activeTab === 'notifications' ? 'active' : ''} onClick={() => setActiveTab('notifications')}>
          Уведомления
        </button>
        {canExportReports && (
          <button className={activeTab === 'reports' ? 'active' : ''} onClick={() => setActiveTab('reports')}>
            Отчеты
          </button>
        )}
        {canManageUsers && (
          <button className={activeTab === 'users' ? 'active' : ''} onClick={() => setActiveTab('users')}>
            Пользователи
          </button>
        )}
        <button className={activeTab === 'inventory' ? 'active' : ''} onClick={() => setActiveTab('inventory')}>
          Инвентаризация
        </button>
        <button className={activeTab === 'hall-requests' ? 'active' : ''} onClick={() => setActiveTab('hall-requests')}>
          Заявки по залам
        </button>
      </nav>

      {activeTab === 'overview' && (
        <section className="overview-layout">
          <section className="panel overview-main">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Overview</p>
                <h2>Командный центр администратора</h2>
              </div>
              <button className="ghost" onClick={() => void refreshCatalog()}>
                Обновить данные
              </button>
            </div>

            <div className="stat-grid">
              <article className="stat-card accent-cyan">
                <span className="stat-label">Всего принтеров</span>
                <strong>{dashboardStats.totalPrinters}</strong>
                <p>{dashboardStats.printersInOperation} в эксплуатации</p>
              </article>
              <article className="stat-card accent-lime">
                <span className="stat-label">Всего картриджей</span>
                <strong>{dashboardStats.totalCartridgeUnits}</strong>
                <p>по всем статусам</p>
              </article>
              <article className="stat-card accent-amber">
                <span className="stat-label">Активные уведомления</span>
                <strong>{dashboardStats.alertCount}</strong>
                <p>позиции ниже порога</p>
              </article>
              <article className="stat-card accent-steel">
                <span className="stat-label">На заправке</span>
                <strong>{dashboardStats.cartridgesOnRefill}</strong>
                <p>нужен контроль возврата</p>
              </article>
            </div>

            <div className="overview-summary-grid">
              <article className="side-card">
                <p className="eyebrow">Printers</p>
                <h3>Сводка по принтерам</h3>
                <div className="overview-chip-grid">
                  <span className="status-badge status-ready">В эксплуатации: {dashboardStats.printersInOperation}</span>
                  <span className="status-badge status-installed">На складе: {dashboardStats.printersInStock}</span>
                  <span className="status-badge status-on_refill">В ремонте: {dashboardStats.printersInRepair}</span>
                  <span className="status-badge status-written_off">Списано: {dashboardStats.printersWrittenOff}</span>
                </div>
              </article>

              <article className="side-card">
                <p className="eyebrow">Cartridges</p>
                <h3>Сводка по картриджам</h3>
                <div className="overview-chip-grid">
                  <span className="status-badge status-ready">На складе: {dashboardStats.cartridgesInStock}</span>
                  <span className="status-badge status-reserve">Резерв: {dashboardStats.cartridgesReserve}</span>
                  <span className="status-badge status-on_refill">На заправке: {dashboardStats.cartridgesOnRefill}</span>
                  <span className="status-badge status-installed">Установлено: {dashboardStats.cartridgesInstalled}</span>
                  <span className="status-badge status-written_off">Списано: {dashboardStats.cartridgesWrittenOff}</span>
                </div>
              </article>

              <article className="side-card">
                <p className="eyebrow">Quick Links</p>
                <h3>Быстрые переходы</h3>
                <div className="overview-quick-links">
                  {canOperate && <button className="ghost" type="button" onClick={() => setActiveTab('create')}>Пополнение</button>}
                  <button className="ghost" type="button" onClick={() => setActiveTab('stock')}>Остатки</button>
                  <button className="ghost" type="button" onClick={() => setActiveTab('printers')}>Принтеры</button>
                  <button className="ghost" type="button" onClick={() => setActiveTab('notifications')}>Уведомления</button>
                  {canExportReports && <button className="ghost" type="button" onClick={() => setActiveTab('reports')}>Отчеты</button>}
                  {canViewLogs && <button className="ghost" type="button" onClick={() => setActiveTab('history')}>Журнал</button>}
                </div>
              </article>
            </div>

            <div className="command-layout">
              <article className="side-card command-card">
                <h3>Выбор картриджа</h3>
                <label>
                  Поиск
                  <input
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    placeholder="Модель или отдел"
                  />
                </label>
                <label>
                  Активный картридж
                  <select
                    value={selectedCartridgeId}
                    onChange={(e) => setSelectedCartridgeId(e.target.value ? Number(e.target.value) : '')}
                  >
                    <option value="">Выберите...</option>
                    {visibleCartridges.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.cartridgeModelName} · {item.departmentName} · {item.quantity} шт.
                      </option>
                    ))}
                  </select>
                </label>
                {selectedCartridge ? (
                  <div className="selected-cartridge-card">
                    <p className="meta">{selectedCartridge.cartridgeModelName}</p>
                    <strong>{selectedCartridge.departmentName}</strong>
                    <span className={`status-badge ${STATUS_TONES[selectedCartridge.status]}`}>
                      {STATUS_LABELS[selectedCartridge.status]}
                    </span>
                    <p>Отдел: {selectedCartridge.departmentName}</p>
                    <p>Количество: {selectedCartridge.quantity}</p>
                    <p>Установлено: {selectedCartridge.installedQuantity ?? 0}</p>
                    <p>Тип: {selectedCartridgeRefillable ? 'Заправляемый' : 'Одноразовый'}</p>
                    <p>Состояние: {selectedCartridgeEmpty ? 'Пустой' : 'Готов к установке'}</p>
                  </div>
                ) : (
                  <div className="empty-state">Выберите картридж, чтобы открыть операции.</div>
                )}
              </article>

              <div className="command-forms">
                <form onSubmit={onAdjustQuantity} className="form-card command-form">
                  <h3>Изменить количество</h3>
                  <label>
                    Новое количество
                    <input
                      type="number"
                      min={0}
                      value={quantity}
                      onChange={(e) => setQuantity(Number(e.target.value))}
                    />
                  </label>
                  <label>
                    Комментарий
                    <input value={comment} onChange={(e) => setComment(e.target.value)} />
                  </label>
                  <button type="submit" disabled={!selectedCartridgeId}>
                    Обновить остаток
                  </button>
                </form>

                <form onSubmit={onReplaceCartridge} className="form-card command-form">
                  <h3>Заменить картридж</h3>
                  <label>
                    Слот принтера
                    <select
                      value={installPrinterId}
                      onChange={(e) => setInstallPrinterId(e.target.value ? Number(e.target.value) : '')}
                    >
                      <option value="">Выберите...</option>
                      {selectedDepartmentSlots.map((slot) => (
                        <option key={slot.slotId} value={slot.slotId ?? ''}>
                          {slot.printerName} · {slot.slotName}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Что делать со старым картриджем
                    <select value={replaceOutcome} onChange={(e) => setReplaceOutcome(e.target.value as RemovalOutcome)}>
                      <option value="STOCK">Вернуть в картриджи</option>
                      <option value="REFILL">Отдать на заправку</option>
                      <option value="WRITE_OFF">Списать</option>
                    </select>
                  </label>
                  <label>
                    Дата операции
                    <input type="date" value={dateValue} onChange={(e) => setDateValue(e.target.value)} />
                  </label>
                  <label>
                    Ответственный
                    <input value={actor} onChange={(e) => setActor(e.target.value)} />
                  </label>
                  <label>
                    Комментарий
                    <input value={comment} onChange={(e) => setComment(e.target.value)} />
                  </label>
                  <button type="submit" disabled={!selectedCartridgeId || selectedDepartmentSlots.length === 0}>
                    Заменить
                  </button>
                </form>

                <form onSubmit={onRemoveInstallation} className="form-card command-form">
                  <h3>Снять с принтера</h3>
                  <label>
                    Слот принтера
                    <select
                      value={removePrinterId}
                      onChange={(e) => setRemovePrinterId(e.target.value ? Number(e.target.value) : '')}
                    >
                      <option value="">Выберите...</option>
                      {selectedDepartmentSlots.map((slot) => (
                        <option key={slot.slotId} value={slot.slotId ?? ''}>
                          {slot.printerName} · {slot.slotName}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Сколько снять
                    <input
                      type="number"
                      min={1}
                      value={removeQuantity}
                      onChange={(e) => setRemoveQuantity(Number(e.target.value))}
                    />
                  </label>
                  <label>
                    После снятия
                    <select value={removeOutcome} onChange={(e) => setRemoveOutcome(e.target.value as RemovalOutcome)}>
                      <option value="STOCK">Вернуть в картриджи</option>
                      <option value="REFILL">Отдать на заправку</option>
                      <option value="WRITE_OFF">Списать</option>
                    </select>
                  </label>
                  <label>
                    Комментарий
                    <input value={comment} onChange={(e) => setComment(e.target.value)} />
                  </label>
                  <button type="submit" disabled={!selectedCartridgeId || selectedDepartmentSlots.length === 0}>
                    Снять
                  </button>
                </form>

                <form onSubmit={onSendToRefill} className="form-card command-form">
                  <h3>Отправить на заправку</h3>
                  {!selectedCartridgeRefillable && <p className="form-help danger-text">Для этой модели доступно только списание.</p>}
                  {selectedCartridgeRefillable && !selectedCartridgeEmpty && (
                    <p className="form-help danger-text">На заправку можно отправлять только пустой перезаправляемый картридж.</p>
                  )}
                  <label>
                    Дата
                    <input type="date" value={dateValue} onChange={(e) => setDateValue(e.target.value)} />
                  </label>
                  <label>
                    Ответственный
                    <input value={actor} onChange={(e) => setActor(e.target.value)} />
                  </label>
                  <label>
                    Комментарий
                    <input value={comment} onChange={(e) => setComment(e.target.value)} />
                  </label>
                  <button type="submit" disabled={!selectedCartridgeId || !selectedCartridgeRefillable || !selectedCartridgeEmpty}>
                    Передать в сервис
                  </button>
                </form>

                <form onSubmit={onReturnFromRefill} className="form-card command-form">
                  <h3>Вернуть с заправки</h3>
                  <label>
                    Дата
                    <input type="date" value={dateValue} onChange={(e) => setDateValue(e.target.value)} />
                  </label>
                  <label>
                    Ответственный
                    <input value={actor} onChange={(e) => setActor(e.target.value)} />
                  </label>
                  <label>
                    Комментарий
                    <input value={comment} onChange={(e) => setComment(e.target.value)} />
                  </label>
                  <button type="submit" disabled={!selectedCartridgeId}>
                    Вернуть в картриджи
                  </button>
                </form>

                <form onSubmit={onWriteOff} className="form-card command-form danger-panel">
                  <h3>Списать картридж</h3>
                  <label>
                    Основание
                    <input value={comment} onChange={(e) => setComment(e.target.value)} />
                  </label>
                  <button type="submit" disabled={!selectedCartridgeId}>
                    Списать позицию
                  </button>
                </form>
              </div>
            </div>
          </section>

          <aside className="overview-side">
            <section className="side-card">
              <p className="eyebrow">Alerts</p>
              <h3>Актуальные уведомления</h3>
              <div className="department-mini-list">
                {overviewAlerts.map((alert) => (
                  <article key={`${alert.cartridgeModelId}-${alert.departmentId}`} className="department-mini-card">
                    <strong>{alert.cartridgeModelName}</strong>
                    <p>{alert.departmentName}</p>
                    <span>Остаток: {alert.currentQuantity} шт. при пороге {alert.thresholdQuantity} шт.</span>
                  </article>
                ))}
                {overviewAlerts.length === 0 && <div className="empty-state">Активных уведомлений сейчас нет.</div>}
              </div>
            </section>

            <section className="side-card">
              <p className="eyebrow">Departments</p>
              <h3>Структура отделов</h3>
              <div className="department-mini-list">
                {departmentStats.map((department) => (
                  <article key={department.id} className="department-mini-card">
                    <strong>{department.name}</strong>
                    <p>
                      {department.cartridgeCount} позиций · {department.totalQuantity} шт. · {(department.printers ?? []).length} точек замены.
                    </p>
                    <span>{department.onRefill} на заправке</span>
                  </article>
                ))}
                {departmentStats.length === 0 && <div className="empty-state">Отделов пока нет.</div>}
              </div>
            </section>

            <section className="side-card">
              <p className="eyebrow">Recent</p>
              <h3>Последние действия</h3>
              <div className="history-preview-list">
                {recentActivity.map((entry) => (
                  <article key={entry.id} className="history-preview-card">
                    <strong>{entry.targetName}</strong>
                    <p>{formatActionType(entry.actionType)}</p>
                    <span>{formatDateTime(entry.createdAt)}</span>
                  </article>
                ))}
                {recentActivity.length === 0 && <div className="empty-state">Журнал действий пока пуст.</div>}
              </div>
            </section>
          </aside>
        </section>
      )}

      {activeTab === 'stock' && (
        <section className="panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Cartridge Catalog</p>
              <h2>Остатки картриджей</h2>
              <p className="table-hint">Здесь хранится общий остаток по моделям и отделам. Без ручного учета отдельных экземпляров.</p>
            </div>
          </div>

          <div className="filters">
            <label>
              Отдел
              <select
                value={departmentFilter}
                onChange={(e) => setDepartmentFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))}
              >
                <option value="all">Все</option>
                {userDepartments.map((department) => (
                  <option key={department.id} value={department.id}>
                    {department.name}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Статус
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as CartridgeStatus | 'all')}
              >
                <option value="all">Все</option>
                {STATUS_LIST.map((status) => (
                  <option key={status} value={status}>
                    {STATUS_LABELS[status]}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Поиск
              <input
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    placeholder="Модель или отдел"
                  />
                </label>

            <div className="filter-actions">
              <button onClick={() => void applyFilters()}>Применить</button>
              <button onClick={() => void refreshCatalog()} className="ghost">
                Сбросить
              </button>
            </div>
          </div>

          <div className="table-wrap">
            <table className="stock-table">
              <thead>
                <tr>
                  <th>Модель</th>
                  <th>
                    <button className="sort-btn" onClick={() => toggleSort('departmentName')}>
                      Место учета
                    </button>
                  </th>
                  <th>В остатке</th>
                  <th>Установлено</th>
                  <th>Состояние</th>
                  <th>
                    <button className="sort-btn" onClick={() => toggleSort('status')}>
                      Статус
                    </button>
                  </th>
                  <th>
                    <button className="sort-btn" onClick={() => toggleSort('refillCount')}>
                      Заправки
                    </button>
                  </th>
                  <th>Действия</th>
                </tr>
              </thead>
              <tbody>
                {paginatedCartridges.map((item) => (
                  <tr key={item.id}>
                    <td>{item.cartridgeModelName}</td>
                    <td>{item.departmentName}</td>
                    <td>{item.status === 'INSTALLED' ? '—' : item.quantity}</td>
                    <td>{item.status === 'INSTALLED' ? (item.installedQuantity ?? 0) : '—'}</td>
                    <td>
                      {(() => {
                        const stockState = getStockStateMeta(item)
                        return <span className={`status-badge ${stockState.tone}`}>{stockState.label}</span>
                      })()}
                    </td>
                    <td>
                      <span className={`status-badge ${STATUS_TONES[item.status]}`}>{STATUS_LABELS[item.status]}</span>
                    </td>
                    <td>{item.refillCount}</td>
                    <td>
                      <div className="table-actions">
                        <button className="ghost" onClick={() => openDetails(item.id)}>
                          Детали
                        </button>
                        <button
                          className="ghost danger-action"
                          onClick={() => onDeleteCartridge(item.id, `${item.cartridgeModelName} / ${item.departmentName}`)}
                          disabled={!canDeleteCartridge(item)}
                          title={getDeleteCartridgeHint(item)}
                        >
                          Удалить
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <span>
              Страница {currentPage} / {totalPages}
            </span>
            <div className="pagination-controls">
              <button onClick={() => setCurrentPage(1)} disabled={currentPage === 1}>
                {'<<'}
              </button>
              <button onClick={() => setCurrentPage((page) => Math.max(1, page - 1))} disabled={currentPage === 1}>
                {'<'}
              </button>
              <button
                onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}
                disabled={currentPage === totalPages}
              >
                {'>'}
              </button>
              <button onClick={() => setCurrentPage(totalPages)} disabled={currentPage === totalPages}>
                {'>>'}
              </button>
            </div>
          </div>
        </section>
      )}

      {activeTab === 'departments' && (
        <section className="departments-layout">
          <section className="panel departments-main-panel">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Departments</p>
                <h2>Матрица замен по отделам</h2>
                <p className="table-hint">
                  Здесь ведётся учёт точек замены: какой картридж нужен, что сейчас стоит и когда была последняя замена.
                </p>
              </div>
            </div>
            <div className="filters">
              <label>
                Отдел
                <select
                  value={departmentUsageDepartmentFilter}
                  onChange={(e) =>
                    setDepartmentUsageDepartmentFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))
                  }
                >
                  <option value="all">Все</option>
                  {userDepartments.map((department) => (
                    <option key={department.id} value={department.id}>
                      {department.name}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Поиск
                <input
                  value={departmentUsageSearch}
                  onChange={(e) => setDepartmentUsageSearch(e.target.value)}
                  placeholder="Отдел, кабинет, принтер, слот, модель"
                />
              </label>
            </div>
            <div className="table-shell">
              <table className="stock-table">
                <thead>
                  <tr>
                    <th>Отдел</th>
                    <th>Кабинет</th>
                    <th>Принтер</th>
                    <th>Слот</th>
                    <th>Нужный картридж</th>
                    <th>Сейчас установлен</th>
                    <th>Предпоследняя замена</th>
                    <th>Последняя замена</th>
                    <th>Действие</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedDepartmentUsageRows.map((row) => (
                    <tr key={row.id}>
                      <td>{row.departmentName}</td>
                      <td>{row.roomName}</td>
                      <td>{row.printerName}</td>
                      <td>{row.slotName}</td>
                      <td>{row.cartridgeModelName}</td>
                      <td>
                        {row.currentInstallation ? (
                          <div className="replacement-point-state">
                            <div className="table-actions">
                              <span className="status-badge status-installed">Установлен</span>
                              {row.currentInstallation.empty === true && (
                                <span className="status-badge status-empty">Пустой</span>
                              )}
                            </div>
                            <span>{row.currentInstallation.cartridgeModelName}</span>
                          </div>
                        ) : (
                          <span className="status-badge status-written_off">Не установлен</span>
                        )}
                      </td>
                      <td>{formatDate(row.previousReplacementDate)}</td>
                      <td>{formatDate(row.lastReplacementDate)}</td>
                      <td>
                        {row.slotId && row.cartridgeModelId ? (
                          <div className="table-actions">
                            <button
                              onClick={() => void handleQuickReplace(row.slotId!, row.cartridgeModelId!, Boolean(row.currentInstallation))}
                            >
                              Заменить
                            </button>
                            {row.currentInstallation && (
                              <>
                                <button
                                  type="button"
                                  className="ghost"
                                  onClick={() => onQuickMarkEmpty(row.currentInstallation!.cartridgeId)}
                                  disabled={row.currentInstallation.empty === true}
                                >
                                  Пустой
                                </button>
                                <button
                                  type="button"
                                  className="ghost"
                                  onClick={() => onQuickRemove(row.slotId!, row.currentInstallation!)}
                                >
                                  Убран
                                </button>
                              </>
                            )}
                          </div>
                        ) : (
                          <span className="table-muted">Недоступно</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {filteredDepartmentUsageRows.length === 0 && (
                <div className="empty-state">
                  {departmentUsageRows.length === 0
                    ? 'Точек замены пока нет. Сначала добавьте отдел и укажите в нём точки замены с нужными картриджами.'
                    : 'По текущим фильтрам точки замены не найдены.'}
                </div>
              )}
            </div>
            <div className="pagination">
              <span>
                Точек замены: {filteredDepartmentUsageRows.length} · Страница {departmentUsagePage} / {departmentUsageTotalPages}
              </span>
              <div className="pagination-controls">
                <button onClick={() => setDepartmentUsagePage(1)} disabled={departmentUsagePage === 1}>
                  {'<<'}
                </button>
                <button
                  onClick={() => setDepartmentUsagePage((page) => Math.max(1, page - 1))}
                  disabled={departmentUsagePage === 1}
                >
                  {'<'}
                </button>
                <button
                  onClick={() => setDepartmentUsagePage((page) => Math.min(departmentUsageTotalPages, page + 1))}
                  disabled={departmentUsagePage === departmentUsageTotalPages}
                >
                  {'>'}
                </button>
                <button
                  onClick={() => setDepartmentUsagePage(departmentUsageTotalPages)}
                  disabled={departmentUsagePage === departmentUsageTotalPages}
                >
                  {'>>'}
                </button>
              </div>
            </div>
          </section>

          <div className="departments-bottom">
            <aside className="side-card">
              <p className="eyebrow">Summary</p>
              <h3>Сводка по картриджам</h3>
              <p className="table-muted">Сколько есть в остатке, сколько нужно на все точки замены и какой будет итоговый баланс.</p>
              {departmentUsageRows.length === 0 ? (
                <div className="empty-state">Сводка появится после того, как вы добавите хотя бы одну точку замены.</div>
              ) : (
                <div className="department-mini-list">
                  {cartridgeDemandSummary.map((item) => (
                    <article key={item.modelName} className="department-mini-card">
                      <strong>{item.modelName}</strong>
                      <p>Количество картриджей: {item.stock}</p>
                      <p>Сумма по отделам: {item.demand}</p>
                      <span className={balanceTone(item.replacementBalance)}>
                        Остаток при замене на всех точках: {item.replacementBalance}
                      </span>
                    </article>
                  ))}
                </div>
              )}
            </aside>

            <aside className="side-card">
              <p className="eyebrow">Manage Departments</p>
              <h3>Существующие отделы</h3>
              <div className="inline-filters">
                <label>
                  Поиск
                  <input
                    value={departmentListSearch}
                    onChange={(e) => setDepartmentListSearch(e.target.value)}
                    placeholder="Название или описание"
                  />
                </label>
                <label>
                  Статус
                  <select
                    value={departmentListStatusFilter}
                    onChange={(e) => setDepartmentListStatusFilter(e.target.value as DepartmentStatus | 'all')}
                  >
                    <option value="all">Все</option>
                    <option value="ACTIVE">Действует</option>
                    <option value="DECOMMISSIONED">Демонтирован / не используется</option>
                  </select>
                </label>
              </div>
              <div className="department-mini-list">
                {paginatedDepartmentCards.map((department) => (
                  <article key={department.id} className="department-mini-card">
                    <strong>{department.name}</strong>
                    <p>
                      <span className={`status-badge ${getStructureStatusTone(department.status)}`}>{formatDepartmentStatus(department.status)}</span>
                    </p>
                    <p>{department.description || 'Без описания'}</p>
                    <div className="table-actions">
                      <button className="ghost" onClick={() => beginDepartmentEdit(department)}>Изменить</button>
                      {department.status === 'ACTIVE' && (
                        <button className="ghost danger-action" onClick={() => onDeleteDepartment(department.id, department.name)}>Вывести из использования</button>
                      )}
                    </div>
                  </article>
                ))}
                {filteredDepartmentCards.length === 0 && <div className="empty-state">Подходящих отделов не найдено.</div>}
              </div>
              <div className="pagination compact-pagination">
                <span>
                  Отделов: {filteredDepartmentCards.length} · Страница {departmentListPage} / {departmentListTotalPages}
                </span>
                <div className="pagination-controls">
                  <button onClick={() => setDepartmentListPage(1)} disabled={departmentListPage === 1}>
                    {'<<'}
                  </button>
                  <button
                    onClick={() => setDepartmentListPage((page) => Math.max(1, page - 1))}
                    disabled={departmentListPage === 1}
                  >
                    {'<'}
                  </button>
                  <button
                    onClick={() => setDepartmentListPage((page) => Math.min(departmentListTotalPages, page + 1))}
                    disabled={departmentListPage === departmentListTotalPages}
                  >
                    {'>'}
                  </button>
                  <button
                    onClick={() => setDepartmentListPage(departmentListTotalPages)}
                    disabled={departmentListPage === departmentListTotalPages}
                  >
                    {'>>'}
                  </button>
                </div>
              </div>
            </aside>

            <aside className="side-card">
              <p className="eyebrow">Manage Rooms</p>
              <h3>Кабинеты и залы</h3>
              <div className="inline-filters">
                <label>
                  Отдел
                  <select
                    value={roomListDepartmentFilter}
                    onChange={(e) => setRoomListDepartmentFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))}
                  >
                    <option value="all">Все</option>
                    {userDepartments.map((department) => (
                      <option key={department.id} value={department.id}>
                        {department.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Статус
                  <select value={roomListStatusFilter} onChange={(e) => setRoomListStatusFilter(e.target.value as RoomStatus | 'all')}>
                    <option value="all">Все</option>
                    <option value="ACTIVE">Действует</option>
                    <option value="DECOMMISSIONED">Демонтирован / не используется</option>
                  </select>
                </label>
                <label>
                  Поиск
                  <input
                    value={roomListSearch}
                    onChange={(e) => setRoomListSearch(e.target.value)}
                    placeholder="Кабинет, зал, комментарий"
                  />
                </label>
              </div>
              <div className="department-mini-list">
                {paginatedRoomCards.map((room) => (
                  <article key={room.id} className="department-mini-card">
                    <strong>{room.name}</strong>
                    <p>{room.departmentName}</p>
                    <p>
                      <span className={`status-badge ${getStructureStatusTone(room.status)}`}>{formatRoomStatus(room.status)}</span>
                    </p>
                    <p>{room.comment || 'Без комментария'}</p>
                    <div className="table-actions">
                      <button className="ghost" onClick={() => beginRoomEdit(room)}>Изменить</button>
                      {room.status === 'ACTIVE' && (
                        <button className="ghost danger-action" onClick={() => onDeleteRoom(room.id, room.name)}>Вывести из использования</button>
                      )}
                    </div>
                  </article>
                ))}
                {filteredRoomCards.length === 0 && <div className="empty-state">Подходящих кабинетов не найдено.</div>}
              </div>
              <div className="pagination compact-pagination">
                <span>
                  Кабинетов: {filteredRoomCards.length} · Страница {roomListPage} / {roomListTotalPages}
                </span>
                <div className="pagination-controls">
                  <button onClick={() => setRoomListPage(1)} disabled={roomListPage === 1}>
                    {'<<'}
                  </button>
                  <button onClick={() => setRoomListPage((page) => Math.max(1, page - 1))} disabled={roomListPage === 1}>
                    {'<'}
                  </button>
                  <button
                    onClick={() => setRoomListPage((page) => Math.min(roomListTotalPages, page + 1))}
                    disabled={roomListPage === roomListTotalPages}
                  >
                    {'>'}
                  </button>
                  <button onClick={() => setRoomListPage(roomListTotalPages)} disabled={roomListPage === roomListTotalPages}>
                    {'>>'}
                  </button>
                </div>
              </div>
            </aside>

            <aside className="side-card">
              <p className="eyebrow">Create Department</p>
              <h3>{editingDepartmentId ? 'Изменить отдел' : 'Добавить отдел'}</h3>
              <p className="table-muted">Отдел содержит только общие сведения. Принтеры и слоты теперь настраиваются отдельно во вкладке <strong>Принтеры</strong>.</p>
              <form onSubmit={onCreateDepartment} className="form-card compact-form">
                <label>
                  Название
                  <input value={departmentName} onChange={(e) => setDepartmentName(e.target.value)} />
                </label>
                <label>
                  Статус
                  <select value={departmentStatus} onChange={(e) => setDepartmentStatus(e.target.value as DepartmentStatus)}>
                    <option value="ACTIVE">Действует</option>
                    <option value="DECOMMISSIONED">Демонтирован / не используется</option>
                  </select>
                </label>
                <label>
                  Описание
                  <textarea
                    value={departmentDescription}
                    onChange={(e) => setDepartmentDescription(e.target.value)}
                    placeholder="Например: административный блок, бухгалтерия, инженерная служба"
                  />
                </label>
                <div className="table-actions">
                  <button type="submit">{editingDepartmentId ? 'Сохранить изменения' : 'Создать отдел'}</button>
                  {editingDepartmentId && (
                    <button type="button" className="ghost" onClick={resetDepartmentForm}>
                      Отмена
                    </button>
                  )}
                </div>
              </form>
            </aside>

            <aside className="side-card">
              <p className="eyebrow">Create Room</p>
              <h3>{editingRoomId ? 'Изменить кабинет' : 'Добавить кабинет'}</h3>
              <form onSubmit={onCreateRoom} className="form-card compact-form">
                <label>
                  Название кабинета/зала
                  <input value={roomName} onChange={(e) => setRoomName(e.target.value)} />
                </label>
                <label>
                  Отдел
                  <select value={roomDepartmentId} onChange={(e) => setRoomDepartmentId(e.target.value ? Number(e.target.value) : '')}>
                    <option value="">Выберите...</option>
                    {availableDepartmentsForRoomForm.map((department) => (
                      <option key={department.id} value={department.id}>
                        {department.name}
                        {department.status === 'DECOMMISSIONED' ? ' (архивный отдел)' : ''}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Статус
                  <select value={roomStatus} onChange={(e) => setRoomStatus(e.target.value as RoomStatus)}>
                    <option value="ACTIVE">Действует</option>
                    <option value="DECOMMISSIONED">Демонтирован / не используется</option>
                  </select>
                </label>
                <label>
                  Комментарий
                  <textarea value={roomComment} onChange={(e) => setRoomComment(e.target.value)} />
                </label>
                <div className="table-actions">
                  <button type="submit">{editingRoomId ? 'Сохранить кабинет' : 'Создать кабинет'}</button>
                  {editingRoomId && (
                    <button type="button" className="ghost" onClick={resetRoomForm}>
                      Отмена
                    </button>
                  )}
                </div>
              </form>
            </aside>
          </div>
        </section>
      )}

      {activeTab === 'printers' && (
        <section className="departments-layout">
          <section className="panel departments-main-panel">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Printers</p>
                <h2>Принтеры и слоты картриджей</h2>
                <p className="table-hint">Для цветных принтеров каждый цвет ведется как отдельный слот с собственной заменой.</p>
              </div>
            </div>
            <div className="filters">
              <label>
                Отдел
                <select
                  value={printerDepartmentFilter}
                  onChange={(e) => setPrinterDepartmentFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))}
                >
                  <option value="all">Все</option>
                  {userDepartments.map((department) => (
                    <option key={department.id} value={department.id}>
                      {department.name}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Статус
                <select
                  value={printerStatusFilter}
                  onChange={(e) => setPrinterStatusFilter(e.target.value as PrinterStatus | 'all')}
                >
                  <option value="all">Все</option>
                  <option value="IN_OPERATION">В эксплуатации</option>
                  <option value="IN_STOCK">На складе</option>
                  <option value="IN_REPAIR">В ремонте</option>
                  <option value="WRITTEN_OFF">Списан</option>
                </select>
              </label>

              <label>
                Поиск
                <input
                  value={printerSearchTerm}
                  onChange={(e) => setPrinterSearchTerm(e.target.value)}
                  placeholder="Принтер, модель, IP, серийный номер"
                />
              </label>
            </div>
            <div className="table-shell">
              <table className="stock-table">
                <thead>
                  <tr>
                    <th>Отдел</th>
                    <th>Кабинет</th>
                    <th>Принтер</th>
                    <th>Тип</th>
                    <th>Слоты</th>
                    <th>Действия</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedPrinters.map((printer) => (
                    <tr key={printer.id}>
                      <td>{printer.departmentName}</td>
                      <td>{printer.roomName || '-'}</td>
                      <td>
                        <div className="replacement-point-state">
                          <strong>{printer.name}</strong>
                          <span>{printer.model || 'Модель не указана'}</span>
                          <span>{printer.ipAddress || 'IP не указан'}</span>
                        </div>
                      </td>
                      <td>
                        <div className="replacement-point-state">
                          <span>{formatPrinterDeviceType(printer.deviceType)}</span>
                          <span>{formatPrinterColorMode(printer.colorMode)}</span>
                          <span>{formatPrinterStatus(printer.status)}</span>
                        </div>
                      </td>
                      <td>
                        <div className="replacement-point-state">
                          {printer.slots.map((slot) => (
                            <span key={slot.id ?? slot.name}>
                              {slot.name}: {slot.cartridgeModelName ?? 'Не назначен'}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td>
                        <div className="table-actions">
                          <button className="ghost" onClick={() => beginPrinterEdit(printer)}>Изменить</button>
                          <button className="ghost danger-action" onClick={() => onDeletePrinter(printer.id!, printer.name)}>Списать</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {filteredPrinters.length === 0 && <div className="empty-state">Подходящих принтеров не найдено.</div>}
            </div>
            <div className="pagination">
              <span>
                Принтеров: {filteredPrinters.length} · Страница {printerPage} / {printerTotalPages}
              </span>
              <div className="pagination-controls">
                <button onClick={() => setPrinterPage(1)} disabled={printerPage === 1}>
                  {'<<'}
                </button>
                <button onClick={() => setPrinterPage((page) => Math.max(1, page - 1))} disabled={printerPage === 1}>
                  {'<'}
                </button>
                <button
                  onClick={() => setPrinterPage((page) => Math.min(printerTotalPages, page + 1))}
                  disabled={printerPage === printerTotalPages}
                >
                  {'>'}
                </button>
                <button onClick={() => setPrinterPage(printerTotalPages)} disabled={printerPage === printerTotalPages}>
                  {'>>'}
                </button>
              </div>
            </div>
          </section>

          <div className="departments-bottom">
            <aside className="side-card">
              <p className="eyebrow">Printer Form</p>
              <h3>{editingPrinterId ? 'Изменить принтер' : 'Добавить принтер'}</h3>
              <form onSubmit={onCreatePrinter} className="form-card compact-form">
                <label>
                  Название принтера
                  <input value={printerName} onChange={(e) => setPrinterName(e.target.value)} />
                </label>
                <label>
                  Модель
                  <input value={printerModel} onChange={(e) => setPrinterModel(e.target.value)} />
                </label>
                {printerModel.trim() && hasExplicitPrinterCompatibility && (
                  <p className="table-muted">
                    {printerStrictCompatibleModels.length > 0
                      ? `По совместимости подходят: ${printerStrictCompatibleModels.map((model) => model.name).join(', ')}.`
                      : 'Для этой модели принтера явная совместимость не найдена. В списках слотов доступны только универсальные модели без заполненной совместимости.'}
                  </p>
                )}
                <label>
                  IP-адрес
                  <input value={printerIpAddress} onChange={(e) => setPrinterIpAddress(e.target.value)} placeholder="192.168.0.10" />
                </label>
                <label>
                  Серийный номер
                  <input value={printerSerialNumber} onChange={(e) => setPrinterSerialNumber(e.target.value)} />
                </label>
                <label>
                  Отдел
                  <select value={printerDepartmentId} onChange={(e) => setPrinterDepartmentId(e.target.value ? Number(e.target.value) : '')}>
                    <option value="">Выберите...</option>
                    {activeUserDepartments.map((department) => (
                      <option key={department.id} value={department.id}>
                        {department.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Кабинет
                  <select
                    value={printerRoomId}
                    onChange={(e) => setPrinterRoomId(e.target.value ? Number(e.target.value) : '')}
                    disabled={!printerDepartmentId}
                  >
                    <option value="">Выберите...</option>
                    {availableRoomsForPrinter.map((room) => (
                      <option key={room.id} value={room.id}>
                        {room.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Тип устройства
                  <select value={printerDeviceType} onChange={(e) => setPrinterDeviceType(e.target.value as PrinterDeviceType)}>
                    <option value="PRINTER">Принтер</option>
                    <option value="MFP">МФУ</option>
                  </select>
                </label>
                <label>
                  Цветность
                  <select value={printerColorMode} onChange={(e) => applyPrinterColorMode(e.target.value as PrinterColorMode)}>
                    <option value="MONOCHROME">Ч/Б</option>
                    <option value="COLOR">Цветной</option>
                  </select>
                </label>
                <label>
                  Статус
                  <select value={printerStatus} onChange={(e) => setPrinterStatus(e.target.value as PrinterStatus)}>
                    <option value="IN_OPERATION">В эксплуатации</option>
                    <option value="IN_STOCK">На складе</option>
                    <option value="IN_REPAIR">В ремонте</option>
                    <option value="WRITTEN_OFF">Списан</option>
                  </select>
                </label>
                <label>
                  Дата ввода в эксплуатацию
                  <input type="date" value={printerCommissionedAt} onChange={(e) => setPrinterCommissionedAt(e.target.value)} />
                </label>
                <label>
                  Дата списания
                  <input
                    type="date"
                    value={printerWrittenOffAt}
                    onChange={(e) => setPrinterWrittenOffAt(e.target.value)}
                    disabled={printerStatus !== 'WRITTEN_OFF'}
                  />
                </label>
                <label>
                  Комментарий
                  <textarea value={printerComment} onChange={(e) => setPrinterComment(e.target.value)} rows={3} />
                </label>
                <div className="dynamic-list">
                  <span className="field-label">Слоты картриджей</span>
                  {printerSlots.map((slot, index) => (
                    <div key={index}>
                      <div className="dynamic-row">
                        <input
                          value={slot.name}
                          onChange={(e) =>
                            setPrinterSlots((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, name: e.target.value } : item))
                          }
                          placeholder={`Слот ${index + 1}`}
                          disabled={printerColorMode === 'COLOR' && ['Black', 'Cyan', 'Magenta', 'Yellow'].includes(slot.name)}
                        />
                        <select
                          value={slot.cartridgeModelId}
                          onChange={(e) =>
                            setPrinterSlots((current) =>
                              current.map((item, itemIndex) =>
                                itemIndex === index ? { ...item, cartridgeModelId: e.target.value ? Number(e.target.value) : '' } : item,
                              ),
                            )
                          }
                        >
                          <option value="">Модель картриджа</option>
                          {models.map((model) => {
                            const isCompatible = printerCompatibleModels.some((item) => item.id === model.id)
                            const isSelectedIncompatible = slot.cartridgeModelId === model.id && !isCompatible
                            return (
                              <option
                                key={model.id}
                                value={model.id}
                                disabled={!isCompatible && !isSelectedIncompatible}
                              >
                                {isCompatible ? model.name : `${model.name} (не подходит)`}
                              </option>
                            )
                          })}
                        </select>
                      </div>
                      {(() => {
                        const selectedModel = models.find((model) => model.id === slot.cartridgeModelId)
                        if (!selectedModel || isCartridgeModelCompatibleWithPrinter(selectedModel, printerModel)) {
                          return null
                        }
                        return (
                          <p className="danger-text table-spacing-sm">
                            Для слота "{slot.name || `#${index + 1}`}" выбрана несовместимая модель: {selectedModel.name}.
                          </p>
                        )
                      })()}
                    </div>
                  ))}
                </div>
                <div className="table-actions">
                  <button type="submit">{editingPrinterId ? 'Сохранить принтер' : 'Создать принтер'}</button>
                  {editingPrinterId && <button type="button" className="ghost" onClick={resetPrinterForm}>Отмена</button>}
                </div>
              </form>
            </aside>
          </div>
        </section>
      )}

      {activeTab === 'history' && canViewLogs && (
        <section className="panel">
          <div className="section-heading">
            <div>
              <h2>Журнал действий</h2>
              <p className="table-hint">Все действия по остаткам, отделам, заменам и заправкам.</p>
            </div>
          </div>
          <div className="filters">
            <label>
              Дата с
              <input
                type="date"
                value={historyDateFrom}
                onChange={(event) => setHistoryDateFrom(event.target.value)}
              />
            </label>
            <label>
              Дата по
              <input
                type="date"
                value={historyDateTo}
                onChange={(event) => setHistoryDateTo(event.target.value)}
              />
            </label>
            <label>
              Тип действия
              <select
                value={historyActionType}
                onChange={(event) => setHistoryActionType(event.target.value)}
              >
                {ACTION_LOG_TYPE_OPTIONS.map((option) => (
                  <option key={option.value || 'all'} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Сущность
              <select
                value={historyEntityType}
                onChange={(event) => setHistoryEntityType(event.target.value)}
              >
                {ACTION_LOG_ENTITY_OPTIONS.map((option) => (
                  <option key={option.value || 'all'} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Результат
              <select
                value={historyResult}
                onChange={(event) => setHistoryResult(event.target.value)}
              >
                {ACTION_LOG_RESULT_OPTIONS.map((option) => (
                  <option key={option.value || 'all'} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Кто
              <input
                type="text"
                value={historyActor}
                placeholder="Например: Администратор"
                onChange={(event) => setHistoryActor(event.target.value)}
              />
            </label>
            <label>
              Объект
              <input
                type="text"
                value={historyTargetName}
                placeholder="Модель, отдел, кабинет"
                onChange={(event) => setHistoryTargetName(event.target.value)}
              />
            </label>
            <label>
              Быстрый поиск
              <input
                type="text"
                value={historySearchTerm}
                placeholder="Подробности, устройство, old/new"
                onChange={(event) => setHistorySearchTerm(event.target.value)}
              />
            </label>
          </div>
          <div className="table-shell">
            <table className="stock-table">
              <thead>
                <tr>
                  <th>Когда</th>
                  <th>Сущность</th>
                  <th>Результат</th>
                  <th>Действие</th>
                  <th>Объект</th>
                  <th>Подробности</th>
                  <th>Кто</th>
                  <th>Устройство</th>
                  <th>Изменения</th>
                </tr>
              </thead>
              <tbody>
                {paginatedActionLogs.map((entry) => (
                  <tr key={entry.id}>
                    <td>{entry.createdAt.replace('T', ' ').slice(0, 16)}</td>
                    <td>{formatActionEntityType(entry.entityType)}</td>
                    <td>{formatActionResult(entry.result)}</td>
                    <td>{formatActionType(entry.actionType)}</td>
                    <td>{entry.targetName}</td>
                    <td>
                      {entry.details || '-'}
                      {entry.manualDateTime && <div className="table-muted">Ручная дата/время</div>}
                    </td>
                    <td>{entry.actor || '-'}</td>
                    <td>{entry.deviceInfo || '-'}</td>
                    <td>
                      {entry.oldValues && <div><strong>Было:</strong> {entry.oldValues}</div>}
                      {entry.newValues && <div><strong>Стало:</strong> {entry.newValues}</div>}
                      {!entry.oldValues && !entry.newValues && '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {filteredActionLogs.length === 0 && <div className="empty-state">Записей по текущим фильтрам не найдено.</div>}
          </div>
          <div className="pagination">
            <span>
              Записей: {filteredActionLogs.length} · Страница {historyPage} / {historyTotalPages}
            </span>
            <div className="pagination-controls">
              <button onClick={() => setHistoryPage(1)} disabled={historyPage === 1}>
                {'<<'}
              </button>
              <button onClick={() => setHistoryPage((page) => Math.max(1, page - 1))} disabled={historyPage === 1}>
                {'<'}
              </button>
              <button
                onClick={() => setHistoryPage((page) => Math.min(historyTotalPages, page + 1))}
                disabled={historyPage === historyTotalPages}
              >
                {'>'}
              </button>
              <button onClick={() => setHistoryPage(historyTotalPages)} disabled={historyPage === historyTotalPages}>
                {'>>'}
              </button>
            </div>
          </div>
        </section>
      )}

      {activeTab === 'notifications' && (
        <section className="departments-layout">
          <section className="panel departments-main-panel">
            <div className="section-heading">
              <div>
                <h2>Уведомления по нехватке</h2>
                <p className="table-hint">Показываются позиции, где остаток достиг или пересек заданный порог.</p>
              </div>
            </div>
            <div className="filters">
              <label>
                Отдел
                <select
                  value={notificationAlertDepartmentFilter}
                  onChange={(e) => setNotificationAlertDepartmentFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))}
                >
                  <option value="all">Все отделы</option>
                  {userDepartments.map((department) => (
                    <option key={department.id} value={department.id}>
                      {department.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Источник
                <select value={notificationAlertSourceFilter} onChange={(e) => setNotificationAlertSourceFilter(e.target.value)}>
                  <option value="all">Все источники</option>
                  <option value="DEPARTMENT">Отдел</option>
                  <option value="MODEL_DEFAULT">Модель</option>
                  <option value="MODEL_MINIMUM">Минимум модели</option>
                </select>
              </label>
              <label>
                Поиск
                <input
                  value={notificationAlertSearch}
                  onChange={(e) => setNotificationAlertSearch(e.target.value)}
                  placeholder="Отдел или модель"
                />
              </label>
            </div>
            <div className="table-shell">
              <table className="stock-table">
                <thead>
                  <tr>
                    <th>Отдел</th>
                    <th>Модель</th>
                    <th>Текущий остаток</th>
                    <th>Порог</th>
                    <th>Источник порога</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedNotificationAlerts.map((alert) => (
                    <tr key={`${alert.departmentId}-${alert.cartridgeModelId}`}>
                      <td>{alert.departmentName}</td>
                      <td>{alert.cartridgeModelName}</td>
                      <td>{alert.currentQuantity}</td>
                      <td>{alert.thresholdQuantity}</td>
                      <td>
                        {alert.source === 'DEPARTMENT'
                          ? 'Отдел'
                          : alert.source === 'MODEL_DEFAULT'
                            ? 'Модель'
                            : 'Минимум модели'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {filteredNotificationAlerts.length === 0 && <div className="empty-state">Нехватки по текущим фильтрам не найдено.</div>}
            </div>
            <div className="pagination">
              <span>
                Уведомлений: {filteredNotificationAlerts.length} · Страница {notificationAlertPage} / {notificationAlertTotalPages}
              </span>
              <div className="pagination-controls">
                <button onClick={() => setNotificationAlertPage(1)} disabled={notificationAlertPage === 1}>
                  {'<<'}
                </button>
                <button
                  onClick={() => setNotificationAlertPage((page) => Math.max(1, page - 1))}
                  disabled={notificationAlertPage === 1}
                >
                  {'<'}
                </button>
                <button
                  onClick={() => setNotificationAlertPage((page) => Math.min(notificationAlertTotalPages, page + 1))}
                  disabled={notificationAlertPage === notificationAlertTotalPages}
                >
                  {'>'}
                </button>
                <button
                  onClick={() => setNotificationAlertPage(notificationAlertTotalPages)}
                  disabled={notificationAlertPage === notificationAlertTotalPages}
                >
                  {'>>'}
                </button>
              </div>
            </div>
          </section>

          <div className="departments-bottom">
            {canManageThresholds && (
              <aside className="side-card">
              <p className="eyebrow">Thresholds</p>
              <h3>Настройки порогов</h3>
              <div className="inline-filters">
                <label>
                  Отдел
                  <select
                    value={thresholdDepartmentFilter}
                    onChange={(e) => setThresholdDepartmentFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))}
                  >
                    <option value="all">Все отделы</option>
                    {userDepartments.map((department) => (
                      <option key={department.id} value={department.id}>
                        {department.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Статус
                  <select value={thresholdActiveFilter} onChange={(e) => setThresholdActiveFilter(e.target.value as 'all' | 'active' | 'inactive')}>
                    <option value="all">Все</option>
                    <option value="active">Активен</option>
                    <option value="inactive">Отключен</option>
                  </select>
                </label>
                <label>
                  Поиск
                  <input
                    value={thresholdSearchTerm}
                    onChange={(e) => setThresholdSearchTerm(e.target.value)}
                    placeholder="Модель, отдел, комментарий"
                  />
                </label>
              </div>
              <div className="department-mini-list">
                {paginatedThresholds.map((item) => (
                  <article key={item.id} className="department-mini-card">
                    <strong>{item.cartridgeModelName}</strong>
                    <p>Отдел: {item.departmentName || 'Все отделы'}</p>
                    <p>Порог: {item.minimumQuantity}</p>
                    <p>Статус: {item.active ? 'Активен' : 'Отключен'}</p>
                    <p>{item.comment || 'Без комментария'}</p>
                    <div className="table-actions">
                      <button className="ghost" onClick={() => beginThresholdEdit(item)}>Изменить</button>
                      <button
                        className="ghost danger-action"
                        onClick={() => onDeleteThreshold(item.id, `${item.cartridgeModelName} / ${item.departmentName || 'Все отделы'}`)}
                      >
                        Удалить
                      </button>
                    </div>
                  </article>
                ))}
                {filteredThresholds.length === 0 && <div className="empty-state">Порогов по текущим фильтрам не найдено.</div>}
              </div>
              <div className="pagination compact-pagination">
                <span>
                  Порогов: {filteredThresholds.length} · Страница {thresholdPage} / {thresholdTotalPages}
                </span>
                <div className="pagination-controls">
                  <button onClick={() => setThresholdPage(1)} disabled={thresholdPage === 1}>
                    {'<<'}
                  </button>
                  <button onClick={() => setThresholdPage((page) => Math.max(1, page - 1))} disabled={thresholdPage === 1}>
                    {'<'}
                  </button>
                  <button
                    onClick={() => setThresholdPage((page) => Math.min(thresholdTotalPages, page + 1))}
                    disabled={thresholdPage === thresholdTotalPages}
                  >
                    {'>'}
                  </button>
                  <button onClick={() => setThresholdPage(thresholdTotalPages)} disabled={thresholdPage === thresholdTotalPages}>
                    {'>>'}
                  </button>
                </div>
              </div>
              </aside>
            )}

            {canManageThresholds && (
              <aside className="side-card">
              <p className="eyebrow">Create Threshold</p>
              <h3>{editingThresholdId ? 'Изменить порог' : 'Добавить порог'}</h3>
              <form onSubmit={onSaveThreshold} className="form-card compact-form">
                <label>
                  Модель картриджа
                  <select value={thresholdModelId} onChange={(e) => setThresholdModelId(e.target.value ? Number(e.target.value) : '')}>
                    <option value="">Выберите...</option>
                    {models.map((model) => (
                      <option key={model.id} value={model.id}>
                        {model.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Отдел (опционально)
                  <select value={thresholdDepartmentId} onChange={(e) => setThresholdDepartmentId(e.target.value ? Number(e.target.value) : '')}>
                    <option value="">Все отделы</option>
                    {userDepartments.map((department) => (
                      <option key={department.id} value={department.id}>
                        {department.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Порог остатка
                  <input
                    type="number"
                    min={0}
                    value={thresholdMinimum}
                    onChange={(e) => setThresholdMinimum(Number(e.target.value))}
                  />
                </label>
                <label className="checkbox-line">
                  <input
                    type="checkbox"
                    checked={thresholdActive}
                    onChange={(e) => setThresholdActive(e.target.checked)}
                  />
                  <span>Порог активен</span>
                </label>
                <label>
                  Комментарий
                  <textarea value={thresholdComment} onChange={(e) => setThresholdComment(e.target.value)} />
                </label>
                <div className="table-actions">
                  <button type="submit">{editingThresholdId ? 'Сохранить порог' : 'Создать порог'}</button>
                  {editingThresholdId && (
                    <button type="button" className="ghost" onClick={resetThresholdForm}>
                      Отмена
                    </button>
                  )}
                </div>
              </form>
              </aside>
            )}
          </div>
        </section>
      )}

      {activeTab === 'reports' && canExportReports && (
        <section className="panel">
          <div className="section-heading">
            <div>
              <h2>Отчет по расходу картриджей</h2>
              <p className="table-hint">Просмотр на экране и выгрузка в Excel/PDF за выбранный период.</p>
            </div>
          </div>

          <div className="filters">
            <label>
              С
              <input type="date" value={reportDateFrom} onChange={(e) => setReportDateFrom(e.target.value)} />
            </label>
            <label>
              По
              <input type="date" value={reportDateTo} onChange={(e) => setReportDateTo(e.target.value)} />
            </label>
            <label>
              Поиск по расходу
              <input
                value={reportConsumptionSearchTerm}
                onChange={(e) => setReportConsumptionSearchTerm(e.target.value)}
                placeholder="Модель картриджа"
              />
            </label>
            <div className="table-actions">
              <button type="button" onClick={() => void loadConsumptionReport()}>Обновить</button>
              <button type="button" className="ghost" onClick={() => void onExportReportXlsx()}>Excel</button>
              <button type="button" className="ghost" onClick={() => void onExportReportPdf()}>PDF</button>
            </div>
          </div>

          <div className="table-shell">
            <table className="stock-table">
              <thead>
                <tr>
                  <th>Модель</th>
                  <th>Установлено</th>
                  <th>На заправку</th>
                  <th>Возврат</th>
                  <th>Списано</th>
                  <th>Всего операций</th>
                  <th>Всего шт</th>
                </tr>
              </thead>
              <tbody>
                {paginatedConsumptionRows.map((row) => (
                  <tr key={row.modelName}>
                    <td>{row.modelName}</td>
                    <td>{row.installedQuantity}</td>
                    <td>{row.sentToRefillQuantity}</td>
                    <td>{row.returnedFromRefillQuantity}</td>
                    <td>{row.writtenOffQuantity}</td>
                    <td>{row.totalOperations}</td>
                    <td>{row.totalQuantity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {filteredConsumptionRows.length === 0 && (
              <div className="empty-state">За выбранный период и по текущему поиску данных не найдено.</div>
            )}
          </div>
          <div className="pagination">
            <span>
              Строк: {filteredConsumptionRows.length} · Страница {reportConsumptionPage} / {reportConsumptionTotalPages}
            </span>
            <div className="pagination-controls">
              <button onClick={() => setReportConsumptionPage(1)} disabled={reportConsumptionPage === 1}>
                {'<<'}
              </button>
              <button
                onClick={() => setReportConsumptionPage((page) => Math.max(1, page - 1))}
                disabled={reportConsumptionPage === 1}
              >
                {'<'}
              </button>
              <button
                onClick={() => setReportConsumptionPage((page) => Math.min(reportConsumptionTotalPages, page + 1))}
                disabled={reportConsumptionPage === reportConsumptionTotalPages}
              >
                {'>'}
              </button>
              <button
                onClick={() => setReportConsumptionPage(reportConsumptionTotalPages)}
                disabled={reportConsumptionPage === reportConsumptionTotalPages}
              >
                {'>>'}
              </button>
            </div>
          </div>

          {consumptionReport && (
            <div className="status-line">
              Итого: {consumptionReport.totalOperations} операций, {consumptionReport.totalQuantity} шт. Период: {consumptionReport.dateFrom} - {consumptionReport.dateTo}
            </div>
          )}

          {stockSnapshotReport && (
            <>
              <div className="filters">
                <span className="status-badge status-ready">На складе: {stockSnapshotReport.totalInStock}</span>
                <span className="status-badge status-reserve">Резерв: {stockSnapshotReport.totalReserve}</span>
                <span className="status-badge status-on_refill">На заправке: {stockSnapshotReport.totalOnRefill}</span>
                <span className="status-badge status-installed">Установлено: {stockSnapshotReport.totalInstalled}</span>
                <span className="status-badge status-written_off">Списано: {stockSnapshotReport.totalWrittenOff}</span>
                <div className="table-actions">
                  <button type="button" className="ghost" onClick={() => void onExportStockSnapshotXlsx()}>Остатки Excel</button>
                  <button type="button" className="ghost" onClick={() => void onExportStockSnapshotPdf()}>Остатки PDF</button>
                </div>
              </div>
              <section className="form-card">
                <h3>{reportSummaryDataset.title}</h3>
                <p className="form-help">Выберите нужный разрез сводного отчёта. Для текущего набора доступен поиск и пагинация.</p>
                <div className="batch-selector-grid">
                  {[
                    { value: 'department' as ReportSummaryView, label: 'По отделам', meta: `${stockSnapshotReport.byDepartment.length} строк` },
                    { value: 'model' as ReportSummaryView, label: 'По моделям', meta: `${stockSnapshotReport.byModel.length} строк` },
                    { value: 'room' as ReportSummaryView, label: 'По кабинетам', meta: `${stockSnapshotReport.byRoom.length} строк` },
                    { value: 'type' as ReportSummaryView, label: 'По типам', meta: `${stockSnapshotReport.byType.length} строк` },
                    { value: 'printer' as ReportSummaryView, label: 'По принтерам', meta: `${stockSnapshotReport.byPrinterModel.length} строк` },
                  ].map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={`batch-model-chip ${reportSummaryView === option.value ? 'active' : ''}`}
                      onClick={() => setReportSummaryView(option.value)}
                    >
                      <span>{option.label}</span>
                      <small>{option.meta}</small>
                    </button>
                  ))}
                </div>
                <div className="filters">
                  <label>
                    Поиск по сводке
                    <input
                      value={reportSummarySearchTerm}
                      onChange={(e) => setReportSummarySearchTerm(e.target.value)}
                      placeholder="Название строки"
                    />
                  </label>
                </div>
                <div className="table-shell">
                  <table className="stock-table">
                    <thead>
                      <tr>
                        {reportSummaryDataset.headers.map((header) => (
                          <th key={header}>{header}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {paginatedReportSummaryRows.map((row) => (
                        <tr key={row.key}>
                          {row.cells.map((cell, index) => (
                            <td key={`${row.key}-${index}`}>{cell}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {filteredReportSummaryRows.length === 0 && <div className="empty-state">По текущему поиску строк не найдено.</div>}
                </div>
                <div className="pagination">
                  <span>
                    Строк: {filteredReportSummaryRows.length} · Страница {reportSummaryPage} / {reportSummaryTotalPages}
                  </span>
                  <div className="pagination-controls">
                    <button onClick={() => setReportSummaryPage(1)} disabled={reportSummaryPage === 1}>
                      {'<<'}
                    </button>
                    <button onClick={() => setReportSummaryPage((page) => Math.max(1, page - 1))} disabled={reportSummaryPage === 1}>
                      {'<'}
                    </button>
                    <button
                      onClick={() => setReportSummaryPage((page) => Math.min(reportSummaryTotalPages, page + 1))}
                      disabled={reportSummaryPage === reportSummaryTotalPages}
                    >
                      {'>'}
                    </button>
                    <button onClick={() => setReportSummaryPage(reportSummaryTotalPages)} disabled={reportSummaryPage === reportSummaryTotalPages}>
                      {'>>'}
                    </button>
                  </div>
                </div>
              </section>

              <section className="form-card">
                <h3>{reportItemsDataset.title}</h3>
                <p className="form-help">Здесь можно быстро просмотреть детальные позиции по выбранному состоянию остатков.</p>
                <div className="batch-selector-grid">
                  {[
                    { value: 'stock' as ReportItemsView, label: 'На складе', meta: `${stockSnapshotReport.inStockItems.length} позиций` },
                    { value: 'reserve' as ReportItemsView, label: 'Резерв', meta: `${stockSnapshotReport.reserveItems.length} позиций` },
                    { value: 'refill' as ReportItemsView, label: 'На заправке', meta: `${stockSnapshotReport.onRefillItems.length} позиций` },
                    { value: 'writtenOff' as ReportItemsView, label: 'Списано', meta: `${stockSnapshotReport.writtenOffItems.length} позиций` },
                  ].map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={`batch-model-chip ${reportItemsView === option.value ? 'active' : ''}`}
                      onClick={() => setReportItemsView(option.value)}
                    >
                      <span>{option.label}</span>
                      <small>{option.meta}</small>
                    </button>
                  ))}
                </div>
                <div className="filters">
                  <label>
                    Поиск по позициям
                    <input
                      value={reportItemsSearchTerm}
                      onChange={(e) => setReportItemsSearchTerm(e.target.value)}
                      placeholder="Код, модель, отдел, кабинет"
                    />
                  </label>
                </div>
                <div className="table-shell">
                  <table className="stock-table">
                    <thead>
                      <tr>
                        <th>Код</th>
                        <th>Модель</th>
                        <th>Отдел</th>
                        <th>Кабинет</th>
                        <th>Количество</th>
                        <th>Тип</th>
                      </tr>
                    </thead>
                    <tbody>
                      {paginatedReportItemRows.map((row) => (
                        <tr key={row.key}>
                          {row.cells.map((cell, index) => (
                            <td key={`${row.key}-${index}`}>{cell}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {filteredReportItemRows.length === 0 && <div className="empty-state">По текущему поиску позиций не найдено.</div>}
                </div>
                <div className="pagination">
                  <span>
                    Позиций: {filteredReportItemRows.length} · Страница {reportItemsPage} / {reportItemsTotalPages}
                  </span>
                  <div className="pagination-controls">
                    <button onClick={() => setReportItemsPage(1)} disabled={reportItemsPage === 1}>
                      {'<<'}
                    </button>
                    <button onClick={() => setReportItemsPage((page) => Math.max(1, page - 1))} disabled={reportItemsPage === 1}>
                      {'<'}
                    </button>
                    <button
                      onClick={() => setReportItemsPage((page) => Math.min(reportItemsTotalPages, page + 1))}
                      disabled={reportItemsPage === reportItemsTotalPages}
                    >
                      {'>'}
                    </button>
                    <button onClick={() => setReportItemsPage(reportItemsTotalPages)} disabled={reportItemsPage === reportItemsTotalPages}>
                      {'>>'}
                    </button>
                  </div>
                </div>
              </section>
            </>
          )}
        </section>
      )}

      {activeTab === 'users' && canManageUsers && (
        <section className="departments-layout">
          <section className="panel departments-main-panel">
            <div className="section-heading">
              <div>
                <h2>Пользователи системы</h2>
                <p className="table-hint">Управление логинами, ролями и активностью учетных записей.</p>
              </div>
            </div>
            <div className="filters">
              <label>
                Роль
                <select value={userRoleFilter} onChange={(e) => setUserRoleFilter(e.target.value as UserRole | 'all')}>
                  <option value="all">Все роли</option>
                  {USER_ROLE_OPTIONS.map((roleOption) => (
                    <option key={roleOption.value} value={roleOption.value}>
                      {roleOption.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Статус
                <select value={userActiveFilter} onChange={(e) => setUserActiveFilter(e.target.value as 'all' | 'active' | 'inactive')}>
                  <option value="all">Все</option>
                  <option value="active">Активен</option>
                  <option value="inactive">Отключен</option>
                </select>
              </label>
              <label>
                Поиск
                <input
                  value={userSearchTerm}
                  onChange={(e) => setUserSearchTerm(e.target.value)}
                  placeholder="Логин или ФИО"
                />
              </label>
            </div>
            <div className="table-shell">
              <table className="stock-table">
                <thead>
                  <tr>
                    <th>Логин</th>
                    <th>ФИО</th>
                    <th>Роль</th>
                    <th>Права</th>
                    <th>Статус</th>
                    <th>Действия</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedUsers.map((user) => (
                    <tr key={user.id}>
                      <td>{user.username}</td>
                      <td>{user.fullName}</td>
                      <td>{user.role}</td>
                      <td>
                        <div className="replacement-point-state">
                          {user.permissions.canViewCatalog && <span>Каталог</span>}
                          {user.permissions.canEditCatalog && <span>Редактирование</span>}
                          {user.permissions.canOperate && <span>Операции</span>}
                          {user.permissions.canViewLogs && <span>Логи</span>}
                          {user.permissions.canExportReports && <span>Отчеты</span>}
                          {user.permissions.canManageThresholds && <span>Пороги</span>}
                          {user.permissions.canManageUsers && <span>Пользователи</span>}
                        </div>
                      </td>
                      <td>{user.active ? 'Активен' : 'Отключен'}</td>
                      <td>
                        <button type="button" className="ghost" onClick={() => beginUserEdit(user)}>
                          Изменить
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {filteredUsers.length === 0 && <div className="empty-state">Пользователей по текущим фильтрам не найдено.</div>}
            </div>
            <div className="pagination">
              <span>
                Пользователей: {filteredUsers.length} · Страница {userPage} / {userTotalPages}
              </span>
              <div className="pagination-controls">
                <button onClick={() => setUserPage(1)} disabled={userPage === 1}>
                  {'<<'}
                </button>
                <button onClick={() => setUserPage((page) => Math.max(1, page - 1))} disabled={userPage === 1}>
                  {'<'}
                </button>
                <button
                  onClick={() => setUserPage((page) => Math.min(userTotalPages, page + 1))}
                  disabled={userPage === userTotalPages}
                >
                  {'>'}
                </button>
                <button onClick={() => setUserPage(userTotalPages)} disabled={userPage === userTotalPages}>
                  {'>>'}
                </button>
              </div>
            </div>
          </section>

          <aside className="side-card">
            <p className="eyebrow">User Form</p>
            <h3>{editingUserId ? 'Изменить пользователя' : 'Новый пользователь'}</h3>
            <form className="form-card compact-form" onSubmit={onSaveUser}>
              <label>
                Логин
                <input value={userLogin} onChange={(e) => setUserLogin(e.target.value)} />
              </label>
              <label>
                ФИО
                <input value={userFullName} onChange={(e) => setUserFullName(e.target.value)} />
              </label>
              <label>
                Роль-шаблон
                <select value={userRole} onChange={(e) => applyRolePreset(e.target.value as UserRole)}>
                  {USER_ROLE_OPTIONS.map((roleOption) => (
                    <option key={roleOption.value} value={roleOption.value}>
                      {roleOption.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="checkbox-line">
                <input
                  type="checkbox"
                  checked={userActive}
                  onChange={(e) => setUserActive(e.target.checked)}
                />
                <span>Пользователь активен</span>
              </label>
              <div className="side-card">
                <p className="eyebrow">Permissions</p>
                <h3>Индивидуальные права</h3>
                <p className="table-muted">Роль выше только подставляет шаблон. Ниже можно вручную настроить доступы под конкретного пользователя.</p>

                <div className="department-mini-list">
                  <article className="department-mini-card">
                    <strong>Каталог</strong>
                    <label className="checkbox-line">
                      <input
                        type="checkbox"
                        checked={userPermissions.canViewCatalog}
                        onChange={(e) => updateUserPermission('canViewCatalog', e.target.checked)}
                      />
                      <span>Просмотр справочников и списков</span>
                    </label>
                    <label className="checkbox-line">
                      <input
                        type="checkbox"
                        checked={userPermissions.canEditCatalog}
                        onChange={(e) => updateUserPermission('canEditCatalog', e.target.checked)}
                      />
                      <span>Редактирование справочников</span>
                    </label>
                  </article>

                  <article className="department-mini-card">
                    <strong>Операции</strong>
                    <label className="checkbox-line">
                      <input
                        type="checkbox"
                        checked={userPermissions.canOperate}
                        onChange={(e) => updateUserPermission('canOperate', e.target.checked)}
                      />
                      <span>Проведение операций</span>
                    </label>
                    <label className="checkbox-line">
                      <input
                        type="checkbox"
                        checked={userPermissions.canManualDatetime}
                        onChange={(e) => updateUserPermission('canManualDatetime', e.target.checked)}
                      />
                      <span>Ручной ввод даты и времени</span>
                    </label>
                  </article>

                  <article className="department-mini-card">
                    <strong>Аналитика</strong>
                    <label className="checkbox-line">
                      <input
                        type="checkbox"
                        checked={userPermissions.canViewLogs}
                        onChange={(e) => updateUserPermission('canViewLogs', e.target.checked)}
                      />
                      <span>Просмотр журналов и аудита</span>
                    </label>
                    <label className="checkbox-line">
                      <input
                        type="checkbox"
                        checked={userPermissions.canExportReports}
                        onChange={(e) => updateUserPermission('canExportReports', e.target.checked)}
                      />
                      <span>Просмотр и экспорт отчетов</span>
                    </label>
                  </article>

                  <article className="department-mini-card">
                    <strong>Администрирование</strong>
                    <label className="checkbox-line">
                      <input
                        type="checkbox"
                        checked={userPermissions.canManageThresholds}
                        onChange={(e) => updateUserPermission('canManageThresholds', e.target.checked)}
                      />
                      <span>Настройка уведомлений и порогов</span>
                    </label>
                    <label className="checkbox-line">
                      <input
                        type="checkbox"
                        checked={userPermissions.canManageUsers}
                        onChange={(e) => updateUserPermission('canManageUsers', e.target.checked)}
                      />
                      <span>Управление пользователями</span>
                    </label>
                  </article>

                  <article className="department-mini-card">
                    <strong>Следующие модули</strong>
                    <p className="table-muted">Инвентаризация и заявки по залам позже можно расширить отдельными флагами, не ломая текущую модель прав.</p>
                  </article>
                </div>
              </div>
              <label>
                {editingUserId ? 'Новый пароль (опционально)' : 'Пароль'}
                <input
                  type="password"
                  value={userPassword}
                  onChange={(e) => setUserPassword(e.target.value)}
                  placeholder={editingUserId ? 'Оставьте пустым, чтобы не менять' : 'Введите пароль'}
                />
              </label>
              <div className="table-actions">
                <button type="submit">{editingUserId ? 'Сохранить' : 'Создать'}</button>
                {editingUserId && (
                  <button type="button" className="ghost" onClick={resetUserForm}>
                    Отмена
                  </button>
                )}
              </div>
            </form>
          </aside>
        </section>
      )}

      {activeTab === 'create' && canOperate && (
        <section className="panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Catalog & Stock</p>
              <h2>Модели и приход картриджей</h2>
              <p className="table-hint">
                Во вкладке <strong>Отделы</strong> настраиваются отделы, точки замены и нужные картриджи.
                Здесь создаются только модели и фактический остаток картриджей на учёте.
              </p>
            </div>
          </div>
          <div className="forms create-forms-grid">
            <form onSubmit={onCreateModel} className="form-card create-model-card">
              <h3>Новая модель картриджа</h3>
              <label>
                Название картриджа
                <input
                  value={modelName}
                  onChange={(e) => setModelName(e.target.value)}
                  placeholder="Например: TK-1170"
                />
              </label>
              <label>
                Тип модели
                <select
                  value={modelRefillable ? 'REFILLABLE' : 'DISPOSABLE'}
                  onChange={(e) => setModelRefillable(e.target.value === 'REFILLABLE')}
                >
                  <option value="REFILLABLE">Заправляемый</option>
                  <option value="DISPOSABLE">Незаправляемый</option>
                </select>
              </label>
              <label>
                Минимальный остаток
                <input
                  type="number"
                  min={0}
                  value={modelMinimumQuantity}
                  onChange={(e) => setModelMinimumQuantity(Number(e.target.value))}
                />
              </label>
              <label>
                Совместимые модели принтеров
                <textarea
                  rows={4}
                  value={modelCompatiblePrintersText}
                  onChange={(e) => setModelCompatiblePrintersText(e.target.value)}
                  placeholder={'HP LaserJet Pro M404\nHP LaserJet Pro M428'}
                />
              </label>
              <p className="form-help">
                Укажите по одной модели принтера на строку. Это помогает хранить совместимость и потом расширять подбор расходников без переделки структуры.
              </p>
              <button type="submit">Создать модель</button>
            </form>

            <div className="create-stock-layout">
              <form onSubmit={onCreateCartridge} className="form-card">
                <h3>Пакетное пополнение остатка</h3>
                <p className="form-help">
                  Выберите несколько моделей. Для каждой появится одинаковая карточка пополнения, между ними можно переключаться.
                </p>
                <div className="batch-selector-grid">
                  {models.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      className={`batch-model-chip ${selectedBatchModelIds.includes(item.id) ? 'active' : ''}`}
                      onClick={() => toggleBatchModel(item.id)}
                    >
                      <span>{item.name}</span>
                      <small>{item.refillable ? 'Заправляемый' : 'Незаправляемый'}</small>
                    </button>
                  ))}
                </div>

                {activeBatchModel ? (
                  <div className="batch-carousel">
                    <div className="batch-carousel-header">
                      <button
                        type="button"
                        className="ghost"
                        onClick={() => setActiveBatchIndex((index) => Math.max(0, index - 1))}
                        disabled={activeBatchIndex === 0}
                      >
                        {'<'}
                      </button>
                      <div>
                        <strong>{activeBatchModel.name}</strong>
                        <p className="table-muted">
                          {activeBatchIndex + 1} из {selectedBatchModels.length} · {activeBatchModel.refillable ? 'Заправляемый' : 'Незаправляемый'} · минимум {activeBatchModel.minimumQuantity}
                        </p>
                      </div>
                      <button
                        type="button"
                        className="ghost"
                        onClick={() => setActiveBatchIndex((index) => Math.min(selectedBatchModels.length - 1, index + 1))}
                        disabled={activeBatchIndex === selectedBatchModels.length - 1}
                      >
                        {'>'}
                      </button>
                    </div>
                    <label>
                      Куда положить
                      <select
                        value={batchTargetStatus}
                        onChange={(e) => setBatchTargetStatus(e.target.value as CartridgeStatus)}
                      >
                        <option value="IN_STOCK">На склад</option>
                        <option value="RESERVE">В резерв</option>
                      </select>
                    </label>
                    <label>
                      Количество
                      <input
                        type="number"
                        min={0}
                        value={activeBatchEntry?.quantity ?? 1}
                        onChange={(e) => updateBatchEntry(activeBatchModel.id, { quantity: Number(e.target.value) })}
                      />
                    </label>
                    <label>
                      Комментарий
                      <textarea
                        value={activeBatchEntry?.comment ?? ''}
                        onChange={(e) => updateBatchEntry(activeBatchModel.id, { comment: e.target.value })}
                      />
                    </label>
                  </div>
                ) : (
                  <div className="empty-state">Выберите одну или несколько моделей, чтобы сформировать партию пополнения.</div>
                )}

                <button type="submit" disabled={selectedBatchModels.length === 0}>Добавить выбранные модели в остаток</button>
              </form>

              <section className="form-card">
                <h3>Модели картриджей</h3>
                <p className="form-help">
                  Здесь можно менять минимальный остаток и тип модели. Удаление доступно только если модель не используется.
                </p>
                <div className="inline-filters">
                  <label>
                    Тип
                    <select
                      value={modelCatalogTypeFilter}
                      onChange={(e) => setModelCatalogTypeFilter(e.target.value as ModelCatalogTypeFilter)}
                    >
                      <option value="all">Все</option>
                      <option value="refillable">Заправляемые</option>
                      <option value="disposable">Незаправляемые</option>
                    </select>
                  </label>
                  <label>
                    Баланс
                    <select
                      value={modelCatalogBalanceFilter}
                      onChange={(e) => setModelCatalogBalanceFilter(e.target.value as ModelCatalogBalanceFilter)}
                    >
                      <option value="all">Любой</option>
                      <option value="deficit">Дефицит</option>
                      <option value="minimum">Ровно по минимуму</option>
                      <option value="surplus">Излишек</option>
                    </select>
                  </label>
                  <label>
                    Поиск
                    <input
                      value={modelCatalogSearchTerm}
                      onChange={(e) => setModelCatalogSearchTerm(e.target.value)}
                      placeholder="Модель или совместимость"
                    />
                  </label>
                </div>
                <div className="model-catalog-grid">
                  {filteredModelCatalogItems.length === 0 && <div className="empty-state">Моделей по текущим фильтрам не найдено.</div>}
                  {paginatedModelCatalogItems.map((item) => (
                    <article key={item.id} className="model-catalog-card">
                      <div className="model-catalog-head">
                        <div>
                          <strong>{item.name}</strong>
                          <p className="table-muted">
                            Готово {modelSummaryById[item.id]?.ready ?? 0} · Пустых {modelSummaryById[item.id]?.empty ?? 0} · Мин. {item.minimumQuantity}
                          </p>
                        </div>
                        <span className={`status-badge ${item.refillable ? 'status-ready' : 'status-disposable'}`}>
                          {item.refillable ? 'Заправляемый' : 'Одноразовый'}
                        </span>
                      </div>
                      <div className="model-edit-grid">
                        <label>
                          Название
                          <input value={item.name} onChange={(e) => onChangeModelField(item.id, { name: e.target.value })} />
                        </label>
                        <label>
                          Тип модели
                          <select
                            value={item.refillable ? 'REFILLABLE' : 'DISPOSABLE'}
                            onChange={(e) => onChangeModelField(item.id, { refillable: e.target.value === 'REFILLABLE' })}
                          >
                            <option value="REFILLABLE">Заправляемый</option>
                            <option value="DISPOSABLE">Незаправляемый</option>
                          </select>
                        </label>
                        <label>
                          Минимальный остаток
                          <input
                            type="number"
                            min={0}
                            value={item.minimumQuantity}
                            onChange={(e) => onChangeModelField(item.id, { minimumQuantity: Number(e.target.value) })}
                          />
                        </label>
                        <label>
                          Совместимые модели принтеров
                          <textarea
                            rows={4}
                            value={modelCompatibilityDrafts[item.id] ?? formatCompatiblePrinterModels(item.compatiblePrinterModels)}
                            onChange={(e) => {
                              const nextValue = e.target.value
                              setModelCompatibilityDrafts((current) => ({
                                ...current,
                                [item.id]: nextValue,
                              }))
                              onChangeModelField(item.id, { compatiblePrinterModels: parseCompatiblePrinterModels(nextValue) })
                            }}
                            placeholder={'HP LaserJet Pro M404\nHP LaserJet Pro M428'}
                          />
                        </label>
                      </div>
                      <div className="model-catalog-footer">
                        <div>
                          <span className={balanceTone(modelSummaryById[item.id]?.balance ?? 0)}>
                            {(modelSummaryById[item.id]?.balance ?? 0) < 0
                              ? `Не хватает ${Math.abs(modelSummaryById[item.id]?.balance ?? 0)} шт.`
                              : (modelSummaryById[item.id]?.balance ?? 0) === 0
                                ? 'Ровно по минимуму'
                                : `Излишек ${modelSummaryById[item.id]?.balance ?? 0} шт.`}
                          </span>
                          <p className="table-muted">
                            {item.compatiblePrinterModels.length > 0
                              ? `Совместимо: ${item.compatiblePrinterModels.join(', ')}`
                              : 'Совместимые модели принтеров пока не указаны.'}
                          </p>
                        </div>
                      </div>
                      <div className="table-actions model-catalog-actions">
                        <button type="button" onClick={() => onSaveModelSettings(item)}>
                          Сохранить
                        </button>
                        <button type="button" className="ghost danger-action" onClick={() => onDeleteCartridgeModel(item.id, item.name)}>
                          Удалить
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
                <div className="pagination compact-pagination">
                  <span>
                    Моделей: {filteredModelCatalogItems.length} · Страница {modelCatalogPage} / {modelCatalogTotalPages}
                  </span>
                  <div className="pagination-controls">
                    <button onClick={() => setModelCatalogPage(1)} disabled={modelCatalogPage === 1}>
                      {'<<'}
                    </button>
                    <button onClick={() => setModelCatalogPage((page) => Math.max(1, page - 1))} disabled={modelCatalogPage === 1}>
                      {'<'}
                    </button>
                    <button
                      onClick={() => setModelCatalogPage((page) => Math.min(modelCatalogTotalPages, page + 1))}
                      disabled={modelCatalogPage === modelCatalogTotalPages}
                    >
                      {'>'}
                    </button>
                    <button onClick={() => setModelCatalogPage(modelCatalogTotalPages)} disabled={modelCatalogPage === modelCatalogTotalPages}>
                      {'>>'}
                    </button>
                  </div>
                </div>
              </section>
            </div>

            <div className="create-summary-grid">
              <section className="form-card">
                <h3>Заправляемые модели</h3>
                <div className="department-mini-list">
                  {refillableModelSummary.length === 0 && <div className="empty-state">Таких моделей пока нет.</div>}
                  {refillableModelSummary.map((item) => (
                    <article key={item.id} className="department-mini-card">
                      <strong>{item.name}</strong>
                      <p>Готово: {item.ready} · Пустых: {item.empty} · На заправке: {item.onRefill}</p>
                      <p>Минимум: {item.minimumQuantity} · Точек замены: {item.assignedPoints}</p>
                      <p>{item.compatiblePrinterModels.length > 0 ? `Совместимо: ${item.compatiblePrinterModels.join(', ')}` : 'Совместимость не заполнена'}</p>
                      <span className={balanceTone(item.balance)}>
                        {item.balance < 0 ? `Не хватает ${Math.abs(item.balance)} шт.` : item.balance === 0 ? 'Ровно по минимуму' : `Излишек ${item.balance} шт.`}
                      </span>
                    </article>
                  ))}
                </div>
              </section>

              <section className="form-card">
                <h3>Незаправляемые модели</h3>
                <div className="department-mini-list">
                  {disposableModelSummary.length === 0 && <div className="empty-state">Таких моделей пока нет.</div>}
                  {disposableModelSummary.map((item) => (
                    <article key={item.id} className="department-mini-card">
                      <strong>{item.name}</strong>
                      <p>Готово: {item.ready} · Пустых: {item.empty} · На заправке: {item.onRefill}</p>
                      <p>Минимум: {item.minimumQuantity} · Точек замены: {item.assignedPoints}</p>
                      <p>{item.compatiblePrinterModels.length > 0 ? `Совместимо: ${item.compatiblePrinterModels.join(', ')}` : 'Совместимость не заполнена'}</p>
                      <span className={balanceTone(item.balance)}>
                        {item.balance < 0 ? `Не хватает ${Math.abs(item.balance)} шт.` : item.balance === 0 ? 'Ровно по минимуму' : `Излишек ${item.balance} шт.`}
                      </span>
                    </article>
                  ))}
                </div>
              </section>
            </div>
          </div>
        </section>
      )}

      {activeTab === 'inventory' && (
        <section className="departments-layout">
          <section className="panel departments-main-panel">
            <div className="section-heading">
              <div>
                <h2>Инвентаризация</h2>
                <p className="table-hint">Учет активов отеля по отделам и кабинетам.</p>
              </div>
            </div>
            <div className="filters">
              <label>
                Отдел
                <select
                  value={assetFilterDepartmentId}
                  onChange={(e) => setAssetFilterDepartmentId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">Все отделы</option>
                  {userDepartments.map((department) => (
                    <option key={department.id} value={department.id}>
                      {department.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Кабинет
                <select
                  value={assetFilterRoomId}
                  onChange={(e) => setAssetFilterRoomId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">Все кабинеты</option>
                  {activeRooms.map((room) => (
                    <option key={room.id} value={room.id}>
                      {room.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Статус
                <select value={assetFilterStatus} onChange={(e) => setAssetFilterStatus(e.target.value as InventoryAssetStatus | '')}>
                  <option value="">Все статусы</option>
                  {INVENTORY_STATUS_OPTIONS.map((statusOption) => (
                    <option key={statusOption.value} value={statusOption.value}>
                      {statusOption.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Поиск
                <input
                  value={assetSearchTerm}
                  onChange={(e) => setAssetSearchTerm(e.target.value)}
                  placeholder="Инв. номер, актив, категория"
                />
              </label>
            </div>
            <div className="table-shell">
              <table className="stock-table">
                <thead>
                  <tr>
                    <th>Инв. номер</th>
                    <th>Наименование</th>
                    <th>Категория</th>
                    <th>Отдел</th>
                    <th>Кабинет</th>
                    <th>Статус</th>
                    <th>Кол-во</th>
                    <th>Действия</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedInventoryAssets.map((asset) => (
                    <tr key={asset.id}>
                      <td>{asset.inventoryCode}</td>
                      <td>{asset.name}</td>
                      <td>{asset.category || '-'}</td>
                      <td>{asset.departmentName || '-'}</td>
                      <td>{asset.roomName || '-'}</td>
                      <td>{INVENTORY_STATUS_OPTIONS.find((item) => item.value === asset.status)?.label || asset.status}</td>
                      <td>{asset.quantity}</td>
                      <td>
                        <div className="table-actions">
                          <button className="ghost" type="button" onClick={() => beginAssetEdit(asset)}>
                            Изменить
                          </button>
                          <button className="ghost" type="button" onClick={() => beginAssetTransfer(asset)}>
                            Переместить
                          </button>
                          <button className="ghost danger-action" type="button" onClick={() => onDeleteInventoryAsset(asset.id, asset.name)}>
                            Удалить
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {filteredInventoryAssets.length === 0 && <div className="empty-state">Активов по текущим фильтрам не найдено.</div>}
            </div>
            <div className="pagination">
              <span>
                Активов: {filteredInventoryAssets.length} · Страница {assetPage} / {assetTotalPages}
              </span>
              <div className="pagination-controls">
                <button onClick={() => setAssetPage(1)} disabled={assetPage === 1}>
                  {'<<'}
                </button>
                <button onClick={() => setAssetPage((page) => Math.max(1, page - 1))} disabled={assetPage === 1}>
                  {'<'}
                </button>
                <button
                  onClick={() => setAssetPage((page) => Math.min(assetTotalPages, page + 1))}
                  disabled={assetPage === assetTotalPages}
                >
                  {'>'}
                </button>
                <button onClick={() => setAssetPage(assetTotalPages)} disabled={assetPage === assetTotalPages}>
                  {'>>'}
                </button>
              </div>
            </div>
          </section>
          <aside className="side-card">
            <p className="eyebrow">Inventory Form</p>
            <h3>{editingAssetId ? 'Изменить актив' : 'Добавить актив'}</h3>
            <form className="form-card compact-form" onSubmit={onSaveInventoryAsset}>
              <label>
                Инвентарный номер
                <input value={assetInventoryCode} onChange={(e) => setAssetInventoryCode(e.target.value)} />
              </label>
              <label>
                Наименование
                <input value={assetName} onChange={(e) => setAssetName(e.target.value)} />
              </label>
              <label>
                Категория
                <input value={assetCategory} onChange={(e) => setAssetCategory(e.target.value)} />
              </label>
              <label>
                Отдел
                <select value={assetDepartmentId} onChange={(e) => setAssetDepartmentId(e.target.value ? Number(e.target.value) : '')}>
                  <option value="">Не указан</option>
                  {userDepartments.map((department) => (
                    <option key={department.id} value={department.id}>
                      {department.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Кабинет
                <select value={assetRoomId} onChange={(e) => setAssetRoomId(e.target.value ? Number(e.target.value) : '')}>
                  <option value="">Не указан</option>
                  {(assetDepartmentId ? availableRoomsForAsset : activeRooms).map((room) => (
                    <option key={room.id} value={room.id}>
                      {room.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Статус
                <select value={assetStatus} onChange={(e) => setAssetStatus(e.target.value as InventoryAssetStatus)}>
                  {INVENTORY_STATUS_OPTIONS.map((statusOption) => (
                    <option key={statusOption.value} value={statusOption.value}>
                      {statusOption.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Количество
                <input type="number" min={0} value={assetQuantity} onChange={(e) => setAssetQuantity(Number(e.target.value))} />
              </label>
              <label>
                Комментарий
                <textarea value={assetComment} onChange={(e) => setAssetComment(e.target.value)} />
              </label>
              <div className="table-actions">
                <button type="submit">{editingAssetId ? 'Сохранить актив' : 'Создать актив'}</button>
                {editingAssetId && (
                  <button type="button" className="ghost" onClick={resetAssetForm}>
                    Отмена
                  </button>
                )}
              </div>
            </form>

            <form className="form-card compact-form" onSubmit={onTransferInventoryAsset}>
              <h3>Перемещение актива</h3>
              <label>
                Актив
                <select value={transferAssetId} onChange={(e) => setTransferAssetId(e.target.value ? Number(e.target.value) : '')}>
                  <option value="">Выберите актив</option>
                  {inventoryAssets.map((asset) => (
                    <option key={asset.id} value={asset.id}>
                      {asset.inventoryCode} · {asset.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Отдел назначения
                <select
                  value={transferDepartmentId}
                  onChange={(e) => setTransferDepartmentId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">Без отдела</option>
                  {userDepartments.map((department) => (
                    <option key={department.id} value={department.id}>
                      {department.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Кабинет назначения
                <select value={transferRoomId} onChange={(e) => setTransferRoomId(e.target.value ? Number(e.target.value) : '')}>
                  <option value="">Без кабинета</option>
                  {(transferDepartmentId ? availableRoomsForTransfer : activeRooms).map((room) => (
                    <option key={room.id} value={room.id}>
                      {room.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Дата/время операции (опционально)
                <input type="datetime-local" value={transferMovedAt} onChange={(e) => setTransferMovedAt(e.target.value)} />
              </label>
              <label>
                Исполнитель
                <input value={transferActor} onChange={(e) => setTransferActor(e.target.value)} />
              </label>
              <label>
                Комментарий
                <textarea value={transferComment} onChange={(e) => setTransferComment(e.target.value)} />
              </label>
              <div className="table-actions">
                <button type="submit">Переместить актив</button>
                <button type="button" className="ghost" onClick={resetTransferForm}>
                  Очистить
                </button>
              </div>
            </form>

            <section className="form-card">
              <h3>История перемещений</h3>
              <label>
                Фильтр по активу
                <select
                  value={assetMovementFilterAssetId}
                  onChange={(e) => setAssetMovementFilterAssetId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">Все активы</option>
                  {inventoryAssets.map((asset) => (
                    <option key={asset.id} value={asset.id}>
                      {asset.inventoryCode} · {asset.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Поиск по истории
                <input
                  value={movementSearchTerm}
                  onChange={(e) => setMovementSearchTerm(e.target.value)}
                  placeholder="Актив, маршрут, исполнитель"
                />
              </label>
              <div className="department-mini-list">
                {paginatedInventoryMovements.map((movement) => (
                  <article key={movement.id} className="department-mini-card">
                    <strong>{movement.assetInventoryCode} · {movement.assetName}</strong>
                    <p>{formatLocation(movement.fromDepartmentName, movement.fromRoomName)} {'->'} {formatLocation(movement.toDepartmentName, movement.toRoomName)}</p>
                    <p>{formatDateTime(movement.movedAt)} · {movement.actor || 'Система'}</p>
                    <p>{movement.comment || 'Без комментария'}</p>
                  </article>
                ))}
                {filteredInventoryMovements.length === 0 && <div className="empty-state">Перемещений по текущим фильтрам не найдено.</div>}
              </div>
              <div className="pagination compact-pagination">
                <span>
                  Перемещений: {filteredInventoryMovements.length} · Страница {movementPage} / {movementTotalPages}
                </span>
                <div className="pagination-controls">
                  <button onClick={() => setMovementPage(1)} disabled={movementPage === 1}>
                    {'<<'}
                  </button>
                  <button onClick={() => setMovementPage((page) => Math.max(1, page - 1))} disabled={movementPage === 1}>
                    {'<'}
                  </button>
                  <button
                    onClick={() => setMovementPage((page) => Math.min(movementTotalPages, page + 1))}
                    disabled={movementPage === movementTotalPages}
                  >
                    {'>'}
                  </button>
                  <button onClick={() => setMovementPage(movementTotalPages)} disabled={movementPage === movementTotalPages}>
                    {'>>'}
                  </button>
                </div>
              </div>
            </section>
          </aside>
        </section>
      )}

      {activeTab === 'hall-requests' && (
        <section className="departments-layout">
          <section className="panel departments-main-panel">
            <div className="section-heading">
              <div>
                <h2>Заявки по залам</h2>
                <p className="table-hint">Очередь заявок по кабинетам и залам с приоритетом и статусом.</p>
              </div>
            </div>
            <div className="filters">
              <label>
                Кабинет
                <select value={hallFilterRoomId} onChange={(e) => setHallFilterRoomId(e.target.value ? Number(e.target.value) : '')}>
                  <option value="">Все кабинеты</option>
                  {activeRooms.map((room) => (
                    <option key={room.id} value={room.id}>
                      {room.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Статус
                <select value={hallFilterStatus} onChange={(e) => setHallFilterStatus(e.target.value as HallRequestStatus | '')}>
                  <option value="">Все статусы</option>
                  {HALL_REQUEST_STATUS_OPTIONS.map((statusOption) => (
                    <option key={statusOption.value} value={statusOption.value}>
                      {statusOption.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Приоритет
                <select value={hallPriorityFilter} onChange={(e) => setHallPriorityFilter(e.target.value as HallRequestPriority | '')}>
                  <option value="">Все приоритеты</option>
                  {HALL_REQUEST_PRIORITY_OPTIONS.map((priorityOption) => (
                    <option key={priorityOption.value} value={priorityOption.value}>
                      {priorityOption.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Поиск
                <input
                  value={hallSearchTerm}
                  onChange={(e) => setHallSearchTerm(e.target.value)}
                  placeholder="Кабинет, заявитель, тема"
                />
              </label>
              <label className="checkbox-line">
                <input
                  type="checkbox"
                  checked={hallOverdueOnly}
                  onChange={(e) => setHallOverdueOnly(e.target.checked)}
                />
                <span>Только просроченные SLA</span>
              </label>
            </div>
            <div className="status-line">
              Просрочено: {hallRequests.filter((request) => request.slaOverdue).length} · Срочные: {hallRequests.filter((request) => request.priority === 'URGENT').length}
            </div>
            <div className="table-shell">
              <table className="stock-table">
                <thead>
                  <tr>
                    <th>Когда</th>
                    <th>Кабинет</th>
                    <th>Заявитель</th>
                    <th>Тема</th>
                    <th>Приоритет</th>
                    <th>Статус</th>
                    <th>SLA дедлайн</th>
                    <th>До SLA</th>
                    <th>Действия</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedHallRequests.map((request) => (
                    <tr key={request.id}>
                      <td>{formatDateTime(request.requestedAt)}</td>
                      <td>{request.roomName}</td>
                      <td>{request.requesterName}</td>
                      <td>{request.title}</td>
                      <td>{HALL_REQUEST_PRIORITY_OPTIONS.find((item) => item.value === request.priority)?.label || request.priority}</td>
                      <td>{HALL_REQUEST_STATUS_OPTIONS.find((item) => item.value === request.status)?.label || request.status}</td>
                      <td>{formatDateTime(request.slaDueAt)}</td>
                      <td className={request.slaOverdue ? 'danger-text' : 'table-muted'}>
                        {request.slaOverdue
                          ? `Просрочено ${Math.abs(request.slaMinutesRemaining)} мин`
                          : `${request.slaMinutesRemaining} мин`}
                      </td>
                      <td>
                        <div className="table-actions">
                          <button className="ghost" type="button" onClick={() => beginHallRequestEdit(request)}>
                            Изменить
                          </button>
                          <button className="ghost danger-action" type="button" onClick={() => onDeleteHallRequest(request.id, request.title)}>
                            Удалить
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {filteredHallRequests.length === 0 && <div className="empty-state">Заявок по текущим фильтрам не найдено.</div>}
            </div>
            <div className="pagination">
              <span>
                Заявок: {filteredHallRequests.length} · Страница {hallPage} / {hallTotalPages}
              </span>
              <div className="pagination-controls">
                <button onClick={() => setHallPage(1)} disabled={hallPage === 1}>
                  {'<<'}
                </button>
                <button onClick={() => setHallPage((page) => Math.max(1, page - 1))} disabled={hallPage === 1}>
                  {'<'}
                </button>
                <button
                  onClick={() => setHallPage((page) => Math.min(hallTotalPages, page + 1))}
                  disabled={hallPage === hallTotalPages}
                >
                  {'>'}
                </button>
                <button onClick={() => setHallPage(hallTotalPages)} disabled={hallPage === hallTotalPages}>
                  {'>>'}
                </button>
              </div>
            </div>
          </section>
          <aside className="side-card">
            <p className="eyebrow">Hall Request Form</p>
            <h3>{editingHallRequestId ? 'Изменить заявку' : 'Новая заявка'}</h3>
            <form className="form-card compact-form" onSubmit={onSaveHallRequest}>
              <label>
                Кабинет
                <select value={hallRoomId} onChange={(e) => setHallRoomId(e.target.value ? Number(e.target.value) : '')}>
                  <option value="">Выберите кабинет</option>
                  {activeRooms.map((room) => (
                    <option key={room.id} value={room.id}>
                      {room.departmentName} / {room.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Заявитель
                <input value={hallRequesterName} onChange={(e) => setHallRequesterName(e.target.value)} />
              </label>
              <label>
                Тема
                <input value={hallTitle} onChange={(e) => setHallTitle(e.target.value)} />
              </label>
              <label>
                Описание
                <textarea value={hallDescription} onChange={(e) => setHallDescription(e.target.value)} />
              </label>
              <label>
                Приоритет
                <select value={hallPriority} onChange={(e) => setHallPriority(e.target.value as HallRequestPriority)}>
                  {HALL_REQUEST_PRIORITY_OPTIONS.map((priorityOption) => (
                    <option key={priorityOption.value} value={priorityOption.value}>
                      {priorityOption.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Статус
                <select value={hallStatus} onChange={(e) => setHallStatus(e.target.value as HallRequestStatus)}>
                  {HALL_REQUEST_STATUS_OPTIONS.map((statusOption) => (
                    <option key={statusOption.value} value={statusOption.value}>
                      {statusOption.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Плановая дата (опционально)
                <input type="datetime-local" value={hallPlannedAt} onChange={(e) => setHallPlannedAt(e.target.value)} />
              </label>
              <div className="table-actions">
                <button type="submit">{editingHallRequestId ? 'Сохранить заявку' : 'Создать заявку'}</button>
                {editingHallRequestId && (
                  <button type="button" className="ghost" onClick={resetHallRequestForm}>
                    Отмена
                  </button>
                )}
              </div>
            </form>
          </aside>
        </section>
      )}

      {detailOpen && selectedCartridge && (
        <section className="detail-backdrop" onClick={() => setDetailOpen(false)}>
          <div className="detail-panel" onClick={(e) => e.stopPropagation()}>
            <div className="detail-header">
              <div>
                <p className="eyebrow">Cartridge Details</p>
                <h3>{selectedCartridge.cartridgeModelName}</h3>
              </div>
              <button className="ghost" onClick={() => setDetailOpen(false)}>
                Закрыть
              </button>
            </div>

            <div className="detail-grid">
              <div className="detail-card">
                <div className="detail-info-list">
                <p>Модель: <strong>{selectedCartridge.cartridgeModelName}</strong></p>
                <p>Отдел: <strong>{selectedCartridge.departmentName}</strong></p>
                <p>Режим: <strong>{selectedCartridgeRefillable ? 'Заправляемый' : 'Одноразовый'}</strong></p>
                <p>Состояние: <strong>{selectedCartridgeEmpty ? 'Пустой' : 'Готов к установке'}</strong></p>
                </div>
                <p className="detail-status-line">
                  Статус: <span className={`status-badge ${STATUS_TONES[selectedCartridge.status]}`}>{STATUS_LABELS[selectedCartridge.status]}</span>
                </p>
                <div className="detail-info-list">
                  <p>Количество: <strong>{selectedCartridge.quantity}</strong></p>
                  <p>Установлено: <strong>{selectedCartridge.installedQuantity ?? 0}</strong></p>
                  {selectedCartridgeRefillable && <p>Заправок: <strong>{selectedCartridge.refillCount}</strong></p>}
                  {selectedCartridgeRefillable && <p>Последняя заправка: <strong>{selectedCartridge.lastRefillDate || '-'}</strong></p>}
                  {!selectedCartridgeRefillable && selectedCartridge.status === 'WRITTEN_OFF' && (
                    <p>Списано за все время: <strong>{disposableWrittenOffByModel[selectedCartridge.cartridgeModelName] ?? 0}</strong></p>
                  )}
                  <p>Комментарий: <strong>{selectedCartridge.comment || '-'}</strong></p>
                </div>
              </div>

              <div className="detail-card">
                <h3>Действия</h3>
                <div className="detail-actions">
                  {selectedCartridgeRefillable && selectedCartridge.status !== 'WRITTEN_OFF' && (
                    <button onClick={() => setDetailAction('send')} disabled={!selectedCartridgeEmpty}>
                      Отправить на заправку
                    </button>
                  )}
                  {selectedCartridgeRefillable && selectedCartridge.status === 'ON_REFILL' && (
                    <button onClick={() => setDetailAction('return')}>Вернуть с заправки</button>
                  )}
                  {selectedCartridge.status !== 'WRITTEN_OFF' && (
                    <button onClick={() => setDetailAction('writeoff')}>Списать</button>
                  )}
                </div>
              </div>
            </div>

            {detailAction === 'send' && (
              <form onSubmit={onDetailSendToRefill} className="detail-form detail-card">
                <h3>Отправить на заправку</h3>
                {!selectedCartridgeRefillable && <p className="form-help danger-text">Для этой модели доступно только списание.</p>}
                {selectedCartridgeRefillable && !selectedCartridgeEmpty && (
                  <p className="form-help danger-text">На заправку можно отправлять только пустой перезаправляемый картридж.</p>
                )}
                <label>
                  Количество
                  <input
                    type="number"
                    min={1}
                    max={selectedCartridge.quantity}
                    value={detailQuantity}
                    onChange={(e) => setDetailQuantity(Number(e.target.value))}
                  />
                </label>
                <label>
                  Дата
                  <input type="date" value={detailDateValue} onChange={(e) => setDetailDateValue(e.target.value)} />
                </label>
                <label>
                  Ответственный
                  <input value={detailActor} onChange={(e) => setDetailActor(e.target.value)} />
                </label>
                <label>
                  Комментарий
                  <input value={detailComment} onChange={(e) => setDetailComment(e.target.value)} />
                </label>
                <div className="detail-form-actions">
                  <button type="submit" disabled={!selectedCartridgeRefillable || !selectedCartridgeEmpty || selectedCartridge.quantity < 1}>Подтвердить</button>
                  <button type="button" className="ghost" onClick={() => setDetailAction(null)}>
                    Отмена
                  </button>
                </div>
              </form>
            )}

            {detailAction === 'return' && (
              <form onSubmit={onDetailReturnFromRefill} className="detail-form detail-card">
                <h3>Вернуть с заправки</h3>
                <label>
                  Количество
                  <input
                    type="number"
                    min={1}
                    max={selectedCartridge.quantity}
                    value={detailQuantity}
                    onChange={(e) => setDetailQuantity(Number(e.target.value))}
                  />
                </label>
                <label>
                  Дата
                  <input type="date" value={detailDateValue} onChange={(e) => setDetailDateValue(e.target.value)} />
                </label>
                <label>
                  Ответственный
                  <input value={detailActor} onChange={(e) => setDetailActor(e.target.value)} />
                </label>
                <label>
                  Комментарий
                  <input value={detailComment} onChange={(e) => setDetailComment(e.target.value)} />
                </label>
                <div className="detail-form-actions">
                  <button type="submit">Подтвердить</button>
                  <button type="button" className="ghost" onClick={() => setDetailAction(null)}>
                    Отмена
                  </button>
                </div>
              </form>
            )}

            {detailAction === 'writeoff' && (
              <form onSubmit={onDetailWriteOff} className="detail-form detail-card danger-panel">
                <h3>Списать</h3>
                <label>
                  Основание
                  <input value={detailComment} onChange={(e) => setDetailComment(e.target.value)} />
                </label>
                <div className="detail-form-actions">
                  <button type="submit">Списать</button>
                  <button type="button" className="ghost" onClick={() => setDetailAction(null)}>
                    Отмена
                  </button>
                </div>
              </form>
            )}
          </div>
        </section>
      )}

      {deleteTarget && (
        <section className="detail-backdrop" onClick={() => setDeleteTarget(null)}>
          <div className="detail-panel confirm-panel" onClick={(e) => e.stopPropagation()}>
            <div className="detail-header">
              <div>
                <p className="eyebrow">Подтверждение</p>
                <h3>{getDeleteDialogMeta(deleteTarget).title}</h3>
                <p className="table-hint">{getDeleteDialogMeta(deleteTarget).subject}</p>
              </div>
            </div>
            <p className="table-hint">{getDeleteDialogMeta(deleteTarget).description}</p>
            <div className="detail-form-actions">
              <button type="button" className="danger-action" onClick={confirmDeleteTarget}>
                {getDeleteDialogMeta(deleteTarget).confirmLabel}
              </button>
              <button type="button" className="ghost" onClick={() => setDeleteTarget(null)}>
                Отмена
              </button>
            </div>
          </div>
        </section>
      )}

      {writeOffRequest && (
        <section className="detail-backdrop" onClick={() => setWriteOffRequest(null)}>
          <div className="detail-panel confirm-panel danger-panel" onClick={(e) => e.stopPropagation()}>
            <div className="detail-header">
              <div>
                <p className="eyebrow">Подтверждение</p>
                <h3>Списание картриджа</h3>
                <p className="table-hint">Списать: {writeOffRequest.label}</p>
              </div>
            </div>
            <p className="table-hint">
              Действие будет зафиксировано в журнале и переведёт остаток в статус списания.
            </p>
            <div className="detail-form-actions">
              <button type="button" className="danger-action" onClick={confirmWriteOffRequest}>
                Списать
              </button>
              <button type="button" className="ghost" onClick={() => setWriteOffRequest(null)}>
                Отмена
              </button>
            </div>
          </div>
        </section>
      )}
    </div>
  )
}
