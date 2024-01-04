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
    
    // Define a function to adjust the window position
    function adjustWindowPosition() {
      var newLeft = Math.max(0, isc.Page.getWidth() - WINDOW_WIDTH);
      var newTop = Math.max(0, isc.Page.getHeight() - WINDOW_HEIGHT);
      myWindow.setLeft(newLeft);
      myWindow.setTop(newTop);
    }

    // Define constants for commonly used values
    WHITE_COLOR = "#FFFFFF"
    GRAY_COLOR = "#666666"
    BUTTON_WIDTH = 80
    BUTTON_HEIGHT = 30
    WINDOW_WIDTH = 380
    WINDOW_HEIGHT = 450
    
    // Adjust Window Position Function
    adjustWindowPosition = function(win) {
      var newLeft = Math.max(0, isc.Page.getWidth() - win.getWidth());
      var newTop = Math.max(0, isc.Page.getHeight() - win.getHeight());
      win.setLeft(newLeft);
      win.setTop(newTop);
    };
      
    // Define toggleWindows function globally
    window.toggleWindows = function() {
    if (window.maximizeCopilotWindow && window.maximizeCopilotWindow.isVisible()) {
      window.maximizeCopilotWindow.hide();
      if (!window.minimizeCopilotWindow) {
        createWindow2();
      }
      window.minimizeCopilotWindow.show();
      } else {
        if (!window.maximizeCopilotWindow) {
          createWindow1();
        }
        window.maximizeCopilotWindow.show();
        if (window.minimizeCopilotWindow) {
          window.minimizeCopilotWindow.hide();
        }
    º}
    };

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
    backgroundColor: WHITE_COLOR,
    items: [
    isc.HTMLPane.create({
    width: "100%",
    height: "100%",
    contents: `
    <html>
    <head>
      <style>
      .close-button {
        cursor: pointer;
        width: 1.3rem;
        height: 1.3rem;
      }
      .copilot-logotype {
        width: 2.5rem;
        height: 2.5rem;
      }
      .container-header {
        display: flex;
        justify-content: space-between;
        margin: 0 12px;
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

      .action-buttons-container {
        display: flex;
        align-items: center;
        gap: 0.75rem;
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
          <img class="close-button" onclick='window.toggleWindows()' src="web/images/minimize.svg" alt="Botón Cerrar">
          <img class="close-button" onclick="window.maximizeCopilotWindow.hide()" src="web/images/Close.png" alt="Botón Cerrar">
        </div>
      </div>
      <iframe width="100%" height="88.75%" src="https://www.youtube.com/embed/m-2ZMUKVboE?si=ZGsXhMnM8Pg0yLco" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
    </body>

    </html>
    `
    })],
    });
    
    adjustWindowPosition(window.maximizeCopilotWindow);
    }

    // Create a window when Copilot is minimized
    if (!window.minimizeCopilotWindow) {
      window.minimizeCopilotWindow = isc.Window.create({
      title: "Window 2",
      width: WINDOW_WIDTH,
      styleName: 'widgetContainer',
      height: 45.5,
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
        .close-button {
          cursor: pointer;
          width: 1.3rem;
          height: 1.3rem;
        }
        .copilot-logotype {
          width: 2.5rem;
          height: 2.5rem;
        }
        .container-header {
          display: flex;
          justify-content: space-between;
          margin: 0 12px;
          align-items: center;
          background-color: white;
        }
        .content-header {
          display: flex;
          gap: 0.75rem;
          align-items: center;
          padding-top: 0.5rem;
          padding-bottom: 0.5rem;
          background-color: white;
        }
        .copilot-title {
          font-size: 1.3rem;
          font-weight: bold;
          color: GRAY_COLOR;
        }
        .action-buttons-container {
          display: flex;
          align-items: center;
          gap: 0.75rem;
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
            <img class="close-button" onclick='window.toggleWindows()' src="web/images/maximize.svg" alt="Botón Cerrar">
            <img class="close-button" onclick="window.minimizeCopilotWindow.hide()" src="web/images/Close.png" alt="Botón Cerrar">
          </div>
        </div>
      </body>
      </html>
      `
      })
      ]
      });
      adjustWindowPosition(window.minimizeCopilotWindow);
    }

    // Initial Toggle Logic
    if (!window.maximizeCopilotWindow) {
        createWindow1();
    }
    window.maximizeCopilotWindow.show();
    if (window.minimizeCopilotWindow && window.minimizeCopilotWindow.isVisible()) {
        window.minimizeCopilotWindow.hide();
    }
  }
})
