/**
 * Created by Nuno on 26-12-2016.
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
window.onload = function () {
    $("#agenda").addClass("loading")
    getRequest("agenda/details", dataRequestedHandler);

    if (document.cookie.indexOf("timezone") >= 0) {
        // They've been here before.

    }
    else {
        // set a new cookie
        expiry = new Date();
        expiry.setTime(expiry.getTime()+(10*60*1000)); // Ten minutes

        // Date()'s toGMTSting() method will format the date correctly for a cookie
        document.cookie = "timezone="+moment.tz.guess()+"; expires=" + expiry.toGMTString();

    }
};

var agenda;

var dataRequestedHandler = function (data) {

    agenda = new Agenda(data);
    var tableElement = agenda.render();

    //var insertionElement = document.getElementById("agenda");
    //insertionElement.appendChild();
    $("#agenda").html(tableElement);
    $("#monthDisplay").html(agenda.startDateUtc.format("MMMM"));
    $("#agenda").removeClass("loading")
};


/**
 *
 * @param {type} data
 * @returns {Agenda}
 */
var Agenda = function (data) {
    this.numberOfDays = data.numberOfDays;
    this.startDateUtc = moment(data.startDateUtc).utc();
    this.entries = [];

    /**
     *
     * @param {type} entries
     * @returns {undefined}
     */
    this.setEntries = function (entries) {

        var newDate = moment(this.startDateUtc).utc();

        var momentEntries = entries.map(function (entry) {
            entry.startTimeUtc = moment(entry.startTimeUtc).utc();
            entry.startTimeLocal = entry.startTimeUtc.clone().local();
            return entry;
        });

        for (var i = 0; i < this.numberOfDays; i++) {
            var adder = i === 0 ? i : 1;
            this.entries.push({
                header: newDate.add(adder, "Day").format("D dddd"),
                Date: newDate.format("DD-MM-YYYY"),
                entriesForDay: momentEntries.filter(function (entry) {
                        var test = newDate.isSame(entry.startTimeUtc, 'day');

                        if (test) {
                            entry.startTimeLocalId = entry.startTimeLocal.format("DD-MM-YYYY.H:mm");
                        }
                        entry.isToErase = false;
                        return test;
                    })
            });
        }

        console.assert(entries.length === (this.entries[0].entriesForDay.length +
                + this.entries[1].entriesForDay.length
                + this.entries[2].entriesForDay.length
                + this.entries[3].entriesForDay.length
            ), { "message": "Records Mismatch" });
    };
    this.setEntries(data.entries);

    /**
     *
     * @param {type} day
     * @param {type} entry
     * @returns {undefined}
     */
    this.addNewEntry = function (day, entry) {
        if (!this.entries[day].entriesForDay.contains(entry))
            this.entries[day].entriesForDay.push(entry);
    };

    /**
     *
     * @param {type} day
     * @param {type} entry
     * @returns {undefined}
     */
    this.removeEntry = function (day, elementId) {
        entryObj = this.entries[day].entriesForDay.find(function (el) { return el.startTimeLocalId === elementId });
        var index = this.entries[day].entriesForDay.indexOf(entryObj);
        this.entries[day].entriesForDay[index].isToErase= true;
    };

    /**
     *
     * @param {int} id
     * @param {int} state
     * @param {string} startTimeLocalId
     * @param {moment} startTimeLocal
     * @param {moment} startTimeUtc
     * @returns {Agenda.newEntry.appAnonym$3}
     */
    this.newEntry = function (id, state, startTimeLocalId, startTimeLocal, startTimeUtc) {
        return {
            id: id,
            state: state,
            startTimeLocal: startTimeLocal,
            startTimeLocalId: startTimeLocalId,
            startTimeUtc: startTimeUtc,
            isToErase : false
        };
    };
};

/**
 *
 * @returns {Element|Agenda.prototype.render.table}
 */
Agenda.prototype.render = function () {
    var table = document.createElement("table");
    table.setAttribute("class", "table table-bordered");
    table.appendChild(this.buildTableHeader());
    table.appendChild(this.buildTableBody());
    return table;
};


/**
 *
 * @returns {Element|Agenda.prototype.buildTableHeader.tableHead}
 */
Agenda.prototype.buildTableHeader = function () {

    var tableHead = document.createElement("thead");
    var tableHeadTr = document.createElement("tr");

    var tableHeadTh = document.createElement("th");
    tableHeadTh.appendChild(document.createTextNode("-"));
    tableHeadTr.appendChild(tableHeadTh);

    for (var i = 0; i < this.numberOfDays; i++) {
        var tableHeadTh = document.createElement("th");
        tableHeadTh.appendChild(document.createTextNode(this.entries[i].header));
        tableHeadTr.appendChild(tableHeadTh);
    }

    tableHead.appendChild(tableHeadTr);

    return tableHead;
};

/**
 *
 * @returns {Element|Agenda.prototype.buildTableBody.tablebody}
 */
Agenda.prototype.buildTableBody = function () {

    var hours = this.calculateHoursHelper();
    var tablebody = document.createElement("tbody");

    for (var i = 0; i < ((hours.length) - 1) ; i++) {
        var tablebodyTr = document.createElement("tr");
        for (var e = 0; e < this.numberOfDays + 1; e++) {
            var self = document.createElement("td");
            if (e === 0) {
                self.appendChild(document.createTextNode(hours[i]));
            } else {

                var elementId = this.entries[(e - 1)].Date + "." + hours[i];

                var entry = (this.entries[(e - 1)]).entriesForDay.find(function (entry) {
                    var test = entry.startTimeLocalId === elementId;
                    return test;
                });

                self.addEventListener("click", tableBodyAddEntryHandler);
                self.setAttribute("id", elementId);

                if (entry !== undefined) {
                    if (entry.state) {
                        self.appendChild(document.createTextNode("Free"));
                        self.removeEventListener("click", tableBodyAddEntryHandler);
                        self.addEventListener("click", tableBodyRemoveEntryHandler);
                        self.classList.toggle("success");
                    } else {
                        self.appendChild(document.createTextNode("Pacient"));
                        self.classList.toggle("alert");
                        self.removeEventListener("click", tableBodyAddEntryHandler);
                    }
                }
            }
            tablebodyTr.appendChild(self);
        }
        tablebody.appendChild(tablebodyTr);
    }
    return tablebody;
};


/**
 *
 * @returns {Array}
 */
Agenda.prototype.calculateHoursHelper = function () {
    var start = 8;
    var minutes = ["00", "30"];
    var hours = [];

    for (var i = start; i < 41; i++) {

        if (hours.length === 0) {
            hours.push(start + ":" + minutes[0]);
            continue;
        }

        if (hours[hours.length - 1].slice(-2) === "00") {
            hours.push(start + ":" + minutes[1]);
            ++start;
        } else {
            hours.push(start + ":" + minutes[0]);
        }
    }
    return hours;
};

var tableBodyAddEntryHandler = function (event) {
    event.preventDefault();
    this.setAttribute("class", "success");

    this.textContent = "Free";

    var startTimeLocal = moment(this.id, "DD-MM-YYYY.H:mm");
    var startTimeUtc = startTimeLocal.clone().utc();
    var entry = agenda.newEntry(0, true, this.id, startTimeLocal, startTimeUtc);

    var dayIndex = startTimeUtc.diff(agenda.startDateUtc, 'days');

    agenda.addNewEntry(dayIndex, entry);

    this.removeEventListener("click", tableBodyAddEntryHandler);
    this.addEventListener("click", tableBodyRemoveEntryHandler);
};

var tableBodyRemoveEntryHandler = function (event) {
    event.preventDefault();
    this.textContent = "";
    this.setAttribute("class", "");
    var startTimeLocal = moment(this.id, "DD-MM-YYYY.H:mm");
    var startTimeUtc = startTimeLocal.clone().utc();
    var dayIndex = startTimeUtc.diff(agenda.startDateUtc, 'days');



    agenda.removeEntry(dayIndex, this.id);

    this.removeEventListener("click", tableBodyRemoveEntryHandler);
    this.addEventListener("click", tableBodyAddEntryHandler);

};


var getRequest = function (url, callback)
{
    $.get(url, callback, "json");
};


$("#backwards").click(function (event) {
    event.preventDefault();
    var data = agenda.startDateUtc.subtract(4, "Day").toISOString();

    $.ajax({
        url: "agenda/details",
        contentType: "application/json",
        data: { start: data },
        type: "GET",
        success: function (result, status, xhr) {
            dataRequestedHandler(result)
        },
        error: function (xhr, status, error) {
            alert("Some error" + error);
        }
    });

});

$("#foward").click(function (event) {
    event.preventDefault();
    var data = agenda.startDateUtc.add(4, "Day").toISOString();

     $.ajax({
        url: "agenda/details",
        contentType: "application/json",
        data: { start: data },
        type: "GET",
        success: function (result, status, xhr) {
            dataRequestedHandler(result)
        },
        error: function (xhr, status, error) {
            alert("Some error" + error);
        }
    });


});

$("#save").click(function (event) {
    event.preventDefault();

    var $input = $("input[name='csrfToken']");
    var token = $input.attr("value");

    $.ajax({
        url: "agenda/save?csrfToken=" + token,
        contentType: "application/json",
        data: JSON.stringify(agenda.entries),
        type: "POST",
        success: function (result, status, xhr) {
            alert(result.message);
        },
        error: function (xhr, status, error) {
            alert("Some error" + error);
        }
    });

});


var convertToLocalTime = function () {
    var $element = $(this);

    var $elements = $element.find("[data-couch-date]");

    var toLocal = function () {
        var $ele = $(this);

        var $date = $ele.attr("data-couch-date");

        var utcDate = moment.utc($date, "DD-MM-YYYY HH:mm:ss");
        var localDate = moment(utcDate, "DD-MM-YYYY HH:mm:ss");

        if ($ele.is("h5")) {
            $ele.html(localDate.format("DD MMMM YYYY"));
        }
        else {
            $ele.html(localDate.format("HH:mm"));
        }

    };

    $elements.each(toLocal);

}


/*
 *
 * ///////////////////////////////////////////////////////////////////////////////////////
 *
 */

if (![].contains) {
    Object.defineProperty(Array.prototype, 'contains', {
        enumerable: false,
        configurable: true,
        writable: true,
        value: function (searchElement/*, fromIndex*/) {
            if (this === undefined || this === null) {
                throw new TypeError('Cannot convert this value to object');
            }
            var O = Object(this);
            var len = parseInt(O.length) || 0;
            if (len === 0) {
                return false;
            }
            var n = parseInt(arguments[1]) || 0;
            if (n >= len) {
                return false;
            }
            var k;
            if (n >= 0) {
                k = n;
            } else {
                k = len + n;
                if (k < 0)
                    k = 0;
            }
            while (k < len) {
                var currentElement = O[k];
                if (searchElement === currentElement ||
                    searchElement !== searchElement && currentElement !== currentElement
                ) {
                    return true;
                }
                k++;
            }
            return false;
        }
    });
}

if (!Array.prototype.find) {
    Array.prototype.find = function (predicate) {
        if (this === null) {
            throw new TypeError('Array.prototype.find called on null or undefined');
        }
        if (typeof predicate !== 'function') {
            throw new TypeError('predicate must be a function');
        }
        var list = Object(this);
        var length = list.length >>> 0;
        var thisArg = arguments[1];
        var value;

        for (var i = 0; i < length; i++) {
            value = list[i];
            if (predicate.call(thisArg, value, i, list)) {
                return value;
            }
        }
        return undefined;
    };
}

if (!Array.prototype.map) {

    Array.prototype.map = function (callback, thisArg) {

        var T, A, k;

        if (this == null) {
            throw new TypeError(' this is null or not defined');
        }

        //  1. Let O be the result of calling ToObject passing the |this|
        //    value as the argument.
        var O = Object(this);

        // 2. Let lenValue be the result of calling the Get internal
        //    method of O with the argument "length".
        // 3. Let len be ToUint32(lenValue).
        var len = O.length >>> 0;

        // 4. If IsCallable(callback) is false, throw a TypeError exception.
        // See: http://es5.github.com/#x9.11
        if (typeof callback !== 'function') {
            throw new TypeError(callback + ' is not a function');
        }

        // 5. If thisArg was supplied, let T be thisArg; else let T be undefined.
        if (arguments.length > 1) {
            T = thisArg;
        }

        // 6. Let A be a new array created as if by the expression new Array(len)
        //    where Array is the standard built-in constructor with that name and
        //    len is the value of len.
        A = new Array(len);

        // 7. Let k be 0
        k = 0;

        // 8. Repeat, while k < len
        while (k < len) {

            var kValue, mappedValue;

            // a. Let Pk be ToString(k).
            //   This is implicit for LHS operands of the in operator
            // b. Let kPresent be the result of calling the HasProperty internal
            //    method of O with argument Pk.
            //   This step can be combined with c
            // c. If kPresent is true, then
            if (k in O) {

                // i. Let kValue be the result of calling the Get internal
                //    method of O with argument Pk.
                kValue = O[k];

                // ii. Let mappedValue be the result of calling the Call internal
                //     method of callback with T as the this value and argument
                //     list containing kValue, k, and O.
                mappedValue = callback.call(T, kValue, k, O);

                // iii. Call the DefineOwnProperty internal method of A with arguments
                // Pk, Property Descriptor
                // { Value: mappedValue,
                //   Writable: true,
                //   Enumerable: true,
                //   Configurable: true },
                // and false.

                // In browsers that support Object.defineProperty, use the following:
                // Object.defineProperty(A, k, {
                //   value: mappedValue,
                //   writable: true,
                //   enumerable: true,
                //   configurable: true
                // });

                // For best browser support, use the following:
                A[k] = mappedValue;
            }
            // d. Increase k by 1.
            k++;
        }

        // 9. return A
        return A;
    };
}

