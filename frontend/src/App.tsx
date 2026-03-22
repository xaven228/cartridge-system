import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import {
  adjustQuantity,
  createCartridge,
  createCartridgeModel,
  createDepartment,
  deleteCartridge,
  deleteCartridgeModel,
  deleteDepartment,
  getCartridges,
  getCartridgeModels,
  getActionLogs,
  getDepartments,
  getRefillHistory,
  installCartridge,
  replaceCartridge,
  removeCartridgeInstallation,
  returnFromRefill,
  sendToRefill,
  updateDepartment,
  updateCartridgeRefillable,
  writeOff,
} from './api'
import type { ActionLogRecord, Cartridge, CartridgeModel, CartridgeStatus, Department, Printer, RefillHistoryRecord } from './api'

const STATUS_LIST: CartridgeStatus[] = ['IN_STOCK', 'INSTALLED', 'ON_REFILL', 'WRITTEN_OFF']
const PAGE_SIZE = 8
const STOCK_DEPARTMENT_NAME = 'Склад'

type SortKey = 'departmentName' | 'status' | 'quantity' | 'refillCount'
type ToastKind = 'success' | 'error'
type TabKey = 'overview' | 'stock' | 'departments' | 'history' | 'create'
type DetailAction = 'adjust' | 'send' | 'return' | 'writeoff' | null
type RemovalOutcome = 'STOCK' | 'REFILL' | 'WRITE_OFF'
type DepartmentPrinterForm = { name: string; cartridgeModelId: number | '' }

interface Toast {
  id: number
  kind: ToastKind
  text: string
}

const STATUS_LABELS: Record<CartridgeStatus, string> = {
  IN_STOCK: 'На складе',
  INSTALLED: 'Установлен',
  ON_REFILL: 'На заправке',
  WRITTEN_OFF: 'Списан',
}

const STATUS_TONES: Record<CartridgeStatus, string> = {
  IN_STOCK: 'status-in_stock',
  INSTALLED: 'status-installed',
  ON_REFILL: 'status-on_refill',
  WRITTEN_OFF: 'status-written_off',
}

function getStockStateMeta(cartridge: Cartridge): { label: string; tone: string } {
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

function formatHistoryStatus(status: string): string {
  if (status === 'SENT') return 'Отправлен'
  if (status === 'RETURNED') return 'Возвращен'
  return status
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
    CARTRIDGE_REFILLABLE_CHANGED: 'Изменение типа',
    CARTRIDGE_DELETED: 'Удаление остатка',
    DEPARTMENT_CREATED: 'Создание отдела',
    DEPARTMENT_UPDATED: 'Изменение отдела',
    DEPARTMENT_DELETED: 'Удаление отдела',
    CARTRIDGE_MODEL_CREATED: 'Создание модели',
    CARTRIDGE_MODEL_DELETED: 'Удаление модели',
  }
  return labels[actionType] || actionType
}

function formatDate(value?: string | null): string {
  if (!value) return '-'
  return value.split('-').reverse().join('.')
}

function balanceTone(value: number): string {
  if (value < 0) return 'metric-negative'
  if (value === 0) return 'metric-warning'
  return 'metric-positive'
}

export default function App() {
  const [departments, setDepartments] = useState<Department[]>([])
  const [models, setModels] = useState<CartridgeModel[]>([])
  const [cartridges, setCartridges] = useState<Cartridge[]>([])
  const [history, setHistory] = useState<RefillHistoryRecord[]>([])
  const [actionLogs, setActionLogs] = useState<ActionLogRecord[]>([])

  const [departmentFilter, setDepartmentFilter] = useState<number | 'all'>('all')
  const [statusFilter, setStatusFilter] = useState<CartridgeStatus | 'all'>('all')
  const [searchTerm, setSearchTerm] = useState('')
  const [activeTab, setActiveTab] = useState<TabKey>('departments')
  const [sortKey, setSortKey] = useState<SortKey>('quantity')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [currentPage, setCurrentPage] = useState(1)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailAction, setDetailAction] = useState<DetailAction>(null)

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
  const [departmentPrinters, setDepartmentPrinters] = useState<DepartmentPrinterForm[]>([{ name: '', cartridgeModelId: '' }])
  const [editingDepartmentId, setEditingDepartmentId] = useState<number | null>(null)
  const [modelName, setModelName] = useState('')
  const [newCartridgeModelId, setNewCartridgeModelId] = useState<number | ''>('')
  const [newQuantity, setNewQuantity] = useState<number>(1)
  const [newCartridgeRefillable, setNewCartridgeRefillable] = useState(true)
  const [newComment, setNewComment] = useState('')
  const [installPrinterId, setInstallPrinterId] = useState<number | ''>('')
  const [preferredPrinterId, setPreferredPrinterId] = useState<number | ''>('')
  const [replaceOutcome, setReplaceOutcome] = useState<RemovalOutcome>('STOCK')
  const [removePrinterId, setRemovePrinterId] = useState<number | ''>('')
  const [removeQuantity, setRemoveQuantity] = useState(1)
  const [removeOutcome, setRemoveOutcome] = useState<RemovalOutcome>('STOCK')

  const [loading, setLoading] = useState(false)
  const [toasts, setToasts] = useState<Toast[]>([])

  const [authPin, setAuthPin] = useState('')
  const [authError, setAuthError] = useState('')
  const [isAuthed, setIsAuthed] = useState(false)
  const [sessionUser, setSessionUser] = useState('')

  const adminPin = import.meta.env.VITE_ADMIN_PIN || '1111'

  const pushToast = useCallback((kind: ToastKind, text: string) => {
    const id = Date.now() + Math.floor(Math.random() * 1000)
    setToasts((current) => [...current, { id, kind, text }])
    window.setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id))
    }, 4000)
  }, [])

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

  const sortedCartridges = useMemo(() => {
    const list = [...visibleCartridges]
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
  }, [visibleCartridges, sortDir, sortKey])

  const totalPages = Math.max(1, Math.ceil(sortedCartridges.length / PAGE_SIZE))
  const paginatedCartridges = useMemo(() => {
    const from = (currentPage - 1) * PAGE_SIZE
    return sortedCartridges.slice(from, from + PAGE_SIZE)
  }, [currentPage, sortedCartridges])

  const dashboardStats = useMemo(() => {
    const inStock = cartridges.filter((item) => item.status === 'IN_STOCK').length
    const onRefill = cartridges.filter((item) => item.status === 'ON_REFILL').length
    const installed = cartridges.filter((item) => item.status === 'INSTALLED').length
    const totalUnits = cartridges.reduce((sum, item) => sum + item.quantity, 0)
    return { inStock, onRefill, installed, totalUnits }
  }, [cartridges])

  const userDepartments = useMemo(
    () => departments.filter((department) => department.name !== STOCK_DEPARTMENT_NAME),
    [departments],
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

  const topHistory = useMemo(() => history.slice(0, 4), [history])
  const selectedCartridgeRefillable = selectedCartridge?.refillable !== false
  const selectedCartridgeEmpty = selectedCartridge?.empty === true
  const selectedDepartmentPrinters: Printer[] = useMemo(() => {
    if (!selectedCartridge) return []
    return (departments.find((item) => item.id === selectedCartridge.departmentId)?.printers ?? []).filter(
      (printer) => !printer.cartridgeModelId || printer.cartridgeModelId === selectedCartridge.cartridgeModelId,
    )
  }, [departments, selectedCartridge])

  useEffect(() => {
    if (!selectedDepartmentPrinters.some((printer) => printer.id === preferredPrinterId)) {
      setPreferredPrinterId('')
    }
  }, [preferredPrinterId, selectedDepartmentPrinters])

  useEffect(() => {
    if (preferredPrinterId && selectedDepartmentPrinters.some((printer) => printer.id === preferredPrinterId)) {
      setInstallPrinterId(preferredPrinterId)
    }
  }, [preferredPrinterId, selectedDepartmentPrinters])

  useEffect(() => {
    setDetailOpen(false)
    setDetailAction(null)
  }, [activeTab])

  const departmentUsageRows = useMemo(
    () =>
      userDepartments.flatMap((department) =>
        (department.printers ?? []).map((printer) => ({
          id: `${department.id}-${printer.id ?? printer.name}`,
          printerId: printer.id ?? null,
          departmentId: department.id,
          departmentName: department.name,
          printerName: printer.name,
          cartridgeModelId: printer.cartridgeModelId ?? null,
          cartridgeModelName: printer.cartridgeModelName ?? 'Не назначен',
          previousReplacementDate: printer.previousReplacementDate ?? null,
          lastReplacementDate: printer.lastReplacementDate ?? null,
          currentInstallation: printer.currentInstallation ?? null,
        })),
      ),
    [userDepartments],
  )

  const cartridgeDemandSummary = useMemo(() => {
    const stockByModel = cartridges.reduce<Record<string, number>>((acc, cartridge) => {
      if (cartridge.status !== 'IN_STOCK') return acc
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

  const refreshCatalog = useCallback(async () => {
    setLoading(true)
    try {
      const [deps, loadedModels, cart] = await Promise.all([
        getDepartments(),
        getCartridgeModels(),
        getCartridges(),
      ])
      setDepartments(deps)
      setModels(loadedModels)
      setCartridges(cart)
      setActionLogs(await getActionLogs())
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Не удалось загрузить данные.')
    } finally {
      setLoading(false)
    }
  }, [pushToast])

  const refreshHistory = useCallback(
    async (cartridgeId: number) => {
      try {
        const records = await getRefillHistory(cartridgeId)
        setHistory(records)
      } catch (e) {
        pushToast('error', e instanceof Error ? e.message : 'Не удалось загрузить историю.')
      }
    },
    [pushToast],
  )

  useEffect(() => {
    if (isAuthed) void refreshCatalog()
  }, [isAuthed, refreshCatalog])

  useEffect(() => {
    if (selectedCartridgeId) void refreshHistory(selectedCartridgeId)
  }, [selectedCartridgeId, refreshHistory])

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
    setCurrentPage(1)
  }, [searchTerm, departmentFilter, statusFilter])

  useEffect(() => {
    if (currentPage > totalPages) setCurrentPage(totalPages)
  }, [currentPage, totalPages])

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
      setActionLogs(await getActionLogs())
      if (selectedCartridgeId) await refreshHistory(selectedCartridgeId)
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : 'Операция завершилась ошибкой.')
    }
  }

  function confirmAdminPin(): boolean {
    const pin = window.prompt('Введите админский PIN для подтверждения удаления')
    if (pin === null) return false
    if (pin !== adminPin) {
      pushToast('error', 'Неверный админский PIN.')
      return false
    }
    return true
  }

  function beginDepartmentEdit(department: Department) {
    setEditingDepartmentId(department.id)
    setDepartmentName(department.name)
    setDepartmentDescription(department.description || '')
    setDepartmentPrinters(
      (department.printers ?? []).length > 0
        ? (department.printers ?? []).map((printer) => ({
            name: printer.name,
            cartridgeModelId: printer.cartridgeModelId ?? '',
          }))
        : [{ name: '', cartridgeModelId: '' }],
    )
    setActiveTab('departments')
  }

  function resetDepartmentForm() {
    setEditingDepartmentId(null)
    setDepartmentName('')
    setDepartmentDescription('')
    setDepartmentPrinters([{ name: '', cartridgeModelId: '' }])
  }

  function onQuickAdjustQuantity(cartridge: Cartridge) {
    const answer = window.prompt(
      `Введите новый остаток для "${cartridge.cartridgeModelName}" в отделе "${cartridge.departmentName}"`,
      String(cartridge.quantity),
    )
    if (answer === null) return

    const nextQuantity = Number(answer)
    if (!Number.isInteger(nextQuantity) || nextQuantity < 0) {
      pushToast('error', 'Количество должно быть целым числом не меньше 0.')
      return
    }

    void withAction(async () => {
      await adjustQuantity(cartridge.id, nextQuantity, cartridge.comment || '')
    }, 'Остаток обновлен.')
  }

  async function handleQuickReplace(printerId: number, cartridgeModelId: number, hasInstalled: boolean) {
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
      const installedCartridgeId = departmentUsageRows.find((row) => row.printerId === printerId)?.currentInstallation?.cartridgeId
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
          printerId,
          removedOutcome,
          comment: '',
          actionDate: today(),
          createdBy: actor,
        })
        return
      }

      await installCartridge(availableCartridge.id, printerId, 1, '')
    }, successText)
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
    void withAction(async () => {
      await sendToRefill(id, dateValue, actor, comment)
    }, 'Картридж отправлен на заправку.')
  }

  function onReturnFromRefill(event: FormEvent) {
    event.preventDefault()
    const id = requireCartridge()
    if (!id) return
    void withAction(async () => {
      await returnFromRefill(id, dateValue, actor, comment)
    }, 'Картридж возвращен с заправки.')
  }

  function onWriteOff(event: FormEvent) {
    event.preventDefault()
    const id = requireCartridge()
    if (!id) return
    if (!window.confirm('Подтвердите списание картриджа.')) return
    void withAction(async () => {
      await writeOff(id, comment)
    }, 'Картридж списан.')
  }

  function onChangeRefillable(refillable: boolean) {
    const id = requireCartridge()
    if (!id) return
    void withAction(async () => {
      await updateCartridgeRefillable(id, refillable)
    }, refillable ? 'Картридж помечен как заправляемый.' : 'Картридж помечен как одноразовый.')
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
    if (authPin !== adminPin) {
      setAuthError('Неверный админский PIN.')
      return
    }
    setIsAuthed(true)
    setSessionUser('Администратор')
    setActor('Администратор')
    setDetailActor('Администратор')
    setAuthPin('')
    setActiveTab('departments')
  }

  function onLogout() {
    setIsAuthed(false)
    setSessionUser('')
    setSelectedCartridgeId('')
    setHistory([])
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

  function onDetailAdjustQuantity(event: FormEvent) {
    event.preventDefault()
    if (!selectedCartridge) return
    void withAction(async () => {
      await adjustQuantity(selectedCartridge.id, detailQuantity, detailComment)
    }, 'Количество обновлено.')
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
    void withAction(async () => {
      await sendToRefill(selectedCartridge.id, detailDateValue, detailActor, detailComment)
    }, 'Картридж отправлен на заправку.')
  }

  function onDetailReturnFromRefill(event: FormEvent) {
    event.preventDefault()
    if (!selectedCartridge) return
    void withAction(async () => {
      await returnFromRefill(selectedCartridge.id, detailDateValue, detailActor, detailComment)
    }, 'Картридж возвращен с заправки.')
  }

  function onDetailWriteOff(event: FormEvent) {
    event.preventDefault()
    if (!selectedCartridge) return
    if (!window.confirm('Подтвердите списание картриджа.')) return
    void withAction(async () => {
      await writeOff(selectedCartridge.id, detailComment)
    }, 'Картридж списан.')
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
        printers: departmentPrinters
          .map((printer) => ({
            name: printer.name.trim(),
            cartridgeModel: printer.cartridgeModelId ? { id: printer.cartridgeModelId } : undefined,
          }))
          .filter((printer) => printer.name)
          .map((printer) => ({ name: printer.name, cartridgeModel: printer.cartridgeModel })),
      }

      if (editingDepartmentId) {
        await updateDepartment(editingDepartmentId, payload)
      } else {
        await createDepartment(payload)
      }
      resetDepartmentForm()
    }, editingDepartmentId ? 'Отдел обновлен.' : 'Отдел создан.')
  }

  function onCreateModel(event: FormEvent) {
    event.preventDefault()
    if (!modelName.trim()) {
      pushToast('error', 'Введите название модели картриджа.')
      return
    }
    void withAction(async () => {
      await createCartridgeModel({ name: modelName.trim() })
      setModelName('')
    }, 'Модель картриджа создана.')
  }

  function onCreateCartridge(event: FormEvent) {
    event.preventDefault()
    if (!newCartridgeModelId) {
      pushToast('error', 'Выберите модель картриджа.')
      return
    }
    void withAction(async () => {
      await createCartridge({
        cartridgeModelId: newCartridgeModelId,
        refillable: newCartridgeRefillable,
        quantity: newQuantity,
        comment: newComment.trim(),
      })
      setNewCartridgeModelId('')
      setNewCartridgeRefillable(true)
      setNewQuantity(1)
      setNewComment('')
      setActiveTab('stock')
    }, 'Картридж добавлен.')
  }

  function onDeleteDepartment(id: number, name: string) {
    if (!confirmAdminPin()) return
    if (!window.confirm(`Удалить отдел "${name}"?`)) return
    void withAction(async () => {
      await deleteDepartment(id)
      if (editingDepartmentId === id) resetDepartmentForm()
    }, 'Отдел удален.')
  }

  function onDeleteCartridge(id: number, title: string) {
    if (!confirmAdminPin()) return
    if (!window.confirm(`Удалить остаток "${title}"?`)) return
    void withAction(async () => {
      await deleteCartridge(id)
      if (selectedCartridgeId === id) {
        setSelectedCartridgeId('')
        setDetailOpen(false)
      }
    }, 'Картриджный остаток удален.')
  }

  function onDeleteCartridgeModel(id: number, name: string) {
    if (!confirmAdminPin()) return
    if (!window.confirm(`Удалить модель "${name}"?`)) return
    void withAction(async () => {
      await deleteCartridgeModel(id)
      if (newCartridgeModelId === id) {
        setNewCartridgeModelId('')
      }
    }, 'Модель картриджа удалена.')
  }

  if (!isAuthed) {
    return (
      <div className="app-shell">
        <section className="auth-panel admin-auth-panel">
          <div className="auth-deco auth-deco-left" />
          <div className="auth-deco auth-deco-right" />
          <p className="eyebrow">Admin Access</p>
          <h1>Control Core</h1>
          <p className="subtitle">Единая админ-панель учета картриджей, отделов и сервисных операций.</p>
          <form className="auth-form" onSubmit={onSignIn}>
            <label>
              Админский PIN
              <input
                type="password"
                value={authPin}
                onChange={(e) => setAuthPin(e.target.value)}
                placeholder="Введите PIN администратора"
              />
            </label>
            <button type="submit">Войти в панель</button>
          </form>
          {authError && <p className="error">{authError}</p>}
        </section>
      </div>
    )
  }

  return (
    <div className="app-shell">
      <header className="simple-header">
        <div>
          <h1>Учет картриджей</h1>
          <p className="table-hint">Отделы, остатки, замены и журнал действий.</p>
        </div>
        <div className="table-actions">
          {loading && <span className="table-muted">Синхронизация...</span>}
          <span className="table-muted">{sessionUser}</span>
          <button className="ghost" onClick={onLogout}>Выйти</button>
        </div>
      </header>

      {toasts.length > 0 && <div className={`status-line status-${toasts[toasts.length - 1].kind}`}>{toasts[toasts.length - 1].text}</div>}

      <nav className="tabs tabs-admin">
        <button className={activeTab === 'stock' ? 'active' : ''} onClick={() => setActiveTab('stock')}>Картриджи</button>
        <button className={activeTab === 'departments' ? 'active' : ''} onClick={() => setActiveTab('departments')}>
          Отделы
        </button>
        <button className={activeTab === 'history' ? 'active' : ''} onClick={() => setActiveTab('history')}>
          История
        </button>
        <button className={activeTab === 'create' ? 'active' : ''} onClick={() => setActiveTab('create')}>
          Пополнение
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
                <span className="stat-label">На складе</span>
                <strong>{dashboardStats.inStock}</strong>
                <p>готово к установке</p>
              </article>
              <article className="stat-card accent-lime">
                <span className="stat-label">Всего единиц</span>
                <strong>{dashboardStats.totalUnits}</strong>
                <p>по всем позициям</p>
              </article>
              <article className="stat-card accent-amber">
                <span className="stat-label">На заправке</span>
                <strong>{dashboardStats.onRefill}</strong>
                <p>нужен контроль возврата</p>
              </article>
              <article className="stat-card accent-steel">
                <span className="stat-label">Установлены</span>
                <strong>{dashboardStats.installed}</strong>
                <p>сейчас в работе</p>
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
                    <label>
                      Обращение
                      <select
                        value={selectedCartridgeRefillable ? 'REFILLABLE' : 'DISPOSABLE'}
                        onChange={(e) => onChangeRefillable(e.target.value === 'REFILLABLE')}
                      >
                        <option value="REFILLABLE">Заправляется</option>
                        <option value="DISPOSABLE">Не заправляется</option>
                      </select>
                    </label>
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
                    Точка замены
                    <select
                      value={installPrinterId}
                      onChange={(e) => setInstallPrinterId(e.target.value ? Number(e.target.value) : '')}
                    >
                      <option value="">Выберите...</option>
                      {selectedDepartmentPrinters.map((printer) => (
                        <option key={printer.id} value={printer.id}>
                          {printer.name}
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
                  <button type="submit" disabled={!selectedCartridgeId || selectedDepartmentPrinters.length === 0}>
                    Заменить
                  </button>
                </form>

                <form onSubmit={onRemoveInstallation} className="form-card command-form">
                  <h3>Снять с принтера</h3>
                  <label>
                    Принтер отдела
                    <select
                      value={removePrinterId}
                      onChange={(e) => setRemovePrinterId(e.target.value ? Number(e.target.value) : '')}
                    >
                      <option value="">Выберите...</option>
                      {selectedDepartmentPrinters.map((printer) => (
                        <option key={printer.id} value={printer.id}>
                          {printer.name}
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
                  <button type="submit" disabled={!selectedCartridgeId || selectedDepartmentPrinters.length === 0}>
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
              <h3>Последние записи истории</h3>
              <div className="history-preview-list">
                {topHistory.map((entry) => (
                  <article key={entry.id} className="history-preview-card">
                    <strong>{selectedCartridge?.cartridgeModelName || 'История операций'}</strong>
                    <p>{formatHistoryStatus(entry.status)}</p>
                    <span>{entry.sentAt || entry.returnedAt || '-'}</span>
                  </article>
                ))}
                {selectedCartridgeId && topHistory.length === 0 && (
                  <div className="empty-state">Для выбранного картриджа история пока пуста.</div>
                )}
                {!selectedCartridgeId && (
                  <div className="empty-state">Выберите картридж, чтобы увидеть его последние события.</div>
                )}
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
                    <td>{item.quantity}</td>
                    <td>{item.installedQuantity ?? 0}</td>
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
                        <button onClick={() => onQuickAdjustQuantity(item)}>Изменить остаток</button>
                        <button className="ghost danger-action" onClick={() => onDeleteCartridge(item.id, `${item.cartridgeModelName} / ${item.departmentName}`)}>
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
            <div className="table-shell">
              <table className="stock-table">
                <thead>
                  <tr>
                    <th>Отдел</th>
                    <th>Точка замены</th>
                    <th>Нужный картридж</th>
                    <th>Сейчас установлен</th>
                    <th>Предпоследняя замена</th>
                    <th>Последняя замена</th>
                    <th>Действие</th>
                  </tr>
                </thead>
                <tbody>
                  {departmentUsageRows.map((row) => (
                    <tr key={row.id}>
                      <td>{row.departmentName}</td>
                      <td>{row.printerName}</td>
                      <td>{row.cartridgeModelName}</td>
                      <td>
                        {row.currentInstallation ? (
                          <div className="replacement-point-state">
                            <span className="status-badge status-installed">Установлен</span>
                            <span>{row.currentInstallation.cartridgeModelName}</span>
                          </div>
                        ) : (
                          <span className="status-badge status-written_off">Не установлен</span>
                        )}
                      </td>
                      <td>{formatDate(row.previousReplacementDate)}</td>
                      <td>{formatDate(row.lastReplacementDate)}</td>
                      <td>
                        {row.printerId && row.cartridgeModelId ? (
                          <button
                            onClick={() => void handleQuickReplace(row.printerId!, row.cartridgeModelId!, Boolean(row.currentInstallation))}
                          >
                            Заменить
                          </button>
                        ) : (
                          <span className="table-muted">Недоступно</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {departmentUsageRows.length === 0 && (
                <div className="empty-state">
                  Точек замены пока нет. Сначала добавьте отдел и укажите в нём точки замены с нужными картриджами.
                </div>
              )}
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
              <div className="department-mini-list">
                {departments.map((department) => (
                  <article key={department.id} className="department-mini-card">
                    <strong>{department.name}</strong>
                    <p>{department.description || 'Без описания'}</p>
                    <div className="table-actions">
                      <button className="ghost" onClick={() => beginDepartmentEdit(department)}>Изменить</button>
                      <button className="ghost danger-action" onClick={() => onDeleteDepartment(department.id, department.name)}>Удалить</button>
                    </div>
                  </article>
                ))}
                {userDepartments.length === 0 && <div className="empty-state">Отделов пока нет.</div>}
              </div>
            </aside>

            <aside className="side-card">
              <p className="eyebrow">Create Department</p>
              <h3>{editingDepartmentId ? 'Изменить отдел' : 'Добавить отдел'}</h3>
              <p className="table-muted">Отдел состоит из названия, описания и списка точек замены с нужными картриджами.</p>
              <form onSubmit={onCreateDepartment} className="form-card compact-form">
                <label>
                  Название
                  <input value={departmentName} onChange={(e) => setDepartmentName(e.target.value)} />
                </label>
                <label>
                  Описание
                  <textarea
                    value={departmentDescription}
                    onChange={(e) => setDepartmentDescription(e.target.value)}
                    placeholder="Например: административный блок, бухгалтерия, инженерная служба"
                  />
                </label>
                <div className="dynamic-list">
                  <span className="field-label">Точки замены в отделе</span>
                  {departmentPrinters.map((printer, index) => (
                    <div key={index} className="dynamic-row">
                      <input
                        value={printer.name}
                        onChange={(e) =>
                          setDepartmentPrinters((current) =>
                            current.map((item, itemIndex) =>
                              itemIndex === index ? { ...item, name: e.target.value } : item,
                            ),
                          )
                        }
                        placeholder={`Точка замены ${index + 1}`}
                      />
                      <select
                        value={printer.cartridgeModelId}
                        onChange={(e) =>
                          setDepartmentPrinters((current) =>
                            current.map((item, itemIndex) =>
                              itemIndex === index
                                ? { ...item, cartridgeModelId: e.target.value ? Number(e.target.value) : '' }
                                : item,
                            ),
                          )
                        }
                      >
                        <option value="">Нужный картридж</option>
                        {models.map((model) => (
                          <option key={model.id} value={model.id}>
                            {model.name}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        className="ghost"
                        onClick={() =>
                          setDepartmentPrinters((current) =>
                            current.length === 1 ? [{ name: '', cartridgeModelId: '' }] : current.filter((_, i) => i !== index),
                          )
                        }
                      >
                        Убрать
                      </button>
                    </div>
                  ))}
                  <button
                    type="button"
                    className="ghost"
                    onClick={() => setDepartmentPrinters((current) => [...current, { name: '', cartridgeModelId: '' }])}
                  >
                    Добавить точку
                  </button>
                </div>
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
          </div>
        </section>
      )}

      {activeTab === 'history' && (
        <section className="panel">
          <div className="section-heading">
            <div>
              <h2>Журнал действий</h2>
              <p className="table-hint">Все действия по остаткам, отделам, заменам и заправкам.</p>
            </div>
          </div>
          <div className="table-shell">
            <table className="stock-table">
              <thead>
                <tr>
                  <th>Когда</th>
                  <th>Действие</th>
                  <th>Объект</th>
                  <th>Подробности</th>
                  <th>Кто</th>
                </tr>
              </thead>
              <tbody>
                {actionLogs.map((entry) => (
                  <tr key={entry.id}>
                    <td>{entry.createdAt.replace('T', ' ').slice(0, 16)}</td>
                    <td>{formatActionType(entry.actionType)}</td>
                    <td>{entry.targetName}</td>
                    <td>{entry.details || '-'}</td>
                    <td>{entry.actor || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {actionLogs.length === 0 && <div className="empty-state">Записей пока нет.</div>}
          </div>
        </section>
      )}

      {activeTab === 'create' && (
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
              <p className="form-help">
                Нужна только модель картриджа. Модель принтера здесь больше не используется.
              </p>
              <button type="submit">Создать модель</button>
            </form>

            <div className="create-stock-layout">
              <form onSubmit={onCreateCartridge} className="form-card">
                <h3>Добавить картриджи в остаток</h3>
                <p className="form-help">
                  Остаток добавляется в общий запас. Отдел при пополнении больше не выбирается.
                </p>
                <label>
                  Модель
                  <select
                    value={newCartridgeModelId}
                    onChange={(e) => setNewCartridgeModelId(e.target.value ? Number(e.target.value) : '')}
                  >
                    <option value="">Выберите...</option>
                    {models.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.name}
                      </option>
                    ))}
                  </select>
                </label>
                <p className="form-help">
                  Список моделей справа. Где именно картридж используется, настраивается во вкладке <strong>Отделы</strong>.
                </p>
                <label>
                  Тип картриджа
                  <select
                    value={newCartridgeRefillable ? 'REFILLABLE' : 'DISPOSABLE'}
                    onChange={(e) => setNewCartridgeRefillable(e.target.value === 'REFILLABLE')}
                  >
                    <option value="REFILLABLE">Перезаправляемый</option>
                    <option value="DISPOSABLE">Одноразовый</option>
                  </select>
                </label>
                <label>
                  Количество
                  <input
                    type="number"
                    min={0}
                    value={newQuantity}
                    onChange={(e) => setNewQuantity(Number(e.target.value))}
                  />
                </label>
                <label>
                  Комментарий
                  <textarea value={newComment} onChange={(e) => setNewComment(e.target.value)} />
                </label>
                <button type="submit">Добавить в остаток</button>
              </form>

              <section className="form-card">
                <h3>Модели картриджей</h3>
                <p className="form-help">
                  Удаление доступно только если модель не используется в остатках и не назначена в точках замены.
                </p>
                <div className="department-mini-list">
                  {models.length === 0 && <div className="empty-state">Моделей пока нет.</div>}
                  {models.map((item) => (
                    <article key={item.id} className="department-mini-item">
                      <div>
                        <strong>{item.name}</strong>
                      </div>
                      <button type="button" className="ghost-button danger-button" onClick={() => onDeleteCartridgeModel(item.id, item.name)}>
                        Удалить
                      </button>
                    </article>
                  ))}
                </div>
              </section>
            </div>
          </div>
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
                <label>
                  Обращение
                  <select
                    value={selectedCartridgeRefillable ? 'REFILLABLE' : 'DISPOSABLE'}
                    onChange={(e) => onChangeRefillable(e.target.value === 'REFILLABLE')}
                  >
                    <option value="REFILLABLE">Заправляется</option>
                    <option value="DISPOSABLE">Не заправляется</option>
                  </select>
                </label>
                <p className="detail-status-line">
                  Статус: <span className={`status-badge ${STATUS_TONES[selectedCartridge.status]}`}>{STATUS_LABELS[selectedCartridge.status]}</span>
                </p>
                <div className="detail-info-list">
                  <p>Количество: <strong>{selectedCartridge.quantity}</strong></p>
                  <p>Установлено: <strong>{selectedCartridge.installedQuantity ?? 0}</strong></p>
                  <p>Заправок: <strong>{selectedCartridge.refillCount}</strong></p>
                  <p>Последняя заправка: <strong>{selectedCartridge.lastRefillDate || '-'}</strong></p>
                  <p>Комментарий: <strong>{selectedCartridge.comment || '-'}</strong></p>
                </div>
              </div>

              <div className="detail-card">
                <h3>Действия</h3>
                <div className="detail-actions">
                  <button onClick={() => setDetailAction('adjust')}>Изменить количество</button>
                  <button onClick={() => setDetailAction('send')} disabled={!selectedCartridgeRefillable || !selectedCartridgeEmpty}>Отправить на заправку</button>
                  <button onClick={() => setDetailAction('return')}>Вернуть с заправки</button>
                  <button onClick={() => setDetailAction('writeoff')}>Списать</button>
                </div>
              </div>
            </div>

            {detailAction === 'adjust' && (
              <form onSubmit={onDetailAdjustQuantity} className="detail-form detail-card">
                <h3>Изменить количество</h3>
                <label>
                  Количество
                  <input
                    type="number"
                    min={0}
                    value={detailQuantity}
                    onChange={(e) => setDetailQuantity(Number(e.target.value))}
                  />
                </label>
                <label>
                  Комментарий
                  <input value={detailComment} onChange={(e) => setDetailComment(e.target.value)} />
                </label>
                <div className="detail-form-actions">
                  <button type="submit">Сохранить</button>
                  <button type="button" className="ghost" onClick={() => setDetailAction(null)}>
                    Отмена
                  </button>
                </div>
              </form>
            )}

            {detailAction === 'send' && (
              <form onSubmit={onDetailSendToRefill} className="detail-form detail-card">
                <h3>Отправить на заправку</h3>
                {!selectedCartridgeRefillable && <p className="form-help danger-text">Для этой модели доступно только списание.</p>}
                {selectedCartridgeRefillable && !selectedCartridgeEmpty && (
                  <p className="form-help danger-text">На заправку можно отправлять только пустой перезаправляемый картридж.</p>
                )}
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
                  <button type="submit" disabled={!selectedCartridgeRefillable || !selectedCartridgeEmpty}>Подтвердить</button>
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
    </div>
  )
}
