/**
 * Created by Nuno on 07-01-2017.
 */
$(function () {

    var element = $("#video");
    var sessionId = element.attr("data-couch-session")
    var apiKey = element.attr("data-couch-apikey");
    var token = element.attr("data-couch-token")
    var session;
    var publisher;
    var connectionCount = 0;

    function connect() {

        // Replace apiKey and sessionId with your own values:
        session = OT.initSession(apiKey, sessionId);
        session.on({
            connectionCreated: function (event) {
                connectionCount++;
                console.log(connectionCount + ' connections.');
            },
            connectionDestroyed: function (event) {
                connectionCount--;
                console.log(connectionCount + ' connections.');
            },
            sessionDisconnected: function sessionDisconnectHandler(event) {
                // The event is defined by the SessionDisconnectEvent class
                console.log('Disconnected from the session.');
                document.getElementById('disconnectBtn').style.display = 'none';
                if (event.reason == 'networkDisconnected') {
                    alert('Your network connection terminated.')
                }
            }
        });
        // Replace token with your own value:
        session.connect(token, function(error) {
            if (error) {
                console.log('Unable to connect: ', error.message);
            } else {
                session.publish(publisher);
                document.getElementById('disconnectBtn').style.display = 'block';
                console.log('Connected to the session.');
                connectionCount = 1;
            }
        });
    }

    function disconnect() {
        session.disconnect();
    }



    // Replace with the replacement element ID:
    publisher = OT.initPublisher("myVideo");
    publisher.on({
        streamCreated: function (event) {
            console.log("Publisher started streaming.");
        },
        streamDestroyed: function (event) {
            console.log("Publisher stopped streaming. Reason: "
                + event.reason);
        }
    });

    connect();

    session.on('streamCreated', function(event) {
        var subscriberProperties = {insertMode: 'append',fitMode:'cover', width:'100%',height:'480px'};
        var subscriber = session.subscribe(event.stream,
            'othersVideo',
            subscriberProperties,
            function (error) {
                if (error) {
                    console.log(error);
                } else {
                    console.log('Subscriber added.');
                }
            });
    });

});