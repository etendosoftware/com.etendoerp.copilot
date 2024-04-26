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
BLUE_COLOR = "#202452"
BUTTON_WIDTH = 80
BUTTON_HEIGHT = 30
WINDOW_WIDTH = 425
WINDOW_HEIGHT = 650
MAXIMIZED_WINDOW_HEIGHT = 650
MAXIMIZED_WINDOW_WIDTH = 425
MINIMIZED_WINDOW_HEIGHT = 48
MINIMIZED_WINDOW_WIDTH = 120
// Function to adjust window position
function adjustFullScreenWindowPosition() {
var reactIframe = document.getElementById('react-iframe');
var reactDoc = reactIframe.contentDocument || reactIframe.contentWindow.document;
var iframeSelector = reactDoc.getElementById('iframe-selector');
var iframeContainer = reactDoc.getElementById('iframe-container');
iframeSelector.classList.add("iframe-selector-full-screen");
iframeContainer.classList.add("iframe-container-full-screen");
//aa
var imgElement = document.getElementById('maximizeIcon');
imgElement.src = "web/images/maximize.svg";
window.copilotWindow.setLeft(0);
window.copilotWindow.setTop(0);
window.copilotWindow.setWidth(isc.Page.getWidth());
window.copilotWindow.setHeight(isc.Page.getHeight());
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
newLeft = isc.Page.getWidth() - MINIMIZED_WINDOW_WIDTH;
newTop = isc.Page.getHeight() - MINIMIZED_WINDOW_HEIGHT;
window.copilotWindow.setLeft(newLeft);
window.copilotWindow.setTop(newTop);
window.copilotWindow.setWidth(MINIMIZED_WINDOW_WIDTH);
window.copilotWindow.setHeight(MINIMIZED_WINDOW_HEIGHT);
}
function adjustMaximizeWindowPosition() {
var widget = document.querySelector('.widgetContainer');
widget.style.setProperty('border', '1px solid #666', 'important');
var reactIframe = document.getElementById('react-iframe');
var reactDoc = reactIframe.contentDocument || reactIframe.contentWindow.document;
if (reactDoc) {
var iframeSelector = reactDoc.getElementById('iframe-selector');
var iframeContainer = reactDoc.getElementById('iframe-container');
if (iframeContainer && iframeSelector) {
console.log('hello')
iframeSelector.classList.remove("iframe-selector-full-screen");
iframeContainer.classList.remove("iframe-container-full-screen");
}
}
var body = document.getElementById('chatBody');
body.style.display = 'flex';
var imgElement = document.getElementById('maximizeIcon');
imgElement.src = "web/images/maximize-2.svg";
var button = document.getElementById('button-minimize');
button.style.display = 'none';
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
            width: 2rem;
            height: 2rem;
            cursor: pointer;
            border-radius: 0.5rem;
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
            <div class="container-header">
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
            <iframe id='react-iframe' style="display: block; width: 100%; flex:1" src="web/com.etendoerp.copilot.dist" title="Copilot Chat" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
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
    }
    })