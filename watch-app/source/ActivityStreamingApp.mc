using Toybox.Application;
using Toybox.WatchUi;
using Toybox.System;

class ActivityStreamingApp extends Application.AppBase {

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
        var view = new ActivityStreamingView();
        var delegate = new ActivityStreamingDelegate(view);
        return [view, delegate];
    }
}
