using Toybox.WatchUi;
using Toybox.System;
using Toybox.Lang;

class ActivityStreamingMenu extends WatchUi.Menu2 {

    function initialize() {
        Menu2.initialize({:title=>"Settings"});

        // Add menu items
        addItem(new WatchUi.MenuItem("Stream Interval", getIntervalLabel(), :interval, null));
        addItem(new WatchUi.MenuItem("Activity Type", getActivityLabel(), :activity, null));
        addItem(new WatchUi.MenuItem("Display Mode", "Detailed", :display, null));
    }

    function getIntervalLabel() {
        var app = Application.getApp();
        var view = app.getView();
        if (view != null) {
            var interval = view.getStreamInterval();
            if (interval == 1000) {
                return "1 second";
            } else if (interval == 3000) {
                return "3 seconds";
            } else if (interval == 5000) {
                return "5 seconds";
            } else if (interval == 10000) {
                return "10 seconds";
            }
        }
        return "3 seconds";
    }

    function getActivityLabel() {
        var app = Application.getApp();
        var view = app.getView();
        if (view != null) {
            var activityType = view.getActivityType();
            if (activityType.equals("running")) {
                return "Running";
            } else if (activityType.equals("cycling")) {
                return "Cycling";
            } else if (activityType.equals("walking")) {
                return "Walking";
            } else if (activityType.equals("hiking")) {
                return "Hiking";
            } else if (activityType.equals("swimming")) {
                return "Swimming";
            }
        }
        return "Running";
    }
}

class ActivityStreamingMenuDelegate extends WatchUi.Menu2InputDelegate {

    private var _view;

    function initialize(view) {
        Menu2InputDelegate.initialize();
        _view = view;
    }

    function onSelect(item) {
        var id = item.getId();

        if (id == :interval) {
            // Show interval sub-menu
            var menu = new IntervalMenu();
            WatchUi.pushView(menu, new IntervalMenuDelegate(_view), WatchUi.SLIDE_LEFT);
        } else if (id == :activity) {
            // Show activity type sub-menu
            var menu = new ActivityTypeMenu();
            WatchUi.pushView(menu, new ActivityTypeMenuDelegate(_view), WatchUi.SLIDE_LEFT);
        } else if (id == :display) {
            // Toggle display mode - could add implementation later
        }

        return true;
    }

    function onBack() {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }
}

// Interval selection sub-menu
class IntervalMenu extends WatchUi.Menu2 {

    function initialize() {
        Menu2.initialize({:title=>"Interval"});

        addItem(new WatchUi.MenuItem("1 second", "Fast updates", :int_1s, null));
        addItem(new WatchUi.MenuItem("3 seconds", "Balanced", :int_3s, null));
        addItem(new WatchUi.MenuItem("5 seconds", "Battery saver", :int_5s, null));
        addItem(new WatchUi.MenuItem("10 seconds", "Low power", :int_10s, null));
    }
}

class IntervalMenuDelegate extends WatchUi.Menu2InputDelegate {

    private var _view;

    function initialize(view) {
        Menu2InputDelegate.initialize();
        _view = view;
    }

    function onSelect(item) {
        var id = item.getId();

        if (id == :int_1s) {
            _view.setStreamInterval(1000);
        } else if (id == :int_3s) {
            _view.setStreamInterval(3000);
        } else if (id == :int_5s) {
            _view.setStreamInterval(5000);
        } else if (id == :int_10s) {
            _view.setStreamInterval(10000);
        }

        // Go back to main menu
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }

    function onBack() {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }
}

// Activity type selection sub-menu
class ActivityTypeMenu extends WatchUi.Menu2 {

    function initialize() {
        Menu2.initialize({:title=>"Activity"});

        addItem(new WatchUi.MenuItem("Running", null, :running, null));
        addItem(new WatchUi.MenuItem("Cycling", null, :cycling, null));
        addItem(new WatchUi.MenuItem("Walking", null, :walking, null));
        addItem(new WatchUi.MenuItem("Hiking", null, :hiking, null));
        addItem(new WatchUi.MenuItem("Swimming", null, :swimming, null));
        addItem(new WatchUi.MenuItem("Other", null, :other, null));
    }
}

class ActivityTypeMenuDelegate extends WatchUi.Menu2InputDelegate {

    private var _view;

    function initialize(view) {
        Menu2InputDelegate.initialize();
        _view = view;
    }

    function onSelect(item) {
        var id = item.getId();

        if (id == :running) {
            _view.setActivityType("running");
        } else if (id == :cycling) {
            _view.setActivityType("cycling");
        } else if (id == :walking) {
            _view.setActivityType("walking");
        } else if (id == :hiking) {
            _view.setActivityType("hiking");
        } else if (id == :swimming) {
            _view.setActivityType("swimming");
        } else if (id == :other) {
            _view.setActivityType("other");
        }

        // Go back to main view
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }

    function onBack() {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }
}
