package com.inventory.backend.service

import com.inventory.backend.dto.SystemModuleResponse
import com.inventory.backend.entity.SystemModuleCode
import com.inventory.backend.entity.SystemModuleStatus
import org.springframework.stereotype.Service

@Service
class SystemModuleService {

    fun getAll(): List<SystemModuleResponse> = listOf(
        SystemModuleResponse(
            code = SystemModuleCode.CARTRIDGE_ACCOUNTING,
            title = "Учёт принтеров и картриджей",
            status = SystemModuleStatus.ACTIVE,
            description = "Текущий рабочий модуль: остатки, операции, заправка, история.",
            plannedScope = "",
        ),
        SystemModuleResponse(
            code = SystemModuleCode.HOTEL_INVENTORY,
            title = "Инвентаризация отеля",
            status = SystemModuleStatus.PLANNED,
            description = "Расширение для учёта материальных ценностей и движений инвентаря.",
            plannedScope = "Справочник активов, статусы, перемещения, инвентаризационные ведомости",
        ),
        SystemModuleResponse(
            code = SystemModuleCode.HALL_REQUESTS,
            title = "Заявки по залам",
            status = SystemModuleStatus.PLANNED,
            description = "Расширение для обработки заявок по залам и сервисных задач.",
            plannedScope = "Очередь заявок, приоритеты, SLA-метки, контроль исполнения",
        ),
    )
}
