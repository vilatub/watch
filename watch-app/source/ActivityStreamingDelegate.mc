using Toybox.WatchUi;
using Toybox.System;

class ActivityStreamingDelegate extends WatchUi.BehaviorDelegate {

    private var _view;

    function initialize(view) {
        BehaviorDelegate.initialize();
        _view = view;
    }

    // Handle select button (start/stop)
    function onSelect() {
        if (_view.isStreaming()) {
            _view.stopStreaming();
        } else {
            _view.startStreaming();
        }
        return true;
    }

    // Handle back button
    function onBack() {
        if (_view.isStreaming()) {
            // Show confirmation dialog before exiting
            var dialog = new WatchUi.Confirmation("Stop streaming?");
            WatchUi.pushView(dialog, new StopConfirmationDelegate(_view), WatchUi.SLIDE_UP);
            return true;
        }
        return false; // Allow default back behavior (exit app)
    }

    // Handle menu button - show settings
    function onMenu() {
        var menu = new ActivityStreamingMenu();
        WatchUi.pushView(menu, new ActivityStreamingMenuDelegate(_view), WatchUi.SLIDE_UP);
        return true;
    }

    // Handle key press - lap button
    function onKey(keyEvent) {
        var key = keyEvent.getKey();

        // Back button for lap (common Garmin pattern)
        if (key == WatchUi.KEY_LAP || key == WatchUi.KEY_ENTER) {
            if (_view.isStreaming()) {
                _view.markLap();
                return true;
            }
        }

        return false;
    }

    // Handle swipe up - start streaming
    function onSwipe(swipeEvent) {
        if (swipeEvent.getDirection() == WatchUi.SWIPE_UP) {
            if (!_view.isStreaming()) {
                _view.startStreaming();
            }
            return true;
        } else if (swipeEvent.getDirection() == WatchUi.SWIPE_DOWN) {
            if (_view.isStreaming()) {
                _view.stopStreaming();
            }
            return true;
        }
        return false;
    }
}

class StopConfirmationDelegate extends WatchUi.ConfirmationDelegate {

    private var _view;

    function initialize(view) {
        ConfirmationDelegate.initialize();
        _view = view;
    }

    function onResponse(response) {
        if (response == WatchUi.CONFIRM_YES) {
            _view.stopStreaming();
            WatchUi.popView(WatchUi.SLIDE_DOWN);
            // Exit app
            System.exit();
        }
        return true;
    }
}
