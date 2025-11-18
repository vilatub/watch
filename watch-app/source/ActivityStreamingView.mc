using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;
using Toybox.Sensor;
using Toybox.Position;
using Toybox.Timer;
using Toybox.Communications;
using Toybox.Lang;

class ActivityStreamingView extends WatchUi.View {

    private var _heartRate = 0;
    private var _latitude = 0.0;
    private var _longitude = 0.0;
    private var _speed = 0.0;
    private var _altitude = 0.0;
    private var _distance = 0.0;
    private var _isStreaming = false;
    private var _timer;
    private var _lastLat = 0.0;
    private var _lastLon = 0.0;
    private var _connectionStatus = "Ready";

    // Streaming interval in milliseconds
    private const STREAM_INTERVAL = 3000;

    function initialize() {
        View.initialize();
        _timer = new Timer.Timer();
    }

    function onLayout(dc) {
        // Layout initialization
    }

    function onShow() {
        // Enable heart rate sensor
        Sensor.setEnabledSensors([Sensor.SENSOR_HEARTRATE]);
        Sensor.enableSensorEvents(method(:onSensorEvent));

        // Enable GPS
        Position.enableLocationEvents(Position.LOCATION_CONTINUOUS, method(:onPositionEvent));
    }

    function onUpdate(dc) {
        // Clear screen
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        var width = dc.getWidth();
        var height = dc.getHeight();
        var centerX = width / 2;

        // Draw status
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(centerX, 20, Graphics.FONT_SMALL, _connectionStatus, Graphics.TEXT_JUSTIFY_CENTER);

        // Draw heart rate (large)
        dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
        var hrText = _heartRate > 0 ? _heartRate.toString() : "--";
        dc.drawText(centerX, height / 3, Graphics.FONT_NUMBER_HOT, hrText, Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(centerX, height / 3 + 50, Graphics.FONT_TINY, "BPM", Graphics.TEXT_JUSTIFY_CENTER);

        // Draw speed
        var speedKmh = _speed * 3.6; // m/s to km/h
        var speedText = speedKmh.format("%.1f") + " km/h";
        dc.drawText(centerX, height * 2 / 3, Graphics.FONT_SMALL, speedText, Graphics.TEXT_JUSTIFY_CENTER);

        // Draw distance
        var distKm = _distance / 1000.0;
        var distText = distKm.format("%.2f") + " km";
        dc.drawText(centerX, height * 2 / 3 + 25, Graphics.FONT_SMALL, distText, Graphics.TEXT_JUSTIFY_CENTER);

        // Draw streaming indicator
        if (_isStreaming) {
            dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
            dc.fillCircle(width - 15, 15, 5);
        }
    }

    function onHide() {
        Sensor.setEnabledSensors([]);
        Position.enableLocationEvents(Position.LOCATION_DISABLE, method(:onPositionEvent));
        stopStreaming();
    }

    function onSensorEvent(sensorInfo) {
        if (sensorInfo has :heartRate && sensorInfo.heartRate != null) {
            _heartRate = sensorInfo.heartRate;
            WatchUi.requestUpdate();
        }
    }

    function onPositionEvent(info) {
        if (info.position != null) {
            var coords = info.position.toDegrees();
            _latitude = coords[0];
            _longitude = coords[1];

            // Calculate distance
            if (_lastLat != 0.0 && _lastLon != 0.0) {
                _distance += calculateDistance(_lastLat, _lastLon, _latitude, _longitude);
            }
            _lastLat = _latitude;
            _lastLon = _longitude;
        }

        if (info.speed != null) {
            _speed = info.speed;
        }

        if (info.altitude != null) {
            _altitude = info.altitude;
        }

        WatchUi.requestUpdate();
    }

    // Haversine formula for distance calculation
    function calculateDistance(lat1, lon1, lat2, lon2) {
        var R = 6371000.0; // Earth radius in meters
        var dLat = Math.toRadians(lat2 - lat1);
        var dLon = Math.toRadians(lon2 - lon1);
        var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    function startStreaming() {
        if (!_isStreaming) {
            _isStreaming = true;
            _distance = 0.0;
            _lastLat = 0.0;
            _lastLon = 0.0;
            _connectionStatus = "Streaming";
            _timer.start(method(:streamData), STREAM_INTERVAL, true);
            WatchUi.requestUpdate();
        }
    }

    function stopStreaming() {
        if (_isStreaming) {
            _isStreaming = false;
            _connectionStatus = "Stopped";
            _timer.stop();
            WatchUi.requestUpdate();
        }
    }

    function isStreaming() {
        return _isStreaming;
    }

    function streamData() {
        if (!_isStreaming) {
            return;
        }

        // Create data payload
        var data = {
            "type" => "activity_data",
            "timestamp" => System.getTimer(),
            "hr" => _heartRate,
            "lat" => _latitude,
            "lon" => _longitude,
            "speed" => _speed,
            "altitude" => _altitude,
            "distance" => _distance
        };

        // Send via Communications API to companion app
        if (Communications has :transmit) {
            Communications.transmit(data, null, new CommListener());
        }

        System.println("Streaming: HR=" + _heartRate + " Lat=" + _latitude + " Lon=" + _longitude);
    }

    function getHeartRate() {
        return _heartRate;
    }

    function getDistance() {
        return _distance;
    }
}

class CommListener extends Communications.ConnectionListener {
    function initialize() {
        Communications.ConnectionListener.initialize();
    }

    function onComplete() {
        System.println("Data transmitted successfully");
    }

    function onError() {
        System.println("Failed to transmit data");
    }
}
