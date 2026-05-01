package com.finabits.hrms.service;

import com.finabits.hrms.entity.Attendance;
import com.finabits.hrms.entity.Leave;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.entity.LeaveTypeConfig;
import com.finabits.hrms.repository.AttendanceRepository;
import com.finabits.hrms.repository.LeaveTypeConfigRepository;
import com.finabits.hrms.repository.LeaveRepository;
import com.finabits.hrms.repository.SalaryRepository;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final AttendanceRepository attendanceRepository;
    private final LeaveRepository      leaveRepository;
    private final SalaryRepository     salaryRepository;
    private final UserRepository            userRepository;
    private final LeaveTypeConfigRepository leaveTypeConfigRepository;

    private static final String[] MONTHS = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

    // ── Attendance Report Excel ───────────────────────────────────────────────
    public byte[] generateAttendanceReport(LocalDate from, LocalDate to,
                                           Long userId) throws IOException {
        XSSFWorkbook wb    = new XSSFWorkbook();
        Sheet        sheet = wb.createSheet("Attendance Report");

        // Styles
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle titleStyle  = createTitleStyle(wb);
        CellStyle dateStyle   = wb.createCellStyle();
        CreationHelper ch = wb.getCreationHelper();
        dateStyle.setDataFormat(ch.createDataFormat().getFormat("yyyy-mm-dd"));

        // Title
        Row titleRow = sheet.createRow(0);
        Cell title   = titleRow.createCell(0);
        title.setCellValue("Attendance Report — " + from + " to " + to);
        title.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        // Header
        Row hdr = sheet.createRow(2);
        String[] cols = {"Employee", "Date", "Slot", "Check-in Time", "Check-out Time", "Hours", "Status"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        // Data
        List<User> employees = userId != null
                ? List.of(userRepository.findById(userId).orElseThrow())
                : userRepository.findByRole(Role.EMPLOYEE).stream().filter(User::isActive).toList();

        int rowNum = 3;
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        for (User emp : employees) {
            List<Attendance> recs = attendanceRepository.findByUserAndDateRange(emp, from, to);
            for (Attendance a : recs) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(emp.getFullName());
                row.createCell(1).setCellValue(a.getDate().toString());
                row.createCell(2).setCellValue(a.getSlotLabel());
                row.createCell(3).setCellValue(a.getCheckInTime() != null ? a.getCheckInTime().format(timeFmt) : "—");
                row.createCell(4).setCellValue(a.getCheckoutTime() != null ? a.getCheckoutTime().format(timeFmt) : "—");
                row.createCell(5).setCellValue(a.getTotalHours() != null ? a.getTotalHours().toString() + "h" : "—");
                row.createCell(6).setCellValue(a.getStatus() != null ? a.getStatus().name() : "—");
            }
        }

        return toBytes(wb);
    }

    // ── Leave Report Excel ────────────────────────────────────────────────────
    public byte[] generateLeaveReport(int year, Long userId) throws IOException {
        XSSFWorkbook wb    = new XSSFWorkbook();
        Sheet        sheet = wb.createSheet("Leave Report");
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle titleStyle  = createTitleStyle(wb);

        Row titleRow = sheet.createRow(0);
        Cell title   = titleRow.createCell(0);
        title.setCellValue("Leave Report — " + year);
        title.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        Row hdr = sheet.createRow(2);
        String[] cols = {"Employee","Start Date","End Date","Days","Type","Status","Reason","Admin Comment"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        List<User> employees = userId != null
                ? List.of(userRepository.findById(userId).orElseThrow())
                : userRepository.findByRole(Role.EMPLOYEE).stream().filter(User::isActive).toList();

        int rowNum = 3;
        for (User emp : employees) {
            List<Leave> leaves = leaveRepository.findAll().stream()
                    .filter(l -> l.getUser().getId().equals(emp.getId()))
                    .filter(l -> l.getStartDate().getYear() == year)
                    .toList();
            for (Leave l : leaves) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(emp.getFullName());
                row.createCell(1).setCellValue(l.getStartDate().toString());
                row.createCell(2).setCellValue(l.getEndDate().toString());
                row.createCell(3).setCellValue(l.getTotalDays());
                row.createCell(4).setCellValue(l.getLeaveType() != null ? l.getLeaveType().name() : "—");
                row.createCell(5).setCellValue(l.getStatus().name());
                row.createCell(6).setCellValue(l.getReason() != null ? l.getReason() : "—");
                row.createCell(7).setCellValue(l.getAdminComment() != null ? l.getAdminComment() : "—");
            }
        }

        return toBytes(wb);
    }

    // ── Salary Report Excel ───────────────────────────────────────────────────
    public byte[] generateSalaryReport(int month, int year, Long userId) throws IOException {
        XSSFWorkbook wb    = new XSSFWorkbook();
        Sheet        sheet = wb.createSheet("Salary Report");
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle titleStyle  = createTitleStyle(wb);

        Row titleRow = sheet.createRow(0);
        Cell title   = titleRow.createCell(0);
        title.setCellValue("Salary Report — " + MONTHS[month-1] + " " + year);
        title.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        Row hdr = sheet.createRow(2);
        String[] cols = {"Employee","Gross Salary","Working Days","Present Days","Half Days","Deduction","Net Salary","Notes"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5500);
        }

        List<User> employees = userId != null
                ? List.of(userRepository.findById(userId).orElseThrow())
                : userRepository.findByRole(Role.EMPLOYEE).stream().filter(User::isActive).toList();

        int rowNum = 3;
        for (User emp : employees) {
            var slipOpt = salaryRepository.findByUserAndMonthAndYear(emp, month, year);
            if (slipOpt.isPresent()) {
                var s   = slipOpt.get();
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(emp.getFullName());
                row.createCell(1).setCellValue(s.getGrossSalary().doubleValue());
                row.createCell(2).setCellValue(s.getWorkingDays());
                row.createCell(3).setCellValue(s.getPresentDays());
                row.createCell(4).setCellValue(s.getHalfDays());
                row.createCell(5).setCellValue(s.getDeductionAmount().doubleValue());
                row.createCell(6).setCellValue(s.getNetSalary().doubleValue());
                row.createCell(7).setCellValue(s.getNotes() != null ? s.getNotes() : "");
            }
            rowNum++;
        }

        return toBytes(wb);
    }

    // ── Leave Balance Report Excel — per leave type breakdown ────────────────
    public byte[] generateLeaveBalanceReport(int year) throws IOException {
        XSSFWorkbook wb    = new XSSFWorkbook();
        Sheet        sheet = wb.createSheet("Leave Balance");
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle titleStyle  = createTitleStyle(wb);

        // Get active leave types from DB
        List<LeaveTypeConfig> leaveTypes = leaveTypeConfigRepository.findByActiveTrue();
        int totalAllowedPerYear = leaveTypes.stream().mapToInt(LeaveTypeConfig::getAllowedPerYear).sum();

        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Leave Balance Report — " + year
                + " (Total Allowed: " + totalAllowedPerYear + " days)");
        titleRow.getCell(0).setCellStyle(titleStyle);

        // Build dynamic columns: Employee + one col per leave type + summary cols
        int typeCount = leaveTypes.size();
        int totalCols = 1 + (typeCount * 2) + 3; // emp + (used+remaining per type) + totalUsed+totalRemaining+unpaid
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalCols - 1));

        // Sub-header row for leave type groups
        Row typeRow = sheet.createRow(1);
        typeRow.createCell(0).setCellValue("Employee");
        int col = 1;
        for (LeaveTypeConfig lt : leaveTypes) {
            Cell c = typeRow.createCell(col);
            c.setCellValue(lt.getTypeName() + " (" + lt.getAllowedPerYear() + "/yr)");
            c.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, col, col + 1));
            col += 2;
        }
        typeRow.createCell(col).setCellValue("Total Used");
        typeRow.createCell(col+1).setCellValue("Total Remaining");
        typeRow.createCell(col+2).setCellValue("Unpaid (LWP)");

        // Header row for sub-columns
        Row hdr = sheet.createRow(2);
        hdr.createCell(0).setCellValue("Employee");
        hdr.getCell(0).setCellStyle(headerStyle);
        col = 1;
        for (LeaveTypeConfig lt : leaveTypes) {
            Cell used = hdr.createCell(col);
            used.setCellValue("Used");
            used.setCellStyle(headerStyle);
            Cell rem = hdr.createCell(col + 1);
            rem.setCellValue("Remaining");
            rem.setCellStyle(headerStyle);
            sheet.setColumnWidth(col,   4000);
            sheet.setColumnWidth(col+1, 4000);
            col += 2;
        }
        Cell c1 = hdr.createCell(col);   c1.setCellValue("Total Used");   c1.setCellStyle(headerStyle); sheet.setColumnWidth(col, 4500);
        Cell c2 = hdr.createCell(col+1); c2.setCellValue("Total Remaining"); c2.setCellStyle(headerStyle); sheet.setColumnWidth(col+1, 5000);
        Cell c3 = hdr.createCell(col+2); c3.setCellValue("Unpaid (LWP)"); c3.setCellStyle(headerStyle); sheet.setColumnWidth(col+2, 4500);
        sheet.setColumnWidth(0, 6000);

        List<User> employees = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();

        int rowNum = 3;
        for (User emp : employees) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(emp.getFullName());

            double totalUsed = 0;
            int dataCol = 1;

            for (LeaveTypeConfig lt : leaveTypes) {
                // Count leaves used for this specific type
                double usedForType = leaveRepository.findAll().stream()
                        .filter(l -> l.getUser().getId().equals(emp.getId()))
                        .filter(l -> l.getStatus() == com.finabits.hrms.enums.LeaveStatus.APPROVED)
                        .filter(l -> l.getLeaveTypeConfig() != null
                                && l.getLeaveTypeConfig().getId().equals(lt.getId()))
                        .mapToDouble(l -> l.getLeaveConsumed() != null ? l.getLeaveConsumed() : 0)
                        .sum();
                double remainingForType = Math.max(0, lt.getAllowedPerYear() - usedForType);
                totalUsed += usedForType;

                row.createCell(dataCol).setCellValue(usedForType);
                row.createCell(dataCol + 1).setCellValue(remainingForType);
                dataCol += 2;
            }

            // Also count auto-generated (CASUAL/NULL config) leaves
            double autoUsed = leaveRepository.findAll().stream()
                    .filter(l -> l.getUser().getId().equals(emp.getId()))
                    .filter(l -> l.getStatus() == com.finabits.hrms.enums.LeaveStatus.APPROVED)
                    .filter(l -> l.isAutoGenerated())
                    .mapToDouble(l -> l.getLeaveConsumed() != null ? l.getLeaveConsumed() : 0)
                    .sum();

            double unpaid = leaveRepository.findAll().stream()
                    .filter(l -> l.getUser().getId().equals(emp.getId()))
                    .filter(l -> l.getStatus() == com.finabits.hrms.enums.LeaveStatus.APPROVED)
                    .filter(l -> l.getLeaveType() == com.finabits.hrms.enums.LeaveType.UNPAID
                            || l.getLeaveType() == com.finabits.hrms.enums.LeaveType.MIXED)
                    .mapToDouble(l -> l.getUnpaidDays())
                    .sum();

            double grandTotal     = leaveRepository.sumLeaveConsumedByYear(emp, year);
            double totalRemaining = Math.max(0, totalAllowedPerYear - grandTotal);

            row.createCell(dataCol).setCellValue(grandTotal);
            row.createCell(dataCol+1).setCellValue(totalRemaining);
            row.createCell(dataCol+2).setCellValue(unpaid);
        }

        return toBytes(wb);
    }

    // ── Per-employee leave report Excel ───────────────────────────────────────
    public byte[] generateEmployeeLeaveReport(Long userId, int year) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.finabits.hrms.exception.ResourceNotFoundException("Employee not found"));

        XSSFWorkbook wb    = new XSSFWorkbook();
        Sheet        sheet = wb.createSheet("Leave Report");
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle titleStyle  = createTitleStyle(wb);

        // Title
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Leave Report — " + user.getFullName() + " — " + year);
        titleRow.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        // Leave type summary section
        List<LeaveTypeConfig> leaveTypes = leaveTypeConfigRepository.findByActiveTrue();
        int totalAllowed = leaveTypes.stream().mapToInt(LeaveTypeConfig::getAllowedPerYear).sum();

        Row summaryHeader = sheet.createRow(2);
        summaryHeader.createCell(0).setCellValue("LEAVE BALANCE SUMMARY");
        summaryHeader.getCell(0).setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 4));

        Row sumHdr = sheet.createRow(3);
        for (int i = 0; i < new String[]{"Leave Type","Allowed/Year","Used","Remaining","Status"}.length; i++) {
            Cell c = sumHdr.createCell(i);
            c.setCellValue(new String[]{"Leave Type","Allowed/Year","Used","Remaining","Status"}[i]);
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }

        int sumRow = 4;
        for (LeaveTypeConfig lt : leaveTypes) {
            double used = leaveRepository.findAll().stream()
                    .filter(l -> l.getUser().getId().equals(userId))
                    .filter(l -> l.getStatus() == com.finabits.hrms.enums.LeaveStatus.APPROVED)
                    .filter(l -> l.getLeaveTypeConfig() != null && l.getLeaveTypeConfig().getId().equals(lt.getId()))
                    .mapToDouble(l -> l.getLeaveConsumed() != null ? l.getLeaveConsumed() : 0).sum();
            double remaining = Math.max(0, lt.getAllowedPerYear() - used);
            Row r = sheet.createRow(sumRow++);
            r.createCell(0).setCellValue(lt.getTypeName());
            r.createCell(1).setCellValue(lt.getAllowedPerYear());
            r.createCell(2).setCellValue(used);
            r.createCell(3).setCellValue(remaining);
            r.createCell(4).setCellValue(remaining <= 0 ? "⚠ Exhausted" : remaining <= 2 ? "⚠ Low" : "✓ Available");
        }

        // Total row
        double totalUsed = leaveRepository.sumLeaveConsumedByYear(user, year);
        Row totalRow = sheet.createRow(sumRow++);
        totalRow.createCell(0).setCellValue("TOTAL");
        totalRow.getCell(0).setCellStyle(headerStyle);
        totalRow.createCell(1).setCellValue(totalAllowed);
        totalRow.createCell(2).setCellValue(totalUsed);
        totalRow.createCell(3).setCellValue(Math.max(0, totalAllowed - totalUsed));

        // Leave history section
        int histStart = sumRow + 2;
        Row histHeader = sheet.createRow(histStart);
        histHeader.createCell(0).setCellValue("LEAVE HISTORY");
        histHeader.getCell(0).setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(histStart, histStart, 0, 7));
        sheet.setColumnWidth(5, 5000);
        sheet.setColumnWidth(6, 5000);
        sheet.setColumnWidth(7, 6000);

        Row histHdr = sheet.createRow(histStart + 1);
        String[] histCols = {"Leave Type","Start Date","End Date","Days","Half Day","Paid/Unpaid","Status","Reason"};
        for (int i = 0; i < histCols.length; i++) {
            Cell c = histHdr.createCell(i);
            c.setCellValue(histCols[i]);
            c.setCellStyle(headerStyle);
        }

        List<Leave> leaves = leaveRepository.findAll().stream()
                .filter(l -> l.getUser().getId().equals(userId))
                .filter(l -> l.getStartDate().getYear() == year)
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();

        int histRow = histStart + 2;
        for (Leave l : leaves) {
            Row r = sheet.createRow(histRow++);
            r.createCell(0).setCellValue(l.getLeaveTypeConfig() != null ? l.getLeaveTypeConfig().getTypeName() : "General");
            r.createCell(1).setCellValue(l.getStartDate().toString());
            r.createCell(2).setCellValue(l.getEndDate().toString());
            r.createCell(3).setCellValue(l.isHalfDay() ? 0.5 : l.getTotalDays());
            r.createCell(4).setCellValue(l.isHalfDay() ? "Yes" : "No");
            r.createCell(5).setCellValue(l.getLeaveType() != null ? l.getLeaveType().name() : "—");
            r.createCell(6).setCellValue(l.getStatus().name());
            r.createCell(7).setCellValue(l.getReason() != null ? l.getReason() : "—");
        }

        return toBytes(wb);
    }

    // ── Style helpers ─────────────────────────────────────────────────────────
    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }
}