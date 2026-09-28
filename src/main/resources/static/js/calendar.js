/* P11 calendar: refreshes the month grid from the public REST endpoint so the
   JSON contract is exercised by the real UI, not only by curl. */
(function () {
    "use strict";

    var form = document.getElementById("calendar-filter");
    var grid = document.getElementById("calendar");
    var title = document.getElementById("calendar-title");
    if (!form || !grid) {
        return;
    }

    function escape(text) {
        if (text === null || text === undefined) {
            return "";
        }
        return String(text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;");
    }

    function params() {
        var month = form.querySelector("#month");
        var category = form.querySelector("#category");
        var team = form.querySelector("#team");
        var query = "?month=" + encodeURIComponent(month ? month.value : "");
        if (category && category.value) {
            query += "&category=" + encodeURIComponent(category.value);
        }
        query += "&scope=" + (team && team.checked ? "team" : "mine");
        return query;
    }

    function weekendOf(day) {
        /* days arrive in Monday-first order, so position 5 and 6 are the weekend */
        var date = new Date(day.date + "T00:00:00");
        var weekday = date.getDay();
        return weekday === 0 || weekday === 6;
    }

    function cell(day) {
        var classes = ["cal-day"];
        if (!day.inMonth) {
            classes.push("out-of-month");
        }
        if (weekendOf(day)) {
            classes.push("weekend");
        }
        if (day.holiday) {
            classes.push("holiday");
        }
        if (day.isToday) {
            classes.push("today");
        }
        var html = '<div class="' + classes.join(" ") + '" data-date="' + escape(day.date) + '">';
        html += '<div class="cal-day-head"><span class="cal-day-number">' + day.dayOfMonth + "</span>"
            + (day.isToday ? '<span class="cal-today">Today</span>' : "") + "</div>";
        if (day.holiday) {
            html += '<div class="cal-holiday">' + escape(day.holiday) + "</div>";
        }
        if (day.items && day.items.length) {
            html += '<ul class="cal-items">';
            day.items.forEach(function (item) {
                html += '<li class="' + (item.own ? "cal-own" : "cal-team") + '">';
                html += item.own
                    ? '<a href="/employee/applications/' + item.applicationId + '">' + escape(item.courseTitle) + "</a>"
                    : "<span>" + escape(item.courseTitle) + "</span>";
                if (!item.own) {
                    html += '<span class="cal-owner">' + escape(item.employeeName) + "</span>";
                }
                html += "</li>";
            });
            html += "</ul>";
        }
        return html + "</div>";
    }

    function render(body) {
        var html = '<div class="cal-head">'
            + "<div>Mon</div><div>Tue</div><div>Wed</div><div>Thu</div><div>Fri</div><div>Sat</div><div>Sun</div></div>";

        var days = body.days || [];
        for (var index = 0; index < days.length; index += 7) {
            html += '<div class="cal-week">';
            days.slice(index, index + 7).forEach(function (day) {
                html += cell(day);
            });
            html += "</div>";
        }

        grid.innerHTML = html;
        if (title) {
            title.innerHTML = "<span>" + escape(body.label) + "</span>"
                + '<span class="muted small">'
                + (body.scope === "team" ? "including your team" : "your training only") + "</span>";
        }
    }

    form.addEventListener("submit", function (event) {
        event.preventDefault();
        fetch("/api/v1/calendar" + params(), { headers: { Accept: "application/json" } })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("The calendar could not be loaded.");
                }
                return response.json();
            })
            .then(function (body) {
                render(body);
                var url = params().replace("scope=" + (body.scope === "team" ? "team" : "mine"),
                    "team=" + (body.scope === "team" ? "true" : "false"));
                window.history.replaceState({}, "", "/calendar" + url);
            })
            .catch(function (error) {
                grid.innerHTML = '<p class="flash flash-error">' + escape(error.message) + "</p>";
            });
    });
})();
