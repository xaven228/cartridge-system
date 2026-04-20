package com.inventory.backend.controller

import com.inventory.backend.dto.ConsumptionReportResponse
import com.inventory.backend.dto.StockSnapshotReportResponse
import com.inventory.backend.service.ReportService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/reports")
@CrossOrigin
@PreAuthorize("@authz.canExportReports()")
class ReportController(
    private val reportService: ReportService,
) {

    @GetMapping("/stock-snapshot")
    fun getStockSnapshot(): StockSnapshotReportResponse = reportService.getStockSnapshotReport()

    @GetMapping("/stock-snapshot.xlsx")
    fun exportStockSnapshotXlsx(): ResponseEntity<ByteArray> {
        val body = reportService.exportStockSnapshotXlsx()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stock-snapshot-report.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body)
    }

    @GetMapping("/stock-snapshot.pdf")
    fun exportStockSnapshotPdf(): ResponseEntity<ByteArray> {
        val body = reportService.exportStockSnapshotPdf()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stock-snapshot-report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(body)
    }

    @GetMapping("/consumption")
    fun getConsumption(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
    ): ConsumptionReportResponse {
        val to = dateTo ?: LocalDate.now()
        val from = dateFrom ?: to.minusMonths(6)
        return reportService.getConsumptionReport(from, to)
    }

    @GetMapping("/consumption.xlsx")
    fun exportConsumptionXlsx(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
    ): ResponseEntity<ByteArray> {
        val to = dateTo ?: LocalDate.now()
        val from = dateFrom ?: to.minusMonths(6)
        val body = reportService.exportConsumptionXlsx(from, to)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=consumption-report.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body)
    }

    @GetMapping("/consumption.pdf")
    fun exportConsumptionPdf(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
    ): ResponseEntity<ByteArray> {
        val to = dateTo ?: LocalDate.now()
        val from = dateFrom ?: to.minusMonths(6)
        val body = reportService.exportConsumptionPdf(from, to)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=consumption-report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(body)
    }
}
