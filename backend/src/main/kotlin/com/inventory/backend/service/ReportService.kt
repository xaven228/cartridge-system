package com.inventory.backend.service

import com.inventory.backend.dto.ConsumptionReportResponse
import com.inventory.backend.dto.ConsumptionReportRowResponse
import com.inventory.backend.dto.CartridgeStateRowResponse
import com.inventory.backend.dto.PrinterModelReportRowResponse
import com.inventory.backend.dto.StockByDepartmentRowResponse
import com.inventory.backend.dto.StockByModelRowResponse
import com.inventory.backend.dto.StockByRoomRowResponse
import com.inventory.backend.dto.StockByTypeRowResponse
import com.inventory.backend.dto.StockSnapshotReportResponse
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.CartridgeStatus
import com.inventory.backend.entity.PrinterStatus
import com.inventory.backend.repository.ActionLogRepository
import com.inventory.backend.repository.CartridgeRepository
import com.inventory.backend.repository.PrinterInstallationRepository
import com.inventory.backend.repository.PrinterRepository
import com.lowagie.text.Document
import com.lowagie.text.Font
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

@Service
class ReportService(
    private val actionLogRepository: ActionLogRepository,
    private val cartridgeRepository: CartridgeRepository,
    private val printerInstallationRepository: PrinterInstallationRepository,
    private val printerRepository: PrinterRepository,
) {

    private val trackedTypes = setOf(
        ActionLogType.CARTRIDGE_INSTALLED,
        ActionLogType.CARTRIDGE_SENT_TO_REFILL,
        ActionLogType.CARTRIDGE_RETURNED_FROM_REFILL,
        ActionLogType.CARTRIDGE_WRITTEN_OFF,
    )

    fun getConsumptionReport(dateFrom: LocalDate, dateTo: LocalDate): ConsumptionReportResponse {
        require(!dateTo.isBefore(dateFrom)) { "Дата окончания не может быть меньше даты начала" }

        val from = dateFrom.atStartOfDay()
        val to = dateTo.plusDays(1).atStartOfDay()

        val logs = actionLogRepository.findByActionTypeInAndCreatedAtBetween(trackedTypes, from, to)

        data class MutableRow(
            val modelName: String,
            var installedOps: Int = 0,
            var installedQty: Int = 0,
            var sentOps: Int = 0,
            var sentQty: Int = 0,
            var returnedOps: Int = 0,
            var returnedQty: Int = 0,
            var writeOffOps: Int = 0,
            var writeOffQty: Int = 0,
        )

        val map = linkedMapOf<String, MutableRow>()

        logs.forEach { log ->
            val row = map.getOrPut(log.targetName) { MutableRow(modelName = log.targetName) }
            when (log.actionType) {
                ActionLogType.CARTRIDGE_INSTALLED -> {
                    row.installedOps += 1
                    row.installedQty += extractQuantity(log.details, fallback = 1)
                }

                ActionLogType.CARTRIDGE_SENT_TO_REFILL -> {
                    row.sentOps += 1
                    row.sentQty += extractQuantity(log.details, fallback = 1)
                }

                ActionLogType.CARTRIDGE_RETURNED_FROM_REFILL -> {
                    row.returnedOps += 1
                    row.returnedQty += extractQuantity(log.details, fallback = 1)
                }

                ActionLogType.CARTRIDGE_WRITTEN_OFF -> {
                    row.writeOffOps += 1
                    row.writeOffQty += extractQuantity(log.details, fallback = 1)
                }

                else -> Unit
            }
        }

        val rows = map.values.map { row ->
            val totalOps = row.installedOps + row.sentOps + row.returnedOps + row.writeOffOps
            val totalQty = row.installedQty + row.sentQty + row.returnedQty + row.writeOffQty
            ConsumptionReportRowResponse(
                modelName = row.modelName,
                installedOperations = row.installedOps,
                installedQuantity = row.installedQty,
                sentToRefillOperations = row.sentOps,
                sentToRefillQuantity = row.sentQty,
                returnedFromRefillOperations = row.returnedOps,
                returnedFromRefillQuantity = row.returnedQty,
                writtenOffOperations = row.writeOffOps,
                writtenOffQuantity = row.writeOffQty,
                totalOperations = totalOps,
                totalQuantity = totalQty,
            )
        }.sortedWith(compareByDescending<ConsumptionReportRowResponse> { it.totalQuantity }
            .thenBy { it.modelName.lowercase(Locale.getDefault()) })

        return ConsumptionReportResponse(
            dateFrom = dateFrom.toString(),
            dateTo = dateTo.toString(),
            generatedAt = LocalDateTime.now().toString(),
            totalOperations = rows.sumOf { it.totalOperations },
            totalQuantity = rows.sumOf { it.totalQuantity },
            rows = rows,
        )
    }

    fun exportConsumptionXlsx(dateFrom: LocalDate, dateTo: LocalDate): ByteArray {
        val report = getConsumptionReport(dateFrom, dateTo)
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Расход")

        val headers = listOf(
            "Модель",
            "Установок (оп)",
            "Установок (шт)",
            "На заправку (оп)",
            "На заправку (шт)",
            "Возврат (оп)",
            "Возврат (шт)",
            "Списание (оп)",
            "Списание (шт)",
            "Всего (оп)",
            "Всего (шт)",
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, title ->
            headerRow.createCell(index).setCellValue(title)
        }

        report.rows.forEachIndexed { i, row ->
            val r = sheet.createRow(i + 1)
            r.createCell(0).setCellValue(row.modelName)
            r.createCell(1).setCellValue(row.installedOperations.toDouble())
            r.createCell(2).setCellValue(row.installedQuantity.toDouble())
            r.createCell(3).setCellValue(row.sentToRefillOperations.toDouble())
            r.createCell(4).setCellValue(row.sentToRefillQuantity.toDouble())
            r.createCell(5).setCellValue(row.returnedFromRefillOperations.toDouble())
            r.createCell(6).setCellValue(row.returnedFromRefillQuantity.toDouble())
            r.createCell(7).setCellValue(row.writtenOffOperations.toDouble())
            r.createCell(8).setCellValue(row.writtenOffQuantity.toDouble())
            r.createCell(9).setCellValue(row.totalOperations.toDouble())
            r.createCell(10).setCellValue(row.totalQuantity.toDouble())
        }

        val totalRow = sheet.createRow(report.rows.size + 2)
        totalRow.createCell(0).setCellValue("ИТОГО")
        totalRow.createCell(9).setCellValue(report.totalOperations.toDouble())
        totalRow.createCell(10).setCellValue(report.totalQuantity.toDouble())

        for (i in headers.indices) {
            sheet.autoSizeColumn(i)
        }

        ByteArrayOutputStream().use { output ->
            workbook.use { it.write(output) }
            return output.toByteArray()
        }
    }

    fun exportConsumptionPdf(dateFrom: LocalDate, dateTo: LocalDate): ByteArray {
        val report = getConsumptionReport(dateFrom, dateTo)

        ByteArrayOutputStream().use { output ->
            val document = Document()
            PdfWriter.getInstance(document, output)
            document.open()

            val titleFont = Font(Font.HELVETICA, 14f, Font.BOLD)
            val bodyFont = Font(Font.HELVETICA, 9f)

            document.add(Paragraph("Отчет по расходу картриджей", titleFont))
            document.add(Paragraph("Период: ${report.dateFrom} - ${report.dateTo}", bodyFont))
            document.add(Paragraph("Сформирован: ${report.generatedAt}", bodyFont))
            document.add(Paragraph(" "))

            val table = PdfPTable(6)
            table.widthPercentage = 100f
            listOf("Модель", "Установлено", "На заправку", "Возвращено", "Списано", "Всего").forEach { header ->
                val cell = PdfPCell(Phrase(header, bodyFont))
                table.addCell(cell)
            }

            report.rows.forEach { row ->
                table.addCell(Phrase(row.modelName, bodyFont))
                table.addCell(Phrase(row.installedQuantity.toString(), bodyFont))
                table.addCell(Phrase(row.sentToRefillQuantity.toString(), bodyFont))
                table.addCell(Phrase(row.returnedFromRefillQuantity.toString(), bodyFont))
                table.addCell(Phrase(row.writtenOffQuantity.toString(), bodyFont))
                table.addCell(Phrase(row.totalQuantity.toString(), bodyFont))
            }

            table.addCell(Phrase("ИТОГО", bodyFont))
            table.addCell(Phrase("", bodyFont))
            table.addCell(Phrase("", bodyFont))
            table.addCell(Phrase("", bodyFont))
            table.addCell(Phrase("", bodyFont))
            table.addCell(Phrase(report.totalQuantity.toString(), bodyFont))

            document.add(table)
            document.close()
            return output.toByteArray()
        }
    }

    fun exportStockSnapshotXlsx(): ByteArray {
        val report = getStockSnapshotReport()
        val workbook = XSSFWorkbook()

        fun fillSheet(name: String, headers: List<String>, rows: List<List<String>>) {
            val sheet = workbook.createSheet(name)
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, title -> headerRow.createCell(index).setCellValue(title) }
            rows.forEachIndexed { rowIndex, row ->
                val excelRow = sheet.createRow(rowIndex + 1)
                row.forEachIndexed { cellIndex, value -> excelRow.createCell(cellIndex).setCellValue(value) }
            }
            headers.indices.forEach(sheet::autoSizeColumn)
        }

        fillSheet(
            "Сводка",
            listOf("Показатель", "Значение"),
            listOf(
                listOf("На складе", report.totalInStock.toString()),
                listOf("В резерве", report.totalReserve.toString()),
                listOf("На заправке", report.totalOnRefill.toString()),
                listOf("Установлено", report.totalInstalled.toString()),
                listOf("Списано", report.totalWrittenOff.toString()),
            ),
        )
        fillSheet(
            "По отделам",
            listOf("Отдел", "На складе", "В резерве", "На заправке", "Установлено", "Списано", "Всего"),
            report.byDepartment.map {
                listOf(
                    it.departmentName,
                    it.inStockQuantity.toString(),
                    it.reserveQuantity.toString(),
                    it.onRefillQuantity.toString(),
                    it.installedQuantity.toString(),
                    it.writtenOffQuantity.toString(),
                    it.totalQuantity.toString(),
                )
            },
        )
        fillSheet(
            "По кабинетам",
            listOf("Кабинет", "На складе", "В резерве", "На заправке", "Установлено", "Списано", "Всего"),
            report.byRoom.map {
                listOf(
                    it.roomName,
                    it.inStockQuantity.toString(),
                    it.reserveQuantity.toString(),
                    it.onRefillQuantity.toString(),
                    it.installedQuantity.toString(),
                    it.writtenOffQuantity.toString(),
                    it.totalQuantity.toString(),
                )
            },
        )
        fillSheet(
            "По типам",
            listOf("Тип картриджа", "На складе", "В резерве", "На заправке", "Установлено", "Списано", "Всего"),
            report.byType.map {
                listOf(
                    it.cartridgeType,
                    it.inStockQuantity.toString(),
                    it.reserveQuantity.toString(),
                    it.onRefillQuantity.toString(),
                    it.installedQuantity.toString(),
                    it.writtenOffQuantity.toString(),
                    it.totalQuantity.toString(),
                )
            },
        )
        fillSheet(
            "Модели принтеров",
            listOf("Модель принтера", "В эксплуатации", "На складе", "В ремонте", "Списано", "Всего"),
            report.byPrinterModel.map {
                listOf(
                    it.printerModelName,
                    it.inOperationCount.toString(),
                    it.inStockCount.toString(),
                    it.inRepairCount.toString(),
                    it.writtenOffCount.toString(),
                    it.totalCount.toString(),
                )
            },
        )

        fun stateRows(rows: List<CartridgeStateRowResponse>) = rows.map {
            listOf(
                it.inventoryCode,
                it.cartridgeModelName,
                it.departmentName,
                it.roomName ?: "-",
                it.quantity.toString(),
                it.cartridgeType,
                it.status,
            )
        }

        fillSheet("Склад", listOf("Код", "Модель", "Отдел", "Кабинет", "Количество", "Тип", "Статус"), stateRows(report.inStockItems))
        fillSheet("Резерв", listOf("Код", "Модель", "Отдел", "Кабинет", "Количество", "Тип", "Статус"), stateRows(report.reserveItems))
        fillSheet("Заправка", listOf("Код", "Модель", "Отдел", "Кабинет", "Количество", "Тип", "Статус"), stateRows(report.onRefillItems))
        fillSheet("Списанные", listOf("Код", "Модель", "Отдел", "Кабинет", "Количество", "Тип", "Статус"), stateRows(report.writtenOffItems))

        ByteArrayOutputStream().use { output ->
            workbook.use { it.write(output) }
            return output.toByteArray()
        }
    }

    fun exportStockSnapshotPdf(): ByteArray {
        val report = getStockSnapshotReport()

        ByteArrayOutputStream().use { output ->
            val document = Document()
            PdfWriter.getInstance(document, output)
            document.open()

            val titleFont = Font(Font.HELVETICA, 14f, Font.BOLD)
            val bodyFont = Font(Font.HELVETICA, 9f)

            document.add(Paragraph("Отчет по остаткам и состояниям картриджей", titleFont))
            document.add(Paragraph("Сформирован: ${report.generatedAt}", bodyFont))
            document.add(Paragraph("На складе: ${report.totalInStock}", bodyFont))
            document.add(Paragraph("В резерве: ${report.totalReserve}", bodyFont))
            document.add(Paragraph("На заправке: ${report.totalOnRefill}", bodyFont))
            document.add(Paragraph("Установлено: ${report.totalInstalled}", bodyFont))
            document.add(Paragraph("Списано: ${report.totalWrittenOff}", bodyFont))
            document.add(Paragraph(" "))

            val printerTable = PdfPTable(6)
            printerTable.widthPercentage = 100f
            listOf("Модель принтера", "Экспл.", "Склад", "Ремонт", "Списан", "Всего").forEach {
                printerTable.addCell(PdfPCell(Phrase(it, bodyFont)))
            }
            report.byPrinterModel.forEach { row ->
                printerTable.addCell(Phrase(row.printerModelName, bodyFont))
                printerTable.addCell(Phrase(row.inOperationCount.toString(), bodyFont))
                printerTable.addCell(Phrase(row.inStockCount.toString(), bodyFont))
                printerTable.addCell(Phrase(row.inRepairCount.toString(), bodyFont))
                printerTable.addCell(Phrase(row.writtenOffCount.toString(), bodyFont))
                printerTable.addCell(Phrase(row.totalCount.toString(), bodyFont))
            }
            document.add(printerTable)
            document.add(Paragraph(" "))

            val departmentTable = PdfPTable(7)
            departmentTable.widthPercentage = 100f
            listOf("Отдел", "Склад", "Резерв", "Заправка", "Установлено", "Списано", "Всего").forEach {
                departmentTable.addCell(PdfPCell(Phrase(it, bodyFont)))
            }
            report.byDepartment.forEach { row ->
                departmentTable.addCell(Phrase(row.departmentName, bodyFont))
                departmentTable.addCell(Phrase(row.inStockQuantity.toString(), bodyFont))
                departmentTable.addCell(Phrase(row.reserveQuantity.toString(), bodyFont))
                departmentTable.addCell(Phrase(row.onRefillQuantity.toString(), bodyFont))
                departmentTable.addCell(Phrase(row.installedQuantity.toString(), bodyFont))
                departmentTable.addCell(Phrase(row.writtenOffQuantity.toString(), bodyFont))
                departmentTable.addCell(Phrase(row.totalQuantity.toString(), bodyFont))
            }
            document.add(departmentTable)
            document.close()
            return output.toByteArray()
        }
    }

    private fun extractQuantity(details: String?, fallback: Int): Int {
        if (details.isNullOrBlank()) {
            return fallback
        }
        val regex = """(\\d+)\\s*шт""".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(details)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: fallback
    }

    fun getStockSnapshotReport(): StockSnapshotReportResponse {
        val cartridges = cartridgeRepository.findAll()
        val printers = printerRepository.findAll()
        val installationsByCartridgeId = printerInstallationRepository.findAll()
            .groupBy { it.cartridge.id }

        data class Acc(
            var inStock: Int = 0,
            var reserve: Int = 0,
            var onRefill: Int = 0,
            var installed: Int = 0,
            var writtenOff: Int = 0,
        ) {
            fun total(): Int = inStock + reserve + onRefill + installed + writtenOff
        }

        val byDepartmentAcc = linkedMapOf<Pair<Long, String>, Acc>()
        val byModelAcc = linkedMapOf<Pair<Long, String>, Acc>()
        val byRoomAcc = linkedMapOf<Pair<Long?, String>, Acc>()
        val byTypeAcc = linkedMapOf<String, Acc>()
        val printerModelAcc = linkedMapOf<String, MutableMap<PrinterStatus, Int>>()

        fun apply(acc: Acc, status: CartridgeStatus, quantity: Int) {
            when (status) {
                CartridgeStatus.IN_STOCK -> acc.inStock += quantity
                CartridgeStatus.RESERVE -> acc.reserve += quantity
                CartridgeStatus.ON_REFILL -> acc.onRefill += quantity
                CartridgeStatus.INSTALLED -> acc.installed += quantity
                CartridgeStatus.WRITTEN_OFF -> acc.writtenOff += quantity
            }
        }

        cartridges.forEach { c ->
            val departmentKey = c.department.id!! to c.department.name
            val modelKey = c.cartridgeModel.id!! to c.cartridgeModel.name
            val typeKey = if (c.refillable == true) "Заправляемые" else "Незаправляемые"
            val departmentAcc = byDepartmentAcc.getOrPut(departmentKey) { Acc() }
            val modelAcc = byModelAcc.getOrPut(modelKey) { Acc() }
            val typeAcc = byTypeAcc.getOrPut(typeKey) { Acc() }

            val quantity = c.quantity
            apply(departmentAcc, c.status, quantity)
            apply(modelAcc, c.status, quantity)
            apply(typeAcc, c.status, quantity)

            if (c.status == CartridgeStatus.INSTALLED) {
                val installations = installationsByCartridgeId[c.id].orEmpty()
                val installedByRooms = installations.groupBy {
                    val room = it.printerSlot.printer.room
                    room?.id to (room?.name ?: "Без кабинета")
                }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }

                var distributed = 0
                installedByRooms.forEach { (roomKey, qty) ->
                    distributed += qty
                    val acc = byRoomAcc.getOrPut(roomKey) { Acc() }
                    apply(acc, CartridgeStatus.INSTALLED, qty)
                }

                val remainder = quantity - distributed
                if (remainder > 0) {
                    val acc = byRoomAcc.getOrPut(null to "Без кабинета") { Acc() }
                    apply(acc, CartridgeStatus.INSTALLED, remainder)
                }
            } else {
                val acc = byRoomAcc.getOrPut(null to "Без кабинета") { Acc() }
                apply(acc, c.status, quantity)
            }
        }

        printers.forEach { printer ->
            val modelName = printer.model?.takeIf { it.isNotBlank() } ?: printer.name
            val acc = printerModelAcc.getOrPut(modelName) { mutableMapOf() }
            acc[printer.status] = (acc[printer.status] ?: 0) + 1
        }

        val byDepartment = byDepartmentAcc.entries
            .map { (key, acc) ->
                StockByDepartmentRowResponse(
                    departmentId = key.first,
                    departmentName = key.second,
                    inStockQuantity = acc.inStock,
                    reserveQuantity = acc.reserve,
                    onRefillQuantity = acc.onRefill,
                    installedQuantity = acc.installed,
                    writtenOffQuantity = acc.writtenOff,
                    totalQuantity = acc.total(),
                )
            }
            .sortedWith(compareByDescending<StockByDepartmentRowResponse> { it.totalQuantity }.thenBy { it.departmentName })

        val byModel = byModelAcc.entries
            .map { (key, acc) ->
                StockByModelRowResponse(
                    cartridgeModelId = key.first,
                    cartridgeModelName = key.second,
                    inStockQuantity = acc.inStock,
                    reserveQuantity = acc.reserve,
                    onRefillQuantity = acc.onRefill,
                    installedQuantity = acc.installed,
                    writtenOffQuantity = acc.writtenOff,
                    totalQuantity = acc.total(),
                )
            }
            .sortedWith(compareByDescending<StockByModelRowResponse> { it.totalQuantity }.thenBy { it.cartridgeModelName })

        val byRoom = byRoomAcc.entries
            .map { (key, acc) ->
                StockByRoomRowResponse(
                    roomId = key.first,
                    roomName = key.second,
                    inStockQuantity = acc.inStock,
                    reserveQuantity = acc.reserve,
                    onRefillQuantity = acc.onRefill,
                    installedQuantity = acc.installed,
                    writtenOffQuantity = acc.writtenOff,
                    totalQuantity = acc.total(),
                )
            }
            .sortedWith(compareByDescending<StockByRoomRowResponse> { it.totalQuantity }.thenBy { it.roomName })

        val byType = byTypeAcc.entries
            .map { (typeName, acc) ->
                StockByTypeRowResponse(
                    cartridgeType = typeName,
                    inStockQuantity = acc.inStock,
                    reserveQuantity = acc.reserve,
                    onRefillQuantity = acc.onRefill,
                    installedQuantity = acc.installed,
                    writtenOffQuantity = acc.writtenOff,
                    totalQuantity = acc.total(),
                )
            }
            .sortedBy { it.cartridgeType }

        val byPrinterModel = printerModelAcc.entries.map { (modelName, counts) ->
            val inOperation = counts[PrinterStatus.IN_OPERATION] ?: 0
            val inStock = counts[PrinterStatus.IN_STOCK] ?: 0
            val inRepair = counts[PrinterStatus.IN_REPAIR] ?: 0
            val writtenOff = counts[PrinterStatus.WRITTEN_OFF] ?: 0
            PrinterModelReportRowResponse(
                printerModelName = modelName,
                inOperationCount = inOperation,
                inStockCount = inStock,
                inRepairCount = inRepair,
                writtenOffCount = writtenOff,
                totalCount = inOperation + inStock + inRepair + writtenOff,
            )
        }.sortedWith(compareByDescending<PrinterModelReportRowResponse> { it.totalCount }.thenBy { it.printerModelName })

        fun toStateRow(cartridge: com.inventory.backend.entity.Cartridge): CartridgeStateRowResponse {
            val roomName = if (cartridge.status == CartridgeStatus.INSTALLED) {
                installationsByCartridgeId[cartridge.id].orEmpty().firstOrNull()?.printerSlot?.printer?.room?.name
            } else {
                null
            }
            return CartridgeStateRowResponse(
                cartridgeId = cartridge.id!!,
                inventoryCode = cartridge.inventoryCode,
                cartridgeModelName = cartridge.cartridgeModel.name,
                departmentName = cartridge.department.name,
                roomName = roomName,
                quantity = cartridge.quantity,
                cartridgeType = if (cartridge.refillable == true) "Заправляемый" else "Незаправляемый",
                status = cartridge.status.name,
            )
        }

        val inStockItems = cartridges.filter { it.status == CartridgeStatus.IN_STOCK }.sortedBy { it.cartridgeModel.name }.map(::toStateRow)
        val reserveItems = cartridges.filter { it.status == CartridgeStatus.RESERVE }.sortedBy { it.cartridgeModel.name }.map(::toStateRow)
        val onRefillItems = cartridges.filter { it.status == CartridgeStatus.ON_REFILL }.sortedBy { it.cartridgeModel.name }.map(::toStateRow)
        val writtenOffItems = cartridges.filter { it.status == CartridgeStatus.WRITTEN_OFF }.sortedBy { it.cartridgeModel.name }.map(::toStateRow)

        return StockSnapshotReportResponse(
            generatedAt = LocalDateTime.now().toString(),
            totalInStock = byDepartment.sumOf { it.inStockQuantity },
            totalReserve = byDepartment.sumOf { it.reserveQuantity },
            totalOnRefill = byDepartment.sumOf { it.onRefillQuantity },
            totalInstalled = byDepartment.sumOf { it.installedQuantity },
            totalWrittenOff = byDepartment.sumOf { it.writtenOffQuantity },
            byDepartment = byDepartment,
            byModel = byModel,
            byRoom = byRoom,
            byType = byType,
            byPrinterModel = byPrinterModel,
            inStockItems = inStockItems,
            reserveItems = reserveItems,
            onRefillItems = onRefillItems,
            writtenOffItems = writtenOffItems,
        )
    }
}
