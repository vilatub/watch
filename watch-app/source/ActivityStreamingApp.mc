using Toybox.Application;
using Toybox.WatchUi;
using Toybox.System;

class ActivityStreamingApp extends Application.AppBase {

    private var _view;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state) {
        System.println("Activity Streaming App started");
    }

    function onStop(state) {
        System.println("Activity Streaming App stopped");
    }

    function getInitialView() {
        _view = new ActivityStreamingView();
        var delegate = new ActivityStreamingDelegate(_view);
        return [_view, delegate];
    }

    function getView() {
        return _view;
    }
}
