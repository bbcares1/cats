package sg.edu.nus.cats.web;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.CategoryCode;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.CalendarService;

/**
 * P11 training calendar. The month grid is rendered server side for the first paint
 * and refreshed from {@code /api/v1/calendar} in the browser, so the REST call is visible
 * in the network panel during the demonstration.
 */
@Controller
@RequestMapping("/calendar")
public class CalendarController {

    private final CurrentUser currentUser;
    private final CalendarService calendar;
    private final Clock clock;

    public CalendarController(CurrentUser currentUser, CalendarService calendar, Clock clock) {
        this.currentUser = currentUser;
        this.calendar = calendar;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String month(@RequestParam(required = false) String month,
            @RequestParam(required = false) CategoryCode category,
            @RequestParam(defaultValue = "false") boolean team, Model model) {
        Employee viewer = currentUser.require();
        YearMonth selected = parse(month);
        CalendarService.CalendarMonth view = calendar.month(viewer.getId(), selected, category, team);
        model.addAttribute("view", view);
        model.addAttribute("month", selected.toString());
        model.addAttribute("category", category);
        model.addAttribute("categories", CategoryCode.values());
        model.addAttribute("teamView", team);
        model.addAttribute("months", recentMonths(selected));
        model.addAttribute("flatItems", view.weeks().stream()
                .flatMap(week -> week.days().stream())
                .filter(day -> !day.items().isEmpty())
                .toList());
        return "calendar/month";
    }

    private YearMonth parse(String value) {
        if (value == null || value.isBlank()) {
            return YearMonth.from(java.time.LocalDate.now(clock));
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (RuntimeException ex) {
            throw new sg.edu.nus.cats.support.BusinessException(sg.edu.nus.cats.support.ErrorCode.INVALID_REQUEST,
                    "The month must be given as YYYY-MM.");
        }
    }

    /** Twelve months either side of the selected month, for the dropdown. */
    private List<String> recentMonths(YearMonth selected) {
        List<String> months = new ArrayList<>();
        for (int offset = -6; offset <= 6; offset++) {
            months.add(selected.plusMonths(offset).toString());
        }
        return months;
    }
}
