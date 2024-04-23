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
click: function() {
// Check if the window is already open
if (window.myWindow && window.myWindow.isVisible()) {
// If it is already open, simply return without doing anything
return;
}
// Define constants for commonly used values
WHITE_COLOR ="#FFFFFF"
GRAY_COLOR = "#666666"
LIGHT_GRAY_COLOR = "#F2F5F9"
LIGHT_GRAY_COLOR_200 = "#EFF1F7"
BUTTON_WIDTH = 80
BUTTON_HEIGHT = 30
WINDOW_WIDTH = 425
WINDOW_HEIGHT = 650
MAXIMIZED_WINDOW_HEIGHT = 650
MAXIMIZED_WINDOW_WIDTH = 425
MINIMIZED_WINDOW_HEIGHT = 51
MINIMIZED_WINDOW_WIDTH = 425
// Function to adjust window position
function adjustFullScreenWindowPosition() {
var iframe = document.getElementById('chatBody');
iframe.style.display = 'block';
var imgElement = document.getElementById('maximizeIcon');
imgElement.src = "web/images/minimize.svg";
window.copilotWindow.setLeft(0);
window.copilotWindow.setTop(0);
window.copilotWindow.setWidth(isc.Page.getWidth());
window.copilotWindow.setHeight(isc.Page.getHeight());
}
function adjustMinimizeWindowPosition() {
var iframe = document.getElementById('chatBody');
iframe.style.display = 'none';
var imgElement = document.getElementById('maximizeIcon');
imgElement.src = "web/images/maximize.svg";
window.copilotWindow.setHeight(MINIMIZED_WINDOW_HEIGHT);
WINDOW_HEIGHT = MINIMIZED_WINDOW_HEIGHT
newLeft = isc.Page.getWidth() - MINIMIZED_WINDOW_WIDTH;
newTop = isc.Page.getHeight() - MINIMIZED_WINDOW_HEIGHT;
window.copilotWindow.setLeft(newLeft);
window.copilotWindow.setTop(newTop);
window.copilotWindow.setWidth(MINIMIZED_WINDOW_WIDTH);
window.copilotWindow.setHeight(MINIMIZED_WINDOW_HEIGHT);
}
function adjustMaximizeWindowPosition() {
var iframe = document.getElementById('chatBody');
iframe.style.display = 'block';
var imgElement = document.getElementById('maximizeIcon');
imgElement.src = "web/images/minimize.svg";
window.copilotWindow.setHeight(MAXIMIZED_WINDOW_HEIGHT);
WINDOW_HEIGHT = MAXIMIZED_WINDOW_HEIGHT;
newLeft = Math.max(0, isc.Page.getWidth() - MAXIMIZED_WINDOW_WIDTH);
newTop = Math.max(0, isc.Page.getHeight() - MAXIMIZED_WINDOW_HEIGHT);
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
} else if (window.copilotWindow.height < MAXIMIZED_WINDOW_HEIGHT){
    adjustMinimizeWindowPosition();
    } else{
    adjustFullScreenWindowPosition();
    }
    }
    // Function to toggle between maximized and minimized window
    window.handleMaximizeWindow=function() {
    if(window.copilotWindow.height===MINIMIZED_WINDOW_HEIGHT){
    adjustMaximizeWindowPosition();
    } else {
    adjustMinimizeWindowPosition();
    }
    }
    window.handleFullScreenWindow=function() {
    if(window.copilotWindow.height <=MAXIMIZED_WINDOW_HEIGHT){
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
            width: 2rem;
            height: 2rem;
            cursor: pointer;
            border-radius: 0.5rem;
        }

        .close-button-container {
            padding: 0.3rem;
            display: flex;
            justify-content: center;
            aling-items: center;
            border-radius: 0.5rem;
            cursor: pointer;
        }

        .close-button-container:hover {
            background-color: LIGHT_GRAY_COLOR_200;
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
            background-color: white;
        }

        .content-header {
            display: flex;
            gap: 0.75rem;
            align-items: center;
            padding-top: 0.5rem;
            padding-bottom: 0.5rem;
        }

        .copilot-title {
            font-size: 1.5rem;
            font-weight: bold;
            color: GRAY_COLOR;
        }

        .normal {
            display: flex;
            flex-direction: column;
        }
        </style>
    </head>

    <body>
        <div class="container-header">
            <div class="content-header">
                <img class="copilot-logotype" src="web/images/copilot.png" alt="Logo Copilot">
                <span class="copilot-title">Copilot</span>
            </div>
            <div class="action-buttons-container">
                <img class="icon-button full" onclick="window.handleFullScreenWindow()" src="web/images/full-screen.svg" alt="F">
                <img id="maximizeIcon" class="icon-button max" onclick="window.handleMaximizeWindow()" src="web/images/minimize.svg" alt="M">
                <img class="icon-button close" onclick="window.closeCopilotWindow()" src="web/images/close.svg" alt="C">
            </div>
        </div>
        <iframe id="chatBody" style="display: block; width: 100%; flex:1" src="web/com.etendoerp.copilot.dist" title="Copilot Chat" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
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
    }
    })