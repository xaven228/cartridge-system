package com.inventory.backend.dto

data class ConsumptionReportRowResponse(
    val modelName: String,
    val installedOperations: Int,
    val installedQuantity: Int,
    val sentToRefillOperations: Int,
    val sentToRefillQuantity: Int,
    val returnedFromRefillOperations: Int,
    val returnedFromRefillQuantity: Int,
    val writtenOffOperations: Int,
    val writtenOffQuantity: Int,
    val totalOperations: Int,
    val totalQuantity: Int,
)

data class ConsumptionReportResponse(
    val dateFrom: String,
    val dateTo: String,
    val generatedAt: String,
    val totalOperations: Int,
    val totalQuantity: Int,
    val rows: List<ConsumptionReportRowResponse>,
)

data class StockByDepartmentRowResponse(
    val departmentId: Long,
    val departmentName: String,
    val inStockQuantity: Int,
    val reserveQuantity: Int,
    val onRefillQuantity: Int,
    val installedQuantity: Int,
    val writtenOffQuantity: Int,
    val totalQuantity: Int,
)

data class StockByModelRowResponse(
    val cartridgeModelId: Long,
    val cartridgeModelName: String,
    val inStockQuantity: Int,
    val reserveQuantity: Int,
    val onRefillQuantity: Int,
    val installedQuantity: Int,
    val writtenOffQuantity: Int,
    val totalQuantity: Int,
)

data class StockByRoomRowResponse(
    val roomId: Long?,
    val roomName: String,
    val inStockQuantity: Int,
    val reserveQuantity: Int,
    val onRefillQuantity: Int,
    val installedQuantity: Int,
    val writtenOffQuantity: Int,
    val totalQuantity: Int,
)

data class StockByTypeRowResponse(
    val cartridgeType: String,
    val inStockQuantity: Int,
    val reserveQuantity: Int,
    val onRefillQuantity: Int,
    val installedQuantity: Int,
    val writtenOffQuantity: Int,
    val totalQuantity: Int,
)

data class PrinterModelReportRowResponse(
    val printerModelName: String,
    val inOperationCount: Int,
    val inStockCount: Int,
    val inRepairCount: Int,
    val writtenOffCount: Int,
    val totalCount: Int,
)

data class CartridgeStateRowResponse(
    val cartridgeId: Long,
    val inventoryCode: String,
    val cartridgeModelName: String,
    val departmentName: String,
    val roomName: String?,
    val quantity: Int,
    val cartridgeType: String,
    val status: String,
)

data class StockSnapshotReportResponse(
    val generatedAt: String,
    val totalInStock: Int,
    val totalReserve: Int,
    val totalOnRefill: Int,
    val totalInstalled: Int,
    val totalWrittenOff: Int,
    val byDepartment: List<StockByDepartmentRowResponse>,
    val byModel: List<StockByModelRowResponse>,
    val byRoom: List<StockByRoomRowResponse>,
    val byType: List<StockByTypeRowResponse>,
    val byPrinterModel: List<PrinterModelReportRowResponse>,
    val inStockItems: List<CartridgeStateRowResponse>,
    val reserveItems: List<CartridgeStateRowResponse>,
    val onRefillItems: List<CartridgeStateRowResponse>,
    val writtenOffItems: List<CartridgeStateRowResponse>,
)
