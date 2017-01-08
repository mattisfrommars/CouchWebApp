/**
 * Created by Nuno on 07-01-2017.
 */
$(function(){


    var requestProfessionalAgenda = function (event) {
        event.preventDefault()

        var $btn = $(this);

        var options = {
            url: $btn.attr("data-couch-action"),
            type: "get"
        };

        $.ajax(options).done(function (data) {
            var target = document.getElementById($btn.attr("data-couch-target"));
            var newHtml = renderProfessionalAgenda(data,$btn.attr("data-couch-target"));
            while (target.firstChild) {
                target.removeChild(target.firstChild);
            }
            target.appendChild(newHtml);
            if(data.length > 0)
            target.appendChild(renderLoadMoreBtn($btn.attr("data-couch-action"),$btn.attr("data-couch-target")))
        });

        return false;
    };

    var renderLoadMoreBtn = function (action,target) {

        var btn = document.createElement("button");
        var str = localDate.add(1, 'days').toISOString();
        btn.setAttribute("class","btn requestAgenda");
        btn.setAttribute("data-couch-target", target);
        var patt = new RegExp("&start=");
        if(patt.test(action)){
            var result = action.slice(0,-31);
            btn.setAttribute("data-couch-action", result + "&start=" + str);
        }else{
            btn.setAttribute("data-couch-action", action + "&start=" + str);
        }

        btn.appendChild(document.createTextNode("Next"));


        return btn;
    };

    var renderProfessionalAgenda = function(data,id){

        var mainDiv = document.createElement("div");
        mainDiv.setAttribute("class","col-md-12");


        if(!(data.length > 0)){
            mainDiv.appendChild(document.createTextNode("No Entries"))
        }

        for(var i = 0 ; i < data.length; i++){

            var utcDate = moment.utc(data[i].date);
            localDate = moment(utcDate, "DD-MM-YYYY HH:mm:ss");
            var header = document.createElement("h4");
            header.appendChild(document.createTextNode(localDate.format("DD MMMM YYYY")));
            var paragraph = document.createElement("p")
            var list = document.createElement("ul");
            list.setAttribute("class", "list-inline")

            data[i].entries.forEach(function(element){
                var utcDateIn = moment.utc(element.startDate);
                var localDateIn = moment(utcDateIn, "DD-MM-YYYY HH:mm:ss");

                var elementHolder = document.createElement("li");
                elementHolder.setAttribute("class", "list-group-item");
                var btn = document.createElement("a");
                btn.setAttribute("href", "session/" + element.id);
                btn.appendChild(document.createTextNode(localDateIn.format("HH:mm")))

                elementHolder.appendChild(btn);
                list.appendChild(elementHolder);
            });
            paragraph.appendChild(list);
            paragraph.appendChild(document.createElement("hr"))
            mainDiv.appendChild(header);
            mainDiv.appendChild(paragraph);
        }

        return mainDiv;
    };

    $(document).on('click','.requestAgenda',requestProfessionalAgenda);

});