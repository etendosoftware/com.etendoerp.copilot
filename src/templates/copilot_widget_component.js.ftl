/* jslint */
// Create the Navbar Button
isc.Button.create({
baseStyle: 'navBarButton',
title: "Copilot",
class: "OBNavBarTextButton",
textColor: "#FFFFFF",
overflow: "visible",
height: 30,
width: 80,
layoutAlign: "center",
showRollOver: false,
showFocused: false,
showDown: false,
icon: "/etendo/web/images/AI.png",
borderRadius: "8px !important",
iconWidth: 14,
iconHeight: 14,
// Event handler for button click
_handleClick: function (q, assistant_id) {
// Check if the window is already open
if (window.myWindow && window.myWindow.isVisible() && q === null) {
// If it is already open, simply return without doing anything
return;
}
if (q === null) {
q = '';
} else {
q = encodeURI(q)
}
if (assistant_id === null) {
assistant_id = '';
} else {
const idRegex = /^[A-Fa-f0-9]{32}$/;
assistant_id = idRegex.test(assistant_id) ? assistant_id : '';
}
URL = `web/com.etendoerp.copilot.dist/?question=$${"{"}q${"}"}&assistant_id=$${"{"}assistant_id${"}"}`;
// Define constants for commonly used values
LIGHT_GRAY_COLOR = "#F2F5F9"
BUTTON_WIDTH = 80
BUTTON_HEIGHT = 30
WINDOW_WIDTH = 425
WINDOW_HEIGHT = 650
MAXIMIZED_WINDOW_HEIGHT = 650
MAXIMIZED_WINDOW_WIDTH = 425
MINIMIZED_WINDOW_HEIGHT = 55
MINIMIZED_WINDOW_WIDTH = 131
MARGIN_CONTAINER_HORIZONTAL = 1
MARGIN_CONTAINER_VERTICAL = 6
MARGIN_CONTAINER_FULL_SCREEN= 12
MARGIN_CONTAINER_FULL_SCREEN_HORIZONTAL= 13
MARGIN_CONTAINER_FULL_SCREEN_VERTICAL= 18
// Function to adjust window position
function adjustFullScreenWindowPosition() {
var header = document.getElementById('chatHeader');
header.style.backgroundColor = '#F2F5F9';
var reactIframe = document.getElementById('react-iframe');
var reactDoc = reactIframe.contentDocument || reactIframe.contentWindow.document;
if (reactDoc) {
var iframeSelector = reactDoc.getElementById('iframe-selector');
var iframeContainer = reactDoc.getElementById('iframe-container');
var assistantTitle = reactDoc.getElementById('assistant-title');
if (assistantTitle) {
assistantTitle.style.display = 'flex';
}
if (iframeContainer && iframeSelector) {
iframeSelector.classList.add("iframe-selector-full-screen");
iframeContainer.classList.add("iframe-container-full-screen");
}
}
var imgElement = document.getElementById('maximizeIcon');
imgElement.src = "web/images/maximize-2.svg";
window.copilotWindow.setLeft(MARGIN_CONTAINER_FULL_SCREEN);
window.copilotWindow.setTop(MARGIN_CONTAINER_FULL_SCREEN);
window.copilotWindow.setWidth(isc.Page.getWidth() - MARGIN_CONTAINER_FULL_SCREEN_HORIZONTAL);
window.copilotWindow.setHeight(isc.Page.getHeight() - MARGIN_CONTAINER_FULL_SCREEN_VERTICAL);
}
function adjustMinimizeWindowPosition() {
var widget = document.querySelector('.widgetContainer');
widget.style.setProperty('border', '0px solid #666', 'important');
var body = document.getElementById('chatBody');
body.style.display = 'none';
var button = document.getElementById('button-minimize');
button.style.display = 'flex';
window.copilotWindow.setHeight(MINIMIZED_WINDOW_HEIGHT);
WINDOW_HEIGHT = MINIMIZED_WINDOW_HEIGHT;
newLeft = Math.max(0, isc.Page.getWidth() - MINIMIZED_WINDOW_WIDTH - MARGIN_CONTAINER_HORIZONTAL);
newTop = Math.max(0, isc.Page.getHeight() - MINIMIZED_WINDOW_HEIGHT - MARGIN_CONTAINER_VERTICAL);
window.copilotWindow.setLeft(newLeft);
window.copilotWindow.setTop(newTop);
window.copilotWindow.setWidth(MINIMIZED_WINDOW_WIDTH);
window.copilotWindow.setHeight(MINIMIZED_WINDOW_HEIGHT);
}
function adjustMaximizeWindowPosition() {
var header = document.getElementById('chatHeader');
header.style.backgroundColor = '#FFFFFF';
var widget = document.querySelector('.widgetContainer');
widget.style.setProperty('margin', '0px', 'important');
widget.style.setProperty('border', '1px solid #666', 'important');
var reactIframe = document.getElementById('react-iframe');
var reactDoc = reactIframe.contentDocument || reactIframe.contentWindow.document;
if (reactDoc) {
var iframeSelector = reactDoc.getElementById('iframe-selector');
var iframeContainer = reactDoc.getElementById('iframe-container');
var assistantTitle = reactDoc.getElementById('assistant-title');
if (assistantTitle) {
assistantTitle.style.display = 'none';
}
if (iframeContainer && iframeSelector) {
iframeSelector.classList.remove("iframe-selector-full-screen");
iframeContainer.classList.remove("iframe-container-full-screen");
}
}
var body = document.getElementById('chatBody');
body.style.display = 'flex';
var imgElement = document.getElementById('maximizeIcon');
imgElement.src = "web/images/maximize.svg";
var button = document.getElementById('button-minimize');
button.style.display = 'none';
window.copilotWindow.setHeight(MAXIMIZED_WINDOW_HEIGHT);
WINDOW_HEIGHT = MAXIMIZED_WINDOW_HEIGHT;
newLeft = Math.max(0, isc.Page.getWidth() - MAXIMIZED_WINDOW_WIDTH - MARGIN_CONTAINER_HORIZONTAL);
newTop = Math.max(0, isc.Page.getHeight() - MAXIMIZED_WINDOW_HEIGHT - MARGIN_CONTAINER_VERTICAL);
window.copilotWindow.setLeft(newLeft);
window.copilotWindow.setTop(newTop);
window.copilotWindow.setWidth(isc.Page.getWidth());
window.copilotWindow.setHeight(isc.Page.getHeight());
window.copilotWindow.setWidth(MAXIMIZED_WINDOW_WIDTH);
window.copilotWindow.setHeight(MAXIMIZED_WINDOW_HEIGHT);
}
function resizeWindow() {
if(window.copilotWindow.height === MAXIMIZED_WINDOW_HEIGHT){
adjustMaximizeWindowPosition();
} else if (window.copilotWindow.height > MAXIMIZED_WINDOW_HEIGHT){
adjustFullScreenWindowPosition();
} else{
adjustMinimizeWindowPosition();
}
}
// Function to toggle between maximized and minimized window
window.handleMinimize=function() {
adjustMinimizeWindowPosition();
}
window.handleMaximize=function() {
adjustMaximizeWindowPosition();
}
window.handleFullScreenWindow=function() {
if(window.copilotWindow.height===MAXIMIZED_WINDOW_HEIGHT){
adjustFullScreenWindowPosition();
} else {
adjustMaximizeWindowPosition();
}
};
// Browser resize event to adjust window position
window.addEventListener('resize', resizeWindow);
// Function to close Copilot windows
window.closeCopilotWindow=function() {
if (window.copilotWindow) {
window.copilotWindow.destroy();
window.copilotWindow=null;
}
}
// Create window for Copilot
if (!window.copilotWindow) {
window.copilotWindow=isc.Window.create({
width: WINDOW_WIDTH,
styleName: 'widgetContainer' ,
height: WINDOW_HEIGHT,
canDragReposition: true,
headerProperties: {
height: "0px" ,
},
backgroundColor: LIGHT_GRAY_COLOR,
items: [
isc.HTMLPane.create({
width: "100%" ,
height: "100%" ,
contents: `
<html>

<head>
    <style>
    .icon-button {
        width: 24px;
        height: 24px;
        cursor: pointer;
        margin-left: 8px;
    }

    .close-button-container {
        padding: 0.3rem;
        display: flex;
        justify-content: center;
        align-items: center;
        border-radius: 0.5rem;
        cursor: pointer;
    }

    .close-button-container:hover {
        background-color: #EFF1F7;
    }

    .copilot-logotype {
        width: 3rem;
        height: 3rem;
    }

    .container-header {
        display: flex;
        justify-content: space-between;
        padding: 0px 12px;
        align-items: center;
        background-color: #FFFFFF;
        border-bottom: 1px solid #D3D6E1;
        height: 56px;
    }

    .content-header {
        display: flex;
        gap: 0.75rem;
        align-items: center;
        padding-top: 0.5rem;
        padding-bottom: 0.5rem;
        justify-content: center;
    }

    .copilot-title {
        font-size: 1.5rem;
        font-weight: bold;
        color: #666666;
    }

    .copilot-title-minimize {
        font-size: 16px;
        font-weight: bold;
        color: #FFFFFF;
    }

    .normal {
        display: flex;
        flex-direction: column;
    }

    .action-buttons-container {
        align-items: center;
        display: flex;
    }

    .copilot-minimize-button {
        display: none;
        height: 100%;
        width: 100%;
        border-radius: 20px;
        background-color: #202452;
        justify-content: center;
        align-items: center;
        border-style: none;
    }

    .copilot-logotype-minimize {
        width: 32px;
        height: 32px;
        margin-right: 8px;
    }

    #chatBody {
        display: flex;
        flex-direction: column;
        height: 100%;
    }
    </style>
</head>

<body>
    <button onclick="window.handleMaximize()" id="button-minimize" class="copilot-minimize-button">
        <img id="copilote-img-max" class="copilot-logotype-minimize" src="web/images/copilot-min.png" alt="Logo Copilot">
        <span class="copilot-title-minimize">Copilot</span>
    </button>
    <div id="chatBody">
        <div id="chatHeader" class="container-header">
            <div class="content-header">
                <img id="copilote-img-max" class="copilot-logotype" src="web/images/copilot.png" alt="Logo Copilot">
                <span class="copilot-title">Copilot</span>
            </div>
            <div class="action-buttons-container">
                <img class="icon-button" onclick="window.handleMinimize()" src="web/images/minimize.svg" alt="F">
                <img id="maximizeIcon" class="icon-button" onclick="window.handleFullScreenWindow()" src="web/images/maximize.svg" alt="M">
                <img class="icon-button" onclick="window.closeCopilotWindow()" src="web/images/close.svg" alt="C">
            </div>
        </div>
        <iframe id='react-iframe' style="display: block; width: 100%; flex:1" src="$${"{"}URL${"}"}" title="Copilot Chat" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
    </div>
</body>

</html>
`
})]
,
});
}
// Initial Toggle Logic
window.copilotWindow.show();
isc.Page.setEvent("resize", resizeWindow);
adjustMaximizeWindowPosition();
},
open: function(q, assistant_id) {
this._handleClick(q, assistant_id);
},
click: function () {
this._handleClick(null, null);
}
})
