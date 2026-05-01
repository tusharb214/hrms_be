package com.finabits.hrms.scheduler;

import com.finabits.hrms.entity.User;
import com.finabits.hrms.repository.AttendanceRepository;
import com.finabits.hrms.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutReminderScheduler {

    private final AttendanceRepository attendanceRepository;
    private final EmailService         emailService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");

    /** 6 PM — First checkout reminder */
    @Scheduled(cron = "0 0 18 * * MON-SAT")
    public void remind6PM() {
        sendReminders("6:00 PM", "first",
                "Don't forget to check out before you finish for the day.",
                "Forgetting to check out may result in your attendance being marked as <strong>Half Day</strong>.");
    }

    /** 8 PM — Second checkout reminder */
    @Scheduled(cron = "0 0 20 * * MON-SAT")
    public void remind8PM() {
        sendReminders("8:00 PM", "second",
                "You have not checked out yet.",
                "⚠️ Please check out now. Not checking out will affect your attendance record.");
    }

    /** 10 PM — Final checkout reminder */
    @Scheduled(cron = "0 0 22 * * MON-SAT")
    public void remind10PM() {
        sendReminders("10:00 PM", "final",
                "🚨 FINAL REMINDER — You have not checked out!",
                "⛔ If you do not check out, your attendance will be automatically calculated based on your last check-in only.");
    }

    private void sendReminders(String time, String reminderNum, String headline, String note) {
        LocalDate today     = LocalDate.now();
        String    dateLabel = today.format(DATE_FMT);

        List<User> notOut = attendanceRepository.findUsersCheckedInButNotOut(today);
        if (notOut.isEmpty()) {
            log.info("Checkout {} reminder ({}): everyone checked out ✅", reminderNum, time);
            return;
        }

        int sent = 0;
        for (User emp : notOut) {
            emailService.sendCheckoutReminder(emp.getEmail(), emp.getFullName(), dateLabel, time, headline, note);
            sent++;
        }
        log.info("Checkout {} reminder ({}): sent to {} employees", reminderNum, time, sent);
    }
}