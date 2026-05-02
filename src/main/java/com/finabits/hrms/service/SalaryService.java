package com.finabits.hrms.service;

import com.finabits.hrms.dto.response.SalaryResponse;
import com.finabits.hrms.entity.Salary;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.LeaveType;
import com.finabits.hrms.exception.BadRequestException;
import com.finabits.hrms.exception.DuplicateResourceException;
import com.finabits.hrms.exception.ResourceNotFoundException;
import com.finabits.hrms.repository.AttendanceRepository;
import com.finabits.hrms.repository.LeaveRepository;
import com.finabits.hrms.repository.SalaryRepository;
import com.finabits.hrms.repository.UserRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryService {

    private final SalaryRepository     salaryRepository;
    private final UserRepository       userRepository;
    private final LeaveRepository      leaveRepository;
    private final AttendanceRepository attendanceRepository;
    private final SystemSettingService settingService;

    @Value("${salary.slip.logo.path}")
    private String logoPath;

    @Value("${salary.slip.stamp.path}")
    private String stampPath;

    private static final String[] MONTH_NAMES = {
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
    };

    // ═════════════════════════════════════════════════════════════════════════
    // GENERATE SALARY
    // ═════════════════════════════════════════════════════════════════════════

    public SalaryResponse generateSalary(Long userId, int month, int year) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));

        if (user.getMonthlySalary() == null)
            throw new BadRequestException("Salary not assigned for: " + user.getEmail());
        if (salaryRepository.existsByUserAndMonthAndYear(user, month, year))
            throw new DuplicateResourceException("Salary already generated for " + month + "/" + year);

        int workingDaysPerMonth = settingService.getWorkingDaysPerMonth();
        int leavesPerYear       = settingService.getLeavesPerYear();

        YearMonth ym   = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to   = ym.atEndOfMonth();

        int presentDays          = attendanceRepository.countPresentDates(user, from, to);
        int halfDays             = attendanceRepository.countHalfDayDates(user, from, to);
        int deductibleAbsentDays = attendanceRepository.countSalaryDeductibleAbsentDays(user, from, to);
        int deductibleHalfDays   = attendanceRepository.countSalaryDeductibleHalfDays(user, from, to);

        int manualUnpaidLeaves = countManualUnpaidLeavesInMonth(user, from, to);
        int manualPaidLeaves   = countManualPaidLeavesInMonth(user, from, to);

        BigDecimal gross       = user.getMonthlySalary();
        BigDecimal perDayRate  = gross.divide(BigDecimal.valueOf(workingDaysPerMonth), 4, RoundingMode.HALF_UP);
        BigDecimal halfDayRate = perDayRate.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);

        BigDecimal absentDeduction = perDayRate
                .multiply(BigDecimal.valueOf(deductibleAbsentDays))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal halfDayDeduction = halfDayRate
                .multiply(BigDecimal.valueOf(deductibleHalfDays))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal unpaidLeaveDeduction = perDayRate
                .multiply(BigDecimal.valueOf(manualUnpaidLeaves))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalDeduction = absentDeduction.add(halfDayDeduction).add(unpaidLeaveDeduction);
        BigDecimal netSalary = gross.subtract(totalDeduction).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        String notes = String.format(
                "Working days: %d | Leaves/year: %d | " +
                        "Deductible absent: %d | Deductible half-days: %d | Manual unpaid: %d",
                workingDaysPerMonth, leavesPerYear,
                deductibleAbsentDays, deductibleHalfDays, manualUnpaidLeaves);

        Salary salary = Salary.builder()
                .user(user).month(month).year(year)
                .grossSalary(gross)
                .perDayRate(perDayRate.setScale(2, RoundingMode.HALF_UP))
                .workingDays(workingDaysPerMonth)
                .presentDays(presentDays).halfDays(halfDays)
                .paidLeaves(manualPaidLeaves).unpaidLeaves(manualUnpaidLeaves)
                .halfDayDeduction(halfDayDeduction)
                .unpaidLeaveDeduction(unpaidLeaveDeduction.add(absentDeduction))
                .deductionAmount(totalDeduction)
                .netSalary(netSalary).notes(notes)
                .build();

        salaryRepository.save(salary);
        log.info("Salary {}/{} | {} | gross={} deduction={} net={}",
                month, year, user.getEmail(), gross, totalDeduction, netSalary);
        return mapToResponse(salary);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GENERATE PDF — returns bytes only, NO disk save
    // ═════════════════════════════════════════════════════════════════════════

    public byte[] generatePdf(Long userId, int month, int year) throws Exception {
        SalaryResponse slip = getSalarySlip(userId, month, year);
        User emp = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        String monthName = MONTH_NAMES[month - 1];
        String period    = monthName + " " + year;

        // ── Employee ID: use FIN-XXXX code, fallback to raw id if null ────────
        String empId = (emp.getEmployeeCode() != null && !emp.getEmployeeCode().isBlank())
                ? emp.getEmployeeCode()
                : String.valueOf(emp.getId());

        // ── Colors ────────────────────────────────────────────────────────────
        BaseColor lightBlue  = new BaseColor(208, 235, 248);
        BaseColor borderGrey = new BaseColor(200, 210, 220);
        BaseColor rowShade   = new BaseColor(240, 242, 245);
        BaseColor headerBlue = new BaseColor(225, 235, 248);
        BaseColor darkText   = new BaseColor(30,  30,  30);
        BaseColor mutedText  = new BaseColor(90,  90,  90);
        BaseColor blueAmt    = new BaseColor(0,  100, 170);

        // ── Fonts ─────────────────────────────────────────────────────────────
        Font fSlipTitle  = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, mutedText);
        Font fPeriod     = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD,   darkText);
        Font fSection    = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD,   darkText);
        Font fLabel      = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL, darkText);
        Font fValue      = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL, darkText);
        Font fNetBig     = new Font(Font.FontFamily.HELVETICA, 15, Font.BOLD,   darkText);
        Font fNetSub     = new Font(Font.FontFamily.HELVETICA, 8,  Font.NORMAL, mutedText);
        Font fPayInfo    = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL, darkText);
        Font fTblHdr     = new Font(Font.FontFamily.HELVETICA, 9,  Font.BOLD,   darkText);
        Font fTblRow     = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL, darkText);
        Font fTblBold    = new Font(Font.FontFamily.HELVETICA, 9,  Font.BOLD,   darkText);
        Font fTotalLabel = new Font(Font.FontFamily.HELVETICA, 9,  Font.BOLD,   darkText);
        Font fTotalSub   = new Font(Font.FontFamily.HELVETICA, 8,  Font.NORMAL, mutedText);
        Font fTotalAmt   = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD,   blueAmt);
        Font fWords      = new Font(Font.FontFamily.HELVETICA, 8,  Font.ITALIC, mutedText);
        Font fSign       = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL, darkText);
        Font fSignBold   = new Font(Font.FontFamily.HELVETICA, 9,  Font.BOLD,   darkText);

        Document doc = new Document(PageSize.A4, 40, 40, 30, 30);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();

        // ─────────────────────────────────────────────────────────────────────
        // TOP ROW — Logo left | "Payslip For the Month / September2025" right
        // ─────────────────────────────────────────────────────────────────────
        PdfPTable topRow = new PdfPTable(2);
        topRow.setWidthPercentage(100);
        topRow.setWidths(new float[]{1f, 1f});
        topRow.setSpacingAfter(0);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPaddingBottom(8);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        try {
//            byte[] logoBytes = Files.readAllBytes(Paths.get(logoPath));
            Image logo = Image.getInstance("https://hrms-fe-ten.vercel.app/logo.png");
            logo.scaleToFit(130, 48);
            logoCell.addElement(logo);
        } catch (Exception e) {
            log.warn("Logo not found: {}", e.getMessage());
            logoCell.addElement(new Paragraph("Sitegenius", fPeriod));
        }
        topRow.addCell(logoCell);

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setPaddingBottom(8);
        Paragraph slipTitle = new Paragraph("Payslip For the Month", fSlipTitle);
        slipTitle.setAlignment(Element.ALIGN_RIGHT);
        Paragraph slipPeriod = new Paragraph(monthName + year, fPeriod);
        slipPeriod.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(slipTitle);
        titleCell.addElement(slipPeriod);
        topRow.addCell(titleCell);

        doc.add(topRow);
        addHRule(doc, borderGrey);

        // ─────────────────────────────────────────────────────────────────────
        // EMPLOYEE SUMMARY ROW
        // ─────────────────────────────────────────────────────────────────────
        PdfPTable empRow = new PdfPTable(2);
        empRow.setWidthPercentage(100);
        empRow.setWidths(new float[]{1.4f, 1f});
        empRow.setSpacingBefore(12);
        empRow.setSpacingAfter(14);

        PdfPCell empCell = new PdfPCell();
        empCell.setBorder(Rectangle.NO_BORDER);
        empCell.setPaddingRight(12);

        Paragraph empHdr = new Paragraph("EMPLOYEE SUMMARY", fSection);
        empHdr.setSpacingAfter(10);
        empCell.addElement(empHdr);

        // ── Employee ID now shows "FIN-1001" instead of raw database id ───────
        String[][] empFields = {
                {"Employee Name", "Mr. " + emp.getFullName()},
                {"Employee ID",   empId},                          // ← CHANGED
                {"Pay Period",    period},
                {"Pay Date",      String.format("%02d/%02d/%d", 5, month, year)},
        };
        for (String[] f : empFields) {
            PdfPTable r = new PdfPTable(3);
            r.setWidthPercentage(100);
            r.setWidths(new float[]{1.5f, 0.15f, 2f});
            PdfPCell c1 = noB(new PdfPCell(new Phrase(f[0], fLabel)));
            PdfPCell c2 = noB(new PdfPCell(new Phrase(":", fLabel)));
            PdfPCell c3 = noB(new PdfPCell(new Phrase(f[1], fValue)));
            c1.setPaddingBottom(4); c2.setPaddingBottom(4); c3.setPaddingBottom(4);
            r.addCell(c1); r.addCell(c2); r.addCell(c3);
            empCell.addElement(r);
        }
        empRow.addCell(empCell);

        // Net pay box
        PdfPCell netBoxCell = new PdfPCell();
        netBoxCell.setBorder(Rectangle.NO_BORDER);
        netBoxCell.setVerticalAlignment(Element.ALIGN_TOP);

        PdfPTable netBox = new PdfPTable(1);
        netBox.setWidthPercentage(100);

        PdfPCell netTop = new PdfPCell();
        netTop.setBackgroundColor(lightBlue);
        netTop.setBorderColor(borderGrey);
        netTop.setBorder(Rectangle.BOX);
        netTop.setPadding(12);
        Paragraph netAmt = new Paragraph("Rs." + fmtComma(slip.getNetSalary()), fNetBig);
        netAmt.setSpacingAfter(3);
        Paragraph netLblP = new Paragraph("Employee Net Pay", fNetSub);
        netTop.addElement(netAmt);
        netTop.addElement(netLblP);
        netBox.addCell(netTop);

        PdfPCell netBottom = new PdfPCell();
        netBottom.setBorder(Rectangle.BOX);
        netBottom.setBorderColor(borderGrey);
        netBottom.setPadding(10);
        int lop = slip.getUnpaidLeaves() != null ? slip.getUnpaidLeaves() : 0;
        Paragraph payDaysPara = new Paragraph("Pay Days:" + slip.getWorkingDays(), fPayInfo);
        payDaysPara.setSpacingAfter(3);
        Paragraph lopPara = new Paragraph("LOP Date:" + lop, fPayInfo);
        netBottom.addElement(payDaysPara);
        netBottom.addElement(lopPara);
        netBox.addCell(netBottom);

        netBoxCell.addElement(netBox);
        empRow.addCell(netBoxCell);
        doc.add(empRow);

        // ─────────────────────────────────────────────────────────────────────
        // EARNINGS | DEDUCTIONS
        // ─────────────────────────────────────────────────────────────────────
        PdfPTable twoCol = new PdfPTable(2);
        twoCol.setWidthPercentage(100);
        twoCol.setWidths(new float[]{1f, 1f});
        twoCol.setSpacingAfter(14);

        PdfPCell earCell = new PdfPCell();
        earCell.setBorder(Rectangle.BOX);
        earCell.setBorderColor(borderGrey);
        earCell.setPadding(0);
        PdfPTable earT = new PdfPTable(2);
        earT.setWidthPercentage(100);
        earT.setWidths(new float[]{2f, 1.2f});
        tblHeader(earT, new String[]{"Earnings", "Amount"}, fTblHdr, headerBlue, borderGrey);
        tblRow(earT, "Basic",                "Rs." + fmtNoComma(slip.getGrossSalary()), fTblRow,  BaseColor.WHITE, borderGrey);
        tblRow(earT, "House Rent Allowance", "Rs.0.00",                                 fTblRow,  rowShade,        borderGrey);
        tblRow(earT, "Gross Earnings",       "Rs." + fmtNoComma(slip.getGrossSalary()), fTblBold, BaseColor.WHITE, borderGrey);
        earCell.addElement(earT);
        twoCol.addCell(earCell);

        PdfPCell dedCell = new PdfPCell();
        dedCell.setBorder(Rectangle.BOX);
        dedCell.setBorderColor(borderGrey);
        dedCell.setPadding(0);
        PdfPTable dedT = new PdfPTable(2);
        dedT.setWidthPercentage(100);
        dedT.setWidths(new float[]{2f, 1.2f});
        tblHeader(dedT, new String[]{"Deductions", "Amount"}, fTblHdr, headerBlue, borderGrey);
        tblRow(dedT, "Income Tax",       "Rs.0.00",                                                          fTblRow,  BaseColor.WHITE, borderGrey);
        tblRow(dedT, "Provident Fund",   "Rs.0.00",                                                          fTblRow,  rowShade,        borderGrey);
        BigDecimal totalDeduct = slip.getDeductionAmount() != null ? slip.getDeductionAmount() : BigDecimal.ZERO;
        tblRow(dedT, "Total Deductions", "Rs." + fmtNoComma(totalDeduct),                                    fTblBold, BaseColor.WHITE, borderGrey);
        dedCell.addElement(dedT);
        twoCol.addCell(dedCell);

        doc.add(twoCol);

        // ─────────────────────────────────────────────────────────────────────
        // TOTAL NET PAYABLE bar
        // ─────────────────────────────────────────────────────────────────────
        PdfPTable netBar = new PdfPTable(2);
        netBar.setWidthPercentage(100);
        netBar.setWidths(new float[]{2f, 1f});
        netBar.setSpacingAfter(6);

        PdfPCell netBarLeft = new PdfPCell();
        netBarLeft.setBorder(Rectangle.BOX);
        netBarLeft.setBorderColor(borderGrey);
        netBarLeft.setPadding(10);
        Paragraph netBarTitle = new Paragraph("TOTAL NET PAYABLE", fTotalLabel);
        netBarTitle.setSpacingAfter(2);
        Paragraph netBarSub = new Paragraph("Gross Earnings - Total Deductions", fTotalSub);
        netBarLeft.addElement(netBarTitle);
        netBarLeft.addElement(netBarSub);
        netBar.addCell(netBarLeft);

        PdfPCell netBarRight = new PdfPCell(new Phrase("Rs." + fmtComma(slip.getNetSalary()), fTotalAmt));
        netBarRight.setBackgroundColor(lightBlue);
        netBarRight.setBorder(Rectangle.BOX);
        netBarRight.setBorderColor(borderGrey);
        netBarRight.setPadding(10);
        netBarRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        netBarRight.setVerticalAlignment(Element.ALIGN_MIDDLE);
        netBar.addCell(netBarRight);

        doc.add(netBar);

        Paragraph wordsP = new Paragraph(
                "Amount In Words : Indian Rupee " + amountInWords(slip.getNetSalary()) + " Only",
                fWords);
        wordsP.setAlignment(Element.ALIGN_RIGHT);
        wordsP.setSpacingAfter(22);
        doc.add(wordsP);

        // ─────────────────────────────────────────────────────────────────────
        // STAMP + SIGNATORY
        // ─────────────────────────────────────────────────────────────────────
        PdfPTable sigTable = new PdfPTable(1);
        sigTable.setWidthPercentage(38);
        sigTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

        PdfPCell sigCell = new PdfPCell();
        sigCell.setBorder(Rectangle.NO_BORDER);
        sigCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        try {
            byte[] stampBytes = Files.readAllBytes(Paths.get(stampPath));
            Image stamp = Image.getInstance(stampBytes);
            stamp.scaleToFit(125, 125);
            stamp.setAlignment(Element.ALIGN_CENTER);
            sigCell.addElement(stamp);
        } catch (Exception e) {
            log.warn("Stamp not found: {}", e.getMessage());
        }

        for (String[] line : new String[][]{
                {"Authorized Signatory,",         "normal"},
                {"Rupesh Sulle",                "bold"},
                {"(Director Hr)",                  "normal"},
                {"Sitegenius .",               "normal"},
        }) {
            Font f = line[1].equals("bold") ? fSignBold : fSign;
            Paragraph p = new Paragraph(line[0], f);
            p.setAlignment(Element.ALIGN_CENTER);
            sigCell.addElement(p);
        }

        sigTable.addCell(sigCell);
        doc.add(sigTable);

        doc.close();
        log.info("Salary PDF generated for userId={} empCode={} month={} year={}", userId, empId, month, year);
        return out.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filename helper — Format: FullName_SalarySlip_May_2026.pdf
    // ─────────────────────────────────────────────────────────────────────────

    public String buildPdfFileName(Long userId, int month, int year) {
        try {
            User u = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));

            String name = (u.getFullName() != null ? u.getFullName() : "Employee")
                    .trim()
                    .replaceAll("\\s+", "_")
                    .replaceAll("[^a-zA-Z0-9_]", "");

            String monthName = MONTH_NAMES[month - 1];
            return name + "_SalarySlip_" + monthName + "_" + year + ".pdf";

        } catch (Exception e) {
            String monthName = MONTH_NAMES[month - 1];
            return "SalarySlip_" + monthName + "_" + year + ".pdf";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF TABLE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void addHRule(Document doc, BaseColor color) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingAfter(4);
        PdfPCell c = new PdfPCell(new Phrase(" "));
        c.setFixedHeight(1.5f);
        c.setBackgroundColor(color);
        c.setBorder(Rectangle.NO_BORDER);
        t.addCell(c);
        doc.add(t);
    }

    private PdfPCell noB(PdfPCell cell) {
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private void tblHeader(PdfPTable table, String[] headers, Font font,
                           BaseColor bg, BaseColor borderColor) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, font));
            c.setBackgroundColor(bg);
            c.setBorderColor(borderColor);
            c.setPadding(7);
            c.setBorderWidth(0.5f);
            table.addCell(c);
        }
    }

    private void tblRow(PdfPTable table, String label, String value,
                        Font font, BaseColor bg, BaseColor borderColor) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, font));
        PdfPCell c2 = new PdfPCell(new Phrase(value, font));
        for (PdfPCell c : new PdfPCell[]{c1, c2}) {
            c.setBackgroundColor(bg);
            c.setBorderColor(borderColor);
            c.setPadding(7);
            c.setBorderWidth(0.5f);
        }
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c1);
        table.addCell(c2);
    }

    private String fmtNoComma(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format("%.2f", v);
    }

    private String fmtComma(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format("%,.2f", v);
    }

    private String amountInWords(BigDecimal amount) {
        if (amount == null) return "Zero";
        long val = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        if (val == 0) return "Zero";
        String[] ones = {"","One","Two","Three","Four","Five","Six","Seven","Eight","Nine",
                "Ten","Eleven","Twelve","Thirteen","Fourteen","Fifteen","Sixteen",
                "Seventeen","Eighteen","Nineteen"};
        String[] tens = {"","","Twenty","Thirty","Forty","Fifty","Sixty","Seventy","Eighty","Ninety"};
        StringBuilder sb = new StringBuilder();
        if (val >= 10000000) { sb.append(ones[(int)(val/10000000)]).append(" Crore ");   val %= 10000000; }
        if (val >= 100000)   { sb.append(below100((int)(val/100000), ones, tens)).append(" Lakh ");     val %= 100000; }
        if (val >= 1000)     { sb.append(below100((int)(val/1000),   ones, tens)).append(" Thousand "); val %= 1000; }
        if (val >= 100)      { sb.append(ones[(int)(val/100)]).append(" Hundred ");      val %= 100; }
        if (val > 0)         { sb.append(below100((int) val, ones, tens)); }
        return sb.toString().trim();
    }

    private String below100(int n, String[] ones, String[] tens) {
        if (n < 20) return ones[n];
        return tens[n / 10] + (n % 10 != 0 ? " " + ones[n % 10] : "");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // OTHER SERVICE METHODS
    // ═════════════════════════════════════════════════════════════════════════

    private int countManualUnpaidLeavesInMonth(User user, LocalDate from, LocalDate to) {
        return leaveRepository.findAll().stream()
                .filter(l -> l.getUser().getId().equals(user.getId()))
                .filter(l -> l.getStatus() == com.finabits.hrms.enums.LeaveStatus.APPROVED)
                .filter(l -> l.getLeaveType() == LeaveType.UNPAID)
                .filter(l -> !l.isAutoGenerated())
                .mapToInt(l -> {
                    LocalDate s = l.getStartDate().isBefore(from) ? from : l.getStartDate();
                    LocalDate e = l.getEndDate().isAfter(to)      ? to   : l.getEndDate();
                    if (s.isAfter(e)) return 0;
                    return countWorkingDaysInRange(s, e);
                }).sum();
    }

    private int countManualPaidLeavesInMonth(User user, LocalDate from, LocalDate to) {
        return leaveRepository.findAll().stream()
                .filter(l -> l.getUser().getId().equals(user.getId()))
                .filter(l -> l.getStatus() == com.finabits.hrms.enums.LeaveStatus.APPROVED)
                .filter(l -> l.getLeaveType() == LeaveType.PAID)
                .filter(l -> !l.isAutoGenerated())
                .mapToInt(l -> {
                    LocalDate s = l.getStartDate().isBefore(from) ? from : l.getStartDate();
                    LocalDate e = l.getEndDate().isAfter(to)      ? to   : l.getEndDate();
                    if (s.isAfter(e)) return 0;
                    return countWorkingDaysInRange(s, e);
                }).sum();
    }

    private int countWorkingDaysInRange(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            if (cur.getDayOfWeek() != DayOfWeek.SUNDAY) count++;
            cur = cur.plusDays(1);
        }
        return count;
    }

    /** Used by SalaryRequestService to prevent duplicate requests */
    public boolean existsForUser(Long userId, int month, int year) {
        return userRepository.findById(userId)
                .map(user -> salaryRepository.existsByUserAndMonthAndYear(user, month, year))
                .orElse(false);
    }

    public Page<SalaryResponse> getMySalaryHistory(Pageable pageable) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return salaryRepository.findByUserOrderByYearDescMonthDesc(user, pageable).map(this::mapToResponse);
    }

    public Page<SalaryResponse> getEmployeeSalaryHistory(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
        return salaryRepository.findByUserOrderByYearDescMonthDesc(user, pageable).map(this::mapToResponse);
    }

    public SalaryResponse getSalarySlip(Long userId, int month, int year) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
        return mapToResponse(salaryRepository.findByUserAndMonthAndYear(user, month, year)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Salary slip not found for " + month + "/" + year)));
    }

    // ── mapToResponse now includes employeeCode ───────────────────────────────
    private SalaryResponse mapToResponse(Salary s) {
        return SalaryResponse.builder()
                .id(s.getId()).userId(s.getUser().getId())
                .employeeCode(s.getUser().getEmployeeCode())   // NEW
                .employeeName(s.getUser().getFullName())
                .month(s.getMonth()).year(s.getYear())
                .grossSalary(s.getGrossSalary()).perDayRate(s.getPerDayRate())
                .workingDays(s.getWorkingDays()).presentDays(s.getPresentDays())
                .halfDays(s.getHalfDays()).paidLeaves(s.getPaidLeaves())
                .unpaidLeaves(s.getUnpaidLeaves())
                .halfDayDeduction(s.getHalfDayDeduction())
                .unpaidLeaveDeduction(s.getUnpaidLeaveDeduction())
                .deductionAmount(s.getDeductionAmount()).netSalary(s.getNetSalary())
                .notes(s.getNotes()).generatedAt(s.getGeneratedAt())
                .build();
    }
}