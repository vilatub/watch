using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;
using Toybox.Sensor;
using Toybox.Position;
using Toybox.Timer;
using Toybox.Communications;
using Toybox.Lang;
using Toybox.Attention;
using Toybox.Application;

class ActivityStreamingView extends WatchUi.View {

    private var _heartRate = 0;
    private var _latitude = 0.0;
    private var _longitude = 0.0;
    private var _speed = 0.0;
    private var _altitude = 0.0;
    private var _distance = 0.0;
    private var _cadence = 0;        // steps per minute (running) or rpm (cycling)
    private var _power = 0;          // watts (if power meter available)
    private var _isStreaming = false;
    private var _timer;
    private var _lastLat = 0.0;
    private var _lastLon = 0.0;
    private var _connectionStatus = "Ready";

    // Lap tracking
    private var _lapCount = 0;
    private var _lapDistance = 0.0;
    private var _lastLapDistance = 0.0;

    // Display mode
    private var _showExtendedData = true;

    // Streaming interval in milliseconds (configurable)
    private var _streamInterval = 3000;

    // Activity type
    private var _activityType = "running";

    // Default intervals
    public static const INTERVAL_1S = 1000;
    public static const INTERVAL_3S = 3000;
    public static const INTERVAL_5S = 5000;
    public static const INTERVAL_10S = 10000;

    // Activity types
    public static const TYPE_RUNNING = "running";
    public static const TYPE_CYCLING = "cycling";
    public static const TYPE_WALKING = "walking";
    public static const TYPE_HIKING = "hiking";
    public static const TYPE_SWIMMING = "swimming";
    public static const TYPE_OTHER = "other";

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

        // Draw battery indicator (top left)
        var battery = System.getSystemStats().battery;
        var batteryColor = Graphics.COLOR_GREEN;
        if (battery < 20) {
            batteryColor = Graphics.COLOR_RED;
        } else if (battery < 50) {
            batteryColor = Graphics.COLOR_YELLOW;
        }
        dc.setColor(batteryColor, Graphics.COLOR_TRANSPARENT);
        dc.drawText(10, 5, Graphics.FONT_XTINY, battery.format("%d") + "%", Graphics.TEXT_JUSTIFY_LEFT);

        // Draw streaming indicator (top right)
        if (_isStreaming) {
            dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
            dc.fillCircle(width - 15, 12, 5);
        }

        // Draw status (top center)
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(centerX, 5, Graphics.FONT_XTINY, _connectionStatus, Graphics.TEXT_JUSTIFY_CENTER);

        // Draw heart rate (large, center)
        dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
        var hrText = _heartRate > 0 ? _heartRate.toString() : "--";
        dc.drawText(centerX, height / 4, Graphics.FONT_NUMBER_HOT, hrText, Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(centerX, height / 4 + 45, Graphics.FONT_XTINY, "BPM", Graphics.TEXT_JUSTIFY_CENTER);

        // Draw speed and distance row
        var speedKmh = _speed * 3.6; // m/s to km/h
        var distKm = _distance / 1000.0;

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        // Speed (left)
        dc.drawText(width / 4, height / 2 + 10, Graphics.FONT_MEDIUM, speedKmh.format("%.1f"), Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(width / 4, height / 2 + 35, Graphics.FONT_XTINY, "km/h", Graphics.TEXT_JUSTIFY_CENTER);

        // Distance (right)
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(width * 3 / 4, height / 2 + 10, Graphics.FONT_MEDIUM, distKm.format("%.2f"), Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(width * 3 / 4, height / 2 + 35, Graphics.FONT_XTINY, "km", Graphics.TEXT_JUSTIFY_CENTER);

        // Extended data row (cadence, power/lap)
        if (_showExtendedData) {
            var bottomY = height - 45;

            // Cadence (left)
            dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
            var cadText = _cadence > 0 ? _cadence.toString() : "--";
            dc.drawText(width / 4, bottomY, Graphics.FONT_SMALL, cadText, Graphics.TEXT_JUSTIFY_CENTER);
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(width / 4, bottomY + 20, Graphics.FONT_XTINY, "CAD", Graphics.TEXT_JUSTIFY_CENTER);

            // Power or Lap (right)
            if (_power > 0) {
                dc.setColor(Graphics.COLOR_ORANGE, Graphics.COLOR_TRANSPARENT);
                dc.drawText(width * 3 / 4, bottomY, Graphics.FONT_SMALL, _power.toString(), Graphics.TEXT_JUSTIFY_CENTER);
                dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
                dc.drawText(width * 3 / 4, bottomY + 20, Graphics.FONT_XTINY, "W", Graphics.TEXT_JUSTIFY_CENTER);
            } else if (_lapCount > 0) {
                dc.setColor(Graphics.COLOR_YELLOW, Graphics.COLOR_TRANSPARENT);
                dc.drawText(width * 3 / 4, bottomY, Graphics.FONT_SMALL, _lapCount.toString(), Graphics.TEXT_JUSTIFY_CENTER);
                dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
                dc.drawText(width * 3 / 4, bottomY + 20, Graphics.FONT_XTINY, "LAP", Graphics.TEXT_JUSTIFY_CENTER);
            }
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
        }

        // Cadence (running cadence or cycling cadence)
        if (sensorInfo has :cadence && sensorInfo.cadence != null) {
            _cadence = sensorInfo.cadence;
        }

        // Power (from power meter if available)
        if (sensorInfo has :power && sensorInfo.power != null) {
            _power = sensorInfo.power;
        }

        WatchUi.requestUpdate();
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
            _lapCount = 0;
            _lapDistance = 0.0;
            _lastLapDistance = 0.0;
            _connectionStatus = "Streaming";
            _timer.start(method(:streamData), _streamInterval, true);
            WatchUi.requestUpdate();
        }
    }

    // Mark a new lap
    function markLap() {
        _lapCount++;
        _lapDistance = _distance - _lastLapDistance;
        _lastLapDistance = _distance;

        // Send lap event to phone
        var lapData = {
            "type" => "lap_event",
            "timestamp" => System.getTimer(),
            "lap_number" => _lapCount,
            "lap_distance" => _lapDistance,
            "total_distance" => _distance,
            "hr" => _heartRate,
            "speed" => _speed
        };

        if (Communications has :transmit) {
            Communications.transmit(lapData, null, new CommListener());
        }

        // Vibrate to confirm
        if (Attention has :vibrate) {
            var vibePattern = [new Attention.VibeProfile(50, 200)];
            Attention.vibrate(vibePattern);
        }

        WatchUi.requestUpdate();
        System.println("Lap " + _lapCount + " marked: " + _lapDistance.format("%.2f") + "m");
    }

    function getLapCount() {
        return _lapCount;
    }

    function setStreamInterval(interval) {
        _streamInterval = interval;
        // Restart timer if streaming
        if (_isStreaming) {
            _timer.stop();
            _timer.start(method(:streamData), _streamInterval, true);
        }
    }

    function getStreamInterval() {
        return _streamInterval;
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
            "distance" => _distance,
            "cadence" => _cadence,
            "power" => _power,
            "activity_type" => _activityType,
            "lap_count" => _lapCount,
            "battery" => System.getSystemStats().battery
        };

        // Send via Communications API to companion app
        if (Communications has :transmit) {
            Communications.transmit(data, null, new CommListener());
        }

        System.println("Streaming: HR=" + _heartRate + " Cad=" + _cadence + " Pwr=" + _power);
    }

    function getCadence() {
        return _cadence;
    }

    function getPower() {
        return _power;
    }

    function getHeartRate() {
        return _heartRate;
    }

    function getDistance() {
        return _distance;
    }

    function setActivityType(activityType) {
        _activityType = activityType;
    }

    function getActivityType() {
        return _activityType;
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
