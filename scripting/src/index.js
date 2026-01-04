"use strict";
var savedTracks = [];
if (Storage.exists("bookmarks.json")) {
    savedTracks = JSON.parse(Storage.read("bookmarks.json"));
}
var menuItem = new ContextMenuItem("Add to Bookmarks", "track", function (id) {
    // Spotify is very inconsistent with their capitalization in the context menu, not sure how to capitalize mine
    savedTracks.push(id);
    Storage.write("bookmarks.json", JSON.stringify(savedTracks, null, 2));
});
menuItem.register();
// const ui: ScriptUI = new ScriptUI("bookmarks", "com.mathr81.bookmarksscript");
var button = new SideDrawerItem("Bookmarks", function () {
    // ui.show("bookmark_page");
    // const layout = ui.createLinearLayout();
    // layout.setBackgroundColor(ui.parseColor("#000000"));

    // const text = ui.createTextView();
    // text.setText("Hello World!");
    // text.setGravity(11);
    // text.setTextSize(26);
    
    // layout.addView(text);
    // ui.attachToRoot(layout);

    var uiManager = new ScriptUI("test", "com.mathr81.bookmarksscript");
    var page = uiManager.show("bookmark_page");
    var container = uiManager.findViewById("container", page);

    for (var i = 0; i < 5; i++) {
        var item = uiManager.create("bookmark_item");
        var title = uiManager.findViewById("title_field", item);
        var artist = uiManager.findViewById("artist_field", item);

        title.setText("Track Title " + (i + 1));
        artist.setText("Artist Name " + (i + 1));

        container.addView(item);
    }

    savedTracks.forEach(function (item) {
        console.log(item);
    });
});
button.register();
