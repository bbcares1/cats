/* P03 preview: calls the same REST endpoint the API exposes, so the browser
   network panel shows the request during the demonstration. Nothing is written. */
(function () {
    "use strict";

    var form = document.getElementById("application-form");
    var button = document.getElementById("preview-button");
    var panel = document.getElementById("preview-panel");
    if (!form || !button || !panel) {
        return;
    }

    function meta(name) {
        var tag = document.querySelector('meta[name="' + name + '"]');
        return tag ? tag.content : "";
    }

    function number(field) {
        var value = field.value;
        return value === "" || value === null ? null : value;
    }

    function payload() {
        return {
            catalogueId: number(form.querySelector("#catalogueId")),
            categoryCode: value("#categoryCode"),
            courseTitle: value("#courseTitle"),
            providerName: value("#providerName"),
            startDate: value("#startDate"),
            endDate: value("#endDate"),
            startSession: value("#startSession"),
            endSession: value("#endSession"),
            courseFee: number(form.querySelector("#courseFee")),
            justification: value("#justification"),
            workDissemination: value("#workDissemination"),
            clientRequestId: value("#clientRequestId"),
            version: number(form.querySelector("#version"))
        };
    }

    function value(selector) {
        var field = form.querySelector(selector);
        return field && field.value !== "" ? field.value : null;
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

    function money(amount) {
        if (amount === null || amount === undefined) {
            return "0.00";
        }
        return Number(amount).toFixed(2);
    }

    function render(result) {
        var html = "";

        if (result.errors && result.errors.length) {
            html += '<ul class="preview-errors">';
            result.errors.forEach(function (error) {
                html += "<li>" + escape(error) + "</li>";
            });
            html += "</ul>";
        } else {
            html += '<p class="flash flash-success">All rules pass: this application can be submitted.</p>';
        }

        if (result.notices && result.notices.length) {
            html += '<ul class="preview-notices">';
            result.notices.forEach(function (notice) {
                html += "<li>" + escape(notice) + "</li>";
            });
            html += "</ul>";
        }

        if (result.conflicts && result.conflicts.length) {
            html += "<h3>Clashes</h3><ul>";
            result.conflicts.forEach(function (conflict) {
                html += "<li><strong>" + escape(conflict.label) + "</strong> — " + escape(conflict.detail) + "</li>";
            });
            html += "</ul>";
        }

        html += "<h3>Training days</h3>";
        if (!result.schedule || !result.schedule.length) {
            html += '<p class="muted">No training day was generated from these dates and sessions.</p>';
        } else {
            html += '<table><thead><tr><th>Date</th><th>Day</th><th>Session</th><th class="num">Units</th></tr></thead><tbody>';
            result.schedule.forEach(function (row) {
                html += "<tr><td>" + escape(row.date) + "</td><td>" + escape(row.dayOfWeek) + "</td><td>"
                    + escape(row.sessionLabel) + '</td><td class="num">' + (row.units / 2).toFixed(1) + "</td></tr>";
            });
            html += '<tr class="total-row"><td colspan="3">Total</td><td class="num">'
                + (result.totalUnits / 2).toFixed(1) + " day(s)</td></tr>";
            html += "</tbody></table>";
        }

        if (result.excludedDays && result.excludedDays.length) {
            html += "<h3>Days not counted</h3><ul>";
            result.excludedDays.forEach(function (day) {
                html += "<li>" + escape(day.date) + " — " + escape(day.reason) + "</li>";
            });
            html += "</ul>";
        }

        html += "<h3>Entitlement check</h3>";
        if (!result.quotas || !result.quotas.length) {
            html += '<p class="muted">No annual entitlement check was needed.</p>';
        } else {
            html += '<table><thead><tr><th>Year</th><th class="num">Requested</th><th class="num">Available</th>'
                + '<th class="money">Fee (SGD)</th><th class="money">Budget left (SGD)</th><th>Result</th></tr></thead><tbody>';
            result.quotas.forEach(function (quota) {
                html += "<tr><td>" + quota.year + '</td><td class="num">' + (quota.requestedUnits / 2).toFixed(1)
                    + '</td><td class="num">' + (quota.availableUnits / 2).toFixed(1)
                    + '</td><td class="money">' + money(quota.requestedAmount)
                    + '</td><td class="money">' + money(quota.availableAmount)
                    + "</td><td>" + escape(quota.accountStatus) + "</td></tr>";
            });
            html += "</tbody></table>";
        }

        html += '<p class="help">Total fee: $' + money(result.totalFee) + ". Nothing has been saved yet.</p>";
        panel.innerHTML = html;
    }

    button.addEventListener("click", function () {
        panel.innerHTML = '<p class="muted">Checking…</p>';
        var headers = { "Content-Type": "application/json", "Accept": "application/json" };
        var token = meta("_csrf");
        var header = meta("_csrf_header");
        if (token && header) {
            headers[header] = token;
        }
        fetch("/api/v1/applications/preview", {
            method: "POST",
            headers: headers,
            body: JSON.stringify(payload())
        }).then(function (response) {
            return response.json().then(function (body) {
                if (!response.ok) {
                    throw new Error(body.message || "The preview could not be calculated.");
                }
                return body;
            });
        }).then(render).catch(function (error) {
            panel.innerHTML = '<p class="flash flash-error">' + escape(error.message) + "</p>";
        });
    });

    /* Picking a catalogue course fills the form so the employee sees the fee immediately. */
    var catalogue = form.querySelector("#catalogueId");
    if (catalogue) {
        catalogue.addEventListener("change", function () {
            var option = catalogue.options[catalogue.selectedIndex];
            if (!option || !option.value) {
                return;
            }
            form.querySelector("#courseTitle").value = option.text.replace(/\s+\(.*\)$/, "");
            if (option.dataset.category) {
                form.querySelector("#categoryCode").value = option.dataset.category;
            }
            if (option.dataset.fee) {
                form.querySelector("#courseFee").value = Number(option.dataset.fee).toFixed(2);
            }
            if (option.dataset.provider) {
                form.querySelector("#providerName").value = option.dataset.provider;
            }
        });
    }
})();
