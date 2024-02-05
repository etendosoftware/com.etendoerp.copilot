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
MINIMIZED_WINDOW_HEIGHT = 50.5
// Function to adjust window position
function adjustWindowPosition() {
// Maximized window adjust
if (window.maximizeCopilotWindow) {
newLeft = Math.max(0, isc.Page.getWidth() - WINDOW_WIDTH);
newTop = Math.max(0, isc.Page.getHeight() - WINDOW_HEIGHT);
window.maximizeCopilotWindow.setLeft(newLeft);
window.maximizeCopilotWindow.setTop(newTop);
}
// Minimized window adjust
if (window.minimizeCopilotWindow) {
newLeft = isc.Page.getWidth() - WINDOW_WIDTH;
newTop = isc.Page.getHeight() - MINIMIZED_WINDOW_HEIGHT;
window.minimizeCopilotWindow.setLeft(newLeft);
window.minimizeCopilotWindow.setTop(newTop);
}
}
// Function to toggle between maximized and minimized window
window.toggleWindows = function() {
if (window.maximizeCopilotWindow && window.maximizeCopilotWindow.isVisible()) {
window.maximizeCopilotWindow.hide();
if (window.minimizeCopilotWindow) {
window.minimizeCopilotWindow.show();
adjustWindowPosition();
}
} else {
window.maximizeCopilotWindow.show();
if (window.minimizeCopilotWindow) {
window.minimizeCopilotWindow.hide();
}
}
};
// Browser resize event to adjust window position
window.addEventListener('resize', adjustWindowPosition);
// Function to close Copilot windows
window.closeCopilotWindow = function() {
if (window.maximizeCopilotWindow) {
window.maximizeCopilotWindow.destroy();
window.maximizeCopilotWindow = null;
}
if (window.minimizeCopilotWindow) {
window.minimizeCopilotWindow.destroy();
window.minimizeCopilotWindow = null;
}
}
// Create window for Copilot
if (!window.maximizeCopilotWindow) {
window.maximizeCopilotWindow = isc.Window.create({
width: WINDOW_WIDTH,
styleName: 'widgetContainer',
height: WINDOW_HEIGHT,
left: isc.Page.getWidth() - WINDOW_WIDTH,
top: isc.Page.getHeight() - WINDOW_HEIGHT,
canDragReposition: true,
headerProperties: {
height: "0px",
},
backgroundColor: LIGHT_GRAY_COLOR,
items: [
isc.HTMLPane.create({
width: "100%",
height: "100%",
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
  </style>
</head>

<body>
  <div class="container-header">
    <div class="content-header">
      <img class="copilot-logotype" src="web/images/copilot.png" alt="Logo Copilot">
      <span class="copilot-title">Copilot</span>
    </div>
    <div class="action-buttons-container">
      <img class="icon-button" onclick='window.toggleWindows()' src="web/images/minimize.svg" alt="Minimize button">
      <img class="icon-button" onclick="window.closeCopilotWindow()" src="web/images/close.svg" alt="Close button">
    </div>
  </div>
  <iframe width="100%" height="585px" src="web/com.etendoerp.copilot.dist" title="Copilot Chat" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
</body>

</html>
`
})]
,
});
}
// Create a window when Copilot is minimized
if (!window.minimizeCopilotWindow) {
window.minimizeCopilotWindow = isc.Window.create({
title: "Window 2",
width: WINDOW_WIDTH,
styleName: 'widgetContainer',
height: 50.5,
canDragReposition: true,
canDragResize: true,
headerProperties: {
height: "0px",
},
items: [
isc.HTMLPane.create({
width: "100%",
height: "100%",
backgroundColor: WHITE_COLOR,
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
    font-size: 1.3rem;
    font-weight: bold;
    color: GRAY_COLOR;
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
      <img class="icon-button" onclick='window.toggleWindows()' src="web/images/maximize.svg" alt="Maximize button">
      <img class="icon-button" onclick="window.closeCopilotWindow()" src="web/images/close.svg" alt="Close button">
    </div>
  </div>
</body>

</html>
`
})
]
});
}
// Initial Toggle Logic
window.maximizeCopilotWindow.show();
if (window.minimizeCopilotWindow && window.minimizeCopilotWindow.isVisible()) {
window.minimizeCopilotWindow.hide();
}
isc.Page.setEvent("resize", adjustWindowPosition);
adjustWindowPosition();
}
})